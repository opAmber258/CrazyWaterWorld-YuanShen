package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.lang.management.ManagementFactory;

/**
 * 6700X3D 右键面板：在物品栏上方显示本机 CPU / 显卡 / 内存，6 秒后自动隐藏。
 *
 * <p>只读展示，不参与游戏逻辑；GPU 名字来自当前 OpenGL 上下文，CPU 名字优先用
 * Windows 的 {@code PROCESSOR_IDENTIFIER}，内存用 {@code com.sun.management} 的物理内存。
 * 文本走翻译键，中英文语言文件都有。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CpuInfoOverlay {

    private static final long VISIBLE_MILLIS = 6000L;
    private static final int COLOR = 0xFF9FE8D0;

    private static long expiresAt;
    private static Component[] cachedLines;

    private CpuInfoOverlay() {
    }

    /** 由 CPU 方块在客户端调用；每次显示都重建缓存，保证可用内存等数值是最新的。 */
    public static void show() {
        expiresAt = Util.getMillis() + VISIBLE_MILLIS;
        cachedLines = null;
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "cpu_info",
                (gui, graphics, partialTick, width, height) -> render(graphics, width, height));
    }

    private static void render(GuiGraphics graphics, int width, int height) {
        if (Util.getMillis() > expiresAt) {
            return;
        }
        Component[] lines = lines();
        Minecraft mc = Minecraft.getInstance();
        float scale = 0.75F;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, height - 64.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        for (int i = 0; i < lines.length; i++) {
            int lineWidth = mc.font.width(lines[i]);
            graphics.drawString(mc.font, lines[i], -lineWidth / 2, i * 10, COLOR, true);
        }
        graphics.pose().popPose();
    }

    private static Component[] lines() {
        if (cachedLines == null) {
            cachedLines = buildLines();
        }
        return cachedLines;
    }

    private static Component[] buildLines() {
        int threads = Runtime.getRuntime().availableProcessors();
        String identifier = System.getenv("PROCESSOR_IDENTIFIER");
        Component cpuLine;
        if (identifier != null && !identifier.isBlank()) {
            String model = identifier.trim();
            if (model.length() > 64) {
                model = model.substring(0, 64) + "…";
            }
            cpuLine = Component.translatable("gui.barotrauma.cpu.model", model, threads);
        } else {
            // 拿不到型号时如实标注为体系结构，不冒充 CPU 型号
            cpuLine = Component.translatable("gui.barotrauma.cpu.arch",
                    System.getProperty("os.arch", "unknown"), threads);
        }
        String gpu = "unknown";
        try {
            String renderer = GL11.glGetString(GL11.GL_RENDERER);
            if (renderer != null && !renderer.isBlank()) {
                gpu = renderer;
            }
        } catch (Throwable ignored) {
            // 没有 GL 上下文时保留 unknown
        }
        Component gpuLine = Component.translatable("gui.barotrauma.cpu.gpu", gpu);
        Component ramLine;
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
            String total = String.format("%.1f", sun.getTotalMemorySize() / 1073741824.0D);
            String free = String.format("%.1f", sun.getFreeMemorySize() / 1073741824.0D);
            ramLine = Component.translatable("gui.barotrauma.cpu.ram", total, free);
        } else {
            String max = String.format("%.1f", Runtime.getRuntime().maxMemory() / 1073741824.0D);
            ramLine = Component.translatable("gui.barotrauma.cpu.ram_jvm", max);
        }
        return new Component[]{cpuLine, gpuLine, ramLine};
    }
}
