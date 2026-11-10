package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.HatchLadderBlock;
import com.opamb.barotrauma.blockentity.HatchBlockEntity;
import com.opamb.barotrauma.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 舱口渲染器。
 *
 * <p>舱口本体由 GeckoLib 的方块实体模型渲染；如果中段是带梯变种
 * {@link HatchLadderBlock}，再在舱口模型之上画一块铁梯模型。梯子是方块自带的，
 * 不随 {@code open} 开合消失——关着舱口时梯子照样在。</p>
 *
 * <p>模型顶点直接建在方块顶面（geo 里 y=15..17），渲染器不再做整体抬高，
 * 和碰撞/拾取形状 y=15..16 对齐。</p>
 *
 * <p>模型是<b>单面</b>的：GeckoLib 默认 {@code entityCutoutNoCull} 会把背面也画出来，
 * 镜头进到舱口格内部（例如关着的舱口下方）时会看到盖板/门框的内壁。
 * 这里改成 {@code entityCutout}（剔除背面），模型内部不再有面。</p>
 */
public class HatchRenderer implements BlockEntityRenderer<HatchBlockEntity> {

    /** 舱口模型：只画正面，镜头在模型内部时不会看到内壁。 */
    private static final class CulledHatchModel extends DefaultedBlockGeoModel<HatchBlockEntity> {
        CulledHatchModel(ResourceLocation id) {
            super(id);
        }

        @Override
        public RenderType getRenderType(HatchBlockEntity animatable, ResourceLocation texture) {
            return RenderType.entityCutout(texture);
        }
    }

    private final GeoBlockRenderer<HatchBlockEntity> geo = new GeoBlockRenderer<>(
            new CulledHatchModel(new ResourceLocation(BarotraumaMod.MOD_ID, "hatch")));

    public HatchRenderer() {
    }

    @Override
    public void render(HatchBlockEntity animatable, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        geo.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
        BlockState state = animatable.getBlockState();
        if (!(state.getBlock() instanceof HatchLadderBlock)) {
            return;
        }
        BlockState ladder = ModBlocks.IRON_LADDER.get().defaultBlockState()
                .setValue(LadderBlock.FACING, HatchLadderBlock.ladderFacing(state))
                .setValue(LadderBlock.WATERLOGGED, Boolean.FALSE);
        poseStack.pushPose();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ladder, poseStack,
                bufferSource, packedLight, packedOverlay, ModelData.EMPTY, RenderType.cutout());
        poseStack.popPose();
    }
}
