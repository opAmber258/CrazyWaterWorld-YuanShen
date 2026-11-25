package com.opamb.barotrauma.world.level;

/** 同一生成器内的关卡地形模板。稳定 id 用于恢复已生成一部分的航次。 */
public enum TerrainTemplate {
    LEGACY("legacy"),
    CANYON("canyon"),
    OPEN_SEA("open_sea");

    private final String id;

    TerrainTemplate(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static TerrainTemplate fromId(String id) {
        for (TerrainTemplate template : values()) {
            if (template.id.equals(id)) {
                return template;
            }
        }
        return LEGACY;
    }

    public static TerrainTemplate forRoute(LevelType type, long seed) {
        return switch (type) {
            case TUTORIAL, MAZE -> CANYON;
            case BASIC -> (LevelSelector.mix(seed, 391) & 1L) == 0L ? CANYON : OPEN_SEA;
            case ADVANCED -> OPEN_SEA;
            case OUTPOST -> LEGACY;
        };
    }
}
