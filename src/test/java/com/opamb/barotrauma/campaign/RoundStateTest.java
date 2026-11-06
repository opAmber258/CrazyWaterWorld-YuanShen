package com.opamb.barotrauma.campaign;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 巡回状态机与 NBT 往返。 */
class RoundStateTest {

    @Test
    @DisplayName("默认停在起点")
    void defaultAtNode() {
        RoundState round = new RoundState();
        assertEquals(RoundPhase.AT_NODE, round.phase());
        assertEquals(-1, round.destination());
        assertFalse(round.isDiving());
    }

    @Test
    @DisplayName("出发 → 抵达")
    void travelThenArrive() {
        RoundState round = new RoundState();
        round.beginTravel(0, 1, 100L);
        assertEquals(RoundPhase.DIVING, round.phase());
        assertEquals(0, round.origin());
        assertEquals(1, round.destination());
        assertEquals(100L, round.startedAtGameTime());
        assertTrue(round.isDiving());

        round.arrive(1);
        assertEquals(RoundPhase.AT_NODE, round.phase());
        assertEquals(1, round.origin());
        assertEquals(-1, round.destination());
    }

    @Test
    @DisplayName("失败阶段")
    void fail() {
        RoundState round = new RoundState();
        round.beginTravel(0, 1, 100L);
        round.fail();
        assertEquals(RoundPhase.FAILED, round.phase());
    }

    @Test
    @DisplayName("NBT 往返保留阶段/起点/终点/时间")
    void nbtRoundTrip() {
        RoundState source = new RoundState();
        source.beginTravel(1, 3, 4200L);

        CompoundTag tag = source.save();
        RoundState restored = new RoundState();
        restored.load(tag);

        assertEquals(RoundPhase.DIVING, restored.phase());
        assertEquals(1, restored.origin());
        assertEquals(3, restored.destination());
        assertEquals(4200L, restored.startedAtGameTime());
    }

    @Test
    @DisplayName("NBT 往返保留任务海域锚点")
    void instanceAnchorRoundTrip() {
        RoundState source = new RoundState();
        source.beginTravel(0, 1, 100L);
        source.setInstance(20000, -84, 0);

        RoundState restored = new RoundState();
        restored.load(source.save());

        assertTrue(restored.hasInstance());
        assertEquals(20000, restored.instanceX());
        assertEquals(-84, restored.instanceY());
        assertEquals(0, restored.instanceZ());
    }

    @Test
    @DisplayName("没出发过就没有任务海域锚点")
    void noInstanceBeforeTravel() {
        RoundState round = new RoundState();
        assertFalse(round.hasInstance());
        RoundState restored = new RoundState();
        restored.load(round.save());
        assertFalse(restored.hasInstance());
    }

    @Test
    @DisplayName("空 NBT 回到安全默认值")
    void emptyTagFallsBack() {
        RoundState round = new RoundState();
        round.beginTravel(0, 2, 5L);
        round.load(new CompoundTag());
        assertEquals(RoundPhase.AT_NODE, round.phase());
        assertEquals(-1, round.destination());
    }

    @Test
    @DisplayName("存档里的目的地越界：不卡在 DIVING")
    void invalidDestinationFallsBack() {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", RoundPhase.DIVING.name());
        tag.putInt("origin", 1);
        tag.putInt("destination", 99);

        RoundState round = new RoundState();
        round.load(tag);

        assertEquals(RoundPhase.AT_NODE, round.phase());
        assertEquals(-1, round.destination());
    }
}
