package com.opamb.barotrauma.world.level;

import java.util.ArrayList;
import java.util.List;

/**
 * 关卡类型选择与难度换算（纯函数，可单测）。
 *
 * <p>原作：关卡难度来自地图连接/群系（Cold Caverns 0–15），关卡类型按
 * {@code minleveldifficulty/maxleveldifficulty + commonness} 抽。本模组没有
 * 独立的 connection difficulty，用“目的节点深度 + 已完成出航数”近似：
 * {@code difficulty = clamp(round((depthMeters - 60) / 80) + rounds, 0, 15)}。</p>
 *
 * <p>tutorial 的 commonness 是 0（只能强制），第一趟出航强制 tutorial；
 * outpost 不参与连接关随机，由站点是否有哨站决定（见 {@code SeaTerrain}）。</p>
 */
public final class LevelSelector {

    /** Cold Caverns 的群系最大难度。 */
    public static final int MAX_DIFFICULTY = 15;

    private LevelSelector() {
    }

    /** 目的节点深度（米）+ 已完成出航数 → 0..15 的关卡难度。 */
    public static int difficulty(int destinationNodeY, int roundsCompleted) {
        int depthMeters = Math.max(0, -destinationNodeY);
        int base = (int) Math.round((depthMeters - 60) / 80.0D);
        return clamp(base + Math.max(0, roundsCompleted), 0, MAX_DIFFICULTY);
    }

    /**
     * 选连接关类型：第一趟强制 tutorial；否则按难度门槛 + commonness 加权。
     * 相同 {@code levelSeed} 的结果固定，读档不会重抽。
     */
    public static LevelType connectionType(boolean firstTrip, int difficulty, long levelSeed) {
        if (firstTrip) {
            return LevelType.TUTORIAL;
        }
        List<LevelType> types = new ArrayList<>(3);
        List<Integer> weights = new ArrayList<>(3);
        // 类型分布的唯一真值在 LevelParams：commonness 是权重，min/maxDifficulty 是门槛。
        for (LevelType type : LevelType.values()) {
            LevelParams params = type.params();
            if (!type.isConnection() || params.commonness() <= 0) {
                continue;
            }
            add(types, weights, type, params.commonness(),
                    difficulty >= params.minDifficulty() && difficulty <= params.maxDifficulty());
        }
        if (types.isEmpty()) {
            // 理论上不会发生（难度 0–15 至少 basic/advanced 有一个），兜底。
            return LevelType.BASIC;
        }
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = Math.floorMod((int) (levelSeed ^ (levelSeed >>> 32)), total);
        for (int i = 0; i < types.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return types.get(i);
            }
        }
        return types.get(types.size() - 1);
    }

    /** 每趟出航的关卡种子：世界种子 + 槽位，确定性混合。 */
    public static long levelSeed(long worldSeed, int slot) {
        return mix(worldSeed, slot);
    }

    /** 确定性 64 位混合（关卡种子、侧洞位置都用它，避免 Random 状态）。 */
    public static long mix(long seed, int salt) {
        long z = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static void add(List<LevelType> types, List<Integer> weights,
                            LevelType type, int weight, boolean eligible) {
        if (eligible) {
            types.add(type);
            weights.add(weight);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
