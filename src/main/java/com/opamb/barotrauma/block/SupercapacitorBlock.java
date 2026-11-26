package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.SupercapacitorBlockEntity;
import com.opamb.barotrauma.menu.SupercapacitorMenu;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 超级电容器：墙挂式储能设备。
 *
 * <p>模型来自 {@code art/models/supercapacitor_20261118}，宽 1.5 格、高 1.4 格、
 * 厚 0.81 格，左右各探出 0.25 格；背面贴墙、正面朝外。屏幕按电量分 6 档
 * （0 空 + 5 段），用 {@link #CHARGE} 方块状态切换贴图。</p>
 *
 * <p>和电池一样是船上的电力设备：放置/拆除要触发船体几何重扫，否则不会进
 * hull、也不会向电网报电量。</p>
 */
public class SupercapacitorBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 电量档位：0 空、1..5 段（原作 5 段电量屏）。 */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 5);

    /** 模型 x -4..20、y -0.399..22、z 3..16（16 单位/格），正面朝 -Z。 */
    private static final VoxelShape SHAPE_NORTH = Shapes.box(
            0.0D, 0.0D, 3.0D / 16.0D,
            1.0D, 1.0D, 1.0D);
    private static final VoxelShape SHAPE_SOUTH = Shapes.box(
            0.0D, 0.0D, 0.0D,
            1.0D, 1.0D, 13.0D / 16.0D);
    private static final VoxelShape SHAPE_EAST = Shapes.box(
            3.0D / 16.0D, 0.0D, 0.0D,
            1.0D, 1.0D, 1.0D);
    private static final VoxelShape SHAPE_WEST = Shapes.box(
            0.0D, 0.0D, 0.0D,
            13.0D / 16.0D, 1.0D, 1.0D);

    public SupercapacitorBlock(Properties properties) {
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
            // 物理船上 use 跑在船坞坐标里；菜单要同时拿到船坞坐标（找 BE）和
            // 世界锚点（客户端 stillValid 用，客户端没有 VsPilot 的变换）。
            boolean physical = VsState.active() && VsPilot.isYardBlock(pos);
            BlockPos cell = physical ? VsPilot.toAssembly(pos) : pos;
            Vec3 anchor = physical ? VsPilot.assemblyToWorld(cell) : Vec3.atCenterOf(pos);
            if (anchor == null || serverPlayer.distanceToSqr(anchor) > 64.0D) {
                return InteractionResult.CONSUME;
            }
            if (!(level.getBlockEntity(pos) instanceof SupercapacitorBlockEntity capacitor)) {
                return InteractionResult.CONSUME;
            }
            if (!capacitor.inHull()) {
                rescanShip(level, pos);
            }
            Vec3 anchorFinal = anchor;
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new SupercapacitorMenu(
                                    id, inventory, pos, anchorFinal),
                            Component.translatable(state.getBlock().getDescriptionId())),
                    buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeDouble(anchorFinal.x);
                        buf.writeDouble(anchorFinal.y);
                        buf.writeDouble(anchorFinal.z);
                    });
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
        super.onRemove(state, level, pos, newState, isMoving);
        if (!state.is(newState.getBlock())) {
            rescanShip(level, pos);
        }
    }

    /** 新放的电容必须让船几何重扫，否则它不在 hull 里，不会向电网上报。 */
    private static void rescanShip(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            ShipCores.refreshNearby(level, pos);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupercapacitorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.SUPERCAPACITOR.get(),
                SupercapacitorBlockEntity::serverTick);
    }
}
