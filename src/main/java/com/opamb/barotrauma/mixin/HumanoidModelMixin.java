package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称抱箱姿态：两条手臂都抬到身前、向内收，和手里的箱子一起形成抱持动作。
 *
 * <p>注入 {@code HumanoidModel.setupAnim} 的 TAIL：原版的手臂摆动/物品姿势已经
 * 算完，这里直接覆盖；{@code PlayerModel} 随后会把袖子复制过去。</p>
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void barotrauma$twoHandedArmPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                             float ageInTicks, float netHeadYaw, float headPitch,
                                             CallbackInfo ci) {
        if (!(entity instanceof Player player) || !TwoHanded.isCarrying(player)) {
            return;
        }
        // 主手还拿着别的东西时不抢手臂：让原版照常渲染那件物品。
        if (!player.getMainHandItem().isEmpty()) {
            return;
        }
        HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;
        self.rightArm.xRot = -0.85F;
        self.rightArm.yRot = -0.45F;
        self.rightArm.zRot = 0.0F;
        self.leftArm.xRot = -0.85F;
        self.leftArm.yRot = 0.45F;
        self.leftArm.zRot = 0.0F;
    }
}
