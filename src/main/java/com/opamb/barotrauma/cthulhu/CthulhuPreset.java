package com.opamb.barotrauma.cthulhu;

import com.opamb.barotrauma.registry.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.RegistryObject;

/**
 * 「克苏鲁的注视」视觉预设：每个预设对应一个状态效果，客户端用同一套后处理
 * （{@code shaders/post/cthulhu_gaze.json}）按 {@link #mode()} 分支渲染。
 *
 * <p>强度由效果等级（amplifier 0/1/2 = 弱/中/强）决定，时长由触发器面板单独选
 * （3/6/12 秒）。所有档位、时长都从同一个触发器里选，不额外造物品。</p>
 */
public enum CthulhuPreset {

    /** 鱼眼：桶形畸变 + RGB 色散 + 紫黑边缘 + 颗粒。 */
    FISHEYE("cthulhu_fisheye", ModEffects.CTHULHU_FISHEYE, 0, 0.76f, 0.4f,
            "鱼眼凝视", "画面鼓成鱼眼，边缘泛紫黑并带色散"),

    /** 深渊低语：多层波动 + 色相漂移。 */
    WHISPER("cthulhu_whisper", ModEffects.CTHULHU_WHISPER, 1, 0.82f, 1.0f,
            "深渊低语", "画面像水面一样缓慢波动，颜色不断漂移"),

    /** 星之彩：异常配色 + 像素化色块。 */
    COLOUR("cthulhu_colour", ModEffects.CTHULHU_COLOUR, 2, 0.92f, 0.5f,
            "星之彩", "颜色被扭成不属于这个世界的组合，并压成色块"),

    /** 时间涟漪：从中心向外扩散的同心波纹。 */
    RIPPLE("cthulhu_ripple", ModEffects.CTHULHU_RIPPLE, 3, 0.92f, 0.5f,
            "时间涟漪", "同心波纹从画面中心扩散，时间像被搅动"),

    /** 静电噪点：扫描线 + 雪花 + 偶发撕裂。 */
    STATIC("cthulhu_static", ModEffects.CTHULHU_STATIC, 4, 1.00f, 0.6f,
            "静电噪点", "扫描线与雪花噪点，偶尔整行画面撕裂"),

    /** 梦境漂移：规则网格错位 + 每格透视缩放 + 接缝压暗（阈限空间）。 */
    DREAM("cthulhu_dream", ModEffects.CTHULHU_DREAM, 5, 0.94f, 0.6f,
            "梦境漂移", "空间被切成规则的格子，每格透视错位，像走不出去的阈限空间");

    /** 效果注册名（同时是 lang key 的后缀）。 */
    private final String id;
    private final RegistryObject<MobEffect> effect;
    /** 后处理里的模式号（着色器 Mode uniform）。 */
    private final float mode;
    /** 满强度时的 FOV 系数（1.0 = 不变，越小越近）。 */
    private final float fovFactor;
    /** 镜头滚转的抖动幅度系数（越大越晕）。 */
    private final float rollFactor;
    private final String displayName;
    private final String description;

    CthulhuPreset(String id, RegistryObject<MobEffect> effect, float mode, float fovFactor,
                  float rollFactor, String displayName, String description) {
        this.id = id;
        this.effect = effect;
        this.mode = mode;
        this.fovFactor = fovFactor;
        this.rollFactor = rollFactor;
        this.displayName = displayName;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public MobEffect effect() {
        return effect.get();
    }

    public float mode() {
        return mode;
    }

    public float fovFactor() {
        return fovFactor;
    }

    public float rollFactor() {
        return rollFactor;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** 档位强度（弱/中/强），索引 = amplifier。 */
    public static float tierStrength(int amplifier) {
        return switch (amplifier) {
            case 0 -> 0.35f;
            case 1 -> 0.65f;
            default -> 1.0f;
        };
    }

    public static String tierName(int amplifier) {
        return switch (amplifier) {
            case 0 -> "弱";
            case 1 -> "中";
            default -> "强";
        };
    }

    /** 面板里可选的持续秒数。 */
    public static final int[] DURATION_SECONDS = {3, 6, 12};

    /** 网络包/旧存档容错：只接受 1…60 秒。 */
    public static int clampDurationSeconds(int seconds) {
        return Math.max(1, Math.min(60, seconds));
    }

    /** 越界返回 null（网络包/旧存档容错）。 */
    public static CthulhuPreset byIndex(int index) {
        CthulhuPreset[] values = values();
        return index < 0 || index >= values.length ? null : values[index];
    }
}
