package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.gui.ScaledScreen;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ShipyardLibraryActionPacket;
import com.opamb.barotrauma.network.ShipyardLibraryListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** 船库：保存当前船、加载/导出/删除已有 structure.nbt。 */
public final class ShipyardLibraryScreen extends ScaledScreen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 280;
    private static final int ROW_H = 20;
    private static final int LIST_TOP = 56;
    private static final int LIST_BOTTOM = PANEL_H - 32;
    private static final int BG = 0xF01B2320;
    private static final int EDGE = 0xFF3B463C;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF8B9184;
    private static final int ACCENT = 0xFFE0762F;
    private static final int HOVER = 0xFF2A352D;

    private EditBox name;
    private int selected;
    private int scroll;
    private String confirmDeleteId = "";
    private long confirmDeleteUntil;
    private String confirmLoadId = "";
    private long confirmLoadUntil;

    public ShipyardLibraryScreen() {
        super(Component.translatable("shipyard.library.title"), PANEL_W, PANEL_H);
    }

    @Override
    protected void init() {
        super.init();
        name = new EditBox(font, 10, 28, 180, 16,
                Component.translatable("shipyard.library.name"));
        name.setMaxLength(48);
        name.setValue("ship_" + ShipyardClient.library().size());
        addRenderableWidget(name);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }

    public void refresh() {
        selected = Math.min(selected, Math.max(0, ShipyardClient.library().size() - 1));
        int visible = Math.max(1, (LIST_BOTTOM - LIST_TOP) / ROW_H);
        if (selected < scroll) {
            scroll = selected;
        } else if (selected >= scroll + visible) {
            scroll = selected - visible + 1;
        }
    }

    @Override
    protected void renderDesign(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, PANEL_W, PANEL_H, BG);
        frame(graphics, 0, 0, PANEL_W, PANEL_H, EDGE);
        graphics.drawString(font, Component.translatable("shipyard.library.title"), 12, 9, ACCENT, false);
        graphics.drawString(font, Component.translatable("shipyard.library.hint"), 10, PANEL_H - 14, DIM, false);

        button(graphics, mouseX, mouseY, 200, 28, 90, 16,
                Component.translatable("shipyard.library.save"), true);

        List<ShipyardLibraryListPacket.Entry> entries = ShipyardClient.library();
        int visible = Math.max(1, (LIST_BOTTOM - LIST_TOP) / ROW_H);
        int maxScroll = Math.max(0, entries.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("shipyard.library.empty"),
                    12, LIST_TOP + 8, DIM, false);
        }
        for (int row = 0; row < visible && scroll + row < entries.size(); row++) {
            int index = scroll + row;
            int y = LIST_TOP + row * ROW_H;
            if (index == selected) {
                graphics.fill(8, y, PANEL_W - 8, y + ROW_H - 2, HOVER);
            }
            ShipyardLibraryListPacket.Entry entry = entries.get(index);
            String badge = entry.verified() ? "✓ " : "! ";
            int badgeColor = entry.verified() ? 0xFF70D090 : 0xFFE88A50;
            graphics.drawString(font, badge, 12, y + 6, badgeColor, false);
            graphics.drawString(font, font.plainSubstrByWidth(entry.name(), 116),
                    24, y + 6, index == selected ? ACCENT : TEXT, false);
            String owner = entry.ownerName().isEmpty() ? "" : " · @" + entry.ownerName();
            String meta = entry.id() + " · " + entry.sizeX() + "×" + entry.sizeY() + "×"
                    + entry.sizeZ() + " · " + entry.blocks() + " 方块" + owner;
            graphics.drawString(font, font.plainSubstrByWidth(meta, PANEL_W - 156),
                    148, y + 6, DIM, false);
        }

        int y = PANEL_H - 28;
        String selectedId = entries.isEmpty() ? ""
                : entries.get(Math.min(selected, entries.size() - 1)).id();
        boolean confirming = !selectedId.isEmpty() && selectedId.equals(confirmDeleteId)
                && net.minecraft.Util.getMillis() < confirmDeleteUntil;
        boolean confirmingLoad = !selectedId.isEmpty() && selectedId.equals(confirmLoadId)
                && net.minecraft.Util.getMillis() < confirmLoadUntil;
        button(graphics, mouseX, mouseY, 10, y, 76, 18,
                confirmingLoad ? Component.literal("确认覆盖")
                        : Component.translatable("shipyard.library.load"), false);
        button(graphics, mouseX, mouseY, 92, y, 76, 18,
                Component.translatable("shipyard.library.export"), false);
        button(graphics, mouseX, mouseY, 174, y, 76, 18,
                confirming ? Component.literal("确认删除")
                        : Component.translatable("shipyard.library.delete"), false);
        button(graphics, mouseX, mouseY, PANEL_W - 86, y, 76, 18,
                Component.translatable("shipyard.library.close"), true);
    }

    private void button(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h,
                        Component label, boolean primary) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, primary ? (hover ? 0xFFF2A063 : ACCENT)
                : (hover ? HOVER : 0xFF232B25));
        frame(graphics, x, y, w, h, EDGE);
        graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2,
                primary ? 0xFF1B1408 : TEXT);
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (super.mouseClicked(screenX, screenY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (inBox(mouseX, mouseY, 200, 28, 90, 16)) {
            confirmDeleteId = "";
            ModNetwork.sendToServer(ShipyardLibraryActionPacket.save(name.getValue()));
            return true;
        }
        if (mouseY >= LIST_TOP && mouseY < LIST_BOTTOM && mouseX >= 8 && mouseX < PANEL_W - 8) {
            int visible = Math.max(1, (LIST_BOTTOM - LIST_TOP) / ROW_H);
            int row = (int) ((mouseY - LIST_TOP) / ROW_H);
            int index = scroll + row;
            if (row >= 0 && row < visible && index >= 0
                    && index < ShipyardClient.library().size()) {
                selected = index;
                confirmDeleteId = "";
            }
            return true;
        }
        List<ShipyardLibraryListPacket.Entry> entries = ShipyardClient.library();
        String id = entries.isEmpty() ? "" : entries.get(Math.min(selected, entries.size() - 1)).id();
        int y = PANEL_H - 28;
        if (inBox(mouseX, mouseY, 10, y, 76, 18) && !id.isEmpty()) {
            boolean dirty = ShipyardClient.state() != null && ShipyardClient.state().dirty();
            long now = net.minecraft.Util.getMillis();
            if (dirty && !(id.equals(confirmLoadId) && now < confirmLoadUntil)) {
                // 加载会覆盖当前地块；未保存到船库的改动需要二次确认。
                confirmLoadId = id;
                confirmLoadUntil = now + 4000L;
                confirmDeleteId = "";
                return true;
            }
            confirmLoadId = "";
            confirmDeleteId = "";
            ModNetwork.sendToServer(ShipyardLibraryActionPacket.load(id));
            return true;
        }
        if (inBox(mouseX, mouseY, 92, y, 76, 18) && !id.isEmpty()) {
            confirmDeleteId = "";
            ModNetwork.sendToServer(ShipyardLibraryActionPacket.export(id));
            return true;
        }
        if (inBox(mouseX, mouseY, 174, y, 76, 18) && !id.isEmpty()) {
            long now = net.minecraft.Util.getMillis();
            if (id.equals(confirmDeleteId) && now < confirmDeleteUntil) {
                confirmDeleteId = "";
                ModNetwork.sendToServer(ShipyardLibraryActionPacket.delete(id));
            } else {
                confirmDeleteId = id;
                confirmDeleteUntil = now + 4000L;
            }
            return true;
        }
        if (inBox(mouseX, mouseY, PANEL_W - 86, y, 76, 18)) {
            returnToEditor();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double delta) {
        int visible = Math.max(1, (LIST_BOTTOM - LIST_TOP) / ROW_H);
        int maxScroll = Math.max(0, ShipyardClient.library().size() - visible);
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - Math.signum(delta)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_TAB) {
            returnToEditor();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean inBox(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void returnToEditor() {
        ShipyardClient.returnToEditor();
    }
}
