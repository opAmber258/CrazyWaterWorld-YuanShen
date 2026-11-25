package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.BalloonStationBlock;
import com.opamb.barotrauma.entity.BalloonDummyEntity;
import com.opamb.barotrauma.entity.TrainingDummyEntity;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 气球充气台：生命周期复用训练人偶台，沿 FACING 方向在相邻格生成气球。 */
public class BalloonStationBlockEntity extends TrainingDummyStationBlockEntity {

    public BalloonStationBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.BALLOON_DUMMY_STATION.get(), pos, state);
    }

    protected BalloonStationBlockEntity(
            net.minecraft.world.level.block.entity.BlockEntityType<?> type,
            BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    @Override
    protected TrainingDummyEntity createDummy(ServerLevel server) {
        return ModEntities.BALLOON_DUMMY.get().create(server);
    }

    @Override
    protected void configureDummy(TrainingDummyEntity dummy, BlockState state) {
        Direction facing = state.getValue(BalloonStationBlock.FACING);
        Vec3 center = Vec3.atCenterOf(worldPosition);
        dummy.moveTo(center.x + facing.getStepX() * BalloonDummyEntity.STATION_OFFSET,
                center.y + facing.getStepY() * BalloonDummyEntity.STATION_OFFSET
                        - BalloonDummyEntity.HITBOX_RADIUS,
                center.z + facing.getStepZ() * BalloonDummyEntity.STATION_OFFSET,
                0.0F, 0.0F);
        dummy.setStationPos(worldPosition);
    }
}
