package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.HatchBlockEntity;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.registry.ModSounds;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 水平舱口：3 格宽 × 1 格深，铺在上下两层甲板之间，整扇沿长轴滑进旁边的地板。
 *
 * <p>方块 id 仍叫 {@code hatch}，但它是<b>地板舱口</b>而不是墙上的门：门板只有
 * 1 像素厚，贴在方块顶面（地板面）；关着时碰撞也只有这块薄板，打开后清空，
 * 人就从洞口掉到下一层。没有观察窗，也没有玻璃。</p>
 *
 * <p>三段共享 open/facing，只有中间段挂 GeckoLib 方块实体渲染整块 3 格模型。</p>
 */
public class HatchBlock extends BaseEntityBlock {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public enum Part implements StringRepresentable {
        LEFT("left"), MIDDLE("middle"), RIGHT("right");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /**
     * 地板舱口板：整格 footprint，1 px 厚，位于方块中段（y=7.5..8.5）。
     *
     * <p>模型顶点也建在 y=7.5..8.5，渲染器不再做整体抬高，形状和模型对齐。</p>
     */
    private static final VoxelShape PANEL = Shapes.box(
            0.0D, 7.5D / 16.0D, 0.0D,
            1.0D, 8.5D / 16.0D, 1.0D);

    /**
     * 舱口固定的外框（1px 边条）：开门后只剩它，右键它才能关门。
     * 形状跟 FACING 一起转，所以每段单独算。
     */
    private static final VoxelShape[][] RIM_SHAPES = new VoxelShape[3][4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int i = facing.get2DDataValue();
            // 长边方向的两条边条（每段都有）
            VoxelShape across = Shapes.or(
                    DoorShapes.slab(facing, -8.0D, 8.0D, -8.0D, -7.0D, 7.5D, 8.5D),
                    DoorShapes.slab(facing, -8.0D, 8.0D, 7.0D, 8.0D, 7.5D, 8.5D));
            RIM_SHAPES[Part.LEFT.ordinal()][i] = Shapes.or(across,
                    DoorShapes.slab(facing, -8.0D, -7.0D, -7.0D, 7.0D, 7.5D, 8.5D));
            RIM_SHAPES[Part.MIDDLE.ordinal()][i] = across;
            RIM_SHAPES[Part.RIGHT.ordinal()][i] = Shapes.or(across,
                    DoorShapes.slab(facing, 7.0D, 8.0D, -7.0D, 7.0D, 7.5D, 8.5D));
        }
    }

    private static VoxelShape rimShape(Direction facing, Part part) {
        return RIM_SHAPES[Math.max(0, Math.min(2, part.ordinal()))][facing.get2DDataValue()];
    }

    public HatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(OPEN, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.MIDDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, FACING, PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        Direction longAxis = facing.getClockWise();
        if (!level.getBlockState(pos.relative(longAxis.getOpposite())).canBeReplaced(context)
                || !level.getBlockState(pos.relative(longAxis)).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(PART, Part.MIDDLE)
                .setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        if (state.getValue(PART) != Part.MIDDLE) {
            return;
        }
        Direction longAxis = state.getValue(FACING).getClockWise();
        level.setBlock(pos.relative(longAxis.getOpposite()),
                state.setValue(PART, Part.LEFT), Block.UPDATE_ALL);
        level.setBlock(pos.relative(longAxis),
                state.setValue(PART, Part.RIGHT), Block.UPDATE_ALL);
        if (!level.isClientSide) {
            // 新舱口格不在旧 hull 里时，后续开合不会触发重扫；放置时主动刷新一次。
            com.opamb.barotrauma.ship.ShipCores.refreshNearby(level, pos);
        }
    }

    /** 主段（中间段）位置：左右段沿长轴反推；中间段返回自身。 */
    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction longAxis = state.getValue(FACING).getClockWise();
        int offset = state.getValue(PART).ordinal() - 1;
        return pos.relative(longAxis, -offset);
    }

