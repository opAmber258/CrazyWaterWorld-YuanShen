package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.entity.TrainingDummyEntity;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 高级充气台：生命周期与普通台完全一致，只把人偶换成 200 血、无受击间隔的高级版。 */
public class AdvancedTrainingDummyStationBlockEntity extends TrainingDummyStationBlockEntity {

    public AdvancedTrainingDummyStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADVANCED_TRAINING_DUMMY_STATION.get(), pos, state);
        // 高级版默认 50 血；面板里可在 1..200 之间调。
        configuredHealth = 50;
    }

    @Nullable
    @Override
    protected TrainingDummyEntity createDummy(ServerLevel server) {
        return ModEntities.ADVANCED_TRAINING_DUMMY.get().create(server);
    }
}
