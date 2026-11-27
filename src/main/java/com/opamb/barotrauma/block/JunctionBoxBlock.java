package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import com.opamb.barotrauma.network.PowerPanelState;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ShipCores;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 接线盒：墙上分线/过压保护节点。
 */
public class JunctionBoxBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 候选 C 安装后的边界约 10.11×16×5.91 像素，向外圆整以覆盖门锁。
    private static final double EDGE = 2.94D;
    private static final double DEPTH = 6.0D;
    private static final VoxelShape SHAPE_NORTH = Block.box(EDGE, 0, 16 - DEPTH, 16 - EDGE, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(EDGE, 0, 0, 16 - EDGE, 16, DEPTH);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 0, EDGE, DEPTH, 16, 16 - EDGE);
    private static final VoxelShape SHAPE_WEST = Block.box(16 - DEPTH, 0, EDGE, 16, 16, 16 - EDGE);

    public JunctionBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 放置时让门板朝向玩家。
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 只有船坞里的方块才做装配坐标换算；普通世界的接线盒仍用世界坐标。
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
            // 旧存档/刚放下的盒子可能还没进船体几何，开面板前补一次重扫。
            if (level.getBlockEntity(pos) instanceof JunctionBoxBlockEntity box) {
                if (!box.inHull()) {
                    rescanShip(level, pos);
                }
            }
            PowerPanelState.send(serverPlayer, cell, physical);
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

    /**
     * 新放的接线盒必须让船几何重扫一次，否则它不在 {@code geometry.hull()} 里，
     * 方块实体会一直 {@code tracksHullBlock == false}，P 面板也找不到它。
     */
    private static void rescanShip(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        // 附近核心按 64 格找（大船的盒子可能离核心很远），并让物理船重扫船坞几何。
        ShipCores.refreshNearby(level, pos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JunctionBoxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.JUNCTION_BOX.get(),
                JunctionBoxBlockEntity::serverTick);
    }
}
