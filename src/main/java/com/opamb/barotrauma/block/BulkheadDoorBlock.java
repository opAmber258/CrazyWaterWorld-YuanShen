package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.BulkheadDoorBlockEntity;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.registry.ModSounds;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.jetbrains.annotations.Nullable;

/**
 * 竖向舱壁门：1 格宽 × 2 格高，薄板居中（类似栅栏门的占地），整扇向右滑入墙内。
 *
 * <p>门板只有 1 像素厚，中心在方块 z=8；关闭时碰撞体只覆盖这块薄板，打开后清空。
 * 观察窗是门板上的真实缺口，所以从两面都能看穿。模型由 GeckoLib 渲染，
 * 方块本身返回 {@link RenderShape#ENTITYBLOCK_ANIMATED}，不再画方块模型。</p>
 */
public class BulkheadDoorBlock extends BaseEntityBlock {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public enum Part implements StringRepresentable {
        LOWER("lower"), UPPER("upper");

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
     * 门板和门框的拾取形状：门板 12px 宽、1px 厚，居中在方块中间（和模型一致），
     * 两侧 2px 是固定门框。只用于射线/右键，不用于碰撞。
     *
     * <p>必须按 {@code FACING} 旋转：模型由 GeckoLib 按 facing 转，形状用固定朝向写
     * 会让东/西向的门只剩中心一小块可点，门框点不到（"点门中间才能开关"）。</p>
     */
    private static final VoxelShape[][] PANEL_SHAPES = new VoxelShape[2][4];
    private static final VoxelShape[][] FRAME_SHAPES = new VoxelShape[2][4];

