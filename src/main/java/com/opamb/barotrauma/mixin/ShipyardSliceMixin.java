package com.opamb.barotrauma.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.opamb.barotrauma.client.ShipyardClient;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 造船厂编辑器 Y-Slice 剖面模式：隐藏工作平面上方的方块，方便直接编辑内部舱室与管线。 */
@Mixin(BlockRenderDispatcher.class)
public abstract class ShipyardSliceMixin {

    @Inject(method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void barotrauma$sliceAboveWorkPlane(BlockState state, BlockPos pos, BlockAndTintGetter level,
                                                PoseStack poseStack, VertexConsumer consumer,
                                                boolean checkSides, RandomSource random,
                                                ModelData modelData, RenderType renderType,
                                                CallbackInfo ci) {
        if (ShipyardClient.isSlicedOut(pos)) {
            ci.cancel();
        }
    }
}
