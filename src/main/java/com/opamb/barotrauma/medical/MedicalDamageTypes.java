package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * 医疗系统的自定义伤害类型。
 *
 * <p>{@link #MEDICAL} 用于 vitality≤0 时的死亡结算：转化器看到它要直接放行
 * （否则"取消原版伤害→转病症→算出 vitality≤0→再造成伤害→再被取消"会死循环，
 * 玩家永远死不掉）。死亡消息键 death.attack.barotrauma.medical 在 lang 里定义。</p>
 */
public final class MedicalDamageTypes {

    public static final ResourceKey<DamageType> MEDICAL = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(BarotraumaMod.MOD_ID, "medical"));

    private MedicalDamageTypes() {
    }
}
