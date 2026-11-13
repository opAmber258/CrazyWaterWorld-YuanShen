package com.opamb.barotrauma.mission;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 一条货运任务的货物清单：照原版 {@code Content/Missions.xml} 的 {@code <Items>} 配置。
 *
 * <p>原版是 {@code <Item identifier="chemicalcrate" reward="140" maxcount="20">} + 嵌套的
 * {@code <Item identifier="antidama2" amount="5"/>}；这里把每箱的单价、箱型、箱内清单
 * 直接写成数据（JSON 目录只存任务名/报酬等摘要，不重复搬 XML）。</p>
 *
 * <p>箱内物品按原作 identifier 映射到本模组已注册的物品：能对上的直接用
 * （morphine/fentanyl/bandage/antibiotic_glue/saline 就是 original 的 antidama1/
 * antidama2/antibleeding1/antibleeding3/antibloodloss1），对不上的先做占位物品。</p>
 */
public record CargoManifest(String crateItemId, int rewardPerCrate, int maxCrates,
                            List<Entry> contents) {

    /** 箱内一件物品：模组物品 id + 数量。 */
    public record Entry(String itemId, int count) {
    }

    /** 寒冷深穴里 4 条在售货运（数值取自 Missions.xml）。 */
    private static final Map<String, CargoManifest> BY_MISSION = Map.of(
            // 化学品运输：化学药剂箱 140/箱；antidama2=芬太尼（已有）
            "cargochemicals", new CargoManifest("chemicalcrate", 140, 20, List.of(
                    new Entry("fentanyl", 5),
                    new Entry("sulphuricacid", 5),
                    new Entry("morbusine", 5),
                    new Entry("flashpowder", 3),
                    new Entry("chloralhydrate", 2))),
            // 医疗物资运输：医疗箱 120/箱；原作 antibloodloss1=生理盐水、antibleeding1=绷带
            "cargomeditems", new CargoManifest("mediccrate", 120, 20, List.of(
                    new Entry("morphine", 5),
                    new Entry("fentanyl", 5),
                    new Entry("saline", 5),
                    new Entry("antibiotics", 5),
                    new Entry("bandage", 16),
                    new Entry("antibiotic_glue", 6))),
            // 矿业中心建材运输：金属箱 200/箱
            "cargoconstructionminerals", new CargoManifest("metalcrate", 200, 25, List.of(
                    new Entry("iron", 10),
                    new Entry("lead", 10),
                    new Entry("copper", 10),
                    new Entry("titanium", 10))),
            // 易爆货物（挥发）：炸药收纳箱 400/箱；原作每个雷管里还塞 1 份挥发性叠氮化合物
            "cargovolatilecompoundn", new CargoManifest("explosivecrate", 400, 10, List.of(
                    new Entry("volatilecompoundn", 20),
                    new Entry("detonator", 10))));

    /** 按任务定义 id（例如 {@code cargochemicals}）取货物清单；不是货运任务返回 null。 */
    @Nullable
    public static CargoManifest forMission(String missionId) {
        return missionId == null ? null : BY_MISSION.get(missionId);
    }

    /** 4 格货架够装的最大箱数（原版按船上 cargocontainer 空位算，这里只用于界面提示）。 */
    public static final int TYPICAL_SHELF_CRATES = 4;

    /** 原版默认 {@code requireddeliveryamount}：98% 的箱子必须还在船上。 */
    public static final float REQUIRED_DELIVERY_AMOUNT = 0.98F;

    /**
     * 原版完成判定：{@code 交到的箱数 / 总箱数 >= 0.98}。
     *
     * <p>箱数少的时候 98% 等价于"一箱都不能少"（4 箱丢 1 箱 = 0.75、20 箱丢 1 箱 = 0.95
     * 都失败）；一次 50 箱以上才容得下丢 1 箱。</p>
     */
    public static boolean deliveredEnough(int total, int delivered) {
        return total > 0 && (float) delivered / (float) total >= REQUIRED_DELIVERY_AMOUNT;
    }
}
