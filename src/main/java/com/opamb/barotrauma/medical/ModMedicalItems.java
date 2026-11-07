package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.medical.affliction.TreatmentSpec;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 医疗物品注册（M2）：绷带 / 抗生素凝胶 / 吗啡 / 血袋 / 生理盐水。
 *
 * <p>数值来自 vanilla-baseline.md §4；画皮（可塑人造皮肤）按用户指示暂不做，
 * 合成表留给制造模块。</p>
 */
public final class ModMedicalItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BarotraumaMod.MOD_ID);

    /** 绷带：止血 + 轻微烧伤处理，一次性（原作 Condition -100）。 */
    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage",
            () -> new MedicalItem(new Item.Properties().stacksTo(16), TreatmentSpec.BANDAGE));

    /** 抗生素凝胶：强效止血（30/s×2s），副作用器官损伤（近似为钝器伤），可用 8 次。 */
    public static final RegistryObject<Item> ANTIBIOTIC_GLUE = ITEMS.register("antibiotic_glue",
            () -> new MedicalItem(new Item.Properties().stacksTo(4).durability(8), TreatmentSpec.ANTIBIOTIC_GLUE));

    /** 吗啡：镇痛（降损伤类），副作用缺氧（原作：抑制呼吸）。一次性。 */
    public static final RegistryObject<Item> MORPHINE = ITEMS.register("morphine",
            () -> new MedicalItem(new Item.Properties().stacksTo(16), TreatmentSpec.MORPHINE));

    /** 血袋：快速回失血（8/s×15s，合计 120 ≥ 上限 100）。一次性。 */
    public static final RegistryObject<Item> BLOOD_PACK = ITEMS.register("blood_pack",
            () -> new MedicalItem(new Item.Properties().stacksTo(16), TreatmentSpec.BLOOD_PACK));

    /** 生理盐水：慢速回失血（2.5/s×10s，合计 25）。一次性。 */
    public static final RegistryObject<Item> SALINE = ITEMS.register("saline",
            () -> new MedicalItem(new Item.Properties().stacksTo(16), TreatmentSpec.SALINE));

    /** 纳洛酮：阿片中毒解药（2/s×30s，原作 antinarc 同款）。一次性。 */
    public static final RegistryObject<Item> NALOXONE = ITEMS.register("naloxone",
            () -> new MedicalItem(new Item.Properties().stacksTo(16), TreatmentSpec.NALOXONE));

    /** 芬太尼：强效镇痛（损伤 −15/s×5s），副作用缺氧/阿片中毒/成瘾（原作 antidama2 同款）。一次性。 */
    public static final RegistryObject<Item> FENTANYL = ITEMS.register("fentanyl",
            () -> new MedicalItem(new Item.Properties().stacksTo(16), TreatmentSpec.FENTANYL));

    private ModMedicalItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
