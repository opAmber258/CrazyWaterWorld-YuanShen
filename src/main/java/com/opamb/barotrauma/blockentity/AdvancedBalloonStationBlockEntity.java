package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.entity.TrainingDummyEntity;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 高级气球充气台：同一套摆放逻辑，生成 200 血、无受击间隔的气球。 */
public class AdvancedBalloonStationBlockEntity extends BalloonStationBlockEntity {

    public AdvancedBalloonStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADVANCED_BALLOON_DUMMY_STATION.get(), pos, state);
        // 高级版默认 50 血；面板里可在 1..200 之间调。
        configuredHealth = 50;
    }

    @Nullable
    @Override
    protected TrainingDummyEntity createDummy(ServerLevel server) {
        return ModEntities.ADVANCED_BALLOON_DUMMY.get().create(server);
    }
}
