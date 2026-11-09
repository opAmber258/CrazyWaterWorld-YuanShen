package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.ReactorBlockEntity;
import com.opamb.barotrauma.menu.ReactorMenu;
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
 * 核反应堆：3 格宽 × 2.5 格高 × 2 格深的落地多方块（框架 3×3×2，顶排半格）。
 *
 * <p>18 段由 {@link Part} 描述：3 列 × 3 行 × 2 层（前层/后层）。主方块是
 * <b>前层 · 下排 · 中列</b>，只有它挂方块实体和 GeckoLib 模型；其余 17 段是
 * 同一方块的隐形附属段。破坏任意一段整台移除，只掉一台。</p>
 *
 * <p>放置：只能放在方块顶面（用户拍板落地安装），正面朝玩家；底面 3×2 的六格
 * 都必须有实心支撑。模型在 Blockbench 里是 3×2.5×2 格，正面朝 +Z；渲染器把
 * {@link #FACING} 取反后交给 GeckoLib，和压载泵的处理一致。</p>
 */
public class ReactorBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 18 段：前/后层 × 上/中/下排 × 左/中/右列。 */
    public enum Part implements StringRepresentable {
        FRONT_LOWER_LEFT("front_lower_left", 0, 0, 0),
        FRONT_LOWER_MIDDLE("front_lower_middle", 1, 0, 0),
        FRONT_LOWER_RIGHT("front_lower_right", 2, 0, 0),
        FRONT_MIDDLE_LEFT("front_middle_left", 0, 1, 0),
        FRONT_MIDDLE_MIDDLE("front_middle_middle", 1, 1, 0),
        FRONT_MIDDLE_RIGHT("front_middle_right", 2, 1, 0),
        FRONT_UPPER_LEFT("front_upper_left", 0, 2, 0),
        FRONT_UPPER_MIDDLE("front_upper_middle", 1, 2, 0),
        FRONT_UPPER_RIGHT("front_upper_right", 2, 2, 0),
        BACK_LOWER_LEFT("back_lower_left", 0, 0, 1),
        BACK_LOWER_MIDDLE("back_lower_middle", 1, 0, 1),
        BACK_LOWER_RIGHT("back_lower_right", 2, 0, 1),
        BACK_MIDDLE_LEFT("back_middle_left", 0, 1, 1),
        BACK_MIDDLE_MIDDLE("back_middle_middle", 1, 1, 1),
        BACK_MIDDLE_RIGHT("back_middle_right", 2, 1, 1),
        BACK_UPPER_LEFT("back_upper_left", 0, 2, 1),
        BACK_UPPER_MIDDLE("back_upper_middle", 1, 2, 1),
        BACK_UPPER_RIGHT("back_upper_right", 2, 2, 1);

        private final String name;
        private final int column;
        private final int row;
        private final int layer;

        Part(String name, int column, int row, int layer) {
            this.name = name;
            this.column = column;
            this.row = row;
            this.layer = layer;
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

        /** 0=前层（主方块所在层），1=后层。 */
        public int layer() {
            return layer;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** 下两排是完整机器体；顶排模型只占 8/16 格（模型总高 2.5 格）。 */
    private static final VoxelShape LOWER_SHAPE = Shapes.block();
    private static final VoxelShape UPPER_SHAPE = Shapes.box(
            0.0D, 0.0D, 0.0D, 1.0D, 8.0D / 16.0D, 1.0D);

    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ReactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.FRONT_LOWER_MIDDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** 主方块位置（前层 · 下排 · 中列）；任何一段都能反推。 */
    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction axis = state.getValue(FACING).getClockWise();
        Part part = state.getValue(PART);
        return pos.relative(axis, -(part.column() - 1))
                .relative(state.getValue(FACING), part.layer())
                .below(part.row());
    }

    /** 某一段相对主方块的坐标。 */
    public static BlockPos partPos(BlockPos master, Direction facing, Part part) {
        Direction axis = facing.getClockWise();
        return master.relative(axis, part.column() - 1)
                .relative(facing.getOpposite(), part.layer())
                .above(part.row());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 落地安装：只认方块顶面。
        if (context.getClickedFace() != Direction.UP) {
            return null;
        }
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();

        for (Part part : Part.values()) {
            BlockPos partPos = partPos(master, facing, part);
            if (!level.getBlockState(partPos).canBeReplaced(context)) {
                return null;
            }
            if (part.row() == 0) {
                BlockPos below = partPos.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.FRONT_LOWER_MIDDLE);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        if (state.getValue(PART) != Part.FRONT_LOWER_MIDDLE) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (Part part : Part.values()) {
            if (part == Part.FRONT_LOWER_MIDDLE) {
                continue;
            }
            level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), Block.UPDATE_ALL);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 只有主方块进掉落表；打任意附属段时在主方块位置补一份。
        if (!level.isClientSide && state.getValue(PART) != Part.FRONT_LOWER_MIDDLE && !player.isCreative()) {
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            if (masterState.getBlock() instanceof ReactorBlock) {
                Block.dropResources(masterState, level, master);
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
                // 燃料棒装在容器里，和原版箱子一样在 onRemove 掉落，不然挖掉/炸掉
                // 反应堆会把 4 根燃料一起吞掉。此时 BE 还在（LevelChunk 先 onRemove
                // 再 removeBlockEntity）；VS2 装配/拆船会先 removeBlockEntity 再改方块，
                // 这里读不到 BE，所以不会和装配快照里的 NBT 重复生成（见 VsPilot）。
                BlockEntity masterBe = level.getBlockEntity(master);
                if (masterBe instanceof Container container
                        && (pos.equals(master) || level.getBlockState(master).getBlock() == this)) {
                    Containers.dropContents(level, master, container);
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
            if (!(masterState.getBlock() instanceof ReactorBlock)) {
                return InteractionResult.PASS;
            }
            NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable(masterState.getBlock().getDescriptionId());
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                        int id, Inventory inventory, Player p) {
                    return new ReactorMenu(id, inventory, master);
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
        return state.getValue(PART) == Part.FRONT_LOWER_MIDDLE
                ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.FRONT_LOWER_MIDDLE
                ? new ReactorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != Part.FRONT_LOWER_MIDDLE) {
            return null;
        }
        return (lvl, tickPos, tickState, be) -> {
            if (be instanceof ReactorBlockEntity reactor) {
                ReactorBlockEntity.serverTick(lvl, tickPos, tickState, reactor);
            }
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
