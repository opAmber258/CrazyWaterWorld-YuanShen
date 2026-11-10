package com.opamb.barotrauma.mission;

/**
 * 一条原版任务定义（来自寒冷深穴目录），只描述静态数据，不含运行时状态。
 *
 * @param id                  原版 identifier，例如 {@code quartzcollection}
 * @param kind                任务大类
 * @param text                原版 textidentifier（本地化 key 的后缀）
 * @param nameZh              官方简中名（从游戏文本读出，方便目录排查）
 * @param nameEn              官方英文名
 * @param reward              报酬（credits）；0 表示原版按货物价值动态计算
 * @param difficulty          原版难度档 1..4
 * @param minLevelDifficulty  允许出现的最低关卡难度（0..100）
 * @param maxLevelDifficulty  允许出现的最高关卡难度（0..100）
 * @param spawnType           目标关卡需要的生成物：none / cave / wreck / ruin / abysscave
 * @param fromType            起点地点类型（AnyOutpost / Outpost / City / any）
 * @param toType              终点地点类型
 */
public record MissionSpec(String id, MissionKind kind, String text, String nameZh, String nameEn,
                          int reward, int difficulty, int minLevelDifficulty, int maxLevelDifficulty,
                          String spawnType, String fromType, String toType) {

    /** 任务依赖的底层功能是否已经实装。 */
    public boolean implemented() {
        return kind != null && kind.implemented();
    }

    /** 本地化 key，例如 {@code mission.barotrauma.quartzcollection}。 */
    public String nameKey() {
        return "mission.barotrauma." + id;
    }
}
