package com.opamb.barotrauma.campaign;

/**
 * 航线委托留下的唯一常量。
 *
 * <p>旧版海图上的 3 条"航线委托"（只记录接取、没有实际玩法）已删除；现在任务由
 * 站务员 NPC 打开海图后勾选（{@code MissionNpc} + {@code CampaignMapScreen}），
 * 下潜时服务端按 {@code MissionSavedData} 校验并接取。这个常量用于包头/存档的
 * 条数上限，和 {@code MissionSavedData.MAX_ACTIVE} 保持一致。</p>
 */
public final class CampaignMission {

    /** 一趟最多几条任务。 */
    public static final int LIMIT = 3;

    private CampaignMission() {
    }
}
