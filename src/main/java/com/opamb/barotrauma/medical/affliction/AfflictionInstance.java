package com.opamb.barotrauma.medical.affliction;

import java.util.Objects;

/**
 * 一个运行时病症：类型 + 部位 + 当前强度。
 *
 * <p>强度恒被钳制在 [0, maxStrength]；limbSpecific=false 的类型 limb 为 null。</p>
 */
public final class AfflictionInstance {

    private final AfflictionType type;
    private final Limb limb; // null = 全身（全局病症）
    private double strength;

    public AfflictionInstance(AfflictionType type, Limb limb, double strength) {
        this.type = Objects.requireNonNull(type, "type");
        this.limb = type.limbSpecific() ? Objects.requireNonNull(limb, "limb") : null;
        this.strength = clamp(type, strength);
    }

    /** 加强度并钳制到上限，返回钳制后的值。 */
    public double add(double amount) {
        strength = clamp(type, strength + amount);
        return strength;
    }

    /** 减强度并钳制到 0，返回钳制后的值。 */
    public double reduce(double amount) {
        strength = Math.max(0.0, strength - Math.abs(amount));
        return strength;
    }

    /** 每秒推进：按所在段应用自然变化，返回变化后的强度。 */
    public double tickSecond() {
        return add(type.strengthChangePerSecond(strength));
    }

    public boolean isEmpty() {
        return strength <= 0.0;
    }

    public AfflictionType type() {
        return type;
    }

    /** null 表示全身（全局病症）。 */
    public Limb limb() {
        return limb;
    }

    public double strength() {
        return strength;
    }

    private static double clamp(AfflictionType type, double value) {
        return Math.max(0.0, Math.min(type.maxStrength(), value));
    }
}
