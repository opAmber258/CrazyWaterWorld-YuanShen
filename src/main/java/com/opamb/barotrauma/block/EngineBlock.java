package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.EngineBlockEntity;
import com.opamb.barotrauma.menu.EngineMenu;
import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
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
 * 潜艇引擎：3 格宽 × 2 格高 × 3 格深的落地多方块，18 格。
 *
 * <p>主方块是前层 · 下排 · 中列。机身占前两格深，最后一格是螺旋桨护罩
 * （当前静态占位，不做动画）。正面朝玩家，背面螺旋桨朝船尾。</p>
 *
 * <p>推进由声纳油门统一控制：引擎方块只负责"上报自己在船上、目标力和耗电"，
 * 真正给刚体施加力的是 {@code VsThruster}。船上没有引擎时推进为 0。</p>
 */
public class EngineBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 18 段：3 层（前/中/后）× 2 排（下/上）× 3 列（左/中/右）。 */
    public enum Part implements StringRepresentable {
        FRONT_LOWER_LEFT("front_lower_left", 0, 0, 0),
        FRONT_LOWER_MIDDLE("front_lower_middle", 1, 0, 0),
        FRONT_LOWER_RIGHT("front_lower_right", 2, 0, 0),
        FRONT_UPPER_LEFT("front_upper_left", 0, 1, 0),
        FRONT_UPPER_MIDDLE("front_upper_middle", 1, 1, 0),
        FRONT_UPPER_RIGHT("front_upper_right", 2, 1, 0),
        MIDDLE_LOWER_LEFT("middle_lower_left", 0, 0, 1),
        MIDDLE_LOWER_MIDDLE("middle_lower_middle", 1, 0, 1),
        MIDDLE_LOWER_RIGHT("middle_lower_right", 2, 0, 1),
        MIDDLE_UPPER_LEFT("middle_upper_left", 0, 1, 1),
        MIDDLE_UPPER_MIDDLE("middle_upper_middle", 1, 1, 1),
        MIDDLE_UPPER_RIGHT("middle_upper_right", 2, 1, 1),
        BACK_LOWER_LEFT("back_lower_left", 0, 0, 2),
        BACK_LOWER_MIDDLE("back_lower_middle", 1, 0, 2),
        BACK_LOWER_RIGHT("back_lower_right", 2, 0, 2),
        BACK_UPPER_LEFT("back_upper_left", 0, 1, 2),
        BACK_UPPER_MIDDLE("back_upper_middle", 1, 1, 2),
        BACK_UPPER_RIGHT("back_upper_right", 2, 1, 2);

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

        /** 0=前层（主方块所在），1=中层，2=后层（螺旋桨）。 */
        public int layer() {
            return layer;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final VoxelShape SHAPE = Shapes.block();
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public EngineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.FRONT_LOWER_MIDDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction axis = state.getValue(FACING).getClockWise();
        Part part = state.getValue(PART);
        return pos.relative(axis, -(part.column() - 1))
                .relative(state.getValue(FACING), part.layer())
                .below(part.row());
    }

    public static BlockPos partPos(BlockPos master, Direction facing, Part part) {
        Direction axis = facing.getClockWise();
        return master.relative(axis, part.column() - 1)
                .relative(facing.getOpposite(), part.layer())
                .above(part.row());
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
            BlockPos partPos = partPos(master, facing, part);
            if (!level.getBlockState(partPos).canBeReplaced(context)) {
                return null;
            }
            // 前两层（机身）底面要有支撑；最后一层是螺旋桨护罩，允许悬在船尾外。
            if (part.row() == 0 && part.layer() < 2) {
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
        if (!level.isClientSide && state.getValue(PART) != Part.FRONT_LOWER_MIDDLE && !player.isCreative()) {
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            if (masterState.getBlock() instanceof EngineBlock) {
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
            if (!(masterState.getBlock() instanceof EngineBlock)) {
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
                    return new EngineMenu(id, inventory, master);
                }
            }, buf -> buf.writeBlockPos(master));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return SHAPE;
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
                ? new EngineBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != Part.FRONT_LOWER_MIDDLE) {
            return null;
        }
        return (lvl, tickPos, tickState, be) -> {
            if (be instanceof EngineBlockEntity engine) {
                EngineBlockEntity.serverTick(lvl, tickPos, tickState, engine);
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
