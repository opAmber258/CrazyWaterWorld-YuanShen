package com.opamb.barotrauma.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 去掉原版氧气泡条。
 *
 * <p>本模组用空气海域 + 医疗服务系统的 OXYGEN_LOW 表达缺氧，原版那排气泡既不
 * 对应潜水服氧气瓶，也不对应缺氧病症（它只看玩家空气值）。缺氧反馈改由
 * {@code HypoxiaVignette} 的屏幕边框变黑承担。</p>
 *
 * <p>只改 {@code Gui.renderPlayerHealth} 里"是否画气泡"的两个判断：
 * 空气值当成满的、眼睛当成不在水里——心跳/饥饿/载具血量等其它 HUD 不受影响。</p>
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Redirect(method = "renderPlayerHealth",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getAirSupply()I"))
    private int barotrauma$hideAirBubbles(Player player) {
        return player.getMaxAirSupply();
    }

    @Redirect(method = "renderPlayerHealth",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean barotrauma$neverEyeInWater(Player player, TagKey<Fluid> fluid) {
        return false;
    }
}
