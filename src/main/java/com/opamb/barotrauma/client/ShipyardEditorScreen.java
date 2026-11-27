package com.opamb.barotrauma.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.opamb.barotrauma.client.gui.ScaledScreen;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ShipyardEditPacket;
import com.opamb.barotrauma.network.ShipyardFillPacket;
import com.opamb.barotrauma.network.ShipyardPastePacket;
import com.opamb.barotrauma.network.ShipyardStatePacket;
import com.opamb.barotrauma.network.ShipyardStatusPacket;
import com.opamb.barotrauma.network.ShipyardWirePacket;
import com.opamb.barotrauma.world.shipyard.ShipyardShapes;
import com.opamb.barotrauma.world.shipyard.ShipyardValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 造船厂三维建模编辑器界面（640×360 大字号悬浮 HUD 布局）：
 * 顶部三排紧凑工具条 + 左栏方块库/舱室预设双标签 + 右栏问题优先校验卡 + 底部快捷栏 + 3D Gizmo。
 */
public final class ShipyardEditorScreen extends ScaledScreen {

    private static final int PANEL_W = 640;
    private static final int PANEL_H = 360;

    private static final int PALETTE_X = 6;
    private static final int PALETTE_Y = 6;
    private static final int PALETTE_W = 152;
    private static final int PALETTE_H = 266;
    private static final int GRID_COLS = 6;
    private static final int CELL = 23;
    private static final int LIST_TOP = 64;
    private static final int LIST_BOTTOM = 248;

    private static final int INSPECTOR_W = 148;
    private static final int INSPECTOR_X = PANEL_W - INSPECTOR_W - 6;
    private static final int INSPECTOR_Y = 6;

    private static final int TOOLBAR_X = 164;
    private static final int TOOLBAR_W = 312;
    private static final int PASTE_BAR_X = 186;
    private static final int PASTE_BAR_W = 268;
    private static final int HOTBAR_SLOTS = 8;
    private static final int HOTBAR_SLOT_SIZE = 22;

    private static final int GIZMO_X = PANEL_W - 36;
    private static final int GIZMO_Y = PANEL_H - 36;
    private static final int GIZMO_LEN = 22;

    private static final int CARD_BG = 0xAA101614;
    private static final int BAR_BG = 0xC0101614;
    private static final int EDGE = 0xFF34433B;
    private static final int TEXT = 0xFFE6E1D4;
    private static final int DIM = 0xFF939B90;
    private static final int ACCENT = 0xFFE87E35;
    private static final int CYAN = 0xFF4CD9F0;
    private static final int VIOLET = 0xFF9585FF;
    private static final int HOVER = 0xFF26342D;

    private EditBox search;
    private ShipyardPalette.Category category = ShipyardPalette.Category.ALL;
    private int selected;
    private int scroll;
    private final List<ShipyardPalette.Entry> quickBar = new ArrayList<>();

    private boolean paletteOpen = true;
    private boolean presetsTab;
    private boolean inspectorOpen = true;
    private boolean expandAllChecks;
    private boolean panelsHidden;
    private boolean cutPending;

    private boolean cameraDrag;
    private boolean leftPressed;
    private boolean middlePressed;
    private boolean middleDragged;
    private boolean leftStartedInWorld;
    private boolean middleStartedInWorld;
    private int pressX;
    private int pressY;
    private int middlePressX;
    private int middlePressY;

    private int gizmoAxis = -1;
    private boolean gizmoDragged;
    private int dragMouseButton = -1;
    private boolean dragPaintActive;
    private BlockPos lastPaintedPos;
    private int lastScreenX;
    private int lastScreenY;

