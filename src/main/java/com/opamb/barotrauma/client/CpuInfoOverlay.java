package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 * Windows 的 {@code PROCESSOR_IDENTIFIER}，内存用 {@code com.sun.management} 的物理内存。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CpuInfoOverlay {

    private static final long VISIBLE_MILLIS = 6000L;
    private static final int COLOR = 0xFF9FE8D0;

    private static long expiresAt;
    private static String[] cachedLines;

    private CpuInfoOverlay() {
    }

    /** 由 CPU 物品在客户端调用。 */
    public static void show() {
        expiresAt = Util.getMillis() + VISIBLE_MILLIS;
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
        String[] lines = lines();
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

    private static String[] lines() {
        if (cachedLines == null) {
            cachedLines = buildLines();
        }
        return cachedLines;
    }

    private static String[] buildLines() {
        String cpu = System.getenv("PROCESSOR_IDENTIFIER");
        if (cpu == null || cpu.isBlank()) {
            cpu = System.getProperty("os.arch", "unknown");
        }
        cpu = cpu.trim();
        if (cpu.length() > 64) {
            cpu = cpu.substring(0, 64) + "…";
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
        String ram;
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
            double total = sun.getTotalMemorySize() / 1073741824.0D;
            double free = sun.getFreeMemorySize() / 1073741824.0D;
            ram = String.format("%.1f GB（可用 %.1f GB）", total, free);
        } else {
            double max = Runtime.getRuntime().maxMemory() / 1073741824.0D;
            ram = String.format("%.1f GB（JVM 上限）", max);
        }
        return new String[]{
                "CPU  " + cpu + " × " + Runtime.getRuntime().availableProcessors(),
                "GPU  " + gpu,
                "内存  " + ram
        };
    }
}
