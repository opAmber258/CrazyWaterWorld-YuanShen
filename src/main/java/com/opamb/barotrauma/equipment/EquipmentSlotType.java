package com.opamb.barotrauma.equipment;

/**
 * 潜渊症风格的玩家装备槽。
 *
 * <p>槽位分两列叠在原版背包界面上：左列接替原版四个护甲槽（y=8/26/44/62），
 * 右列从 y=26 起是氧气瓶、潜水服、腰带/背包。{@link #menuIndex()} 是
 * {@code InventoryMenu.slots} 里的菜单下标——0..4 是合成，5..8 是原护甲槽，
 * 9..44 是背包/快捷栏，45 是原副手槽；46/47 是本模组追加的两个槽。</p>
 *
 * <p>{@link #slotIndex()} 是装备容器自己的下标，和菜单下标无关；
 * 这样即使以后调整界面位置也不会动到存档里的槽位顺序。</p>
 */
public enum EquipmentSlotType {

    /** 左列第 1 格：ID 卡。 */
    ID_CARD(0, 5, 8, 8, "id_card", "ID"),
    /** 左列第 2 格：耳机。 */
    HEADSET(1, 6, 8, 26, "headset", "HD"),
    /** 左列第 3 格：头盔。 */
    HELMET(2, 7, 8, 44, "helmet", "HM"),
    /** 左列第 4 格：身体护甲。 */
    BODY_ARMOR(3, 8, 8, 62, "body_armor", "AR"),
    /** 原副手槽：腰带或背包（二选一）。 */
    BAG(4, 45, 77, 62, "bag", "BG"),
    /** 副手槽上方：潜水服。 */
    DIVING_SUIT(5, 46, 77, 44, "diving_suit", "DS"),
    /** 副手槽上方第二格：氧气瓶。 */
    OXYGEN_TANK(6, 47, 77, 26, "oxygen_tank", "O2");

    private final int slotIndex;
    private final int menuIndex;
    private final int x;
    private final int y;
    private final String id;
    private final String hint;

    EquipmentSlotType(int slotIndex, int menuIndex, int x, int y, String id, String hint) {
        this.slotIndex = slotIndex;
        this.menuIndex = menuIndex;
        this.x = x;
        this.y = y;
        this.id = id;
        this.hint = hint;
    }

    /** 装备容器内的下标（存档用这个）。 */
    public int slotIndex() {
        return this.slotIndex;
    }

    /** {@code InventoryMenu.slots} 里的下标。 */
    public int menuIndex() {
        return this.menuIndex;
    }

    /** 槽位左上角在背包背景内的 x（像素）。 */
    public int x() {
        return this.x;
    }

    /** 槽位左上角在背包背景内的 y（像素）。 */
    public int y() {
        return this.y;
    }

    /** 资源/标签用的短标识，例如 {@code id_card}。 */
    public String id() {
        return this.id;
    }

    /** 空槽位里画的两字符提示。 */
    public String hint() {
        return this.hint;
    }

    /** 槽位名 lang key（tooltip 用）。 */
    public String slotNameKey() {
        return "tooltip.barotrauma.equipment.slot." + this.id;
    }

    /** 按装备容器下标查找。 */
    public static EquipmentSlotType byIndex(int index) {
        for (EquipmentSlotType type : values()) {
            if (type.slotIndex == index) {
                return type;
            }
        }
        return null;
    }

    /** 按菜单下标查找；不是装备槽时返回 null。 */
    public static EquipmentSlotType byMenuIndex(int menuIndex) {
        for (EquipmentSlotType type : values()) {
            if (type.menuIndex == menuIndex) {
                return type;
            }
        }
        return null;
    }
}
