package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.equipment.EquipmentEffects;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 潜水服陆上减速挂在 {@code MOVEMENT_SPEED} 属性上，而原版 FOV 也按这个属性算，
 * 结果穿服会拉近镜头。这里把我们的 −40% 从 FOV 公式里扣掉：只保留减速，
 * 不再产生放大镜头。
 */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerFovMixin {

    /** 与 {@code EquipmentEffects.SLOW_MODIFIER} 的 MULTIPLY_TOTAL -0.4 对应。 */
    private static final double SUIT_SLOW_FACTOR = 0.6D;

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void barotrauma$removeSuitZoom(CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (!EquipmentEffects.hasDivingSuitSlowdown(player)) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        float walking = player.getAbilities().getWalkingSpeed();
        if (walking <= 0.0F) {
            return;
        }
        double now = speed.getValue();
        double natural = now / SUIT_SLOW_FACTOR;
        float factorNow = (float) (now / walking + 1.0D) / 2.0F;
        float factorNatural = (float) (natural / walking + 1.0D) / 2.0F;
        if (factorNow <= 0.0F) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue() * (factorNatural / factorNow));
    }
}
