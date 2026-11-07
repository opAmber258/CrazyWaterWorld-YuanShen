package com.opamb.barotrauma.medical.affliction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 一个角色的全部病症——纯 Java 核心，MC 侧通过 Capability 挂到玩家上。
 *
 * <p>关键语义（均来自 vanilla-baseline.md）：</p>
 * <ul>
 *   <li>部位病症按 (类型, 部位) 分实例；全身病症（失血/缺氧/眩晕）合并为单实例，
 *       多来源注入直接累加（原作 global 语义）。</li>
 *   <li>{@link #tickSecond()} 先推进自然变化，再汇总本秒的 vitality 消耗百分比；
 *       调用方（桥接层）把它换算成 MC 生命值伤害。</li>
 *   <li>持久化用行字符串（id|limb|strength），NBT 转换在桥接层做，保证本类可 L1 测试。</li>
 * </ul>
 */
public final class AfflictionContainer {

    private final Map<String, AfflictionInstance> byKey = new LinkedHashMap<>();

    /** 进行中的治疗（药品的"每秒治疗量×持续时间"），随病症一起持久化。 */
    private final List<TreatmentEntry> treatments = new ArrayList<>();

    /** 倒地时置 true：进入倒地只清一次残余治疗；倒地后队友新登记的治疗仍结算。 */
    private boolean downed;

    /** 倒地期间的额外活力消耗（原作：倒地后伤势继续恶化，活力自然流失至 −100 真死）。 */
    private double downedDrain;

    /** 一条进行中的治疗；perSecond &gt; 0 = 治疗，&lt; 0 = 副作用（按秒施加）。 */
    static final class TreatmentEntry {
        final AfflictionType type;
        final Limb limb; // null = 部位类型的"最强优先"模式；副作用作用于躯干
        final double perSecond;
        double secondsRemaining;

        TreatmentEntry(AfflictionType type, Limb limb, double perSecond, double secondsRemaining) {
            this.type = type;
            this.limb = limb;
            this.perSecond = perSecond;
            this.secondsRemaining = secondsRemaining;
        }
    }

    private static final int MAX_TREATMENTS = 16;

    /** 网络/存档行数上限：全部 (类型×部位) 实例 + 治疗槽。 */
    public static final int MAX_ROWS =
            AfflictionType.ALL.size() * Limb.values().length + MAX_TREATMENTS;

    /** key：全身病症用 id，部位病症用 id|LIMB。 */
    private static String key(AfflictionType type, Limb limb) {
        return type.limbSpecific() ? type.id() + "|" + limb.name() : type.id();
    }

    /** 注入病症强度（正数）。全身类型自动合并；返回该实例注入后的强度。 */
    public double add(AfflictionType type, Limb limb, double amount) {
        if (amount <= 0) {
            return existingStrength(type, limb);
        }
        Limb effective = type.limbSpecific() ? limb : null;
        AfflictionInstance instance = byKey.computeIfAbsent(key(type, effective),
                k -> new AfflictionInstance(type, effective, 0.0));
        return instance.add(amount);
    }

    /**
     * 降低病症强度。limb 传 null 且类型是部位归属时，从**最强实例**开始依次清减
     * （对应原作治疗作用于被治疗肢体的直觉；多个部位带血时先包最重的）。
     * 返回实际减少的总量。
     */
    public double reduce(AfflictionType type, Limb limb, double amount) {
        if (amount <= 0) {
            return 0.0;
        }
        if (!type.limbSpecific()) {
            AfflictionInstance instance = byKey.get(type.id());
            return instance == null ? 0.0 : reduceFrom(instance, amount);
        }
        if (limb != null) {
            AfflictionInstance instance = byKey.get(key(type, limb));
            return instance == null ? 0.0 : reduceFrom(instance, amount);
        }
        double remaining = amount;
        for (AfflictionInstance instance : sortedByStrengthDesc(type)) {
            remaining -= reduceFrom(instance, remaining);
            if (remaining <= 0) {
                break;
            }
        }
        return amount - Math.max(0, remaining);
    }

    /** 查单实例强度（全身类型忽略 limb）。 */
    public double strength(AfflictionType type, Limb limb) {
        return existingStrength(type, type.limbSpecific() ? limb : null);
    }

    /** 某类型的总强度（部位类型 = 各部位之和；给 HUD 摘要用）。 */
    public double totalStrength(AfflictionType type) {
        double sum = 0;
        for (AfflictionInstance instance : byKey.values()) {
            if (instance.type() == type) {
                sum += instance.strength();
            }
        }
        return sum;
    }

    /**
     * 使用药品：登记一条"每秒治疗量 × 持续时间"的治疗（原作 ReduceAffliction 语义）。
     * perSecond &lt; 0 表示副作用（按秒施加病症；部位类型作用于躯干）。
     */
    public void applyTreatment(AfflictionType type, Limb limb, double perSecond, double seconds) {
        if (seconds <= 0 || perSecond == 0) {
            return;
        }
        if (treatments.size() >= MAX_TREATMENTS) {
            treatments.remove(0); // 满了挤掉最旧的
        }
        treatments.add(new TreatmentEntry(type, limb, perSecond, seconds));
    }

    /**
     * 推进一秒：先结算治疗（每秒治疗量×持续时间，原作 ReduceAffliction 语义），
     * 再出血按 强度/60 推高全身失血（原作 AfflictionBleeding.Update，底表 §8.4），
     * 然后所有实例走自然变化，移除空实例与到期的治疗。
     * （原作没有"每秒持续扣血"——血量是随算的，见 {@link #vitality(double)}。）
     */
    public void tickSecond() {
        // 1) 治疗 buffer（倒地前残留的治疗在进入倒地时已清空；倒地后队友打的药仍结算）
        if (!treatments.isEmpty()) {
            List<TreatmentEntry> expired = new ArrayList<>();
            for (TreatmentEntry entry : treatments) {
                if (entry.perSecond > 0) {
                    reduce(entry.type, entry.limb, entry.perSecond);
                } else {
                    Limb sideLimb = entry.type.limbSpecific() && entry.limb == null ? Limb.TORSO : entry.limb;
                    add(entry.type, sideLimb, -entry.perSecond);
                }
                entry.secondsRemaining -= 1;
                if (entry.secondsRemaining <= 0) {
                    expired.add(entry);
                }
            }
            treatments.removeAll(expired);
        }
        // 2) AfflictionBleeding.Update：BloodlossAmount += Strength / 60 × deltaTime（v1 无抗性，系数 1）
        double bloodlossPush = 0;
        double withdrawalPush = 0;
        for (AfflictionInstance instance : byKey.values()) {
            if (instance.type() == AfflictionType.BLEEDING) {
                bloodlossPush += instance.strength() / 60.0;
            } else if (instance.type() == AfflictionType.OPIATE_ADDICTION) {
                // 原作：成瘾按段位推高戒断（20-40: 0.15/s；40-80: 0.25/s；80-100: 0.5/s）
                double s = instance.strength();
                withdrawalPush += s < 20 ? 0 : s < 40 ? 0.15 : s < 80 ? 0.25 : 0.5;
            }
        }
        if (bloodlossPush > 0) {
            add(AfflictionType.BLOODLOSS, null, bloodlossPush);
        }
        if (withdrawalPush > 0) {
            add(AfflictionType.OPIATE_WITHDRAWAL, null, withdrawalPush);
        }
        // 3) 自然强度变化
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, AfflictionInstance> entry : byKey.entrySet()) {
            AfflictionInstance instance = entry.getValue();
            instance.tickSecond();
            if (instance.isEmpty()) {
                dead.add(entry.getKey());
            }
        }
        for (String key : dead) {
            byKey.remove(key);
        }
    }

    /**
     * 当前全部病症的 vitality 消耗合计（点数）。
     * 未达激活阈值的实例不计（原作 GetVitalityDecrease 同款判断）。
     */
    public double vitalityDecreaseTotal(double maxVitality) {
        double total = 0;
        for (AfflictionInstance instance : byKey.values()) {
            total += instance.type().vitalityDecrease(instance.strength(), maxVitality);
        }
        return total;
    }

    /** 人类死亡线（原作 MinVitality：IsHuman ? -100 : 0，−100 缓冲留给"重伤倒地"救援）。 */
    public static final double MIN_VITALITY = -100.0;

    /**
     * **未钳制**的实时血量（可为负）。语义（原作源码 + MinVitality）：
     * &gt; 0 正常；∈ (MIN_VITALITY, 0] = 重伤倒地（可被队友治疗救回）；
     * ≤ MIN_VITALITY = 真死。下限钳到 MIN_VITALITY。
     * 倒地期间的 downedDrain 让活力像原作一样持续自然流失（用户指令）。
     */
    public double rawVitality(double maxVitality) {
        return Math.max(MIN_VITALITY,
                Math.min(maxVitality, maxVitality - vitalityDecreaseTotal(maxVitality) + downedDrain));
    }

    /** 倒地期间的额外活力流失（负值/秒）。被救回时归零。 */
    public void addDownedDrain(double perSecond) {
        downedDrain += perSecond;
    }

    /** 倒地流失归零（被救回/死亡清空时）。 */
    public void resetDownedDrain() {
        downedDrain = 0;
    }

    /**
     * 以病症实时算血量（原作 CharacterHealth.CalculateVitality 语义）。
     * 结果钳制到 [0, maxVitality]（心数镜像用；倒地/死亡判定用 {@link #rawVitality}）。
     */
    public double vitality(double maxVitality) {
        return Math.max(0.0, Math.min(maxVitality, maxVitality - vitalityDecreaseTotal(maxVitality)));
    }

    /** 全部清空（死亡时调用；REQUIREMENTS §5.5 验收点）。治疗也一并中止。 */
    public void clear() {
        byKey.clear();
        treatments.clear();
        downedDrain = 0;
        downed = false;
    }

    /** 进入倒地：只在状态切换时清空倒地前的残余治疗，之后登记的治疗继续结算。 */
    public void enterDowned() {
        if (!downed) {
            downed = true;
            treatments.clear();
        }
    }

    /** 离开倒地/被救回。 */
    public void exitDowned() {
        downed = false;
    }

    /** 倒地流失量（持久化用）。 */
    public double downedDrainValue() {
        return downedDrain;
    }

    /** 从存档恢复倒地流失量；须在 clear() 之后调用。 */
    public void restoreDownedDrain(double value) {
        downedDrain = Double.isFinite(value) ? value : 0.0;
    }

    /** 非死亡克隆：把旧容器的全部医疗状态复制到新容器。 */
    public void copyFrom(AfflictionContainer other) {
        if (other == null || other == this) {
            return;
        }
        clear();
        for (AfflictionInstance instance : other.byKey.values()) {
            add(instance.type(), instance.limb(), instance.strength());
        }
        for (TreatmentEntry entry : other.treatments) {
            if (treatments.size() >= MAX_TREATMENTS) {
                break;
            }
            treatments.add(new TreatmentEntry(entry.type, entry.limb,
                    entry.perSecond, entry.secondsRemaining));
        }
        downedDrain = other.downedDrain;
        downed = other.downed;
    }

    public boolean isEmpty() {
        return byKey.isEmpty() && treatments.isEmpty();
    }

    /** 序列化行：病症 id|LIMB|strength，治疗 t|id|LIMB|rate|secs（全身 limb 段为 -），顺序稳定。 */
    public List<String> rows() {
        List<String> rows = new ArrayList<>();
        for (AfflictionInstance instance : byKey.values()) {
            StringJoiner joiner = new StringJoiner("|");
            joiner.add(instance.type().id());
            if (instance.limb() != null) {
                joiner.add(instance.limb().name());
            }
            joiner.add(String.valueOf(round(instance.strength())));
            rows.add(joiner.toString());
        }
        for (TreatmentEntry entry : treatments) {
            StringJoiner joiner = new StringJoiner("|");
            joiner.add("t");
            joiner.add(entry.type.id());
            joiner.add(entry.limb == null ? "-" : entry.limb.name());
            joiner.add(String.valueOf(round(entry.perSecond)));
            joiner.add(String.valueOf(round(entry.secondsRemaining)));
            rows.add(joiner.toString());
        }
        return rows;
    }

    /** 从行恢复；未知 id 忽略（版本兼容）。 */
    public void loadRow(String row) {
        String[] parts = row.split("\\|");
        if (parts.length < 2) {
            return;
        }
        if (parts[0].equals("t")) {
            if (parts.length < 5) {
                return;
            }
            AfflictionType type = AfflictionType.byId(parts[1]);
            if (type == null) {
                return;
            }
            try {
                Limb limb = parts[2].equals("-") ? null : Limb.valueOf(parts[2]);
                double perSecond = Double.parseDouble(parts[3]);
                double seconds = Double.parseDouble(parts[4]);
                if (!Double.isFinite(perSecond) || !Double.isFinite(seconds)
                        || treatments.size() >= MAX_TREATMENTS) {
                    return;
                }
                treatments.add(new TreatmentEntry(type, limb, perSecond, seconds));
            } catch (IllegalArgumentException ignored) {
                // 坏行不致命
            }
            return;
        }
        AfflictionType type = AfflictionType.byId(parts[0]);
        if (type == null) {
            return;
        }
        if (type.limbSpecific()) {
            if (parts.length < 3) {
                return;
            }
            try {
                double strength = Double.parseDouble(parts[2]);
                if (!Double.isFinite(strength)) {
                    return;
                }
                add(type, Limb.valueOf(parts[1]), strength);
            } catch (IllegalArgumentException ignored) {
                // 坏行不致命
            }
        } else {
            try {
                double strength = Double.parseDouble(parts[1]);
                if (!Double.isFinite(strength)) {
                    return;
                }
                add(type, null, strength);
            } catch (NumberFormatException ignored) {
                // 坏行不致命
            }
        }
    }

    private double existingStrength(AfflictionType type, Limb limb) {
        AfflictionInstance instance = byKey.get(key(type, limb));
        return instance == null ? 0.0 : instance.strength();
    }

    private List<AfflictionInstance> sortedByStrengthDesc(AfflictionType type) {
        List<AfflictionInstance> matches = new ArrayList<>();
        for (AfflictionInstance instance : byKey.values()) {
            if (instance.type() == type) {
                matches.add(instance);
            }
        }
        matches.sort((a, b) -> Double.compare(b.strength(), a.strength()));
        return matches;
    }

    private static double reduceFrom(AfflictionInstance instance, double amount) {
        double before = instance.strength();
        instance.reduce(amount);
        return before - instance.strength();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
