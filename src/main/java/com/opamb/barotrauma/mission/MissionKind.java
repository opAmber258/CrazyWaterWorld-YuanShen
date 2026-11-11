package com.opamb.barotrauma.mission;

/** 原版任务大类；每个大类对应一个底层功能依赖。 */
public enum MissionKind {
    CARGO("CargoMission", MissionFeature.CARGO_ITEMS),
    ESCORT("EscortMission", MissionFeature.ESCORT),
    MINERAL("MineralMission", MissionFeature.MINERALS),
    BEACON("BeaconMission", MissionFeature.BEACON),
    SALVAGE("SalvageMission", MissionFeature.SALVAGE_ITEM),
    MONSTER("MonsterMission", MissionFeature.CREATURES),
    NEST("NestMission", MissionFeature.NEST),
    ABANDONED("AbandonedOutpostMission", MissionFeature.ABANDONED),
    PIRATE("PirateMission", MissionFeature.PIRATES),
    RUIN("RuinMission", MissionFeature.RUINS),
    THALAMUS("EliminateTargetsMission", MissionFeature.THALAMUS);

    private final String sourceTag;
    private final MissionFeature feature;

    MissionKind(String sourceTag, MissionFeature feature) {
        this.sourceTag = sourceTag;
        this.feature = feature;
    }

    /** 原版 Missions.xml 里的元素名。 */
    public String sourceTag() {
        return sourceTag;
    }

    public MissionFeature feature() {
        return feature;
    }

    public boolean implemented() {
        return feature.implemented();
    }

    public static MissionKind fromJson(String value) {
        for (MissionKind kind : values()) {
            if (kind.name().equalsIgnoreCase(value)) {
                return kind;
            }
        }
        return null;
    }
}
