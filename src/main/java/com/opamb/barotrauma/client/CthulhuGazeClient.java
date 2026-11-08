package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.cthulhu.CthulhuPreset;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 「克苏鲁的注视」的客户端表现：镜头拉近 + 后处理 + 进出场过渡。
 *
 * <p>服务端只挂 MobEffect（amplifier 0/1/2 = 弱/中/强）。这里每 tick：</p>
 * <ul>
 *   <li>从玩家身上的效果读出预设与档位，算出目标强度；</li>
 *   <li>强度按线性包络淡入/淡出（效果结束后仍继续淡出，避免画面硬切）；</li>
 *   <li>同一套后处理 {@code cthulhu_gaze} 用 Mode/Strength uniform 切换模式与强度；</li>
 *   <li>FOV 随强度收拢并做呼吸脉动，镜头附加轻微滚转，让异常“活”起来。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CthulhuGazeClient {

    private static final ResourceLocation SHADER =
            new ResourceLocation(BarotraumaMod.MOD_ID, "shaders/post/cthulhu_gaze.json");

    /** 淡入每 tick 增量（约 0.7 秒到满）。 */
    private static final float FADE_IN_STEP = 0.05F;
    /** 淡出每 tick 减量（约 1.2 秒归零，作为退出动画）。 */
    private static final float FADE_OUT_STEP = 0.028F;

    /** 最近一次生效的预设（淡出期间保留，用于继续渲染）。 */
    private static CthulhuPreset activePreset;
    private static int activeTier;
    /** 0…1 的视觉强度（档位 + 淡入淡出包络）。 */
    private static float strength;
    /** 后处理是否已加载。 */
    private static boolean shaderLoaded;
    /** 我们加载的 PostChain（卸载时避免误关别的效果，如倒地模糊）。 */
    private static PostChain ourChain;

    private static Field passesField;

    private CthulhuGazeClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            reset(mc);
            return;
        }

        CthulhuPreset found = null;
        int amplifier = 0;
        for (CthulhuPreset preset : CthulhuPreset.values()) {
            MobEffectInstance instance = player.getEffect(preset.effect());
            if (instance != null) {
                found = preset;
                amplifier = instance.getAmplifier();
                break;
            }
        }
        if (found != null) {
            activePreset = found;
            activeTier = Math.max(0, Math.min(2, amplifier));
        }

        float target = found != null ? CthulhuPreset.tierStrength(activeTier) : 0.0F;
        if (strength < target) {
            strength = Math.min(target, strength + FADE_IN_STEP);
        } else if (strength > target) {
            strength = Math.max(target, strength - FADE_OUT_STEP);
        }

        if (strength > 0.001F && activePreset != null) {
            if (!shaderLoaded || mc.gameRenderer.currentEffect() == null) {
                mc.gameRenderer.loadEffect(SHADER);
                ourChain = mc.gameRenderer.currentEffect();
                shaderLoaded = true;
            }
            updateUniforms(strength, activePreset.mode());
        } else if (shaderLoaded) {
            // 只在当前后处理还是我们的时候卸载，别把倒地模糊等效果一起关掉
            if (mc.gameRenderer.currentEffect() == ourChain || mc.gameRenderer.currentEffect() == null) {
                mc.gameRenderer.shutdownEffect();
            }
            shaderLoaded = false;
            ourChain = null;
            activePreset = null;
            strength = 0.0F;
        }
    }

    /** 镜头拉近 + 呼吸脉动（强度已含淡入淡出，所以进出场自然）。 */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (strength <= 0.001F || activePreset == null) {
            return;
        }
        double factor = 1.0 + (activePreset.fovFactor() - 1.0) * strength;
        double pulse = 1.0 + 0.012 * Math.sin(shaderTime() * 1.7) * strength;
        event.setFOV(event.getFOV() * factor * pulse);
    }

    /** 轻微镜头滚转：低语/旧日更明显，像视线被什么东西拽着。 */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (strength <= 0.001F || activePreset == null) {
            return;
        }
        double phase = shaderTime() * 1.25;
        float roll = (float) (Math.sin(phase) * 1.8 * strength * activePreset.rollFactor());
        event.setRoll(event.getRoll() + roll);
    }

    private static double shaderTime() {
        return Util.getMillis() / 1000.0;
    }

    private static void reset(Minecraft mc) {
        if (shaderLoaded) {
            if (mc.gameRenderer.currentEffect() == ourChain || mc.gameRenderer.currentEffect() == null) {
                mc.gameRenderer.shutdownEffect();
            }
            shaderLoaded = false;
        }
        ourChain = null;
        activePreset = null;
        activeTier = 0;
        strength = 0.0F;
    }

    /** 把 Strength / Mode 写进后处理里的每个 pass；反射拿 passes（没有公开 getter）。 */
    private static void updateUniforms(float value, float mode) {
        PostChain chain = Minecraft.getInstance().gameRenderer.currentEffect();
        if (chain == null) {
            return;
        }
        try {
            if (passesField == null) {
                passesField = PostChain.class.getDeclaredField("passes");
                passesField.setAccessible(true);
            }
            Object raw = passesField.get(chain);
            if (!(raw instanceof List<?> list)) {
                return;
            }
            for (Object element : list) {
                if (!(element instanceof PostPass pass)) {
                    continue;
                }
                EffectInstance effect = pass.getEffect();
                setUniform(effect, "Strength", value);
                setUniform(effect, "Mode", mode);
            }
        } catch (Throwable ignored) {
            // 拿不到就保持默认值（Strength=0 时着色器直接输出原画面，不会花屏）
        }
    }

    private static void setUniform(EffectInstance effect, String name, float value) {
        Uniform uniform = effect.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }
}
