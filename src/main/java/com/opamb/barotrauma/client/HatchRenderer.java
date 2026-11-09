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
 */
public class HatchRenderer implements BlockEntityRenderer<HatchBlockEntity> {

    private final GeoBlockRenderer<HatchBlockEntity> geo = new GeoBlockRenderer<>(
            new DefaultedBlockGeoModel<>(new ResourceLocation(BarotraumaMod.MOD_ID, "hatch")));

    public HatchRenderer() {
    }

    @Override
    public void render(HatchBlockEntity animatable, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 模型整体抬到方块顶面：盖板和地板/天花板齐平，碰撞/拾取形状也是 y=15..16。
        // 爬梯子顶到关闭的舱口时，看到的是正常天花板，而不是悬在半空的薄板。
        poseStack.pushPose();
        poseStack.translate(0.0D, 7.5D / 16.0D, 0.0D);
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