    public ShipyardEditorScreen() {
        super(Component.translatable("shipyard.editor.title"), PANEL_W, PANEL_H);
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, PALETTE_X + 4, PALETTE_Y + 22, PALETTE_W - 8, 15,
                Component.translatable("shipyard.editor.search"));
        search.setHint(Component.translatable("shipyard.editor.search"));
        search.setMaxLength(40);
        search.setResponder(text -> {
            scroll = 0;
            selectFirst();
        });
        addRenderableWidget(search);
        selectFirst();
        initQuickBar();
        syncSearchVisibility();
    }

    private void initQuickBar() {
        if (!quickBar.isEmpty()) {
            return;
        }
        List<ShipyardPalette.Entry> all = ShipyardPalette.entries();
        for (int i = 0; i < Math.min(HOTBAR_SLOTS, all.size()); i++) {
            quickBar.add(all.get(i));
        }
    }

    private void pushQuickBar(ShipyardPalette.Entry entry) {
        if (entry == null) {
            return;
        }
        quickBar.removeIf(e -> e.state().getBlock() == entry.state().getBlock());
        quickBar.add(0, entry);
        while (quickBar.size() > HOTBAR_SLOTS) {
            quickBar.remove(quickBar.size() - 1);
        }
    }

    private void selectFirst() {
        List<ShipyardPalette.Entry> entries = filtered();
        if (entries.isEmpty()) {
            selected = 0;
        } else {
            selected = Math.min(selected, entries.size() - 1);
        }
    }

    private void syncSearchVisibility() {
        if (search != null) {
            boolean show = !panelsHidden && paletteOpen && !presetsTab;
            if (!show) {
                search.setFocused(false);
            }
            search.setVisible(show);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // 编辑器透出三维世界，不绘制原版压暗遮罩。
    }

    @Override
    protected void renderDesign(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<ShipyardPalette.Entry> entries = filtered();
        boolean overUi = overUiScreen(lastScreenX, lastScreenY);
        ShipyardClient.setPointerOverUi(overUi);
        if (!overUi) {
            ShipyardClient.updateTarget(lastScreenX, lastScreenY, width, height);
        } else if (!ShipyardClient.dragActive()) {
            ShipyardClient.clearTarget();
        }

        ShipyardPalette.Entry activeEntry = activeEntry(entries);
        if (activeEntry != null && !overUi
                && ShipyardClient.toolMode() != ShipyardClient.ToolMode.BOX_ERASE
                && ShipyardClient.toolMode() != ShipyardClient.ToolMode.REPLACE
                && ShipyardClient.toolMode() != ShipyardClient.ToolMode.COPY_SELECT
                && ShipyardClient.toolMode() != ShipyardClient.ToolMode.CUT_SELECT
                && ShipyardClient.toolMode() != ShipyardClient.ToolMode.PASTE) {
            ShipyardClient.setGhostState(ShipyardClient.applyPlacementRotation(activeEntry.state()));
        } else {
            ShipyardClient.setGhostState(null);
        }

        if (!panelsHidden) {
            drawTopToolbar(graphics, mouseX, mouseY);
            if (ShipyardClient.toolMode() == ShipyardClient.ToolMode.PASTE && ShipyardClient.hasClipboard()) {
                drawPasteSubBar(graphics, mouseX, mouseY);
            }
            drawPaletteCard(graphics, mouseX, mouseY, entries, activeEntry);
            drawInspectorCard(graphics, mouseX, mouseY);
            drawQuickHotbar(graphics, mouseX, mouseY, activeEntry);
        }
        drawGizmo(graphics, mouseX, mouseY);
        drawStatusBar(graphics);
    }

    private ShipyardPalette.Entry activeEntry(List<ShipyardPalette.Entry> entries) {
        if (!entries.isEmpty()) {
            return entries.get(Math.min(selected, entries.size() - 1));
        }
        return quickBar.isEmpty() ? null : quickBar.get(0);
    }

    private void drawTopToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(TOOLBAR_X, 6, TOOLBAR_X + TOOLBAR_W, 64, BAR_BG);
        frame(graphics, TOOLBAR_X, 6, TOOLBAR_W, 58, EDGE);

        ShipyardClient.ToolMode[] row1 = {
                ShipyardClient.ToolMode.SINGLE,
                ShipyardClient.ToolMode.BOX_FILL,
                ShipyardClient.ToolMode.BOX_HOLLOW,
                ShipyardClient.ToolMode.ELLIPSOID,
                ShipyardClient.ToolMode.BOX_WALLS,
                ShipyardClient.ToolMode.LINE
        };
        String[] row1Labels = {"单块", "填充", "方壳", "艇壳", "舱壁", "直线"};
        for (int i = 0; i < row1.length; i++) {
            int bx = TOOLBAR_X + 4 + i * 51;
            boolean active = ShipyardClient.toolMode() == row1[i];
            int color = row1[i] == ShipyardClient.ToolMode.ELLIPSOID ? 0xFF4CD9A0 : ACCENT;
            drawMiniButton(graphics, mouseX, mouseY, bx, 9, 49, 17, row1Labels[i], active, color);
        }

        ShipyardClient.ToolMode[] row2 = {
                ShipyardClient.ToolMode.REPLACE,
                ShipyardClient.ToolMode.BOX_ERASE,
                ShipyardClient.ToolMode.COPY_SELECT,
                ShipyardClient.ToolMode.CUT_SELECT,
                ShipyardClient.ToolMode.PASTE,
                ShipyardClient.ToolMode.WIRE
        };
        String[] row2Labels = {"替换", "框删", "复制", "剪切", "粘贴", "连线"};
        for (int i = 0; i < row2.length; i++) {
            int bx = TOOLBAR_X + 4 + i * 51;
            boolean active = ShipyardClient.toolMode() == row2[i];
            int color = row2[i] == ShipyardClient.ToolMode.BOX_ERASE ? 0xFFE06A5A
                    : row2[i] == ShipyardClient.ToolMode.REPLACE ? 0xFF4CD9A0
                    : row2[i] == ShipyardClient.ToolMode.WIRE ? CYAN : VIOLET;
            drawMiniButton(graphics, mouseX, mouseY, bx, 28, 49, 17, row2Labels[i], active, color);
        }

        int r3y = 47;
        int x = TOOLBAR_X + 4;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 20, 15, "透",
                ShipyardClient.viewMode() == ShipyardClient.ViewMode.PERSPECTIVE);
        x += 22;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 15, 15, "前",
                ShipyardClient.viewMode() == ShipyardClient.ViewMode.FRONT
                        || ShipyardClient.viewMode() == ShipyardClient.ViewMode.BACK);
        x += 17;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 15, 15, "侧",
                ShipyardClient.viewMode() == ShipyardClient.ViewMode.SIDE
                        || ShipyardClient.viewMode() == ShipyardClient.ViewMode.LEFT);
        x += 17;
        drawMiniButton(graphics, mouseX, r3y, x, r3y, 15, 15, "顶",
                ShipyardClient.viewMode() == ShipyardClient.ViewMode.TOP
                        || ShipyardClient.viewMode() == ShipyardClient.ViewMode.BOTTOM);
        x += 17;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 16, 15, "重", false);
        x += 19;
        boolean mirrorOn = ShipyardClient.mirrorAxis() != ShipyardShapes.MIRROR_NONE;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 29, 15,
                "镜:" + mirrorShortLabel(), mirrorOn, CYAN);
        x += 31;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 29, 15,
                "网:" + gridShortLabel(),
                ShipyardClient.gridMode() != ShipyardClient.GridMode.OFF);
        x += 31;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 31, 15,
                "旋:" + ShipyardClient.rotationLabel(), false);
        x += 33;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 17, 15, "Y-", false);
        x += 19;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 17, 15, "Y+", false);
        x += 19;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 17, 15, "剖",
                ShipyardClient.sliceEnabled(), 0xFF4CD9F0);
        x += 19;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 17, 15, "舱",
                ShipyardClient.showCompartments(), 0xFF55D98C);
        x += 19;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 17, 15, "尺",
                ShipyardClient.showRuler(), 0xFF55D98C);
        x += 19;
        drawMiniButton(graphics, mouseX, mouseY, x, r3y, 17, 15, "诊",
                ShipyardClient.showDiagnostics(), 0xFFE0B050);
    }

    private void drawPasteSubBar(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(PASTE_BAR_X, 66, PASTE_BAR_X + PASTE_BAR_W, 84, BAR_BG);
        frame(graphics, PASTE_BAR_X, 66, PASTE_BAR_W, 18, VIOLET);
        int x = PASTE_BAR_X + 4;
        drawMiniButton(graphics, mouseX, mouseY, x, 68, 40, 14, "顺转 R", false, VIOLET);
        x += 42;
        drawMiniButton(graphics, mouseX, mouseY, x, 68, 34, 14, "逆转", false, VIOLET);
        x += 36;
        drawMiniButton(graphics, mouseX, mouseY, x, 68, 42, 14, "左右翻", false, VIOLET);
        x += 44;
        drawMiniButton(graphics, mouseX, mouseY, x, 68, 42, 14, "前后翻", false, VIOLET);
        x += 44;
        drawMiniButton(graphics, mouseX, mouseY, x, 68, 32, 14, "清空", false, 0xFFE06A5A);
        x += 36;
        graphics.drawString(font, ShipyardClient.clipboardSize() + "格", x, 71, TEXT, false);
    }

    private void drawPaletteCard(GuiGraphics graphics, int mouseX, int mouseY,
                                 List<ShipyardPalette.Entry> entries,
                                 ShipyardPalette.Entry activeEntry) {
        if (!paletteOpen) {
            drawMiniButton(graphics, mouseX, mouseY, PALETTE_X, PALETTE_Y, 72, 20, "方块/预设 [Q]", false);
            return;
        }
        graphics.fill(PALETTE_X, PALETTE_Y, PALETTE_X + PALETTE_W, PALETTE_Y + PALETTE_H, CARD_BG);
        frame(graphics, PALETTE_X, PALETTE_Y, PALETTE_W, PALETTE_H, EDGE);

        drawMiniButton(graphics, mouseX, mouseY, PALETTE_X + 4, PALETTE_Y + 4, 62, 15,
                "方块库", !presetsTab);
        drawMiniButton(graphics, mouseX, mouseY, PALETTE_X + 68, PALETTE_Y + 4, 62, 15,
                "舱室预设", presetsTab, VIOLET);
        drawMiniButton(graphics, mouseX, mouseY, PALETTE_X + PALETTE_W - 18, PALETTE_Y + 4, 14, 15,
                "-", false);

        if (presetsTab) {
            List<ShipyardPresets.Preset> presets = ShipyardPresets.all();
            for (int i = 0; i < presets.size(); i++) {
                ShipyardPresets.Preset preset = presets.get(i);
                int py = PALETTE_Y + 26 + i * 56;
                int pw = PALETTE_W - 10;
                boolean hover = inBox(mouseX, mouseY, PALETTE_X + 5, py, pw, 50);
                graphics.fill(PALETTE_X + 5, py, PALETTE_X + 5 + pw, py + 50,
                        hover ? HOVER : 0xB018201D);
                frame(graphics, PALETTE_X + 5, py, pw, 50, hover ? VIOLET : EDGE);
                graphics.drawString(font, preset.name(), PALETTE_X + 10, py + 6, ACCENT, false);
                graphics.drawString(font, font.plainSubstrByWidth(preset.subtitle(), pw - 10),
                        PALETTE_X + 10, py + 20, TEXT, false);
                graphics.drawString(font, preset.blocks().size() + " 格 · 点击装入粘贴",
                        PALETTE_X + 10, py + 34, DIM, false);
            }
            return;
        }

        ShipyardPalette.Category[] cats = ShipyardPalette.Category.values();
        for (int i = 0; i < cats.length; i++) {
            int cx = PALETTE_X + 4 + i * 29;
            int cy = PALETTE_Y + 40;
            boolean active = category == cats[i];
            boolean hover = inBox(mouseX, mouseY, cx, cy, 27, 14);
            graphics.fill(cx, cy, cx + 27, cy + 14,
                    active ? 0xFF33261B : hover ? HOVER : 0xA01B2320);
            frame(graphics, cx, cy, 27, 14, active ? ACCENT : EDGE);
            graphics.drawCenteredString(font, cats[i].label(), cx + 13, cy + 3,
                    active ? ACCENT : TEXT);
        }

        int visibleRows = Math.max(1, (LIST_BOTTOM - LIST_TOP) / CELL);
        int totalRows = Math.max(1, (entries.size() + GRID_COLS - 1) / GRID_COLS);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int first = scroll * GRID_COLS;
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = first + row * GRID_COLS + col;
                if (index >= entries.size()) {
                    break;
                }
                int x = PALETTE_X + 7 + col * CELL;
                int y = LIST_TOP + row * CELL;
                boolean hover = inBox(mouseX, mouseY, x, y, CELL - 1, CELL - 1);
                if (index == selected) {
                    graphics.fill(x, y, x + CELL - 2, y + CELL - 2, HOVER);
                    frame(graphics, x, y, CELL - 1, CELL - 1, ACCENT);
                } else if (hover) {
                    graphics.fill(x, y, x + CELL - 2, y + CELL - 2, 0x402A352D);
                }
                graphics.renderItem(entries.get(index).icon(), x + 3, y + 3);
            }
        }

        if (activeEntry != null) {
            int footprintSize = ShipyardShapes.footprintOffsets(activeEntry.state()).size();
            String spec = footprintSize > 1 ? " (" + footprintSize + "格)" : "";
            graphics.drawString(font, font.plainSubstrByWidth(activeEntry.name() + spec, PALETTE_W - 12),
                    PALETTE_X + 6, PALETTE_Y + PALETTE_H - 16, ACCENT, false);
        }
    }

    private List<ShipyardValidator.Requirement> visibleRequirements(ShipyardStatusPacket status) {
        if (status == null) {
            return List.of();
        }
        if (expandAllChecks) {
            return status.requirements();
        }
        return status.requirements().stream()
                .filter(req -> req.state() != ShipyardValidator.State.OK)
                .toList();
    }

    private int inspectorHeight() {
        ShipyardStatusPacket status = ShipyardClient.status();
        int rows = status == null ? 1 : Math.min(8, visibleRequirements(status).size()) + 1;
        return 124 + rows * 12;
    }

    private void drawInspectorCard(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!inspectorOpen) {
            drawMiniButton(graphics, mouseX, mouseY, PANEL_W - 74, INSPECTOR_Y, 68, 20, "校验 [I]", false);
            return;
        }
        int cardH = inspectorHeight();
        graphics.fill(INSPECTOR_X, INSPECTOR_Y, INSPECTOR_X + INSPECTOR_W, INSPECTOR_Y + cardH, CARD_BG);
        frame(graphics, INSPECTOR_X, INSPECTOR_Y, INSPECTOR_W, cardH, EDGE);

        graphics.drawString(font, "船厂校验", INSPECTOR_X + 6, INSPECTOR_Y + 6, ACCENT, false);
        ShipyardStatePacket state = ShipyardClient.state();
        if (state != null) {
            String saved = state.dirty() ? "●未存" : "已存";
            graphics.drawString(font, saved,
                    INSPECTOR_X + INSPECTOR_W - 22 - font.width(saved), INSPECTOR_Y + 6,
                    state.dirty() ? 0xFFE0B050 : 0xFF7BE38B, false);
        }
        drawMiniButton(graphics, mouseX, mouseY, INSPECTOR_X + INSPECTOR_W - 18, INSPECTOR_Y + 4,
                14, 14, "-", false);

        int line = INSPECTOR_Y + 22;
        ShipyardStatusPacket status = ShipyardClient.status();
        if (status == null) {
            graphics.drawString(font, "校验中…", INSPECTOR_X + 6, line, DIM, false);
        } else {
            graphics.drawString(font, status.blocks() + "块 · " + status.sizeX() + "×"
                    + status.sizeY() + "×" + status.sizeZ(), INSPECTOR_X + 6, line, TEXT, false);
            line += 12;
            graphics.drawString(font, "舱室" + status.compartments() + " · 连海"
                            + status.openPockets(), INSPECTOR_X + 6, line,
                    status.sealed() ? TEXT : 0xFFE0B050, false);
            line += 12;
            int ballastPct = status.totalInteriorVolume() > 0
                    ? (int) Math.round(status.ballastVolume() * 100.0D / status.totalInteriorVolume()) : 0;
            String budget = "压载" + ballastPct + "% · 电" + status.powerGenKw() + "/"
                    + status.powerLoadKw() + " · 引擎" + status.engineCount();
            int budgetColor = (ballastPct >= 10 && status.powerGenKw() >= status.powerLoadKw())
                    ? 0xFF7BE38B : 0xFFE0B050;
            graphics.drawString(font, font.plainSubstrByWidth(budget, INSPECTOR_W - 12),
                    INSPECTOR_X + 6, line, budgetColor, false);
            line += 14;
            List<ShipyardValidator.Requirement> shown = visibleRequirements(status);
            int maxBottom = INSPECTOR_Y + cardH - 74;
            for (ShipyardValidator.Requirement req : shown) {
                if (line > maxBottom) {
                    break;
                }
                int color = req.state() == ShipyardValidator.State.OK ? 0xFF7BE38B
                        : req.state() == ShipyardValidator.State.WARN ? 0xFFE0B050 : 0xFFE06A5A;
                String mark = req.state() == ShipyardValidator.State.OK ? "✓"
                        : req.state() == ShipyardValidator.State.WARN ? "!" : "×";
                boolean clickable = req.target() != null;
                boolean hoverRow = clickable && inBox(mouseX, mouseY,
                        INSPECTOR_X + 4, line - 1, INSPECTOR_W - 8, 11);
                if (hoverRow) {
                    graphics.fill(INSPECTOR_X + 4, line - 1,
                            INSPECTOR_X + INSPECTOR_W - 4, line + 10, HOVER);
                }
                graphics.drawString(font, mark, INSPECTOR_X + 6, line, color, false);
                String detail = font.plainSubstrByWidth(req.detail(), INSPECTOR_W - (clickable ? 32 : 22));
                graphics.drawString(font, detail, INSPECTOR_X + 16, line, color, false);
                if (clickable) {
                    graphics.drawString(font, "⌖", INSPECTOR_X + INSPECTOR_W - 13, line,
                            hoverRow ? 0xFFFFFFFF : DIM, false);
                }
                line += 12;
            }
            int okCount = (int) status.requirements().stream()
                    .filter(r -> r.state() == ShipyardValidator.State.OK).count();
            boolean hoverToggle = inBox(mouseX, mouseY, INSPECTOR_X + 4, line - 1, INSPECTOR_W - 8, 11);
            if (hoverToggle) {
                graphics.fill(INSPECTOR_X + 4, line - 1, INSPECTOR_X + INSPECTOR_W - 4, line + 10, HOVER);
            }
            graphics.drawString(font, "✓ " + okCount + "项就绪 " + (expandAllChecks ? "[收起]" : "[展开]"),
                    INSPECTOR_X + 6, line, 0xFF7BE38B, false);
        }

        int btnY0 = INSPECTOR_Y + cardH - 60;
        int btnY1 = INSPECTOR_Y + cardH - 41;
        int btnY2 = INSPECTOR_Y + cardH - 21;
        int halfW = (INSPECTOR_W - 14) / 2;
        drawMiniButton(graphics, mouseX, mouseY, INSPECTOR_X + 5, btnY0, INSPECTOR_W - 10, 16,
                "定位下个问题 ⌖", false, 0xFFE0B050);
        drawMiniButton(graphics, mouseX, mouseY, INSPECTOR_X + 5, btnY1, halfW, 17, "撤销", false);
        drawMiniButton(graphics, mouseX, mouseY, INSPECTOR_X + 9 + halfW, btnY1, halfW, 17, "重做", false);
        drawMiniButton(graphics, mouseX, mouseY, INSPECTOR_X + 5, btnY2, halfW, 17, "船库", false);
        drawMiniButton(graphics, mouseX, mouseY, INSPECTOR_X + 9 + halfW, btnY2, halfW, 17,
                ShipyardClient.inTest() ? "返航" : "试航", true);
    }

    private void drawQuickHotbar(GuiGraphics graphics, int mouseX, int mouseY,
                                 ShipyardPalette.Entry activeEntry) {
        if (quickBar.isEmpty()) {
            return;
        }
        int barW = quickBar.size() * HOTBAR_SLOT_SIZE + 8;
        int x0 = (PANEL_W - barW) / 2;
        int y0 = PANEL_H - 29;
        graphics.fill(x0, y0, x0 + barW, y0 + 24, BAR_BG);
        frame(graphics, x0, y0, barW, 24, EDGE);
        for (int i = 0; i < quickBar.size(); i++) {
            ShipyardPalette.Entry entry = quickBar.get(i);
            int sx = x0 + 4 + i * HOTBAR_SLOT_SIZE;
            int sy = y0 + 2;
            boolean isCurrent = activeEntry != null
                    && activeEntry.state().getBlock() == entry.state().getBlock();
            boolean hover = inBox(mouseX, mouseY, sx, sy, HOTBAR_SLOT_SIZE - 1, HOTBAR_SLOT_SIZE - 1);
            if (isCurrent) {
                graphics.fill(sx, sy, sx + 20, sy + 20, HOVER);
                frame(graphics, sx, sy, 20, 20, ACCENT);
            } else if (hover) {
                graphics.fill(sx, sy, sx + 20, sy + 20, 0x402A352D);
            }
            graphics.renderItem(entry.icon(), sx + 2, sy + 2);
        }
    }

    private void drawMiniButton(GuiGraphics graphics, int mouseX, int mouseY,
                                int x, int y, int w, int h, String label, boolean active) {
        drawMiniButton(graphics, mouseX, mouseY, x, y, w, h, label, active, ACCENT);
    }

    private void drawMiniButton(GuiGraphics graphics, int mouseX, int mouseY,
                                int x, int y, int w, int h, String label, boolean active, int accentColor) {
        boolean hover = inBox(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, active ? 0xFF283430 : hover ? HOVER : 0xB81B2320);
        frame(graphics, x, y, w, h, active ? accentColor : EDGE);
        graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2,
                active ? accentColor : TEXT);
    }

    private void drawStatusBar(GuiGraphics graphics) {
        String text;
        int color = TEXT;
        if (ShipyardClient.dragActive()) {
            BlockPos a = ShipyardClient.dragAnchor();
            BlockPos b = ShipyardClient.dragCurrent();
            int w = Math.abs(b.getX() - a.getX()) + 1;
            int h = Math.abs(b.getY() - a.getY()) + 1;
            int d = Math.abs(b.getZ() - a.getZ()) + 1;
            long count = ShipyardClient.dragMode() == ShipyardShapes.MODE_ELLIPSOID
                    ? ShipyardShapes.collectCells(a, b, ShipyardClient.dragMode(), ShipyardShapes.MAX_VOLUME).size()
                    : ShipyardShapes.estimateCount(a, b, ShipyardClient.dragMode());
            boolean over = count > ShipyardShapes.MAX_VOLUME || count <= 0;
            color = over ? 0xFFFF5555 : 0xFFFFC055;
            text = "选区 " + w + "×" + h + "×" + d + " = " + count + " 格"
                    + (over ? " (超限>" + ShipyardShapes.MAX_VOLUME + ")" : " · 松开确认 · 右键/Esc取消");
        } else {
            BlockPos pos = ShipyardClient.placePos() != null ? ShipyardClient.placePos() : ShipyardClient.target();
            String coord = pos == null ? "--" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
            text = coord + " · Y=" + ShipyardClient.workPlaneY()
                    + " · " + toolLabel(ShipyardClient.toolMode())
                    + " · H隐藏 · Tab第一人称";
        }
        int pad = 6;
        int tw = font.width(text);
        int cx = PANEL_W / 2;
        int y0 = panelsHidden ? PANEL_H - 18 : PANEL_H - 46;
        int x0 = cx - tw / 2 - pad;
        int x1 = cx + tw / 2 + pad;
        graphics.fill(x0, y0, x1, y0 + 14, BAR_BG);
        frame(graphics, x0, y0, x1 - x0, 14, EDGE);
        graphics.drawCenteredString(font, text, cx, y0 + 3, color);
    }

    private void drawGizmo(GuiGraphics graphics, int mouseX, int mouseY) {
        double[][] axes = gizmoAxes();
        int hover = gizmoAxisAt(mouseX, mouseY);
        int ox = GIZMO_X;
        int oy = GIZMO_Y;
        int[] colors = {0xFFFF5555, 0xFF55FF55, 0xFF5599FF};
        String[] labels = {"X", "Y", "Z"};
        Integer[] order = {0, 1, 2};
        java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(i -> axes[i][3]));

        graphics.fill(ox - 15, oy - 15, ox + 15, oy + 15, 0x45000000);
        graphics.fill(ox - 2, oy - 2, ox + 2, oy + 2, 0xFFFFFFFF);
        for (int idx : order) {
            double projection = axes[idx][2];
            int color = (idx == hover || idx == gizmoAxis) ? 0xFFFFFFFF : colors[idx];
            if (projection < 0.12D) {
                graphics.fill(ox - 4, oy - 4, ox + 4, oy + 4, color);
                graphics.drawString(font, labels[idx], ox + 7, oy + (idx - 1) * 9, color, false);
                continue;
            }
            double nx = axes[idx][0];
            double ny = axes[idx][1];
            double length = GIZMO_LEN * (0.55D + 0.45D * Math.min(1.0D, projection));
            int ex = (int) Math.round(ox + nx * length);
            int ey = (int) Math.round(oy + ny * length);
            drawAxisLine(graphics, ox, oy, ex, ey, color);
            graphics.fill(ex - 2, ey - 2, ex + 3, ey + 3, color);
            int labelX = (int) Math.round(ex + nx * 6.0D) - 2;
            int labelY = (int) Math.round(ey + ny * 6.0D) - 4;
            graphics.drawString(font, labels[idx], labelX, labelY, color, false);
        }
    }

    private static double[][] gizmoAxes() {
        double[][] world = {{1.0D, 0.0D, 0.0D}, {0.0D, 1.0D, 0.0D}, {0.0D, 0.0D, 1.0D}};
        double[][] out = new double[3][];
        for (int i = 0; i < 3; i++) {
            double[] projected = ShipyardClient.projectAxis(
                    world[i][0], world[i][1], world[i][2]);
            double length = projected[2];
            if (length < 1.0E-4D) {
                out[i] = new double[]{0.0D, 0.0D, 0.0D, projected[3]};
            } else {
                out[i] = new double[]{projected[0] / length, projected[1] / length,
                        length, projected[3]};
            }
        }
        return out;
    }

    private static void drawAxisLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) steps);
            int y = y0 + Math.round((y1 - y0) * i / (float) steps);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static int gizmoAxisAt(double mouseX, double mouseY) {
        double[][] axes = gizmoAxes();
        int best = -1;
        double bestDistance = 8.0D;
        for (int i = 0; i < 3; i++) {
            double distance;
            if (axes[i][2] < 0.12D) {
                distance = Math.hypot(mouseX - GIZMO_X, mouseY - GIZMO_Y);
            } else {
                double length = GIZMO_LEN * (0.55D + 0.45D * Math.min(1.0D, axes[i][2]));
                distance = distanceToSegment(mouseX, mouseY, GIZMO_X, GIZMO_Y,
                        GIZMO_X + axes[i][0] * length,
                        GIZMO_Y + axes[i][1] * length);
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static double distanceToSegment(double px, double py, double x0, double y0,
                                            double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = dx * dx + dy * dy;
        if (length == 0.0D) {
            return Math.hypot(px - x0, py - y0);
        }
        double t = Math.max(0.0D, Math.min(1.0D, ((px - x0) * dx + (py - y0) * dy) / length));
        return Math.hypot(px - (x0 + t * dx), py - (y0 + t * dy));
    }

    private List<ShipyardPalette.Entry> filtered() {
        String query = search == null ? "" : search.getValue();
        return ShipyardPalette.entries(category, query);
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (panelsHidden) {
            return;
        }
        double mx = designX(mouseX);
        double my = designY(mouseY);
        if (paletteOpen && !presetsTab && mx >= PALETTE_X + 7 && my >= LIST_TOP && my < LIST_BOTTOM) {
            int col = (int) ((mx - (PALETTE_X + 7)) / CELL);
            int row = (int) ((my - LIST_TOP) / CELL);
            if (col >= 0 && col < GRID_COLS) {
                List<ShipyardPalette.Entry> entries = filtered();
                int index = scroll * GRID_COLS + row * GRID_COLS + col;
                if (index >= 0 && index < entries.size()) {
                    graphics.renderTooltip(font, entries.get(index).icon(), mouseX, mouseY);
                    return;
                }
            }
        }
        if (!quickBar.isEmpty()) {
            int barW = quickBar.size() * HOTBAR_SLOT_SIZE + 8;
            int x0 = (PANEL_W - barW) / 2;
            int y0 = PANEL_H - 29;
            if (inBox(mx, my, x0 + 4, y0 + 2, quickBar.size() * HOTBAR_SLOT_SIZE, HOTBAR_SLOT_SIZE)) {
                int slot = (int) ((mx - (x0 + 4)) / HOTBAR_SLOT_SIZE);
                if (slot >= 0 && slot < quickBar.size()) {
                    graphics.renderTooltip(font, quickBar.get(slot).icon(), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastScreenX = (int) mouseX;
        lastScreenY = (int) mouseY;
        super.mouseMoved(designX(mouseX), designY(mouseY));
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        lastScreenX = (int) screenX;
        lastScreenY = (int) screenY;
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);

        if (ShipyardClient.dragActive() && button == 1) {
            ShipyardClient.cancelDrag();
            cutPending = false;
            dragMouseButton = -1;
            return true;
        }
        if (super.mouseClicked(screenX, screenY, button)) {
            return true;
        }
        if (button == 0) {
            int axis = gizmoAxisAt(mouseX, mouseY);
            if (axis >= 0) {
                gizmoAxis = axis;
                return true;
            }
        }
        if (!panelsHidden && button == 0 && handleUiClicks(mouseX, mouseY)) {
            return true;
        }
        if (overUiScreen(screenX, screenY)) {
            return true;
        }

        ShipyardClient.ToolMode tool = ShipyardClient.toolMode();
        if (button == 0 && (tool == ShipyardClient.ToolMode.BOX_ERASE
                || tool == ShipyardClient.ToolMode.COPY_SELECT
                || tool == ShipyardClient.ToolMode.CUT_SELECT)) {
            ShipyardClient.updateTarget((int) screenX, (int) screenY, width, height);
            BlockPos anchor = ShipyardClient.target() != null
                    ? ShipyardClient.target() : ShipyardClient.placePos();
            if (anchor != null) {
                dragMouseButton = 0;
                cutPending = tool == ShipyardClient.ToolMode.CUT_SELECT;
                ShipyardClient.beginDrag(anchor, tool == ShipyardClient.ToolMode.BOX_ERASE
                        ? ShipyardShapes.MODE_CLEAR : ShipyardShapes.MODE_COPY);
                return true;
            }
        }
        if (button == 0 && tool == ShipyardClient.ToolMode.SINGLE && hasShiftDown()) {
            ShipyardClient.updateTarget((int) screenX, (int) screenY, width, height);
            BlockPos anchor = ShipyardClient.placePos();
            if (anchor != null) {
                dragMouseButton = 0;
                cutPending = false;
                ShipyardClient.beginDrag(anchor, ShipyardShapes.MODE_FILL_SOLID);
                return true;
            }
        }
        if (button == 1 && tool == ShipyardClient.ToolMode.PASTE && ShipyardClient.hasClipboard()) {
            ShipyardClient.updateTarget((int) screenX, (int) screenY, width, height);
            BlockPos anchor = ShipyardClient.placePos();
            if (anchor != null) {
                sendPaste(anchor.offset(ShipyardClient.pasteNudgeOffset()));
                ShipyardClient.resetPasteNudge();
            }
            return true;
        }
        if (button == 1 && tool == ShipyardClient.ToolMode.WIRE) {
            ShipyardClient.updateTarget((int) screenX, (int) screenY, width, height);
            BlockPos target = ShipyardClient.target();
            if (target != null) {
                BlockPos anchor = ShipyardClient.wireAnchor();
                if (anchor == null) {
                    ShipyardClient.setWireAnchor(target);
                } else {
                    if (!anchor.equals(target)) {
                        ModNetwork.sendToServer(new ShipyardWirePacket(anchor, target));
                    }
                    ShipyardClient.setWireAnchor(null);
                }
            }
            return true;
        }
        if (button == 1 && isAreaPlaceTool(tool)) {
            ShipyardClient.updateTarget((int) screenX, (int) screenY, width, height);
            BlockPos anchor = ShipyardClient.placePos();
            if (tool == ShipyardClient.ToolMode.REPLACE) {
                BlockPos target = ShipyardClient.target();
                Minecraft mc = Minecraft.getInstance();
                if (target != null && mc.level != null) {
                    ShipyardClient.setReplaceFilter(mc.level.getBlockState(target));
                    anchor = target;
                } else {
                    ShipyardClient.setReplaceFilter(null);
                }
            }
            if (anchor != null) {
                dragMouseButton = 1;
                cutPending = false;
                ShipyardClient.beginDrag(anchor, shapeModeForTool(tool));
                return true;
            }
        }
        if (button == 0) {
            pressX = (int) mouseX;
            pressY = (int) mouseY;
            leftPressed = true;
            leftStartedInWorld = true;
            return true;
        }
        if (button == 2) {
            cameraDrag = true;
            middlePressed = true;
            middleDragged = false;
            middleStartedInWorld = true;
            middlePressX = (int) mouseX;
            middlePressY = (int) mouseY;
            return true;
        }
        if (button == 1) {
            ShipyardClient.updateTarget((int) screenX, (int) screenY, width, height);
            ShipyardPalette.Entry entry = activeEntry(filtered());
            BlockPos place = ShipyardClient.placePos();
            if (tool == ShipyardClient.ToolMode.SINGLE && hasAltDown()) {
                dragPaintActive = true;
                lastPaintedPos = null;
            }
            if (entry != null && place != null) {
                BlockState rotated = ShipyardClient.applyPlacementRotation(entry.state());
                ModNetwork.sendToServer(ShipyardEditPacket.place(
                        place, rotated, ShipyardClient.mirrorAxis(), ShipyardClient.mirrorCoord()));
                if (dragPaintActive) {
                    lastPaintedPos = place.immutable();
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleUiClicks(double mouseX, double mouseY) {
        ShipyardClient.ToolMode[] row1 = {
                ShipyardClient.ToolMode.SINGLE,
                ShipyardClient.ToolMode.BOX_FILL,
                ShipyardClient.ToolMode.BOX_HOLLOW,
                ShipyardClient.ToolMode.ELLIPSOID,
                ShipyardClient.ToolMode.BOX_WALLS,
                ShipyardClient.ToolMode.LINE
        };
        for (int i = 0; i < row1.length; i++) {
            if (inBox(mouseX, mouseY, TOOLBAR_X + 4 + i * 51, 9, 49, 17)) {
                ShipyardClient.setToolMode(row1[i]);
                return true;
            }
        }
        ShipyardClient.ToolMode[] row2 = {
                ShipyardClient.ToolMode.REPLACE,
                ShipyardClient.ToolMode.BOX_ERASE,
                ShipyardClient.ToolMode.COPY_SELECT,
                ShipyardClient.ToolMode.CUT_SELECT,
                ShipyardClient.ToolMode.PASTE,
                ShipyardClient.ToolMode.WIRE
        };
        for (int i = 0; i < row2.length; i++) {
            if (inBox(mouseX, mouseY, TOOLBAR_X + 4 + i * 51, 28, 49, 17)) {
                ShipyardClient.setToolMode(row2[i]);
                return true;
            }
        }

        int x = TOOLBAR_X + 4;
        if (inBox(mouseX, mouseY, x, 47, 20, 15)) {
            ShipyardClient.setViewMode(ShipyardClient.ViewMode.PERSPECTIVE);
            return true;
        }
        x += 22;
        if (inBox(mouseX, mouseY, x, 47, 15, 15)) {
            ShipyardClient.snapToAxis(2);
            return true;
        }
        x += 17;
        if (inBox(mouseX, mouseY, x, 47, 15, 15)) {
            ShipyardClient.snapToAxis(0);
            return true;
        }
        x += 17;
        if (inBox(mouseX, mouseY, x, 47, 15, 15)) {
            ShipyardClient.snapToAxis(1);
            return true;
        }
        x += 17;
        if (inBox(mouseX, mouseY, x, 47, 16, 15)) {
            ShipyardClient.resetView();
            return true;
        }
        x += 19;
        if (inBox(mouseX, mouseY, x, 47, 29, 15)) {
            ShipyardClient.cycleMirror();
            return true;
        }
        x += 31;
        if (inBox(mouseX, mouseY, x, 47, 29, 15)) {
            ShipyardClient.cycleGrid();
            return true;
        }
        x += 31;
        if (inBox(mouseX, mouseY, x, 47, 31, 15)) {
            ShipyardClient.rotatePlacement(!hasShiftDown());
            return true;
        }
        x += 33;
        if (inBox(mouseX, mouseY, x, 47, 17, 15)) {
            ShipyardClient.adjustWorkPlane(-1);
            return true;
        }
        x += 19;
        if (inBox(mouseX, mouseY, x, 47, 17, 15)) {
            ShipyardClient.adjustWorkPlane(1);
            return true;
        }
        x += 19;
        if (inBox(mouseX, mouseY, x, 47, 17, 15)) {
            ShipyardClient.toggleSlice();
            return true;
        }
        x += 19;
        if (inBox(mouseX, mouseY, x, 47, 17, 15)) {
            ShipyardClient.toggleCompartments();
            return true;
        }
        x += 19;
        if (inBox(mouseX, mouseY, x, 47, 17, 15)) {
            ShipyardClient.toggleRuler();
            return true;
        }
        x += 19;
        if (inBox(mouseX, mouseY, x, 47, 17, 15)) {
            ShipyardClient.toggleDiagnostics();
            return true;
        }

        if (ShipyardClient.toolMode() == ShipyardClient.ToolMode.PASTE && ShipyardClient.hasClipboard()) {
            int px = PASTE_BAR_X + 4;
            if (inBox(mouseX, mouseY, px, 68, 40, 14)) {
                ShipyardClient.rotatePlacement(true);
                return true;
            }
            px += 42;
            if (inBox(mouseX, mouseY, px, 68, 34, 14)) {
                ShipyardClient.rotatePlacement(false);
                return true;
            }
            px += 36;
            if (inBox(mouseX, mouseY, px, 68, 42, 14)) {
                ShipyardClient.flipClipboard(true);
                return true;
            }
            px += 44;
            if (inBox(mouseX, mouseY, px, 68, 42, 14)) {
                ShipyardClient.flipClipboard(false);
                return true;
            }
            px += 44;
            if (inBox(mouseX, mouseY, px, 68, 32, 14)) {
                ShipyardClient.clearClipboard();
                return true;
            }
        }

        // 左栏方块库/预设
        if (!paletteOpen) {
            if (inBox(mouseX, mouseY, PALETTE_X, PALETTE_Y, 72, 20)) {
                paletteOpen = true;
                syncSearchVisibility();
                return true;
            }
        } else {
            if (inBox(mouseX, mouseY, PALETTE_X + 4, PALETTE_Y + 4, 62, 15)) {
                presetsTab = false;
                syncSearchVisibility();
                return true;
            }
            if (inBox(mouseX, mouseY, PALETTE_X + 68, PALETTE_Y + 4, 62, 15)) {
                presetsTab = true;
                syncSearchVisibility();
                return true;
            }
            if (inBox(mouseX, mouseY, PALETTE_X + PALETTE_W - 18, PALETTE_Y + 4, 14, 15)) {
                paletteOpen = false;
                syncSearchVisibility();
                return true;
            }
            if (presetsTab) {
                List<ShipyardPresets.Preset> presets = ShipyardPresets.all();
                for (int i = 0; i < presets.size(); i++) {
                    int py = PALETTE_Y + 26 + i * 56;
                    if (inBox(mouseX, mouseY, PALETTE_X + 5, py, PALETTE_W - 10, 50)) {
                        ShipyardClient.loadClipboardEntries(presets.get(i).blocks());
                        return true;
                    }
                }
                return inBox(mouseX, mouseY, PALETTE_X, PALETTE_Y, PALETTE_W, PALETTE_H);
            }
            ShipyardPalette.Category[] cats = ShipyardPalette.Category.values();
            for (int i = 0; i < cats.length; i++) {
                if (inBox(mouseX, mouseY, PALETTE_X + 4 + i * 29, PALETTE_Y + 40, 27, 14)) {
                    category = cats[i];
                    scroll = 0;
                    selectFirst();
                    return true;
                }
            }
            if (inBox(mouseX, mouseY, PALETTE_X, PALETTE_Y, PALETTE_W, PALETTE_H)) {
                selectRow(mouseX, mouseY);
                return true;
            }
        }

        // 右栏校验卡
        if (!inspectorOpen) {
            if (inBox(mouseX, mouseY, PANEL_W - 74, INSPECTOR_Y, 68, 20)) {
                inspectorOpen = true;
                return true;
            }
        } else {
            int cardH = inspectorHeight();
            if (inBox(mouseX, mouseY, INSPECTOR_X + INSPECTOR_W - 18, INSPECTOR_Y + 4, 14, 14)) {
                inspectorOpen = false;
                return true;
            }
            int btnY0 = INSPECTOR_Y + cardH - 60;
            int btnY1 = INSPECTOR_Y + cardH - 41;
            int btnY2 = INSPECTOR_Y + cardH - 21;
            int halfW = (INSPECTOR_W - 14) / 2;
            if (inBox(mouseX, mouseY, INSPECTOR_X + 5, btnY0, INSPECTOR_W - 10, 16)) {
                ShipyardClient.focusNextIssue();
                return true;
            }
            if (inBox(mouseX, mouseY, INSPECTOR_X + 5, btnY1, halfW, 17)) {
                ModNetwork.sendToServer(ShipyardEditPacket.undo());
                return true;
            }
            if (inBox(mouseX, mouseY, INSPECTOR_X + 9 + halfW, btnY1, halfW, 17)) {
                ModNetwork.sendToServer(ShipyardEditPacket.redo());
                return true;
            }
            if (inBox(mouseX, mouseY, INSPECTOR_X + 5, btnY2, halfW, 17)) {
                ShipyardClient.openLibrary();
                return true;
            }
            if (inBox(mouseX, mouseY, INSPECTOR_X + 9 + halfW, btnY2, halfW, 17)) {
                ModNetwork.sendToServer(new com.opamb.barotrauma.network.ShipyardTestPacket(true));
                return true;
            }
            ShipyardStatusPacket status = ShipyardClient.status();
            if (status != null && inBox(mouseX, mouseY, INSPECTOR_X, INSPECTOR_Y, INSPECTOR_W, cardH)) {
                int line = INSPECTOR_Y + 60;
                int maxBottom = INSPECTOR_Y + cardH - 74;
                List<ShipyardValidator.Requirement> shown = visibleRequirements(status);
                for (ShipyardValidator.Requirement req : shown) {
                    if (line > maxBottom) {
                        break;
                    }
                    if (req.target() != null && inBox(mouseX, mouseY,
                            INSPECTOR_X + 4, line - 1, INSPECTOR_W - 8, 11)) {
                        ShipyardClient.setFocus(req.target());
                        return true;
                    }
                    line += 12;
                }
                if (inBox(mouseX, mouseY, INSPECTOR_X + 4, line - 1, INSPECTOR_W - 8, 11)) {
                    expandAllChecks = !expandAllChecks;
                    return true;
                }
                return true;
            }
        }

        // 底部快捷热键栏
        if (!quickBar.isEmpty()) {
            int barW = quickBar.size() * HOTBAR_SLOT_SIZE + 8;
            int x0 = (PANEL_W - barW) / 2;
            int y0 = PANEL_H - 29;
            if (inBox(mouseX, mouseY, x0, y0, barW, 24)) {
                int slot = (int) ((mouseX - (x0 + 4)) / HOTBAR_SLOT_SIZE);
                if (slot >= 0 && slot < quickBar.size()) {
                    selectPaletteEntry(quickBar.get(slot));
                }
                return true;
            }
        }
        return false;
    }

    private void selectPaletteEntry(ShipyardPalette.Entry targetEntry) {
        if (targetEntry == null) {
            return;
        }
        List<ShipyardPalette.Entry> currentList = filtered();
        for (int i = 0; i < currentList.size(); i++) {
            if (currentList.get(i).state().getBlock() == targetEntry.state().getBlock()) {
                selected = i;
                return;
            }
        }
        category = ShipyardPalette.Category.ALL;
        if (search != null && !search.getValue().isEmpty()) {
            search.setValue("");
        }
        List<ShipyardPalette.Entry> all = filtered();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).state().getBlock() == targetEntry.state().getBlock()) {
                selected = i;
                return;
            }
        }
    }

    private boolean overUiScreen(double screenX, double screenY) {
        double dx = designX(screenX);
        double dy = designY(screenY);
        if (Math.hypot(dx - GIZMO_X, dy - GIZMO_Y) <= 32.0D) {
            return true;
        }
        if (panelsHidden) {
            return false;
        }
        if (inBox(dx, dy, TOOLBAR_X, 6, TOOLBAR_W, 58)) {
            return true;
        }
        if (ShipyardClient.toolMode() == ShipyardClient.ToolMode.PASTE
                && ShipyardClient.hasClipboard()
                && inBox(dx, dy, PASTE_BAR_X, 66, PASTE_BAR_W, 18)) {
            return true;
        }
        if (paletteOpen ? inBox(dx, dy, PALETTE_X, PALETTE_Y, PALETTE_W, PALETTE_H)
                : inBox(dx, dy, PALETTE_X, PALETTE_Y, 72, 20)) {
            return true;
        }
        if (inspectorOpen ? inBox(dx, dy, INSPECTOR_X, INSPECTOR_Y, INSPECTOR_W, inspectorHeight())
                : inBox(dx, dy, PANEL_W - 74, INSPECTOR_Y, 68, 20)) {
            return true;
        }
        if (!quickBar.isEmpty()) {
            int barW = quickBar.size() * HOTBAR_SLOT_SIZE + 8;
            int x0 = (PANEL_W - barW) / 2;
            if (inBox(dx, dy, x0, PANEL_H - 29, barW, 24)) {
                return true;
            }
        }
        return false;
    }

    private void setPanelsHidden(boolean hidden) {
        panelsHidden = hidden;
        syncSearchVisibility();
        if (!hidden) {
            ShipyardClient.updateTarget(lastScreenX, lastScreenY, width, height);
        }
    }

    private void selectRow(double mouseX, double mouseY) {
        List<ShipyardPalette.Entry> entries = filtered();
        if (mouseX < PALETTE_X + 7 || mouseY < LIST_TOP || mouseY >= LIST_BOTTOM) {
            return;
        }
        int col = (int) ((mouseX - (PALETTE_X + 7)) / CELL);
        int row = (int) ((mouseY - LIST_TOP) / CELL);
        if (col < 0 || col >= GRID_COLS) {
            return;
        }
        int index = scroll * GRID_COLS + row * GRID_COLS + col;
        if (index >= 0 && index < entries.size()) {
            selected = index;
            pushQuickBar(entries.get(index));
        }
    }

    @Override
    public boolean mouseDragged(double screenX, double screenY, int button, double dragX, double dragY) {
        lastScreenX = (int) screenX;
        lastScreenY = (int) screenY;
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (dragPaintActive && button == 1) {
            ShipyardClient.updateTarget((int) screenX, (int) screenY, width, height);
            ShipyardPalette.Entry entry = activeEntry(filtered());
            BlockPos place = ShipyardClient.placePos();
            if (entry != null && place != null && !place.equals(lastPaintedPos)) {
                BlockState rotated = ShipyardClient.applyPlacementRotation(entry.state());
                ModNetwork.sendToServer(ShipyardEditPacket.place(
                        place, rotated, ShipyardClient.mirrorAxis(), ShipyardClient.mirrorCoord()));
                lastPaintedPos = place.immutable();
            }
            return true;
        }
        if (ShipyardClient.dragActive() && button == dragMouseButton) {
            ShipyardClient.updateTarget((int) screenX, (int) screenY, width, height);
            BlockPos current = ShipyardClient.dragMode() == ShipyardShapes.MODE_CLEAR
                    || ShipyardClient.dragMode() == ShipyardShapes.MODE_COPY
                    ? (ShipyardClient.target() != null ? ShipyardClient.target() : ShipyardClient.placePos())
                    : ShipyardClient.placePos();
            if (current != null) {
                ShipyardClient.updateDrag(current);
            }
            return true;
        }
        if (gizmoAxis >= 0) {
            if (Math.abs(dragX) + Math.abs(dragY) > 1.0D) {
                gizmoDragged = true;
            }
            ShipyardClient.orbitFromGizmo(dragX / uiScale, dragY / uiScale);
            return true;
        }
        if (cameraDrag) {
            if (middlePressed && (Math.abs(mouseX - middlePressX) > 4
                    || Math.abs(mouseY - middlePressY) > 4)) {
                middleDragged = true;
            }
            if (hasShiftDown() || ShipyardClient.orthographic()) {
                ShipyardClient.pan(dragX, dragY);
            } else {
                ShipyardClient.orbit(dragX, dragY);
            }
            return true;
        }
        if (leftPressed && (Math.abs(mouseX - pressX) > 4 || Math.abs(mouseY - pressY) > 4)) {
            if (ShipyardClient.orthographic()) {
                ShipyardClient.pan(dragX, dragY);
            } else {
                ShipyardClient.orbit(dragX, dragY);
            }
            return true;
        }
        return super.mouseDragged(screenX, screenY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double screenX, double screenY, int button) {
        if (dragPaintActive && button == 1) {
            dragPaintActive = false;
            lastPaintedPos = null;
            return true;
        }
        if (ShipyardClient.dragActive() && button == dragMouseButton) {
            commitDrag();
            dragMouseButton = -1;
            return true;
        }
        if (button == 0 && gizmoAxis >= 0) {
            int axis = gizmoAxis;
            boolean wasDragged = gizmoDragged;
            gizmoAxis = -1;
            gizmoDragged = false;
            if (!wasDragged) {
                ShipyardClient.snapToAxis(axis);
            }
            return true;
        }
        gizmoAxis = -1;
        if (button == 2) {
            cameraDrag = false;
            if (middleStartedInWorld && middlePressed && !middleDragged) {
                pickBlock((int) screenX, (int) screenY);
            }
            middlePressed = false;
            middleDragged = false;
            middleStartedInWorld = false;
            return true;
        }
        cameraDrag = false;
        if (leftStartedInWorld && leftPressed && button == 0) {
            double mouseX = designX(screenX);
            double mouseY = designY(screenY);
            boolean overWorld = !overUiScreen(screenX, screenY);
            boolean dragged = Math.abs(mouseX - pressX) > 4 || Math.abs(mouseY - pressY) > 4;
            leftPressed = false;
            leftStartedInWorld = false;
            if (!dragged && overWorld) {
                ShipyardClient.updateTarget((int) screenX, (int) screenY, width, height);
                BlockPos target = ShipyardClient.target();
                if (target != null) {
                    ModNetwork.sendToServer(ShipyardEditPacket.remove(
                            target, ShipyardClient.mirrorAxis(), ShipyardClient.mirrorCoord()));
                }
                return true;
            }
        }
        return super.mouseReleased(screenX, screenY, button);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double delta) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (!panelsHidden && paletteOpen && !presetsTab
                && inBox(mouseX, mouseY, PALETTE_X, PALETTE_Y, PALETTE_W, PALETTE_H)) {
            int visibleRows = Math.max(1, (LIST_BOTTOM - LIST_TOP) / CELL);
            int totalRows = Math.max(1, (filtered().size() + GRID_COLS - 1) / GRID_COLS);
            int maxScroll = Math.max(0, totalRows - visibleRows);
            scroll = (int) Math.max(0, Math.min(maxScroll, scroll - Math.signum(delta)));
            return true;
        }
        ShipyardClient.zoom(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null && search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                search.setFocused(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (ShipyardClient.dragActive()) {
                ShipyardClient.cancelDrag();
                cutPending = false;
                dragMouseButton = -1;
                return true;
            }
            ShipyardClient.closeEditor(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            ShipyardClient.closeEditor(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_H) {
            setPanelsHidden(!panelsHidden);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Q) {
            paletteOpen = !paletteOpen;
            syncSearchVisibility();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_I) {
            inspectorOpen = !inspectorOpen;
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            ModNetwork.sendToServer(hasShiftDown()
                    ? ShipyardEditPacket.redo() : ShipyardEditPacket.undo());
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
            ModNetwork.sendToServer(ShipyardEditPacket.redo());
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_4) {
            ShipyardClient.setViewMode(switch (keyCode) {
                case GLFW.GLFW_KEY_2 -> ShipyardClient.ViewMode.FRONT;
                case GLFW.GLFW_KEY_3 -> ShipyardClient.ViewMode.SIDE;
                case GLFW.GLFW_KEY_4 -> ShipyardClient.ViewMode.TOP;
                default -> ShipyardClient.ViewMode.PERSPECTIVE;
            });
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G) {
            ShipyardClient.cycleGrid();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            ShipyardClient.rotatePlacement(!hasShiftDown());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_M) {
            ShipyardClient.cycleMirror();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F) {
            ShipyardClient.focusOnHovered();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            ShipyardClient.resetView();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT_BRACKET || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            ShipyardClient.adjustWorkPlane(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_BRACKET || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            ShipyardClient.adjustWorkPlane(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V) {
            ShipyardClient.setToolMode(ShipyardClient.ToolMode.SINGLE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_B) {
            ShipyardClient.setToolMode(ShipyardClient.ToolMode.BOX_FILL);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_N) {
            ShipyardClient.setToolMode(ShipyardClient.ToolMode.BOX_HOLLOW);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_O) {
            ShipyardClient.setToolMode(ShipyardClient.ToolMode.ELLIPSOID);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_T) {
            ShipyardClient.setToolMode(ShipyardClient.ToolMode.REPLACE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_L) {
            ShipyardClient.setToolMode(ShipyardClient.ToolMode.LINE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_X) {
            if (hasControlDown()) {
                ShipyardClient.setToolMode(ShipyardClient.ToolMode.CUT_SELECT);
            } else {
                ShipyardClient.setToolMode(ShipyardClient.ToolMode.BOX_ERASE);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C) {
            ShipyardClient.setToolMode(ShipyardClient.ToolMode.COPY_SELECT);
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V && ShipyardClient.hasClipboard()) {
            ShipyardClient.setToolMode(ShipyardClient.ToolMode.PASTE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_K) {
            ShipyardClient.toggleDiagnostics();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_J) {
            ShipyardClient.setToolMode(ShipyardClient.ToolMode.WIRE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_U) {
            ShipyardClient.toggleSlice();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_P) {
            ShipyardClient.toggleCompartments();
            return true;
        }
        if (ShipyardClient.toolMode() == ShipyardClient.ToolMode.PASTE && ShipyardClient.hasClipboard()) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                ShipyardClient.nudgePaste(-1, 0, 0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                ShipyardClient.nudgePaste(1, 0, 0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                ShipyardClient.nudgePaste(0, hasShiftDown() ? 1 : 0, hasShiftDown() ? 0 : -1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                ShipyardClient.nudgePaste(0, hasShiftDown() ? -1 : 0, hasShiftDown() ? 0 : 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                BlockPos anchor = ShipyardClient.placePos();
                if (anchor != null) {
                    sendPaste(anchor.offset(ShipyardClient.pasteNudgeOffset()));
                    ShipyardClient.resetPasteNudge();
                }
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_W || keyCode == GLFW.GLFW_KEY_S
                || keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_D
                || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_LEFT_SHIFT
                || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (search != null) {
            search.tick();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null
                || (search != null && search.isFocused() && !panelsHidden && paletteOpen && !presetsTab)) {
            return;
        }
        long window = mc.getWindow().getWindow();
        double right = (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D) ? 1 : 0)
                - (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A) ? 1 : 0);
        double forward = (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W) ? 1 : 0)
                - (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S) ? 1 : 0);
        boolean shiftDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        double up = (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE) ? 1 : 0)
                - (!ShipyardClient.dragActive() && shiftDown ? 1 : 0);
        if (right != 0 || forward != 0 || up != 0) {
            double speed = Math.max(0.12D, ShipyardClient.distance() * 0.012D);
            ShipyardClient.moveFocus(right * speed, forward * speed, up * speed);
        }
        boolean overUi = overUiScreen(lastScreenX, lastScreenY);
        ShipyardClient.setPointerOverUi(overUi);
        if (!overUi) {
            ShipyardClient.updateTarget(lastScreenX, lastScreenY, width, height);
        } else if (!ShipyardClient.dragActive()) {
            ShipyardClient.clearTarget();
        }
    }

    private void commitDrag() {
        BlockPos anchor = ShipyardClient.dragAnchor();
        BlockPos current = ShipyardClient.dragCurrent();
        byte mode = ShipyardClient.dragMode();
        boolean doCut = cutPending;
        cutPending = false;
        ShipyardClient.cancelDrag();
        if (anchor == null || current == null) {
            return;
        }
        long count = mode == ShipyardShapes.MODE_ELLIPSOID
                ? ShipyardShapes.collectCells(anchor, current, mode, ShipyardShapes.MAX_VOLUME).size()
                : ShipyardShapes.estimateCount(anchor, current, mode);
        if (count <= 0 || count > ShipyardShapes.MAX_VOLUME) {
            return;
        }
        if (mode == ShipyardShapes.MODE_COPY) {
            boolean captured = ShipyardClient.captureClipboard(anchor, current);
            if (captured && doCut) {
                ModNetwork.sendToServer(ShipyardFillPacket.clear(
                        anchor, current, ShipyardShapes.MIRROR_NONE, 0));
            }
            return;
        }
        if (mode == ShipyardShapes.MODE_CLEAR) {
            ModNetwork.sendToServer(ShipyardFillPacket.clear(
                    anchor, current, ShipyardClient.mirrorAxis(), ShipyardClient.mirrorCoord()));
            return;
        }
        ShipyardPalette.Entry entry = activeEntry(filtered());
        if (entry == null) {
            return;
        }
        BlockState rotated = ShipyardClient.applyPlacementRotation(entry.state());
        if (mode == ShipyardShapes.MODE_REPLACE) {
            ModNetwork.sendToServer(ShipyardFillPacket.replace(
                    anchor, current, ShipyardClient.mirrorAxis(), ShipyardClient.mirrorCoord(),
                    rotated, ShipyardClient.replaceFilter()));
            return;
        }
        ModNetwork.sendToServer(ShipyardFillPacket.of(
                anchor, current, mode,
                ShipyardClient.mirrorAxis(), ShipyardClient.mirrorCoord(), rotated));
    }

    private void sendPaste(BlockPos anchor) {
        List<ShipyardClient.ClipboardBlock> rotated = ShipyardClient.rotatedClipboard();
        if (rotated.isEmpty()) {
            return;
        }
        List<ShipyardPastePacket.Entry> entries = new ArrayList<>(rotated.size());
        for (ShipyardClient.ClipboardBlock block : rotated) {
            entries.add(new ShipyardPastePacket.Entry(block.rel(), NbtUtils.writeBlockState(block.state())));
        }
        ModNetwork.sendToServer(new ShipyardPastePacket(
                anchor, entries, ShipyardClient.mirrorAxis(), ShipyardClient.mirrorCoord()));
    }

    private static boolean isAreaPlaceTool(ShipyardClient.ToolMode mode) {
        return mode == ShipyardClient.ToolMode.BOX_FILL
                || mode == ShipyardClient.ToolMode.BOX_HOLLOW
                || mode == ShipyardClient.ToolMode.ELLIPSOID
                || mode == ShipyardClient.ToolMode.BOX_WALLS
                || mode == ShipyardClient.ToolMode.LINE
                || mode == ShipyardClient.ToolMode.REPLACE;
    }

    private static byte shapeModeForTool(ShipyardClient.ToolMode mode) {
        return switch (mode) {
            case BOX_HOLLOW -> ShipyardShapes.MODE_FILL_HOLLOW;
            case ELLIPSOID -> ShipyardShapes.MODE_ELLIPSOID;
            case BOX_WALLS -> ShipyardShapes.MODE_FILL_WALLS;
            case LINE -> ShipyardShapes.MODE_LINE;
            case REPLACE -> ShipyardShapes.MODE_REPLACE;
            case BOX_ERASE -> ShipyardShapes.MODE_CLEAR;
            default -> ShipyardShapes.MODE_FILL_SOLID;
        };
    }

    private static String toolLabel(ShipyardClient.ToolMode mode) {
        return switch (mode) {
            case SINGLE -> "单块";
            case BOX_FILL -> "填充";
            case BOX_HOLLOW -> "方壳";
            case ELLIPSOID -> "艇壳";
            case BOX_WALLS -> "舱壁";
            case LINE -> "直线";
            case REPLACE -> "替换";
            case BOX_ERASE -> "框删";
            case COPY_SELECT -> "复制";
            case CUT_SELECT -> "剪切";
            case PASTE -> "粘贴(" + ShipyardClient.clipboardSize() + ")";
            case WIRE -> "连线";
        };
    }

    private static String mirrorShortLabel() {
        return switch (ShipyardClient.mirrorAxis()) {
            case ShipyardShapes.MIRROR_X -> "X";
            case ShipyardShapes.MIRROR_Z -> "Z";
            default -> "关";
        };
    }

    private static String gridShortLabel() {
        return switch (ShipyardClient.gridMode()) {
            case LOCAL -> "局";
            case GLOBAL -> "全";
            case OFF -> "关";
        };
    }

    private void pickBlock(int screenX, int screenY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ShipyardClient.updateTarget(screenX, screenY, width, height);
        BlockPos target = ShipyardClient.target();
        if (target == null) {
            return;
        }
        BlockState state = mc.level.getBlockState(target);
        ShipyardClient.syncRotationFromBlock(state);
        category = ShipyardPalette.Category.ALL;
        presetsTab = false;
        syncSearchVisibility();
        if (search != null && !search.getValue().isEmpty()) {
            search.setValue("");
        }
        List<ShipyardPalette.Entry> all = ShipyardPalette.entries(category, "");
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).state().getBlock() == state.getBlock()) {
                selected = i;
                pushQuickBar(all.get(i));
                return;
            }
        }
    }

    private static boolean inBox(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }
}
