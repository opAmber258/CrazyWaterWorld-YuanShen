package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.BallastPumpBlockEntity;
import com.opamb.barotrauma.menu.PumpMenu;
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
 * 压载泵：3 格宽 × 2 格高 × 1 格深的大型墙面泵，装在压载舱的内壁上。
 *
 * <p>六格多方块：下排三格是泵体，上排三格是管道/阀组占位。只有
 * {@link Part#LOWER_MIDDLE} 挂方块实体和 GeckoLib 模型；其余五格是同一方块的
 * 附属段（渲染隐形），破坏任意一段都会整台移除。</p>
 *
 * <p>控制语义（用户 2026-11-08 拍板）：<b>跟随声纳台垂直指令</b>——舵台给"下潜"
 * 时泵入、给"上浮"时泵出、停车时保持；压载舱水量直接进
 * {@code ShipSimulation.ballastPercent()}，由 {@code VsThruster} 的浮力因子生效。</p>
 *
 * <p>安装方式：贴墙时正面背离墙面；也可以直接立在地面/方块顶上，此时不需要背后
 * 有墙，正面朝玩家。两种方式模型都保持竖直。</p>
 *
 * <p>朝向：{@link #FACING} 是泵正面朝向（贴墙时背离墙面、落地时朝向玩家）。
 * 模型是朝 +Z（南）做的，渲染器把 FACING 取反后再交给 GeckoLib，保证
 * FACING=south 时正面朝南。</p>
 */
public class BallastPumpBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 六段：下排左/中/右 + 上排左/中/右；中下是主方块。 */
    public enum Part implements StringRepresentable {
        LOWER_LEFT("lower_left", 0, 0),
        LOWER_MIDDLE("lower_middle", 1, 0),
        LOWER_RIGHT("lower_right", 2, 0),
        UPPER_LEFT("upper_left", 0, 1),
        UPPER_MIDDLE("upper_middle", 1, 1),
        UPPER_RIGHT("upper_right", 2, 1);

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

    /** 下排是完整的机器体；上排模型只伸进 7/16 格（模型高 1.44 格）。 */
    private static final VoxelShape LOWER_SHAPE = Shapes.block();
    private static final VoxelShape UPPER_SHAPE = Shapes.box(
            0.0D, 0.0D, 0.0D, 1.0D, 7.0D / 16.0D, 1.0D);

    /** 防止 onRemove 递归拆兄弟段时互相调用。 */
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public BallastPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.LOWER_MIDDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** 主方块位置（下排中段）；任何一段都能反推。 */
    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction axis = state.getValue(FACING).getClockWise();
        Part part = state.getValue(PART);
        return pos.relative(axis, -(part.column() - 1)).below(part.row());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (face == Direction.UP) {
            // 落地立放：放在方块顶面上，不要求背后有墙，正面朝玩家。
            Direction facing = context.getHorizontalDirection().getOpposite();
            if (!footprintFree(level, context, pos, facing.getClockWise())) {
                return null;
            }
            return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.LOWER_MIDDLE);
        }
        // 天花板吊装不做；只支持贴墙和落地。
        if (face.getAxis().isVertical()) {
            return null;
        }
        Direction axis = face.getClockWise();
        if (!footprintFree(level, context, pos, axis)) {
            return null;
        }
        for (int row = 0; row < 2; row++) {
            for (int col = -1; col <= 1; col++) {
                BlockPos partPos = pos.relative(axis, col).above(row);
                BlockPos behind = partPos.relative(face.getOpposite());
                if (!level.getBlockState(behind).isFaceSturdy(level, behind, face)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, face).setValue(PART, Part.LOWER_MIDDLE);
    }

    /** 3×2 的六格是否都能被替换（空气/水/可替换方块）。 */
    private static boolean footprintFree(Level level, BlockPlaceContext context, BlockPos master,
                                         Direction axis) {
        for (int row = 0; row < 2; row++) {
            for (int col = -1; col <= 1; col++) {
                BlockPos partPos = master.relative(axis, col).above(row);
                if (!level.getBlockState(partPos).canBeReplaced(context)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        if (state.getValue(PART) != Part.LOWER_MIDDLE) {
            return;
        }
        Direction axis = state.getValue(FACING).getClockWise();
        for (Part part : Part.values()) {
            if (part == Part.LOWER_MIDDLE) {
                continue;
            }
            BlockPos partPos = pos.relative(axis, part.column() - 1).above(part.row());
            level.setBlock(partPos, state.setValue(PART, part), Block.UPDATE_ALL);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 只有主方块进掉落表；打掉附属段时在主方块位置补一份，避免玩家拆不到整台。
        if (!level.isClientSide && state.getValue(PART) != Part.LOWER_MIDDLE && !player.isCreative()) {
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            if (masterState.getBlock() instanceof BallastPumpBlock) {
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
                Direction axis = state.getValue(FACING).getClockWise();
                for (Part part : Part.values()) {
                    BlockPos partPos = master.relative(axis, part.column() - 1).above(part.row());
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
            if (!(masterState.getBlock() instanceof BallastPumpBlock)) {
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
                    return new PumpMenu(id, inventory, master);
                }
            }, buf -> buf.writeBlockPos(master));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return state.getValue(PART).row() == 0 ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return state.getValue(PART).row() == 0 ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 主方块交给 GeckoLib BER；其余段纯隐形占位。
        return state.getValue(PART) == Part.LOWER_MIDDLE
                ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.LOWER_MIDDLE
                ? new BallastPumpBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != Part.LOWER_MIDDLE) {
            return null;
        }
        return (lvl, tickPos, tickState, be) -> {
            if (be instanceof com.opamb.barotrauma.blockentity.DrainPumpBlockEntity pump) {
                com.opamb.barotrauma.blockentity.DrainPumpBlockEntity.serverTick(
                        lvl, tickPos, tickState, pump);
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
