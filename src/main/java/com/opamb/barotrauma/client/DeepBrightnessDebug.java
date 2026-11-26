package com.opamb.barotrauma.client;

import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 深海亮度/光照的客户端设置（调试器面板与 F7 共用）。
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li>{@link Mode#MOD}：模组光照。环境底光由 {@link #percent()} 调（0..100%），
 *       再叠加手电后处理、黑雾与水体吸收；{@link #seaFogDisabled()} 可单独关海雾。</li>
 *   <li>{@link Mode#VANILLA}：原版光照。光图放行原版，环境底光固定用
 *       {@link #vanillaAmbient()}（默认 0.1，即引入灯光系统前的维度值）；
 *       是否同时跳过黑雾/水体由 {@link #vanillaRestoresWater()} 决定。</li>
 * </ul>
 *
 * <p>只在本地单人深海、创造模式或权限 ≥ 2 时生效；设置本身保留到游戏退出。</p>
 */
public final class DeepBrightnessDebug {

    public enum Mode {
        MOD,
        VANILLA
    }

    private static final String MESSAGE = "item.barotrauma.brightness_debugger.";
    private static final float DEFAULT_VANILLA_AMBIENT = 0.1F;
    private static final float MAX_VANILLA_AMBIENT = 0.2F;

    private static int percent;
    private static Mode mode = Mode.MOD;
    private static float vanillaAmbient = DEFAULT_VANILLA_AMBIENT;
    private static boolean vanillaRestoresWater = true;
    private static boolean noSeaFog;

    private DeepBrightnessDebug() {
    }

    /** 打开面板前调用：允许时返回 true，否则提示作用范围。 */
    public static boolean activate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (!canUse(mc)) {
            mc.player.displayClientMessage(Component.translatable(MESSAGE + "scope"), true);
            return false;
        }
        return true;
    }

    /** 模组模式的额外环境底光（0..0.6），只在允许的世界里返回非零。 */
    public static float ambientMix() {
        return canUse() ? 0.60F * percent / 100.0F : 0.0F;
    }

    public static Mode mode() {
        return mode;
    }

    public static void setMode(Mode value) {
        mode = value == null ? Mode.MOD : value;
    }

    public static void toggleMode() {
        mode = mode == Mode.MOD ? Mode.VANILLA : Mode.MOD;
    }

    public static boolean isVanilla() {
        return mode == Mode.VANILLA;
    }

    public static int percent() {
        return percent;
    }

    public static void setPercent(int value) {
        percent = Mth.clamp(value, 0, 100);
    }

    public static float vanillaAmbient() {
        return vanillaAmbient;
    }

    public static void setVanillaAmbient(float value) {
        vanillaAmbient = Mth.clamp(value, 0.0F, MAX_VANILLA_AMBIENT);
    }

    /** 原版模式下是否同时跳过黑雾、黑色天空清屏与水体压暗。 */
    public static boolean vanillaRestoresWater() {
        return vanillaRestoresWater;
    }

    public static void setVanillaRestoresWater(boolean value) {
        vanillaRestoresWater = value;
    }

    public static boolean hidesWaterAndFog() {
        return isVanilla() && vanillaRestoresWater;
    }

    /** 模组模式下单独关闭海雾（原 DeepBrightnessDebug 的 Ctrl+右键功能）。 */
    public static boolean seaFogDisabled() {
        return noSeaFog && canUse();
    }

    public static void setSeaFogDisabled(boolean value) {
        noSeaFog = value;
    }

    public static void reset() {
        percent = 0;
        mode = Mode.MOD;
        vanillaAmbient = DEFAULT_VANILLA_AMBIENT;
        vanillaRestoresWater = true;
        noSeaFog = false;
    }

    static int nextPercent(int current, boolean reset) {
        return reset || current == 100 ? 0 : current + 25;
    }

    public static boolean canUse() {
        return canUse(Minecraft.getInstance());
    }

    private static boolean canUse(Minecraft mc) {
        return mc.isSingleplayer() && mc.level != null && mc.level.dimension().equals(ModDimensions.THE_DEEP)
                && mc.player != null && (mc.player.isCreative() || mc.player.hasPermissions(2));
    }
}
