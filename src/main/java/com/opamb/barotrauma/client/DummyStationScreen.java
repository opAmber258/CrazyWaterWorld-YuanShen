package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.menu.DummyStationMenu;
import com.opamb.barotrauma.network.DummySettingsPacket;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * 高级训练设施设置面板：血量 1..200、重生间隔 0..10 秒、自发光开关。
 *
 * <p>交互按“表单”的常规习惯来：</p>
 * <ul>
 *   <li>右下角主按钮是<b>保存</b>，点完立刻发 {@code DummySettingsPacket} 并关界面，
 *       同时播点击音；服务端应用成功后回一条动作栏确认。</li>
 *   <li>左下角是<b>取消</b>，Esc 同效，直接丢弃改动（滑条只改本地草稿）。</li>
 *   <li>回车 = 保存；鼠标滚轮落在滑条上可以 ±1 微调，按住 Shift ±10。</li>
 *   <li>标题右侧显示“未保存 / 已同步”，让玩家知道草稿还没落到方块上。</li>
 * </ul>
 *
 * <p>窗口缩放会重新走 {@code init()}，这里只取一次初值，避免改到一半被重置。</p>
 */
public class DummyStationScreen extends ScaledContainerScreen<DummyStationMenu> {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 204;

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

    private static final int HEALTH_MIN = 1;
    private static final int HEALTH_MAX = 200;
    private static final int RESPAWN_MIN = 0;
    private static final int RESPAWN_MAX = 200;

    private static final int HEADER_H = 26;
    private static final int CARD_X = 10;
    private static final int CARD_W = PANEL_W - 2 * CARD_X;
    private static final int CARD_H = 46;
    private static final int HEALTH_CARD_Y = 32;
    private static final int RESPAWN_CARD_Y = 84;
    private static final int TOGGLE_CARD_Y = 136;
    private static final int TOGGLE_CARD_H = 26;
    private static final int HINT_Y = 168;
    private static final int BUTTON_Y = 180;
    private static final int BUTTON_H = 18;
    private static final int CANCEL_W = 76;
    private static final int SAVE_W = 108;

    /** 滑条左右各留 4 px：7 px 宽的滑块（中心 ±3/+4）正好不出轨道两端。 */
    private static final int SLIDER_INSET = 4;
    private static final int SLIDER_H = 5;

    private int health;
    private int respawnTicks;
    private boolean selfIlluminated;

    /** 打开面板时的值，用来判断“未保存”。 */
    private int initialHealth;
    private int initialRespawn;
    private boolean initialIlluminated;

    private boolean initialised;
    /** 0 = 没拖，1 = 血量，2 = 重生间隔。 */
    private int dragging;

    public DummyStationScreen(DummyStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
        // 窗口缩放会再调 init()：草稿值只从菜单取一次，之后保留玩家改到一半的结果。
        if (!initialised) {
            health = Mth.clamp(menu.health(), HEALTH_MIN, HEALTH_MAX);
            respawnTicks = Mth.clamp(menu.respawnTicks(), RESPAWN_MIN, RESPAWN_MAX);
            selfIlluminated = menu.selfIlluminated();
            initialHealth = health;
            initialRespawn = respawnTicks;
            initialIlluminated = selfIlluminated;
            initialised = true;
        }
    }

    /* ------------------------------------------------------------------ 绘制 */

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(1, 8, 3, 19, ACCENT);
        graphics.drawString(font, panelTitle(), 12, 9, ACCENT, false);

