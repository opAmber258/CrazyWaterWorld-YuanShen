package com.opamb.barotrauma.world.level;

/**
 * 寒冷深穴关卡类型（对照原作 {@code Content/Map/Biomes/ColdCaverns/ColdCaverns.xml}）。
 *
 * <p>连接关（tutorial/basic/advanced/maze）用于两站之间的航行；outpost 不参与
 * 连接关随机，由“目的地/出发地是不是哨站”决定，只覆盖哨站周围的开阔空腔。
 * 所有数值在 {@link LevelParams} 里按 0.5 比例换算成格。</p>
 */
public enum LevelType {

    TUTORIAL("coldcavernstutorial"),
    BASIC("coldcavernsbasic"),
    ADVANCED("coldcavernsadvanced"),
    MAZE("coldcavernsmaze"),
    OUTPOST("coldcavernsoutpost");

    private final String id;

    LevelType(String id) {
        this.id = id;
    }

    /** 原作关卡类型 id（存档里也存这个，避免枚举改名破坏旧档）。 */
    public String id() {
        return this.id;
    }

    /** 连接关：参与按难度/权重随机；outpost 不参与。 */
    public boolean isConnection() {
        return this != OUTPOST;
    }

    public LevelParams params() {
        return LevelParams.forType(this);
    }

    public static LevelType fromId(String id) {
        if (id != null) {
            for (LevelType type : values()) {
                if (type.id.equals(id) || type.name().equalsIgnoreCase(id)) {
                    return type;
                }
            }
        }
        return BASIC;
    }
}
