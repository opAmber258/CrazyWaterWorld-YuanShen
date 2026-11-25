package com.opamb.barotrauma.client;

import com.opamb.barotrauma.menu.LightMenu;
import com.opamb.barotrauma.network.LightColorPacket;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * 灯具面板：照原版 {@code SerializableEntityEditor.CreateColorField} 做
 * 四个 0–255 整数输入框 R/G/B/A + 颜色预览 + 开关。
 *
 * <p>原作 A 不是透明度，而是发光强度（{@code RGB * A/255 * brightness}），
 * 所以 A 和 RGB 并列成四个输入框，预览按强度变暗。原版没有预设色，这里也没有。
 * 输入在回车、失焦或关闭面板时提交；拖动/输入过程中不逐字发包。</p>
 */
public class LightScreen extends ScaledContainerScreen<LightMenu> {

    private static final int PANEL_WIDTH = 210;
    private static final int PANEL_HEIGHT = 172;

    private int red;
    private int green;
    private int blue;
    private int alpha = 255;
    private boolean lit = true;
    private boolean powered = true;

    private int syncedColor = -1;
    private int syncedAlpha = -1;
    private boolean syncedLit;
    private boolean syncedPowered = true;

    private NumberField redField;
    private NumberField greenField;
    private NumberField blueField;
    private NumberField alphaField;
    private Button toggleButton;