        Component status = Component.translatable(
                dirty() ? "dummy.settings.dirty" : "dummy.settings.synced");
        graphics.drawString(font, status,
                PANEL_W - CARD_X - font.width(status), 9,
                dirty() ? ACCENT : DIM, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 面板本体
        graphics.fill(0, 0, PANEL_W, PANEL_H, BG);
        frame(graphics, 0, 0, PANEL_W, PANEL_H, EDGE);
        graphics.fill(CARD_X, HEADER_H, PANEL_W - CARD_X, HEADER_H + 1, EDGE);

        drawCard(graphics, HEALTH_CARD_Y, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("dummy.settings.health"),
                CARD_X + 8, HEALTH_CARD_Y + 6, TEXT, false);
        drawValue(graphics, HEALTH_CARD_Y + 6, String.valueOf(health), " / " + HEALTH_MAX);
        drawSlider(graphics, HEALTH_CARD_Y + 24, HEALTH_MIN, HEALTH_MAX, health,
                inCard(mouseX, mouseY, HEALTH_CARD_Y));
        drawScale(graphics, HEALTH_CARD_Y + 33, String.valueOf(HEALTH_MIN),
                String.valueOf(HEALTH_MAX));

        drawCard(graphics, RESPAWN_CARD_Y, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("dummy.settings.respawn"),
                CARD_X + 8, RESPAWN_CARD_Y + 6, TEXT, false);
        drawValue(graphics, RESPAWN_CARD_Y + 6, seconds(respawnTicks), "");
        drawSlider(graphics, RESPAWN_CARD_Y + 24, RESPAWN_MIN, RESPAWN_MAX, respawnTicks,
                inCard(mouseX, mouseY, RESPAWN_CARD_Y));
        drawScale(graphics, RESPAWN_CARD_Y + 33, seconds(RESPAWN_MIN), seconds(RESPAWN_MAX));

        // 自发光：整张卡片都可点，不用非得点到开关上
        boolean toggleHover = inBox(mouseX, mouseY, CARD_X, TOGGLE_CARD_Y, CARD_W, TOGGLE_CARD_H);
        graphics.fill(CARD_X, TOGGLE_CARD_Y, CARD_X + CARD_W, TOGGLE_CARD_Y + TOGGLE_CARD_H,
                toggleHover ? CARD_HOVER : CARD);
        frame(graphics, CARD_X, TOGGLE_CARD_Y, CARD_W, TOGGLE_CARD_H, EDGE);
        drawToggle(graphics, CARD_X + 8, TOGGLE_CARD_Y + 7, selfIlluminated);
        graphics.drawString(font, Component.translatable("dummy.settings.self_illuminated"),
                CARD_X + 42, TOGGLE_CARD_Y + 9, selfIlluminated ? GREEN : TEXT, false);
        Component desc = Component.translatable("dummy.settings.desc_illuminated");
        graphics.drawString(font, desc,
                CARD_X + CARD_W - 8 - font.width(desc), TOGGLE_CARD_Y + 9, DIM, false);

        Component hint = Component.translatable("dummy.settings.hint");
        graphics.drawString(font, hint, CARD_X, HINT_Y, DIM, false);

        drawButton(graphics, CARD_X, BUTTON_Y, CANCEL_W, BUTTON_H, mouseX, mouseY,
                Component.translatable("dummy.settings.close").getString(), false);
        drawButton(graphics, PANEL_W - CARD_X - SAVE_W, BUTTON_Y, SAVE_W, BUTTON_H,
                mouseX, mouseY,
                Component.translatable("dummy.settings.apply").getString(), true);
    }

    private void drawCard(GuiGraphics graphics, int y, int mouseX, int mouseY) {
        graphics.fill(CARD_X, y, CARD_X + CARD_W, y + CARD_H,
                inCard(mouseX, mouseY, y) ? CARD_HOVER : CARD);
        frame(graphics, CARD_X, y, CARD_W, CARD_H, EDGE);
    }

    /** 右上角读数：主值用强调色，后缀用暗色。 */
    private void drawValue(GuiGraphics graphics, int y, String value, String suffix) {
        int right = CARD_X + CARD_W - 8;
        int suffixW = font.width(suffix);
        graphics.drawString(font, suffix, right - suffixW, y, DIM, false);
        graphics.drawString(font, value, right - suffixW - font.width(value), y, ACCENT, false);
    }

