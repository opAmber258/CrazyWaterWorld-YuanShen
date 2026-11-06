package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 空气海域里游泳时，让身体俯仰跟随视线方向。
 *
 * <p>原版 {@code PlayerRenderer#setupRotations} 只有在 {@code isInWater()}（或 Forge
 * 的流体判定）为真时，才把 {@code -90 - xRot} 作为身体俯仰；本模组的深海维度是
 * “空气海洋”（没有真实水方块），这个判定恒为 false，于是疾跑游泳时人物永远平躺，
 * 朝上/下游也不转身（用户实测“游泳动画的朝向被限制在平面”）。</p>
 *
 * <p>这里只把 {@link OceanEnvironment#isSubmerged} 补进这个判定，其余渲染逻辑保持原版。</p>
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerSwimRotationMixin {

    @Redirect(
            method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;isInWater()Z"))
    private boolean barotrauma$airOceanCountsAsWater(AbstractClientPlayer player) {
        return player.isInWater()
                || OceanEnvironment.isSubmerged(player.level(),
                        player.position().add(0.0D, 0.4D, 0.0D));
    }
}
