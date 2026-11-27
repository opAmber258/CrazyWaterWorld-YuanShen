package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.PeriscopeBlockEntity;
import com.opamb.barotrauma.item.TurretBinderItem;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** 潜望镜把瞄准及扳机输入发给绑定的炮台，旧原型炮塔保留就近接管。 */
public class PeriscopeBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 能接管的炮塔半径（和炮塔自身射程 48 格一致）。 */
    private static final double TURRET_RANGE = 48.0D;

    public PeriscopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return hasStand(level, pos);
    }

    /** 上方或下方任意一侧有潜望镜底座。 */
    public static boolean hasStand(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.above()).getBlock() instanceof PeriscopeStandBlock
                || level.getBlockState(pos.below()).getBlock() instanceof PeriscopeStandBlock;
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof TurretBinderItem) return InteractionResult.PASS;
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server)) {
            return InteractionResult.PASS;
        }
        if (!hasStand(server, pos)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("msg.barotrauma.periscope.need_stand"), true);
            return InteractionResult.CONSUME;
        }
        BlockPos target = server.getBlockEntity(pos) instanceof PeriscopeBlockEntity periscope
                ? periscope.turretPos() : null;
        TurretBlockEntity turret = target == null ? nearestTurret(server, pos)
                : server.hasChunkAt(target) ? TurretBlockEntity.at(server, target) : null;
        if (turret == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("msg.barotrauma.periscope.no_turret"), true);
            return InteractionResult.CONSUME;
        }
        return turret.enter(serverPlayer, pos);
    }

    /** 已加载区块里最近的一台炮塔；没有返回 null。 */
    @Nullable
    private static TurretBlockEntity nearestTurret(ServerLevel level, BlockPos origin) {
        int chunkRadius = (int) Math.ceil(TURRET_RANGE / 16.0D);
        net.minecraft.world.level.ChunkPos base = new net.minecraft.world.level.ChunkPos(origin);
        TurretBlockEntity best = null;
        double bestDistance = TURRET_RANGE * TURRET_RANGE;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(base.x + dx, base.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    if (chunk.getBlockEntity(pos) instanceof TurretBlockEntity turret && !turret.weaponKind().usesAmmo()) {
                        double distance = pos.distSqr(origin);
                        if (distance <= bestDistance) {
                            bestDistance = distance;
                            best = turret;
                        }
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PeriscopeBlockEntity(pos, state);
    }
}
