package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.FabricatorBlockEntity;
import com.opamb.barotrauma.menu.FabricatorMenu;
import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 加工台：2 格宽 × 3 格高 × 2 格深的多方块（原作 fabricator，模型等比缩到 2.5 格高）。
 *
 * <p>6 段由 {@link Part} 描述：2 列 × 3 行 × 1 层。主方块是
 * <b>下排 · 左列</b>，只有它挂方块实体和 GeckoLib 模型；其余 5 段是
 * 同一方块的隐形附属段。破坏任意一段整台移除，只掉一台。</p>
 *
 * <p>放置：只能放在方块顶面，正面朝玩家；底面 2×2 的四格都必须有实心支撑。</p>
 */
public class FabricatorBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 6 段：上/中/下排 × 左/右列（深度只用 1 格，模型 1.3–1.5 格深略微外露）。 */
    public enum Part implements StringRepresentable {
        LOWER_LEFT("lower_left", 0, 0),
        LOWER_RIGHT("lower_right", 1, 0),
        MIDDLE_LEFT("middle_left", 0, 1),
        MIDDLE_RIGHT("middle_right", 1, 1),
        UPPER_LEFT("upper_left", 0, 2),
        UPPER_RIGHT("upper_right", 1, 2);

        private final String name;
        private final int column;
        private final int row;

        Part(String name, int column, int row) {
            this.name = name;
            this.column = column;
            this.row = row;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public int column() {
            return column;
        }

        public int row() {
            return row;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** 模型缩到 2.5 格高：下两排整格，顶排半格。 */
    private static final VoxelShape LOWER_SHAPE = Shapes.block();
    private static final VoxelShape UPPER_SHAPE = Shapes.box(
            0.0D, 0.0D, 0.0D, 1.0D, 8.0D / 16.0D, 1.0D);

    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 玩家拆非主段时 playerWillDestroy 已经补过主方块掉落，onRemove 不要再补一次。 */
    private static final ThreadLocal<Boolean> MASTER_DROPPED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public FabricatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.LOWER_LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** 主方块位置（下排 · 左列）；任何一段都能反推。 */
    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction axis = state.getValue(FACING).getClockWise();
        Part part = state.getValue(PART);
        return pos.relative(axis, -part.column()).below(part.row());
    }

    /** 某一段相对主方块的坐标。 */
    public static BlockPos partPos(BlockPos master, Direction facing, Part part) {
        Direction axis = facing.getClockWise();
        return master.relative(axis, part.column()).above(part.row());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return null;
        }
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (Part part : Part.values()) {
            BlockPos pos = partPos(master, facing, part);
            if (!level.getBlockState(pos).canBeReplaced(context)) {
                return null;
            }
            if (part.row() == 0) {
                BlockPos below = pos.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.LOWER_LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        if (state.getValue(PART) != Part.LOWER_LEFT) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (Part part : Part.values()) {
            if (part == Part.LOWER_LEFT) {
                continue;
            }
            level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), Block.UPDATE_ALL);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(PART) != Part.LOWER_LEFT) {
            // 玩家拆非主段：无论创造与否都标记 onRemove 不要补掉落。
            MASTER_DROPPED.set(Boolean.TRUE);
            if (!player.isCreative()) {
                BlockPos master = masterPos(pos, state);
                BlockState masterState = level.getBlockState(master);
                if (masterState.getBlock() instanceof FabricatorBlock) {
                    Block.dropResources(masterState, level, master);
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && newState.getBlock() != this && !REMOVING.get()) {
            REMOVING.set(Boolean.TRUE);
            try {
                BlockPos master = masterPos(pos, state);
                BlockEntity masterBe = level.getBlockEntity(master);
                if (masterBe instanceof Container container
                        && (pos.equals(master) || level.getBlockState(master).getBlock() == this)) {
                    Containers.dropContents(level, master, container);
                }
                // 非玩家原因拆掉非主段（爆炸等）：主段会被 removeBlock(false)
                // 无掉落移除，这里补一份方块物品；玩家拆除已在 playerWillDestroy 里补过。
                boolean masterDropped = MASTER_DROPPED.get();
                MASTER_DROPPED.set(Boolean.FALSE);
                if (!masterDropped && state.getValue(PART) != Part.LOWER_LEFT
                        && level.getBlockState(master).getBlock() == this) {
                    Block.dropResources(level.getBlockState(master), level, master);
                }
                Direction facing = state.getValue(FACING);
                for (Part part : Part.values()) {
                    BlockPos partPos = partPos(master, facing, part);
                    if (!partPos.equals(pos) && level.getBlockState(partPos).getBlock() == this) {
                        level.removeBlock(partPos, false);
                    }
                }
            } finally {
                REMOVING.set(Boolean.FALSE);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            if (!(masterState.getBlock() instanceof FabricatorBlock)) {
                return InteractionResult.PASS;
            }
            // 原作加工台是机械维修：手持扳手且耐久低于 80% 先开修理窗口。
            if (level.getBlockEntity(master) instanceof FabricatorBlockEntity fabricator
                    && com.opamb.barotrauma.ship.RepairInteractions.tryOpen(
                            serverPlayer, master, fabricator, false)) {
                return InteractionResult.CONSUME;
            }
            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable(masterState.getBlock().getDescriptionId());
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                        int id, Inventory inventory, Player p) {
                    return new FabricatorMenu(id, inventory, master);
                }
            }, buf -> buf.writeBlockPos(master));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return state.getValue(PART).row() < 2 ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return state.getValue(PART).row() < 2 ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == Part.LOWER_LEFT
                ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.LOWER_LEFT
                ? new FabricatorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != Part.LOWER_LEFT) {
            return null;
        }
        return (lvl, tickPos, tickState, be) -> {
            if (be instanceof FabricatorBlockEntity fabricator) {
                FabricatorBlockEntity.serverTick(lvl, tickPos, tickState, fabricator);
            }
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
