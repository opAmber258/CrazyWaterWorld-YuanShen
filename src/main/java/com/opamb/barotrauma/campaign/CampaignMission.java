package com.opamb.barotrauma.campaign;

import java.util.List;

/** M1 的共享航线委托；只记录接取，不伪造目标、货物或已完成的副本。 */
public record CampaignMission(String id, String kind, int reward) {
    public static final int LIMIT = 3;

    public static List<CampaignMission> offered(int from, int to) {
        if (!CampaignMap.connects(from, to)) return List.of();
        String route = from + "_" + to + "_";
        return List.of(new CampaignMission(route + "salvage", "salvage", 1200),
                new CampaignMission(route + "recon", "recon", 800),
                new CampaignMission(route + "delivery", "delivery", 600));
    }

    /** 空任务出发合法；重复、未知、其他航线、超上限一律拒绝。 */
    public static boolean valid(RoundPhase phase, int active, int expectedOrigin, int destination,
                                List<String> ids) {
        if (phase != RoundPhase.AT_NODE || active != expectedOrigin
                || !CampaignMap.connects(active, destination) || ids.size() > LIMIT
                || new java.util.HashSet<>(ids).size() != ids.size()) return false;
        var offered = offered(active, destination).stream().map(CampaignMission::id).toList();
        return offered.containsAll(ids);
    }
}