    private void drawSlider(GuiGraphics graphics, int trackY, int min, int max, int value,
                            boolean hovered) {
        int x = CARD_X + 8;
        int w = CARD_W - 16;
        int knob = sliderKnobX(min, max, value);

        graphics.fill(x, trackY, x + w, trackY + SLIDER_H, TRACK);
        graphics.fill(x, trackY, x + w, trackY + 1, 0xFF0A0E0B);
        graphics.fill(x, trackY + 1, knob, trackY + SLIDER_H - 1, ACCENT);
        graphics.fill(x, trackY + 1, knob, trackY + 2, ACCENT_LIGHT);

        // 四等分刻度，只画在轨道下沿 1 px，避免抢视线
        for (int i = 0; i <= 4; i++) {
            int tx = x + Math.round(i * (w - 2) / 4.0F);
            graphics.fill(tx, trackY + SLIDER_H, tx + 1, trackY + SLIDER_H + 2, EDGE);
        }

        // 滑块：深色描边 + 亮面 + 一条强调色，比纯白方块耐看
        graphics.fill(knob - 3, trackY - 4, knob + 4, trackY + SLIDER_H + 4, 0xFF0B0F0C);
        graphics.fill(knob - 2, trackY - 3, knob + 3, trackY + SLIDER_H + 3,
                hovered ? 0xFFFFE9D2 : TEXT);
        graphics.fill(knob - 2, trackY + 1, knob + 3, trackY + 2, ACCENT);
    }

    private void drawScale(GuiGraphics graphics, int y, String left, String right) {
        graphics.drawString(font, left, CARD_X + 8, y, DIM, false);
        graphics.drawString(font, right, CARD_X + CARD_W - 8 - font.width(right), y, DIM, false);
    }

    private void drawToggle(GuiGraphics graphics, int x, int y, boolean on) {
        int w = 26;
        int h = 12;
        int trackColor = on ? 0xFF2E6B3A : 0xFF2A332C;

        graphics.fill(x, y + 1, x + w, y + h - 1, 0xFF0B0F0C);
        graphics.fill(x + 1, y, x + w - 1, y + h, 0xFF0B0F0C);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, trackColor);
        if (on) {
            graphics.fill(x + 2, y + 2, x + w - 2, y + 3, GREEN);
        }

