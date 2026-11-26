package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.CoilgunBlock;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

final class CoilgunRenderer extends GeoBlockRenderer<TurretBlockEntity> {
    CoilgunRenderer() {
        super(new Model());
    }

    void draw(TurretBlockEntity turret, float partialTick, PoseStack pose,
              MultiBufferSource buffer, int light, int overlay) {
        // The remote camera is mounted on this assembly. Do not draw its own barrel across the lens.
        if (!TurretClient.isOperating(turret.getBlockPos())) {
            super.render(turret, partialTick, pose, buffer, light, overlay);
        }
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack pose) {
        var state = animatable.getBlockState();
        pose.mulPose(Axis.YP.rotationDegrees(-TurretBlockEntity.initialYaw(state)));
        if (TurretBlockEntity.mount(state) == Direction.DOWN) {
            pose.translate(0, 1, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
        }
        pose.translate(0, 0, CoilgunBlock.baseCenterZ());
    }

    private static final class Model extends DefaultedBlockGeoModel<TurretBlockEntity> {
        Model() {
            super(new ResourceLocation(BarotraumaMod.MOD_ID, "coilgun"));
        }

        @Override
        public RenderType getRenderType(TurretBlockEntity turret, ResourceLocation texture) {
            return RenderType.entityCutout(texture);
        }

        @Override
        public void setCustomAnimations(TurretBlockEntity turret, long instanceId,
                                        AnimationState<TurretBlockEntity> state) {
            float partial = state.getPartialTick();
            float mountSign = TurretBlockEntity.mount(turret.getBlockState()) == Direction.DOWN ? -1 : 1;
            float yaw = Mth.rotLerp(partial, turret.prevYaw(), turret.yaw());
            float localYaw = Mth.wrapDegrees(yaw - TurretBlockEntity.initialYaw(turret.getBlockState()));
            float pitch = Mth.lerp(partial, turret.prevPitch(), turret.pitch());
            getBone("turret_yaw").ifPresent(bone -> bone.setRotY(-localYaw * mountSign * Mth.DEG_TO_RAD));
            getBone("gun_pitch").ifPresent(bone -> bone.setRotX(pitch * mountSign * Mth.DEG_TO_RAD));
            getBone("pitch_wheel_left").ifPresent(bone -> bone.setRotX(-pitch * mountSign * Mth.DEG_TO_RAD));
            getBone("pitch_wheel_right").ifPresent(bone -> bone.setRotX(-pitch * mountSign * Mth.DEG_TO_RAD));
        }
    }
}