    /**
     * 门关着时的碰撞板：整格宽、全高、1px 厚（门板所在平面）。
     * 比门板宽，是为了让 MC 的碰撞解算只沿门法线推人，不从侧边挤人。
     */
    private static final VoxelShape[] COLLISION_PLATES = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int i = facing.get2DDataValue();
            // 左右立柱（沿门宽的两端）+ 本段的下槛/上槛，厚度与模型一致：门框 z -1..1。
            VoxelShape posts = Shapes.or(
                    DoorShapes.slab(facing, -8.0D, -6.0D, -1.0D, 1.0D, 0.0D, 16.0D),
                    DoorShapes.slab(facing, 6.0D, 8.0D, -1.0D, 1.0D, 0.0D, 16.0D));
            FRAME_SHAPES[Part.LOWER.ordinal()][i] = Shapes.or(posts,
                    DoorShapes.slab(facing, -6.0D, 6.0D, -1.0D, 1.0D, 0.0D, 2.0D));
            FRAME_SHAPES[Part.UPPER.ordinal()][i] = Shapes.or(posts,
                    DoorShapes.slab(facing, -6.0D, 6.0D, -1.0D, 1.0D, 14.0D, 16.0D));
            PANEL_SHAPES[Part.LOWER.ordinal()][i] =
                    DoorShapes.slab(facing, -6.0D, 6.0D, -0.5D, 0.5D, 0.0D, 16.0D);
            PANEL_SHAPES[Part.UPPER.ordinal()][i] = PANEL_SHAPES[Part.LOWER.ordinal()][i];
            COLLISION_PLATES[i] = DoorShapes.slab(facing, -8.0D, 8.0D, -0.5D, 0.5D, 0.0D, 16.0D);
        }
    }

    private static VoxelShape panelShape(BlockState state) {
        return PANEL_SHAPES[state.getValue(PART).ordinal()][state.getValue(FACING).get2DDataValue()];
    }

    private static VoxelShape collisionPlate(BlockState state) {
        return COLLISION_PLATES[state.getValue(FACING).get2DDataValue()];
    }

    /** 固定门框（左右立柱 + 本段的下槛或上槛），开门后只留它可点。 */
    private static VoxelShape frameShape(BlockState state) {
        return FRAME_SHAPES[state.getValue(PART).ordinal()][state.getValue(FACING).get2DDataValue()];
    }

    public BulkheadDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(OPEN, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, FACING, PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(PART, Part.LOWER)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        if (state.getValue(PART) != Part.LOWER) {
            return;
        }
        level.setBlock(pos.above(), state.setValue(PART, Part.UPPER), Block.UPDATE_ALL);
        if (!level.isClientSide) {
            // 新门格不在旧 hull 里时，后续开合不会触发重扫；放置时主动刷新一次。
            com.opamb.barotrauma.ship.ShipCores.refreshNearby(level, pos);
        }
    }

    /** 主段（下半段）位置：上半段返回下方那格，下半段返回自身。 */
    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.LOWER ? pos : pos.below();
    }

    private static BlockPos lowerPos(Level level, BlockPos pos, BlockState state) {
        BlockPos lower = pos.below(state.getValue(PART).ordinal());
        BlockState lowerState = level.getBlockState(lower);
        if (lowerState.getBlock() instanceof BulkheadDoorBlock
                && lowerState.getValue(PART) == Part.LOWER) {
            return lower;
        }
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos lower = lowerPos(level, pos, state);
            if (lower == null) {
                return InteractionResult.PASS;
            }
            if (level.getBlockEntity(lower) instanceof BulkheadDoorBlockEntity door) {
                boolean wrench = player.getMainHandItem().is(ModItems.WRENCH.get())
                        || player.getOffhandItem().is(ModItems.WRENCH.get());
                // 原作门也带 Repairable 的 GUI 帧：手里有扳手就开修理窗口（满耐久
                // 显示“无需修理”），空手右键才是开关门。
                if (wrench && player instanceof ServerPlayer serverPlayer
                        && com.opamb.barotrauma.ship.RepairInteractions.tryOpen(
                                serverPlayer, lower, door, true)) {
                    return InteractionResult.CONSUME;
                }
                if (door.condition() <= 0.0D) {
                    player.displayClientMessage(
                            Component.translatable("gui.barotrauma.bulkhead_door.broken"), true);
                    return InteractionResult.CONSUME;
                }
                if (player.isShiftKeyDown()) {
                    door.cycleAutomation();
                    player.displayClientMessage(
                            Component.translatable(door.automation().translationKey()), true);
                    return InteractionResult.CONSUME;
                }
            }
            setOpen(level, pos, state, !state.getValue(OPEN));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 门的内置自动化/手动开关共用：设置整扇门并重扫几何。 */
    public static boolean setOpen(Level level, BlockPos pos, BlockState state, boolean open) {
        BlockPos lower = lowerPos(level, pos, state);
        if (lower == null) {
            return false;
        }
        if (level.getBlockEntity(lower) instanceof BulkheadDoorBlockEntity door
                && door.condition() <= 0.0D) {
            return false;
        }
        for (int offset = 0; offset < 2; offset++) {
            BlockPos partPos = lower.above(offset);
            BlockState partState = level.getBlockState(partPos);
            if (partState.getBlock() instanceof BulkheadDoorBlock) {
                level.setBlock(partPos, partState.setValue(OPEN, open), Block.UPDATE_ALL);
            }
        }
        // 原版门音：Door1..4 四段随机（见 sounds.json）。
        level.playSound(null, pos, ModSounds.DOOR_TOGGLE.get(),
                SoundSource.BLOCKS, 1.0F, 0.95F + level.random.nextFloat() * 0.1F);
        // 方块船：重扫世界几何；物理船：方块类型没变，必须显式重扫船坞几何。
        if (level instanceof ServerLevel server) {
            // 按船体归属找核心再重扫：固定 16 格扫描会漏掉离核心更远的门。
            if (!VsState.isPhysical(server, pos)) {
                com.opamb.barotrauma.blockentity.ShipCoreBlockEntity core =
                        com.opamb.barotrauma.blockentity.DrainPumpBlockEntity
                                .resolveCore(server, pos);
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
        return createTickerHelper(type, ModBlockEntities.BULKHEAD_DOOR.get(),
                BulkheadDoorBlockEntity::serverTick);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos lower = lowerPos(level, pos, state);
            if (lower != null) {
                BlockState lowerState = level.getBlockState(lower);
                if (state.getValue(PART) != Part.LOWER && !player.isCreative()) {
                    Block.dropResources(lowerState, level, lower);
                }
                for (int offset = 0; offset < 2; offset++) {
                    BlockPos partPos = lower.above(offset);
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
        // 整格宽、全高、1px 厚的碰撞板：宽度覆盖整格后，MC 解算只会沿门法线
        // 把人推回去，不会从侧边挤人；门板仍居中，玩家能贴到门板前停下。
        return state.getValue(OPEN) ? Shapes.empty() : collisionPlate(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        // 拾取/右键只看门板和门框：开门后门洞中间不挡射线（能打怪、不会左键打碎门）。
        VoxelShape frame = frameShape(state);
        return state.getValue(OPEN) ? frame : Shapes.or(panelShape(state), frame);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == Part.LOWER
                ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.LOWER
                ? new BulkheadDoorBlockEntity(pos, state) : null;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return !state.getValue(OPEN);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN) ? 0 : super.getLightBlock(state, level, pos);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN) || super.propagatesSkylightDown(state, level, pos);
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