    private static BlockPos middlePos(Level level, BlockPos pos, BlockState state) {
        Direction longAxis = state.getValue(FACING).getClockWise();
        int offset = state.getValue(PART).ordinal() - 1;
        BlockPos middle = pos.relative(longAxis, -offset);
        BlockState middleState = level.getBlockState(middle);
        if (middleState.getBlock() instanceof HatchBlock
                && middleState.getValue(PART) == Part.MIDDLE) {
            return middle;
        }
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos middle = middlePos(level, pos, state);
            if (middle == null) {
                return InteractionResult.PASS;
            }
            if (level.getBlockEntity(middle) instanceof HatchBlockEntity hatch) {
                boolean wrench = player.getMainHandItem().is(ModItems.WRENCH.get())
                        || player.getOffhandItem().is(ModItems.WRENCH.get());
                // 手里有扳手就开修理窗口，空手右键才是开关舱口。
                if (wrench && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                        && com.opamb.barotrauma.ship.RepairInteractions.tryOpen(
                                serverPlayer, middle, hatch, true)) {
                    return InteractionResult.CONSUME;
                }
                if (hatch.condition() <= 0.0D) {
                    player.displayClientMessage(
                            Component.translatable("gui.barotrauma.hatch.broken"), true);
                    return InteractionResult.CONSUME;
                }
                // 同舱壁门：按住右键会每 4 tick 触发一次，必须节流。
                if (!hatch.tryManualUse(level.getGameTime())) {
                    return InteractionResult.CONSUME;
                }
                if (player.isShiftKeyDown()) {
                    hatch.cycleAutomation();
                    player.displayClientMessage(
                            Component.translatable(hatch.automation().translationKey()), true);
                    return InteractionResult.CONSUME;
                }
            }
            boolean open = !state.getValue(OPEN);
            if (setOpen(level, pos, state, open)
                    && level.getBlockEntity(middle) instanceof HatchBlockEntity hatch) {
                if (open) {
                    hatch.holdAfterManualOpen();
                } else {
                    // 手动关闭必须清掉运动保持，否则自动模式会立刻把舱口顶开。
                    hatch.releaseManualHold();
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 舱口的内置自动化/手动开关共用：设置整扇舱口并重扫几何。 */
    public static boolean setOpen(Level level, BlockPos pos, BlockState state, boolean open) {
        BlockPos middle = middlePos(level, pos, state);
        if (middle == null) {
            return false;
        }
        if (level.getBlockEntity(middle) instanceof HatchBlockEntity hatch
                && hatch.condition() <= 0.0D) {
            return false;
        }
        Direction longAxis = state.getValue(FACING).getClockWise();
        for (int offset = -1; offset <= 1; offset++) {
            BlockPos partPos = middle.relative(longAxis, offset);
            BlockState partState = level.getBlockState(partPos);
            if (partState.getBlock() instanceof HatchBlock) {
                level.setBlock(partPos, partState.setValue(OPEN, open), Block.UPDATE_ALL);
            }
        }
        // 原版门音：Door1..4 四段随机（见 sounds.json）。
        level.playSound(null, pos, ModSounds.DOOR_TOGGLE.get(),
                SoundSource.BLOCKS, 1.0F, 0.95F + level.random.nextFloat() * 0.1F);
        if (level instanceof ServerLevel server) {
            // 按船体归属找核心再重扫：固定 16 格扫描会漏掉离核心更远的舱口。
            if (!VsState.isPhysical(server, middle)) {
                com.opamb.barotrauma.blockentity.ShipCoreBlockEntity core =
                        com.opamb.barotrauma.blockentity.DrainPumpBlockEntity
                                .resolveCore(server, middle);
                if (core != null) {
                    core.rescan(server);
                }
            }
            VsState.refreshGeometry(server);
        }
        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.HATCH.get(),
                HatchBlockEntity::serverTick);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos middle = middlePos(level, pos, state);
            if (middle != null) {
                BlockState middleState = level.getBlockState(middle);
                Direction longAxis = state.getValue(FACING).getClockWise();
                if (state.getValue(PART) != Part.MIDDLE && !player.isCreative()) {
                    Block.dropResources(middleState, level, middle);
                }
                for (int offset = -1; offset <= 1; offset++) {
                    BlockPos partPos = middle.relative(longAxis, offset);
                    if (!partPos.equals(pos)) {
                        level.removeBlock(partPos, false);
                    }
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : PANEL;
    }

    /**
     * 打开的舱口算攀爬面：对接井的铁梯上下两道舱口，人要从舱口中间爬过去。
     * 关着的时候不算，否则站在 1px 薄板上会被当成在梯子上。
     */
    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return state.getValue(OPEN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        // 开着的时候只剩外框：门洞不挡射线（能打/放东西），右键外框关门。
        VoxelShape rim = rimShape(state.getValue(FACING), state.getValue(PART));
        return state.getValue(OPEN) ? rim : Shapes.or(PANEL, rim);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == Part.MIDDLE
                ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MIDDLE ? new HatchBlockEntity(pos, state) : null;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return !state.getValue(OPEN);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        // 关着的舱口必须完全挡光：舱口是 1px 薄板，super 只会衰减 1 级，
        // 下层灯光/上层灯光仍然能穿过去；开着才让光从洞口通过。
        return state.getValue(OPEN) ? 0 : 15;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN);
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
