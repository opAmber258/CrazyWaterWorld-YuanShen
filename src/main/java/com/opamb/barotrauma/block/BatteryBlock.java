package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.BatteryBlockEntity;
import com.opamb.barotrauma.menu.BatteryMenu;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ShipCores;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 电池（设备）：墙挂式蓄电方块。
 *
 * <p>模型来自 {@code art/models/battery_20261115}：宽 0.625 格、高 0.969 格、
 * 厚 0.375 格，背面贴墙、正面朝外。屏幕按电量分四档（空/低/半/满），
 * 用 {@link #CHARGE} 方块状态切换贴图。</p>
 */
public class BatteryBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 电量档位：0 空、1 低（1 段）、2 半（2 段）、3 满（3 段）。 */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 3);

    /** 模型本体：x 3..13、y 0..15.5、背面 z=16、正面 z≈10（朝北）。 */
    private static final VoxelShape SHAPE_NORTH = Shapes.box(
            3.0D / 16.0D, 0.0D, 10.0D / 16.0D,
            13.0D / 16.0D, 15.5D / 16.0D, 1.0D);
    private static final VoxelShape SHAPE_SOUTH = Shapes.box(
            3.0D / 16.0D, 0.0D, 0.0D,
            13.0D / 16.0D, 15.5D / 16.0D, 6.0D / 16.0D);
    private static final VoxelShape SHAPE_EAST = Shapes.box(
            0.0D, 0.0D, 3.0D / 16.0D,
            6.0D / 16.0D, 15.5D / 16.0D, 13.0D / 16.0D);
    private static final VoxelShape SHAPE_WEST = Shapes.box(
            10.0D / 16.0D, 0.0D, 3.0D / 16.0D,
            1.0D, 15.5D / 16.0D, 13.0D / 16.0D);

    public BatteryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(CHARGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHARGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(CHARGE, 0);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private static VoxelShape shape(BlockState state) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return shape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return shape(state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof com.opamb.barotrauma.item.TurretBinderItem) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            boolean physical = com.opamb.barotrauma.ship.vs.VsState.active()
                    && com.opamb.barotrauma.ship.vs.VsPilot.isYardBlock(pos);
            BlockPos cell = physical
                    ? com.opamb.barotrauma.ship.vs.VsPilot.toAssembly(pos) : pos;
            net.minecraft.world.phys.Vec3 center = physical
                    ? com.opamb.barotrauma.ship.vs.VsPilot.assemblyToWorld(cell)
                    : net.minecraft.world.phys.Vec3.atCenterOf(pos);
            if (center == null || serverPlayer.distanceToSqr(center) > 64.0D) {
                return InteractionResult.CONSUME;
            }
            if (!(level.getBlockEntity(pos) instanceof BatteryBlockEntity battery)) {
                return InteractionResult.CONSUME;
            }
            // 新放的电池可能还没进船体几何（旧存档/刚放下），开面板前补一次重扫。
            if (!battery.inHull()) {
                rescanShip(level, pos);
            }
            // 手持螺丝刀且耐久低于阈值：开独立修理窗口（原作 Repairable 的 GUI 帧）。
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new BatteryMenu(id, inventory, pos),
                            Component.translatable(state.getBlock().getDescriptionId())),
                    buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        rescanShip(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery) {
            // 三个充电槽里的电池 cell 跟着掉出来，不能挖掉电池就吞掉。
            net.minecraft.world.Containers.dropContents(level, pos, battery);
        }
        super.onRemove(state, level, pos, newState, isMoving);
        if (!state.is(newState.getBlock())) {
            rescanShip(level, pos);
        }
    }

    /** 新放的电池必须让船几何重扫，否则它不在 hull 里，不会向电网上报。 */
    private static void rescanShip(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        // 核心扫描支持 24 格，电池可能离核心 20 格：用 refreshNearby 的 64 格范围，
        // 不能用 16 格 rescanNear，否则合法位置的电池永远进不了几何。
        ShipCores.refreshNearby(level, pos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.BATTERY.get(),
                BatteryBlockEntity::serverTick);
    }
}
