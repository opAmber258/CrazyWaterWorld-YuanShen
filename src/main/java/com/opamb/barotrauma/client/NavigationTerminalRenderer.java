package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.NavigationTerminalBlock;
import com.opamb.barotrauma.blockentity.NavigationTerminalBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 导航终端渲染：三格宽 GeckoLib 静态模型 + 台面上方的 3D 点云全息。
 *
 * <p>模型做在 +Z（南），和反应堆/压载泵一样把 {@link NavigationTerminalBlock#FACING}
 * 取反再交给 GeckoLib。全息用 {@link SonarHologram}，锚点抬到模型顶面之上
 * （模型高 2.375 格，取 2.85 格，给下方距离环留 0.45 格）。</p>
 *
 * <p>GeckoLib 的 {@code GeoBlockRenderer.render} 擦除到 {@code BlockEntity}，
 * 直接继承再重写会撞名；按舱口渲染器的写法包一层委托。</p>
 */
public class NavigationTerminalRenderer implements BlockEntityRenderer<NavigationTerminalBlockEntity> {

    /** 全息相对方块底面的高度（模型顶 2.375 格）。 */
    private static final float HOLOGRAM_Y = 2.85F;

    private final GeoBlockRenderer<NavigationTerminalBlockEntity> geo = new GeoBlockRenderer<>(
            new DefaultedBlockGeoModel<>(
                    new ResourceLocation(BarotraumaMod.MOD_ID, "navigation_terminal"))) {
        @Override
        protected Direction getFacing(NavigationTerminalBlockEntity animatable) {
            return animatable.getBlockState().getValue(NavigationTerminalBlock.FACING).getOpposite();
        }
    };

    public NavigationTerminalRenderer() {
    }

    @Override
    public void render(NavigationTerminalBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        geo.render(be, partialTick, pose, buffers, packedLight, packedOverlay);
        SonarHologram.render(be.points(), be.getBlockPos(), HOLOGRAM_Y, pose, buffers,
                be.getLevel() == null ? 0L : be.getLevel().getGameTime());
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
