package com.opamb.barotrauma.campaign;

import com.opamb.barotrauma.world.level.TerrainTemplate;
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
    @DisplayName("短重载的默认值保存后不被旧布局迁移覆盖")
    void shortLevelOverloadRoundTripKeepsFallback() {
        RoundState source = new RoundState();
        source.setInstance(24096, -80, 0);
        // 3 参数重载：距离/间距显式留 0，表示按关卡默认值恢复。
        source.setLevel("coldcavernsbasic", 7L, 3);
        RoundState restored = new RoundState();
        restored.load(source.save());
        assertEquals(0, restored.levelDistance());
        assertEquals(0, restored.levelSpacing());
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

    @Test
    @DisplayName("NBT 往返保留关卡类型/种子/难度/路线距离")
    void levelRoundTrip() {
        RoundState source = new RoundState();
        source.beginTravel(0, 1, 100L);
        source.setLevel("coldcavernsmaze", 987654321L, 9, 1300);

        RoundState restored = new RoundState();
        restored.load(source.save());

        assertEquals("coldcavernsmaze", restored.levelType());
        assertEquals(987654321L, restored.levelSeed());
        assertEquals(9, restored.levelDifficulty());
        assertEquals(1300, restored.levelDistance());
    }

    @Test
    @DisplayName("旧存档有实例锚点但没关卡距离/间距：回退 192 / 1024")
    void legacyInstanceWithoutLevelFieldsFallsBack() {
        // 加关卡距离/间距之前存的档：坐标都在，两个新字段都没有。
        // 回退值必须正好是当时的固定出发距离和槽位间距，否则已经生成的
        // 出发站/航道端点会被挪走。
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", RoundPhase.DIVING.name());
        tag.putInt("origin", 0);
        tag.putInt("destination", 1);
        tag.putInt("instanceX", 25120);
        tag.putInt("instanceY", -84);
        tag.putInt("instanceZ", 0);

        RoundState round = new RoundState();
        round.load(tag);

        assertTrue(round.hasInstance());
        assertEquals(25120, round.instanceX());
        assertEquals(192, round.levelDistance());
        assertEquals(1024, round.levelSpacing());
    }

    @Test
    @DisplayName("NBT 往返保留实例锚点 + 关卡距离/间距")
    void instanceLevelRoundTrip() {
        RoundState source = new RoundState();
        source.beginTravel(0, 1, 100L);
        source.setInstance(25120, -84, 0);
        source.setLevel("coldcavernsadvanced", 42L, 6, 600, 2048, TerrainTemplate.OPEN_SEA);

        RoundState restored = new RoundState();
        restored.load(source.save());

        assertTrue(restored.hasInstance());
        assertEquals(25120, restored.instanceX());
        assertEquals(-84, restored.instanceY());
        assertEquals(0, restored.instanceZ());
        assertEquals(600, restored.levelDistance());
        assertEquals(2048, restored.levelSpacing());
        assertEquals(TerrainTemplate.OPEN_SEA, restored.terrain());
    }

    @Test
    @DisplayName("旧存档没有关卡字段：默认 basic/0/0")
    void levelDefaults() {
        RoundState round = new RoundState();
        round.load(new CompoundTag());
        assertEquals("coldcavernsbasic", round.levelType());
        assertEquals(0L, round.levelSeed());
        assertEquals(0, round.levelDifficulty());
        assertEquals(0, round.levelDistance());
        assertEquals(TerrainTemplate.LEGACY, round.terrain());
    }

    @Test
    @DisplayName("未知关卡类型回退 basic")
    void unknownLevelFallsBack() {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", RoundPhase.AT_NODE.name());
        tag.putString("levelType", "not_a_level");
        RoundState round = new RoundState();
        round.load(tag);
        assertEquals("coldcavernsbasic", round.levelType());
    }
}
