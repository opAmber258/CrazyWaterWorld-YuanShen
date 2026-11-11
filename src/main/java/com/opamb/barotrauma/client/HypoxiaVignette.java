package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 缺氧镜头效果：屏幕四边逐渐变黑（边框暗角）。
 *
 * <p>强度直接跟医疗服务系统的 {@code oxygenlow} 走（客户端从
 * {@link ClientMedicalCache} 读同步过来的病症行）。原作
 * {@code CharacterHealth.UpdateHUD} 的表现强度是
 * {@code min(1 - (OxygenAmount - 50) / 50, 1)}——即氧气 100→50（强度 0→50）
 * 时从无到最满，50 以下保持最满；这里用同一映射画边框暗角。</p>
 *
 * <p>画在 {@link RenderGuiEvent.Pre}：盖在世界之上、原版 HUD 和背包界面之下，
 * 所以开背包时暗角仍然在（和原作"打开界面不停止缺氧表现"一致）。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HypoxiaVignette {

    /** 原作视觉拉满对应的 oxygenlow 强度（OxygenAmount = 50）。 */
    private static final double FULL_STRENGTH = 50.0;
    /** 边缘最黑时的透明度。 */
    private static final float EDGE_ALPHA = 0.85F;
    /** 暗角宽度占屏幕短边的比例。 */
    private static final float BAND_RATIO = 0.30F;
    /** 低于这个强度不画，避免回氧尾巴一直挂着一层灰。 */
    private static final float MIN_SEVERITY = 0.01F;

    private HypoxiaVignette() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        double oxygenLow = ClientMedicalCache.strength(player.getUUID(), "oxygenlow");
        float severity = (float) Math.min(1.0, oxygenLow / FULL_STRENGTH);
        if (severity <= MIN_SEVERITY) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int band = Math.max(1, (int) (Math.min(width, height) * BAND_RATIO));
        int edgeAlpha = Math.round(EDGE_ALPHA * severity * 255.0F);

        // 上下各一条整宽渐变（GuiGraphics 的渐变只能沿 y 方向）。
        graphics.fillGradient(0, 0, width, band, edgeAlpha << 24, 0x00000000);
        graphics.fillGradient(0, height - band, width, height, 0x00000000, edgeAlpha << 24);

        // 左右按 1px 竖条画，透明度按二次曲线向内衰减。
        for (int i = 0; i < band; i++) {
            float t = 1.0F - i / (float) band;
            int alpha = Math.round(EDGE_ALPHA * severity * t * t * 255.0F);
            if (alpha <= 0) {
                continue;
            }
            int color = alpha << 24;
            graphics.fill(i, 0, i + 1, height, color);
            graphics.fill(width - 1 - i, 0, width - i, height, color);
        }
    }
}