    public LightScreen(LightMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        readServerState();
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        readServerState();
        syncedColor = currentColor();
        syncedAlpha = alpha;
        syncedLit = lit;

        toggleButton = addRenderableWidget(PanelStyle.button(toggleLabel(), button -> {
            // 点按钮会让输入框失焦，先把正在输入的通道提交掉再切换。
            commitFields();
            lit = !lit;
            button.setMessage(toggleLabel());
            send();
        }).bounds(x + 8, y + 30, 62, 18));

        redField = addRenderableWidget(new NumberField(x + 22, y + 54, 52, 18, red));
        greenField = addRenderableWidget(new NumberField(x + 22, y + 76, 52, 18, green));
        blueField = addRenderableWidget(new NumberField(x + 22, y + 98, 52, 18, blue));
        alphaField = addRenderableWidget(new NumberField(x + 22, y + 120, 52, 18, alpha));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int serverColor = menu.data().get(LightMenu.DATA_COLOR) & 0xFFFFFF;
        int serverAlpha = menu.data().get(LightMenu.DATA_ALPHA) & 0xFF;
        boolean serverLit = menu.data().get(LightMenu.DATA_LIT) != 0;
        boolean serverPowered = menu.data().get(LightMenu.DATA_POWERED) != 0;
        if (serverColor == syncedColor && serverAlpha == syncedAlpha
                && serverLit == syncedLit && serverPowered == syncedPowered) {
            return;
        }
        syncedColor = serverColor;
        syncedAlpha = serverAlpha;
        syncedLit = serverLit;
        syncedPowered = serverPowered;
        // 正在输入的框不要被服务端回包覆盖。
        if (!anyFieldFocused()) {
            applyFromServer(serverColor, serverAlpha, serverLit, serverPowered);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        PanelStyle.frame(graphics, x, y, imageWidth, imageHeight, PanelStyle.AMBER);

        // 右上角预览：照原作 A=强度，按强度压暗。
        int previewX = x + 178;
        int previewY = y + 4;
        graphics.fill(previewX, previewY, previewX + 24, previewY + 24, 0xFF000000);
        graphics.fill(previewX + 1, previewY + 1, previewX + 23, previewY + 23,
                lit ? 0xFF000000 | dimmedColor() : 0xFF23262B);

        for (int i = 0; i < 4; i++) {
            graphics.drawString(font, channelLabel(i), x + 8, y + 59 + i * 22,
                    0xFFDDE6EF, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 10, 0xFFDDE6EF, false);
        graphics.drawString(font, String.format("#%06X", currentColor()),
                78, 36, 0xFF9FB0C0, false);
        if (lit && !powered) {
            graphics.drawString(font, Component.translatable("gui.barotrauma.light.unpowered"),
                    134, 36, 0xFFFF5A4A, false);
        }
        graphics.drawString(font, Component.translatable("gui.barotrauma.light.alpha_hint"),
                8, 148, 0xFF9FB0C0, false);
    }

    @Override
    public void removed() {
        commitFields();
        super.removed();
    }

    private static String channelLabel(int channel) {
        return switch (channel) {
            case 0 -> "R";
            case 1 -> "G";
            case 2 -> "B";
            default -> "A";
        };
    }

    private int currentColor() {
        return ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    /** 预览用：按 A/255 压暗 RGB（原作 A 是强度，不是透明度）。 */
    private int dimmedColor() {
        int r = (red & 0xFF) * (alpha & 0xFF) / 255;
        int g = (green & 0xFF) * (alpha & 0xFF) / 255;
        int b = (blue & 0xFF) * (alpha & 0xFF) / 255;
        return (r << 16) | (g << 8) | b;
    }

    private boolean anyFieldFocused() {
        return (redField != null && redField.isFocused())
                || (greenField != null && greenField.isFocused())
                || (blueField != null && blueField.isFocused())
                || (alphaField != null && alphaField.isFocused());
    }

    private void readServerState() {
        int rgb = menu.data().get(LightMenu.DATA_COLOR) & 0xFFFFFF;
        red = (rgb >> 16) & 0xFF;
        green = (rgb >> 8) & 0xFF;
        blue = rgb & 0xFF;
        alpha = menu.data().get(LightMenu.DATA_ALPHA) & 0xFF;
        lit = menu.data().get(LightMenu.DATA_LIT) != 0;
        powered = menu.data().get(LightMenu.DATA_POWERED) != 0;
    }

    private void applyFromServer(int rgb, int alphaValue, boolean on, boolean hasPower) {
        red = (rgb >> 16) & 0xFF;
        green = (rgb >> 8) & 0xFF;
        blue = rgb & 0xFF;
        alpha = alphaValue;
        lit = on;
        powered = hasPower;
        if (redField != null) {
            redField.setValue(String.valueOf(red));
            greenField.setValue(String.valueOf(green));
            blueField.setValue(String.valueOf(blue));
            alphaField.setValue(String.valueOf(alpha));
        }
        if (toggleButton != null) {
            toggleButton.setMessage(toggleLabel());
        }
    }

    /** 解析四个输入框（越界夹到 0–255，非法/空值保留旧值）并提交。 */
    private void commitFields() {
        if (redField == null) {
            return;
        }
        int nextRed = parseChannel(redField, red);
        int nextGreen = parseChannel(greenField, green);
        int nextBlue = parseChannel(blueField, blue);
        int nextAlpha = parseChannel(alphaField, alpha);
        boolean changed = nextRed != red || nextGreen != green
                || nextBlue != blue || nextAlpha != alpha;
        red = nextRed;
        green = nextGreen;
        blue = nextBlue;
        alpha = nextAlpha;
        // 把 "007" 之类规范化成 7。
        redField.setValue(String.valueOf(red));
        greenField.setValue(String.valueOf(green));
        blueField.setValue(String.valueOf(blue));
        alphaField.setValue(String.valueOf(alpha));
        if (changed) {
            send();
        }
    }

    private static int parseChannel(EditBox field, int fallback) {
        String text = field.getValue().trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return Math.max(0, Math.min(255, Integer.parseInt(text)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void send() {
        if (minecraft == null || minecraft.getConnection() == null) {
            return;
        }
        int rgb = currentColor();
        // 先认下本次值，避免服务端数据回来前的 tick 把输入框拉回旧值。
        syncedColor = rgb;
        syncedAlpha = alpha;
        syncedLit = lit;
        syncedPowered = powered;
        ModNetwork.sendToServer(new LightColorPacket(menu.lightPos(), rgb, alpha, lit));
    }

    private Component toggleLabel() {
        return Component.translatable(lit
                ? "gui.barotrauma.light.on" : "gui.barotrauma.light.off");
    }

    /** 只能输 0–255 的数字框；回车/失焦/关面板时提交（和原作数字输入一致）。 */
    private final class NumberField extends EditBox {

        private NumberField(int x, int y, int width, int height, int value) {
            super(font, x, y, width, height, Component.empty());
            setMaxLength(3);
            setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
            setValue(String.valueOf(Math.max(0, Math.min(255, value))));
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitFields();
                setFocused(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void setFocused(boolean focused) {
            boolean wasFocused = isFocused();
            super.setFocused(focused);
            if (wasFocused && !focused) {
                commitFields();
            }
        }
    }
}
