package com.opamb.barotrauma.block;

/**
 * 炮塔武器规格：开火冷却、弹药、供电和命中伤害。
 *
 * <p>瞄准/相机/占用/网络仍然是 {@link com.opamb.barotrauma.blockentity.TurretBlockEntity}
 * 那一套，武器差异只放在这里。数值按原版 {@code Content/Items/Weapons/coilgun.xml}：
 * 电磁炮 {@code powerconsumption=1000}、{@code reload=0.25}（5 tick）、
 * 弹头 {@code structuredamage=20}、{@code lacerations=15}、{@code bleeding=1.0}。</p>
 */
public record TurretWeaponKind(
        int cooldownTicks,
        boolean usesAmmo,
        double powerKw,
        float entityDamage) {

    /** 现有原型炮塔：无限弹药、不耗电、10 tick 一发。 */
    public static final TurretWeaponKind PROTOTYPE = new TurretWeaponKind(
            10, false, 0.0D, 12.0F);

    /** 电磁枪：200 发/箱，5 tick 装填，每发从电容扣除 1000/3600 kW·min。 */
    public static final TurretWeaponKind COILGUN = new TurretWeaponKind(
            5, true, 1000.0D, com.opamb.barotrauma.entity.CoilgunBolt.DAMAGE);
}
