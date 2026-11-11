package com.opamb.barotrauma.mission;

/**
 * 任务依赖的底层功能。只有 {@link #implemented()} 为 true 的功能，对应任务才会被
 * 任务终端真正发布；其余条目只登记在 {@link MissionCatalog} 里，等系统落地后再打开。
 */
public enum MissionFeature {
    /** 通用货物/交付物：现有的交付机制。 */
    CARGO_ITEMS(true),
    /** 可打捞的任务物品（黑匣子/异星遗物）：现有的打捞机制。 */
    SALVAGE_ITEM(true),
    /** 信标方块与激活交互。 */
    BEACON(false),
    /** 可在世界里采到的矿物（矿脉/矿点）。 */
    MINERALS(false),
    /** 生物实体与击杀判定。 */
    CREATURES(false),
    /** 巢穴地形与巢穴目标。 */
    NEST(false),
    /** 沉船地形与沉船打捞点。 */
    WRECKS(false),
    /** 异星遗迹地形与扫描/清剿。 */
    RUINS(false),
    /** 护航 NPC 与跟随逻辑。 */
    ESCORT(false),
    /** 废弃哨站结构/NPC。 */
    ABANDONED(false),
    /** 海盗 NPC 与战斗。 */
    PIRATES(false),
    /** 丘脑（Thalamus）结构。 */
    THALAMUS(false);

    private final boolean implemented;

    MissionFeature(boolean implemented) {
        this.implemented = implemented;
    }

    public boolean implemented() {
        return implemented;
    }
}
