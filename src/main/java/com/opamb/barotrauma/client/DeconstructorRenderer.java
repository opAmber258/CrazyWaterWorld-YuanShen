package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.DeconstructorBlock;
import com.opamb.barotrauma.blockentity.DeconstructorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 解构仪渲染。2 格宽 × 3 格高 × 1 格深的多块，主方块在下排左列；
 * 模型 2.5 格高、1.98 格宽、1.54 格深。工作时由方块状态 working 驱动
 * 双刀轴 1 秒循环动画。
 *
 * <p>平移放在 rotateBlock 的 super 之前：这是最外层变换，按世界方向偏半格到
 * 框架中心（2 格宽、主方块在左列）。</p>
 */
public class DeconstructorRenderer extends GeoBlockRenderer<DeconstructorBlockEntity> {

    public DeconstructorRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "deconstructor")));
    }

    @Override
    protected Direction getFacing(DeconstructorBlockEntity animatable) {
        return animatable.getBlockState().getValue(DeconstructorBlock.FACING).getOpposite();
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        DeconstructorBlockEntity entity = this.animatable;
        if (entity != null) {
            Direction blockFacing = entity.getBlockState().getValue(DeconstructorBlock.FACING);
            Direction axis = blockFacing.getClockWise();
            poseStack.translate(axis.getStepX() * 0.5D, 0.0D, axis.getStepZ() * 0.5D);
        }
        super.rotateBlock(facing, poseStack);
    }
}
