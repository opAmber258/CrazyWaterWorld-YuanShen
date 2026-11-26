package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.gui.ScaledScreen;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.NightVisionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * 深海亮度调节器面板（右键开发者物品打开）。
 *
 * <p>把原来分散的两套调试入口合成一个表单：</p>
 * <ul>
 *   <li>光照模式：模组光照 / 原版光照（F7 仍可快捷切换）；</li>
 *   <li>模组环境底光 0..100%（沿用旧调试器的 ambientMix）；</li>
 *   <li>原版环境底光 0.00..0.20（默认 0.10，即引入灯光系统前的维度值）；</li>
 *   <li>原版模式是否同时还原雾与水体（关掉则只换光图）；</li>
 *   <li>模组模式下单独关闭海雾；</li>
 *   <li>一键给予/移除无限时长夜视（服务端校验创造/权限）。</li>
 * </ul>
 *
 * <p>所有改动立即生效，不经过服务端；Esc / 完成关闭。</p>
 */
public final class BrightnessDebuggerScreen extends ScaledScreen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 298;

    private static final int BG = 0xF01B2320;
    private static final int EDGE = 0xFF3B463C;
    private static final int CARD = 0xFF1E2721;
    private static final int CARD_HOVER = 0xFF24302A;
    private static final int BOX = 0xFF232B25;
    private static final int TRACK = 0xFF101713;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF8B9184;
    private static final int ACCENT = 0xFFE0762F;
    private static final int ACCENT_LIGHT = 0xFFF2A063;
    private static final int GREEN = 0xFF7BE38B;
    private static final int HOVER = 0xFF39483A;
    private static final int DISABLED = 0xFF5A6157;

    private static final int MODE_Y = 30;
    private static final int MODE_H = 18;
    private static final int MOD_BTN_X = 92;
    private static final int VANILLA_BTN_X = 192;
    private static final int MODE_BTN_W = 96;

    private static final int CARD_X = 10;
    private static final int CARD_W = PANEL_W - 2 * CARD_X;
    private static final int CARD_H = 48;
    private static final int MOD_CARD_Y = 56;
    private static final int VANILLA_CARD_Y = 110;
    private static final int TOGGLE_CARD_Y = 162;
    private static final int TOGGLE_CARD_H = 34;

    private static final int NIGHT_CARD_Y = 200;
    private static final int NIGHT_CARD_H = 26;
    private static final int NIGHT_BTN_W = 84;
    private static final int NIGHT_BTN_H = 14;

    private static final int FOG_CARD_Y = 230;
    private static final int FOG_CARD_H = 26;

    private static final int HINT_Y = 262;
    private static final int BUTTON_Y = 274;
    private static final int BUTTON_H = 18;
    private static final int RESET_W = 96;
    private static final int DONE_W = 96;

    private static final float VANILLA_MIN = 0.0F;
    private static final float VANILLA_MAX = 0.2F;
    private static final float VANILLA_STEP = 0.01F;

    /** 0 = 没拖，1 = 模组底光，2 = 原版底光。 */
    private int dragging;

    public BrightnessDebuggerScreen() {
        super(Component.translatable("brightness.screen.title"), PANEL_W, PANEL_H);
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!DeepBrightnessDebug.activate()) {
            return;
        }
        mc.setScreen(new BrightnessDebuggerScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /* ------------------------------------------------------------------ 绘制 */

    @Override
    protected void renderDesign(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean vanilla = DeepBrightnessDebug.isVanilla();

        graphics.fill(0, 0, PANEL_W, PANEL_H, BG);
        frame(graphics, 0, 0, PANEL_W, PANEL_H, EDGE);
        graphics.fill(CARD_X, 26, PANEL_W - CARD_X, 27, EDGE);

        graphics.fill(1, 8, 3, 19, ACCENT);
        graphics.drawString(font, title, 12, 9, ACCENT, false);
        Component status = Component.translatable("brightness.screen.live");
        graphics.drawString(font, status, PANEL_W - CARD_X - font.width(status), 9, DIM, false);

        graphics.drawString(font, Component.translatable("brightness.screen.mode"),
                CARD_X, MODE_Y + 5, TEXT, false);
        drawModeButton(graphics, MOD_BTN_X, MODE_Y, MODE_BTN_W, MODE_H, mouseX, mouseY,
                Component.translatable("brightness.screen.mode.mod"), !vanilla);
        drawModeButton(graphics, VANILLA_BTN_X, MODE_Y, MODE_BTN_W, MODE_H, mouseX, mouseY,
                Component.translatable("brightness.screen.mode.vanilla"), vanilla);

        drawSliderCard(graphics, MOD_CARD_Y, mouseX, mouseY,
                Component.translatable("brightness.screen.mod_ambient"),
                DeepBrightnessDebug.percent() + "%", !vanilla,
                DeepBrightnessDebug.percent() / 100.0F, inCard(mouseX, mouseY, MOD_CARD_Y) && !vanilla);
        graphics.drawString(font, "0%", CARD_X + 8, MOD_CARD_Y + 35, DIM, false);
        String modMax = "100%";
        graphics.drawString(font, modMax, CARD_X + CARD_W - 8 - font.width(modMax),
                MOD_CARD_Y + 35, DIM, false);

        drawSliderCard(graphics, VANILLA_CARD_Y, mouseX, mouseY,
                Component.translatable("brightness.screen.vanilla_ambient"),
                String.format(Locale.ROOT, "%.2f", DeepBrightnessDebug.vanillaAmbient()), vanilla,
                (DeepBrightnessDebug.vanillaAmbient() - VANILLA_MIN) / (VANILLA_MAX - VANILLA_MIN),
                inCard(mouseX, mouseY, VANILLA_CARD_Y) && vanilla);
        String vanillaMin = String.format(Locale.ROOT, "%.2f", VANILLA_MIN);
        String vanillaMax = String.format(Locale.ROOT, "%.2f", VANILLA_MAX);
        graphics.drawString(font, vanillaMin, CARD_X + 8, VANILLA_CARD_Y + 35, DIM, false);
        graphics.drawString(font, vanillaMax,
                CARD_X + CARD_W - 8 - font.width(vanillaMax), VANILLA_CARD_Y + 35, DIM, false);

        boolean toggleHover = vanilla && inBox(mouseX, mouseY, CARD_X, TOGGLE_CARD_Y, CARD_W, TOGGLE_CARD_H);
        graphics.fill(CARD_X, TOGGLE_CARD_Y, CARD_X + CARD_W, TOGGLE_CARD_Y + TOGGLE_CARD_H,
                toggleHover ? CARD_HOVER : CARD);
        frame(graphics, CARD_X, TOGGLE_CARD_Y, CARD_W, TOGGLE_CARD_H, EDGE);
        int toggleColor = !vanilla ? DISABLED
                : DeepBrightnessDebug.vanillaRestoresWater() ? GREEN : TEXT;
        graphics.drawString(font, Component.translatable("brightness.screen.restore_water"),
                CARD_X + 8, TOGGLE_CARD_Y + 7, toggleColor, false);
        drawToggle(graphics, CARD_X + CARD_W - 42, TOGGLE_CARD_Y + 5,
                DeepBrightnessDebug.vanillaRestoresWater() && vanilla, vanilla);
        Component desc = Component.translatable("brightness.screen.restore_water.desc");
        graphics.drawString(font, desc, CARD_X + 8, TOGGLE_CARD_Y + 20, DIM, false);

        boolean nightHover = inBox(mouseX, mouseY, CARD_X, NIGHT_CARD_Y, CARD_W, NIGHT_CARD_H);
        graphics.fill(CARD_X, NIGHT_CARD_Y, CARD_X + CARD_W, NIGHT_CARD_Y + NIGHT_CARD_H,
                nightHover ? CARD_HOVER : CARD);
        frame(graphics, CARD_X, NIGHT_CARD_Y, CARD_W, NIGHT_CARD_H, EDGE);
        graphics.drawString(font, Component.translatable("brightness.screen.night_vision"),
                CARD_X + 8, NIGHT_CARD_Y + 9, TEXT, false);
        boolean infinite = infiniteNightVision();
        drawButton(graphics, nightButtonX(), NIGHT_CARD_Y + 6, NIGHT_BTN_W, NIGHT_BTN_H,
                mouseX, mouseY, Component.translatable(infinite
                        ? "brightness.screen.night_vision.remove"
                        : "brightness.screen.night_vision.give"), !infinite);

        boolean fogRedundant = DeepBrightnessDebug.hidesWaterAndFog();
        boolean fogHover = !fogRedundant
                && inBox(mouseX, mouseY, CARD_X, FOG_CARD_Y, CARD_W, FOG_CARD_H);
        graphics.fill(CARD_X, FOG_CARD_Y, CARD_X + CARD_W, FOG_CARD_Y + FOG_CARD_H,
                fogHover ? CARD_HOVER : CARD);
        frame(graphics, CARD_X, FOG_CARD_Y, CARD_W, FOG_CARD_H, EDGE);
        graphics.drawString(font, Component.translatable("brightness.screen.no_sea_fog"),
                CARD_X + 8, FOG_CARD_Y + 9, fogRedundant ? DISABLED : TEXT, false);
        drawToggle(graphics, CARD_X + CARD_W - 42, FOG_CARD_Y + 7,
                DeepBrightnessDebug.seaFogDisabled() || fogRedundant, !fogRedundant);

        Component hint = Component.translatable("brightness.screen.hint");
        graphics.drawString(font, hint, CARD_X, HINT_Y, DIM, false);

        drawButton(graphics, CARD_X, BUTTON_Y, RESET_W, BUTTON_H, mouseX, mouseY,
                Component.translatable("brightness.screen.reset"), false);
        drawButton(graphics, PANEL_W - CARD_X - DONE_W, BUTTON_Y, DONE_W, BUTTON_H, mouseX, mouseY,
                Component.translatable("brightness.screen.done"), true);
    }

    private void drawModeButton(GuiGraphics graphics, int x, int y, int w, int h,
                                int mouseX, int mouseY, Component label, boolean selected) {
        boolean hover = inBox(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, selected ? 0xFF3A2A1B : hover ? HOVER : BOX);
        frame(graphics, x, y, w, h, selected ? ACCENT : EDGE);
        graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2,
                selected ? ACCENT_LIGHT : TEXT);
    }

    private void drawSliderCard(GuiGraphics graphics, int y, int mouseX, int mouseY,
                                Component label, String value, boolean enabled,
                                float fraction, boolean hovered) {
        graphics.fill(CARD_X, y, CARD_X + CARD_W, y + CARD_H,
                hovered ? CARD_HOVER : CARD);
        frame(graphics, CARD_X, y, CARD_W, CARD_H, EDGE);
        graphics.drawString(font, label, CARD_X + 8, y + 7, enabled ? TEXT : DISABLED, false);
        int right = CARD_X + CARD_W - 8;
        graphics.drawString(font, value, right - font.width(value), y + 7,
                enabled ? ACCENT : DISABLED, false);

        int x = CARD_X + 8;
        int w = CARD_W - 16;
        int trackY = y + 26;
        int filled = x + Mth.floor(Mth.clamp(fraction, 0.0F, 1.0F) * w);
        graphics.fill(x, trackY, x + w, trackY + 5, TRACK);
        graphics.fill(x, trackY, x + w, trackY + 1, 0xFF0A0E0B);
        graphics.fill(x, trackY + 1, filled, trackY + 4, enabled ? ACCENT : DISABLED);
        if (enabled) {
            graphics.fill(x, trackY + 1, filled, trackY + 2, ACCENT_LIGHT);
        }
        for (int i = 0; i <= 4; i++) {
            int tx = x + Math.round(i * (w - 2) / 4.0F);
            graphics.fill(tx, trackY + 5, tx + 1, trackY + 7, EDGE);
        }
        int knob = filled;
        graphics.fill(knob - 3, trackY - 4, knob + 4, trackY + 9, 0xFF0B0F0C);
        graphics.fill(knob - 2, trackY - 3, knob + 3, trackY + 8,
                enabled && hovered ? 0xFFFFE9D2 : enabled ? TEXT : DISABLED);
        graphics.fill(knob - 2, trackY + 1, knob + 3, trackY + 2,
                enabled ? ACCENT : DISABLED);
    }

    private void drawToggle(GuiGraphics graphics, int x, int y, boolean on, boolean enabled) {
        int w = 26;
        int h = 12;
        int trackColor = !enabled ? 0xFF2A332C : on ? 0xFF2E6B3A : 0xFF2A332C;

        graphics.fill(x, y + 1, x + w, y + h - 1, 0xFF0B0F0C);
        graphics.fill(x + 1, y, x + w - 1, y + h, 0xFF0B0F0C);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, trackColor);
        if (on && enabled) {
            graphics.fill(x + 2, y + 2, x + w - 2, y + 3, GREEN);
        }
        int knobX = on ? x + w - 12 : x + 2;
        graphics.fill(knobX, y + 1, knobX + 10, y + h - 1, 0xFF0B0F0C);
        graphics.fill(knobX + 1, y + 2, knobX + 9, y + h - 2,
                !enabled ? DISABLED : on ? 0xFFEAF7EC : 0xFFD8D2BF);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            int mouseX, int mouseY, Component label, boolean primary) {
        boolean hover = inBox(mouseX, mouseY, x, y, w, h);
        if (primary) {
            graphics.fill(x, y, x + w, y + h, hover ? ACCENT_LIGHT : ACCENT);
            graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, 0xFF1B1408);
        } else {
            graphics.fill(x, y, x + w, y + h, hover ? HOVER : BOX);
            frame(graphics, x, y, w, h, EDGE);
            graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, TEXT);
        }
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, int edge) {
        graphics.fill(x, y, x + w, y + 1, edge);
        graphics.fill(x, y + h - 1, x + w, y + h, edge);
        graphics.fill(x, y, x + 1, y + h, edge);
        graphics.fill(x + w - 1, y, x + w, y + h, edge);
    }

    /* ------------------------------------------------------------------ 输入 */

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (button != 0) {
            return super.mouseClicked(screenX, screenY, button);
        }
        if (inBox(mouseX, mouseY, MOD_BTN_X, MODE_Y, MODE_BTN_W, MODE_H)) {
            DeepBrightnessDebug.setMode(DeepBrightnessDebug.Mode.MOD);
            click();
            return true;
        }
        if (inBox(mouseX, mouseY, VANILLA_BTN_X, MODE_Y, MODE_BTN_W, MODE_H)) {
            DeepBrightnessDebug.setMode(DeepBrightnessDebug.Mode.VANILLA);
            click();
            return true;
        }
        if (!DeepBrightnessDebug.isVanilla() && inSliderRow(mouseX, mouseY, MOD_CARD_Y)) {
            dragging = 1;
            DeepBrightnessDebug.setPercent(percentFromMouse(mouseX));
            return true;
        }
        if (DeepBrightnessDebug.isVanilla() && inSliderRow(mouseX, mouseY, VANILLA_CARD_Y)) {
            dragging = 2;
            DeepBrightnessDebug.setVanillaAmbient(ambientFromMouse(mouseX));
            return true;
        }
        if (DeepBrightnessDebug.isVanilla()
                && inBox(mouseX, mouseY, CARD_X, TOGGLE_CARD_Y, CARD_W, TOGGLE_CARD_H)) {
            DeepBrightnessDebug.setVanillaRestoresWater(!DeepBrightnessDebug.vanillaRestoresWater());
            click();
            return true;
        }
        if (inBox(mouseX, mouseY, nightButtonX(), NIGHT_CARD_Y + 6, NIGHT_BTN_W, NIGHT_BTN_H)) {
            ModNetwork.sendToServer(new NightVisionPacket());
            click();
            return true;
        }
        if (!DeepBrightnessDebug.hidesWaterAndFog()
                && inBox(mouseX, mouseY, CARD_X, FOG_CARD_Y, CARD_W, FOG_CARD_H)) {
            DeepBrightnessDebug.setSeaFogDisabled(!DeepBrightnessDebug.seaFogDisabled());
            click();
            return true;
        }
        if (inBox(mouseX, mouseY, CARD_X, BUTTON_Y, RESET_W, BUTTON_H)) {
            DeepBrightnessDebug.reset();
            click();
            return true;
        }
        if (inBox(mouseX, mouseY, PANEL_W - CARD_X - DONE_W, BUTTON_Y, DONE_W, BUTTON_H)) {
            click();
            onClose();
            return true;
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public boolean mouseDragged(double screenX, double screenY, int button,
                                double dragX, double dragY) {
        if (dragging == 1) {
            DeepBrightnessDebug.setPercent(percentFromMouse(designX(screenX)));
            return true;
        }
        if (dragging == 2) {
            DeepBrightnessDebug.setVanillaAmbient(ambientFromMouse(designX(screenX)));
            return true;
        }
        return super.mouseDragged(screenX, screenY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double screenX, double screenY, int button) {
        dragging = 0;
        return super.mouseReleased(screenX, screenY, button);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double delta) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (!DeepBrightnessDebug.isVanilla() && inSliderRow(mouseX, mouseY, MOD_CARD_Y)) {
            int step = hasShiftDown() ? 10 : 1;
            DeepBrightnessDebug.setPercent(DeepBrightnessDebug.percent() + (delta > 0 ? step : -step));
            return true;
        }
        if (DeepBrightnessDebug.isVanilla() && inSliderRow(mouseX, mouseY, VANILLA_CARD_Y)) {
            float step = VANILLA_STEP * (hasShiftDown() ? 5 : 1);
            DeepBrightnessDebug.setVanillaAmbient(
                    DeepBrightnessDebug.vanillaAmbient() + (delta > 0 ? step : -step));
            return true;
        }
        return super.mouseScrolled(screenX, screenY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            DeepBrightnessDebug.reset();
            click();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /* ------------------------------------------------------------------ 数值换算 */

    private static int nightButtonX() {
        return CARD_X + CARD_W - 8 - NIGHT_BTN_W;
    }

    private boolean infiniteNightVision() {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        MobEffectInstance effect = minecraft.player.getEffect(MobEffects.NIGHT_VISION);
        return effect != null && effect.isInfiniteDuration();
    }

    private int percentFromMouse(double mouseX) {
        int x = CARD_X + 8;
        int w = CARD_W - 16;
        double t = Mth.clamp((mouseX - x) / w, 0.0D, 1.0D);
        return (int) Math.round(t * 100.0D);
    }

    private float ambientFromMouse(double mouseX) {
        int x = CARD_X + 8;
        int w = CARD_W - 16;
        double t = Mth.clamp((mouseX - x) / w, 0.0D, 1.0D);
        float raw = (float) (VANILLA_MIN + t * (VANILLA_MAX - VANILLA_MIN));
        return Math.round(raw / VANILLA_STEP) * VANILLA_STEP;
    }

    private static boolean inSliderRow(double mouseX, double mouseY, int cardY) {
        int trackY = cardY + 26;
        return mouseX >= CARD_X + 8 - 3 && mouseX <= CARD_X + CARD_W - 8 + 3
                && mouseY >= trackY - 4 && mouseY < trackY + 9;
    }

    private static boolean inCard(double mouseX, double mouseY, int cardY) {
        return inBox(mouseX, mouseY, CARD_X, cardY, CARD_W, CARD_H);
    }

    private static boolean inBox(double mouseX, double mouseY,
                                 int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void click() {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
