package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.blockentity.TurretStationBlockEntity;
import com.opamb.barotrauma.item.TurretBinderItem;
import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 炮塔瞄准站（开发者工具，暂时以方块形式）。
 *
 * <p>用炮台绑定器绑定一台炮塔后，右键站台直接接管那台炮塔的瞄准视角。
 * 相机仍在炮塔本体位，操作和直接进炮塔完全一致；站台只是把“操作者必须站在
 * 炮塔 6 格内”换成“操作者站在站台旁、站台仍绑定该炮塔”。</p>
 *
 * <p>模型正面朝 +Z（south），放置时正面朝向玩家。</p>
 */
public class TurretStationBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 控制台外形：底座 + 台体 + 潜望柱都在这一格内，碰撞按外形收到 1..15 像素。 */
    private static final VoxelShape SHAPE = Shapes.box(
            1.0D / 16.0D, 0.0D, 1.0D / 16.0D,
            15.0D / 16.0D, 15.5D / 16.0D, 15.0D / 16.0D);

    public TurretStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 控制台是落地设备：脚下必须是可站立的完整面，避免贴着墙侧浮空。
        BlockPos below = context.getClickedPos().below();
        if (!context.getLevel().getBlockState(below)
                .isFaceSturdy(context.getLevel(), below, Direction.UP)) {
            return null;
        }
        // 屏幕/操作台正面朝向玩家，和接线盒/原版工作台一致。
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.getBlock().canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurretStationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        // 站台没有每 tick 逻辑：绑定由绑定器写入，右键时即时校验。
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        // Forge 服务端先调方块 use 再调物品 useOn：手持绑定器时让位，否则右键站台
        // 会先进入“未绑定”提示，绑定器根本没机会记录第一端。
        if (player.getItemInHand(hand).getItem() instanceof TurretBinderItem) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof TurretStationBlockEntity station)) {
            return InteractionResult.PASS;
        }

        BlockPos turretPos = station.turretPos();
        if (turretPos == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("msg.barotrauma.turret.station.unbound"), true);
            return InteractionResult.CONSUME;
        }
        // 未加载区块不要 getBlockEntity（会同步加载）；这属于配置问题，直接提示。
        if (!level.hasChunkAt(turretPos)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("msg.barotrauma.turret.station.unloaded"), true);
            return InteractionResult.CONSUME;
        }
        TurretBlockEntity turret = TurretBlockEntity.at(level, turretPos);
        if (turret == null) {
            station.clearBinding();
            serverPlayer.displayClientMessage(
                    Component.translatable("msg.barotrauma.turret.station.missing"), true);
            return InteractionResult.CONSUME;
        }
        return turret.enterFromStation(serverPlayer, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof TurretStationBlockEntity station) {
            BlockPos turretPos = station.turretPos();
            if (turretPos != null && level.hasChunkAt(turretPos)
                    && level.getBlockEntity(turretPos) instanceof TurretBlockEntity turret) {
                // 正在用这个站台遥控的玩家立刻退出；目标炮塔未加载时由炮塔 tick 兜底。
                turret.onStationRemoved(pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
