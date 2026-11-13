package com.opamb.barotrauma.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 去掉原版氧气泡条与心数血条。
 *
 * <p>本模组用空气海域 + 医疗服务系统的 OXYGEN_LOW 表达缺氧，原版那排气泡既不
 * 对应潜水服氧气瓶，也不对应缺氧病症（它只看玩家空气值）。缺氧反馈改由
 * {@code HypoxiaVignette} 的屏幕边框变黑承担。</p>
 *
 * <p>血条改由 {@code BaroHud} 在右下角按潜渊症布局重画（长方形活力条 + 残影动画），
 * 原版 10 颗心整段取消；饥饿/载具血量等其它 HUD 不受影响。</p>
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

    /** 原版心数血条整段不画；活力条由 {@code BaroHud} 负责。 */
    @Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
    private void barotrauma$hideVanillaHearts(GuiGraphics graphics, Player player,
                                              int x, int y, int height, int offset,
                                              float health, int lastHealth, int healthDiff,
                                              int absorption, boolean highlight, CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * 双手持物品时，热键栏的选中框改画在左侧的双手格里，原版副手指示格也一起藏掉。
     *
     * <p>{@code renderHotbar} 里三次 {@code blit} 依次是：热键栏底图（182×22）、
     * 选中框（0,22,24×22）、副手格（u=24/53, v=22, 29×24）。按贴图参数区分，
     * 热键栏底图照常画。</p>
     */
    @Redirect(method = "renderHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void barotrauma$hideHotbarSelectionWhenCarrying(GuiGraphics graphics, ResourceLocation location,
                                                            int x, int y, int u, int v,
                                                            int width, int height) {
        Player player = Minecraft.getInstance().player;
        if (player != null && com.opamb.barotrauma.item.TwoHanded.isCarrying(player)) {
            boolean selection = v == 22 && u == 0 && width == 24;
            boolean offhandIndicator = v == 22 && width == 29;
            if (selection || offhandIndicator) {
                return;
            }
        }
        graphics.blit(location, x, y, u, v, width, height);
    }
}
