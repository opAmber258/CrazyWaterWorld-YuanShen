package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.FabricatorBlock;
import com.opamb.barotrauma.blockentity.FabricatorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 加工台渲染。2 格宽 × 3 格高 × 1 格深的多块，主方块在下排左列；
 * 模型 2.5 格高、2.16 格宽、1.32 格深。
 *
 * <p>GeckoLib 的变换顺序是 translate(0.5, 0, 0.5) → rotateBlock。在 rotateBlock 里
 * 先 translate 再 super.rotateBlock：这个平移处于最外层，是**世界坐标**，所以框架
 * 相对主方块中心的偏移可以直接按世界方向加（2 格宽、主方块在左列 → 右列方向半格）。</p>
 */
public class FabricatorRenderer extends GeoBlockRenderer<FabricatorBlockEntity> {

    public FabricatorRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "fabricator")));
    }

    @Override
    protected Direction getFacing(FabricatorBlockEntity animatable) {
        // 模型正面做在 +Z；取反后 GeckoLib 的 180° 旋转让正面朝向 FACING。
        return animatable.getBlockState().getValue(FabricatorBlock.FACING).getOpposite();
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        FabricatorBlockEntity entity = this.animatable;
        if (entity != null) {
            Direction blockFacing = entity.getBlockState().getValue(FabricatorBlock.FACING);
            Direction axis = blockFacing.getClockWise();
            poseStack.translate(axis.getStepX() * 0.5D, 0.0D, axis.getStepZ() * 0.5D);
        }
        super.rotateBlock(facing, poseStack);
    }
}
