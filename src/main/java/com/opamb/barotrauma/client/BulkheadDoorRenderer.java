package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.BulkheadDoorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 舱壁门渲染器。
 *
 * <p>资源路径：{@code geo/block/bulkhead_door.geo.json}、
 * {@code animations/block/bulkhead_door.animation.json}、
 * {@code textures/block/bulkhead_door.png}。</p>
 *
 * <p>门关着时整格完全不透光（{@code getLightBlock}=15），方块自己那一格的光照是 0，
 * 所以渲染前用 {@link BlockLightSampler} 换成周围空气格的最亮值——
 * 否则手电筒照在关着的门上会整块全黑。</p>
 *
 * <p>这里用组合而不是继承 {@link GeoBlockRenderer}：4.8.4 的
 * {@code render} 擦除后参数是 {@code BlockEntity}，和
 * {@code BlockEntityRenderer<BulkheadDoorBlockEntity>} 的擦除签名冲突，没法直接覆盖。</p>
 */
public class BulkheadDoorRenderer implements BlockEntityRenderer<BulkheadDoorBlockEntity> {

    private final GeoBlockRenderer<BulkheadDoorBlockEntity> geo = new GeoBlockRenderer<>(
            new DefaultedBlockGeoModel<>(
                    new ResourceLocation(BarotraumaMod.MOD_ID, "bulkhead_door")));

    public BulkheadDoorRenderer() {
    }

    @Override
    public void render(BulkheadDoorBlockEntity animatable, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        geo.render(animatable, partialTick, poseStack, bufferSource,
                BlockLightSampler.neighborLight(animatable, packedLight), packedOverlay);
    }
}