        int knobX = on ? x + w - 12 : x + 2;
        graphics.fill(knobX, y + 1, knobX + 10, y + h - 1, 0xFF0B0F0C);
        graphics.fill(knobX + 1, y + 2, knobX + 9, y + h - 2,
                on ? 0xFFEAF7EC : 0xFFD8D2BF);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            int mouseX, int mouseY, String label, boolean primary) {
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

    /** 1 px 描边：填充 + 四边。 */
    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, int edge) {
        graphics.fill(x, y, x + w, y + 1, edge);
        graphics.fill(x, y + h - 1, x + w, y + h, edge);
        graphics.fill(x, y, x + 1, y + h, edge);
        graphics.fill(x + w - 1, y, x + w, y + h, edge);
    }

    /* ------------------------------------------------------------------ 数值换算 */

    private static int sliderKnobX(int min, int max, int value) {
        int x = CARD_X + 8;
        int w = CARD_W - 16;
        double t = Mth.clamp((value - min) / (double) (max - min), 0.0D, 1.0D);
        return x + SLIDER_INSET + (int) Math.round(t * (w - 2 * SLIDER_INSET));
    }

    private static int valueFromMouse(double mouseX, int min, int max) {
        int x = CARD_X + 8;
        int w = CARD_W - 16;
        double t = Mth.clamp((mouseX - x - SLIDER_INSET) / (double) (w - 2 * SLIDER_INSET),
                0.0D, 1.0D);
        return (int) Math.round(min + t * (max - min));
    }

    private static boolean inCard(double mouseX, double mouseY, int cardY) {
        return inBox(mouseX, mouseY, CARD_X, cardY, CARD_W, CARD_H);
    }

    private static boolean inSliderRow(double mouseX, double mouseY, int cardY) {
        int trackY = cardY + 24;
        // 纵向只覆盖滑块本体（trackY-4..trackY+8），别压到下面的刻度文字行。
        return mouseX >= CARD_X + 8 - 3 && mouseX <= CARD_X + CARD_W - 8 + 3
                && mouseY >= trackY - 4 && mouseY < trackY + SLIDER_H + 4;
    }

    private static boolean inBox(double mouseX, double mouseY,
                                 int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean dirty() {
        return health != initialHealth
                || respawnTicks != initialRespawn
                || selfIlluminated != initialIlluminated;
    }

    private String seconds(int ticks) {
        String value = String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
        return Component.translatable("dummy.settings.seconds", value).getString();
    }

    private Component panelTitle() {
        if (minecraft != null && minecraft.level != null) {
            Block block = minecraft.level.getBlockState(menu.pos()).getBlock();
            if (block == ModBlocks.ADVANCED_BALLOON_DUMMY_STATION.get()) {
                return Component.translatable("dummy.settings.title_balloon");
            }
            if (block == ModBlocks.ADVANCED_TRAINING_DUMMY_STATION.get()) {
                return Component.translatable("dummy.settings.title_dummy");
            }
        }
        return title;
    }

    /* ------------------------------------------------------------------ 输入 */

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (button == 0) {
            if (inSliderRow(mouseX, mouseY, HEALTH_CARD_Y)) {
                dragging = 1;
                health = valueFromMouse(mouseX, HEALTH_MIN, HEALTH_MAX);
                return true;
            }
            if (inSliderRow(mouseX, mouseY, RESPAWN_CARD_Y)) {
                dragging = 2;
                respawnTicks = valueFromMouse(mouseX, RESPAWN_MIN, RESPAWN_MAX);
                return true;
            }
            if (inBox(mouseX, mouseY, CARD_X, TOGGLE_CARD_Y, CARD_W, TOGGLE_CARD_H)) {
                selfIlluminated = !selfIlluminated;
                return true;
            }
            if (inBox(mouseX, mouseY, CARD_X, BUTTON_Y, CANCEL_W, BUTTON_H)) {
                click();
                onClose();
                return true;
            }
            if (inBox(mouseX, mouseY, PANEL_W - CARD_X - SAVE_W, BUTTON_Y, SAVE_W, BUTTON_H)) {
                save();
                return true;
            }
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public boolean mouseDragged(double screenX, double screenY, int button,
                                double dragX, double dragY) {
        if (dragging == 1) {
            health = valueFromMouse(designX(screenX), HEALTH_MIN, HEALTH_MAX);
            return true;
        }
        if (dragging == 2) {
            respawnTicks = valueFromMouse(designX(screenX), RESPAWN_MIN, RESPAWN_MAX);
            return true;
        }
        return super.mouseDragged(screenX, screenY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double screenX, double screenY, int button) {
        dragging = 0;
        return super.mouseReleased(screenX, screenY, button);
    }

    /** 滚轮微调：指针落在滑条行里才算，Shift 一次 10 点。 */
    @Override
    public boolean mouseScrolled(double screenX, double screenY, double delta) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        int step = hasShiftDown() ? 10 : 1;
        if (inSliderRow(mouseX, mouseY, HEALTH_CARD_Y)) {
            health = Mth.clamp(health + (delta > 0 ? step : -step), HEALTH_MIN, HEALTH_MAX);
            return true;
        }
        if (inSliderRow(mouseX, mouseY, RESPAWN_CARD_Y)) {
            respawnTicks = Mth.clamp(respawnTicks + (delta > 0 ? step : -step),
                    RESPAWN_MIN, RESPAWN_MAX);
            return true;
        }
        return super.mouseScrolled(screenX, screenY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 只有没有可聚焦控件时回车才是“保存”；以后加文本框/按钮不会把回车吞掉。
        if (getFocused() == null
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 保存：发包 + 点击音 + 立刻关界面；结果由服务端动作栏确认。 */
    private void save() {
        ModNetwork.CHANNEL.sendToServer(new DummySettingsPacket(
                menu.pos(), health, respawnTicks, selfIlluminated));
        click();
        onClose();
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
