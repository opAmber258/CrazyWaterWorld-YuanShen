package com.opamb.barotrauma.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 哨站对接通道旧档修复的完成标记（挂 {@code barotrauma:the_deep}）。
 *
 * <p>修复是幂等的，但玩家可能在通道坐标放置哨站装甲——没有这个标记的话，
 * 每次登录都会把它删掉（issue #22 / CodeRabbit 审查意见）。修复跑过一次
 * 就永久跳过，玩家后续的改动不再被覆盖。</p>
 */
public final class StationRepairSavedData extends SavedData {

    public static final String NAME = "barotrauma_station_repair";

    private boolean done;

    public static StationRepairSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StationRepairSavedData::load, StationRepairSavedData::new, NAME);
    }

    /** 修复是否已经跑过一次。 */
    public boolean done() {
        return done;
    }

    /** 标记完成并落盘。 */
    public void markDone() {
        done = true;
        setDirty();
    }

    private static StationRepairSavedData load(CompoundTag tag) {
        StationRepairSavedData data = new StationRepairSavedData();
        data.done = tag.getBoolean("done");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("done", done);
        return tag;
    }
}
