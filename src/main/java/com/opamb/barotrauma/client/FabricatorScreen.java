package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.equipment.BagOverlay;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.equipment.BagManager;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.fabricator.FabricatorCatalog;
import com.opamb.barotrauma.fabricator.FabricatorData;
import com.opamb.barotrauma.menu.FabricatorMenu;
import com.opamb.barotrauma.network.FabricatorActionPacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 加工台界面，分区照原作 {@code Fabricator.CreateGUI}：
 *
 * <pre>
 *   [分类]  筛选[______]    | 输出槽 [ ]  成品名
 *   [按钮]  ----------------| 所需材料： 图标  x/y
 *   [按钮]  配方列表         | 图标  x/y
 *   [按钮]  ...             | 时间 / 功率
 *                           | 数量 [-] 1 [+]   [ Fabricate ]
 *                           | [====进度====]
 *   输入 [][][][][]  输出[]
 *   装备背包区（3×9，和玩家物品栏同款）/ 快捷栏
 * </pre>
 *
 * <p>分类按钮是原作左侧竖排那一列（All + 各分类），名字做了中文本地化。</p>
 */
public class FabricatorScreen extends ScaledContainerScreen<FabricatorMenu> {

    private static final int W = 440;
    private static final int H = 336;

    private static final int BG = 0xF0141B16;
    private static final int EDGE = 0xFF3B463C;
    private static final int PANEL = 0xFF1E2A22;
    private static final int INNER = 0xFF121A15;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF7C8478;
    private static final int ACCENT = 0xFFE0762F;
    private static final int GREEN = 0xFF6FD07A;
    private static final int RED = 0xFFC8483C;
    private static final int TRACK = 0xFF0C120E;

    private static final int LIST_X = 46;
    private static final int LIST_Y = 44;
    private static final int LIST_W = 150;
    private static final int ROW = 12;
    private static final int MAX_ROWS = 12;

    private String category;
    private String search = "";
    private int scroll;
    private String selectedId;
    private int amount = 1;

    private EditBox filterBox;

    public FabricatorScreen(FabricatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    /* ---------------------------------------------------------------- 数据 */

    private List<FabricatorCatalog.Recipe> visibleRecipes() {
        List<FabricatorCatalog.Recipe> list = new ArrayList<>();
        for (FabricatorCatalog.Recipe recipe : FabricatorData.catalog().recipes()) {
            if (category != null && !category.isEmpty()
                    && !recipe.category().equals(category)) {
                continue;
            }
            if (!search.isEmpty()) {
                String name = FabricatorData.result(recipe).getHoverName().getString();
                if (!name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            list.add(recipe);
        }
        return list;
    }

    private FabricatorCatalog.Recipe selected() {
        return selectedId == null ? null : FabricatorData.catalog().recipe(selectedId);
    }

    /** 背包 + 台面输入格里一共有多少件（背包格也算）。 */
    private int available(String itemId) {
        var item = FabricatorData.item(itemId);
        int have = 0;
        if (minecraft != null && minecraft.player != null) {
            var inv = minecraft.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && stack.is(item)) {
                    have += stack.getCount();
                }
            }
            var bag = BagManager.container(minecraft.player);
            for (int i = 0; i < bag.getContainerSize(); i++) {
                ItemStack stack = bag.getItem(i);
                if (!stack.isEmpty() && stack.is(item)) {
                    have += stack.getCount();
                }
            }
        }
        for (int i = 0; i < FabricatorMenu.INPUT_SLOTS; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.is(item)) {
                have += stack.getCount();
            }
        }
        return have;
    }

    /** 分类名本地化：Material → 材料，找不到翻译就退回原名。 */
    private static String categoryName(String raw, boolean all) {
        if (all) {
            return Component.translatable("gui.barotrauma.fabricator.category.all").getString();
        }
        String token = raw.split(",")[0].trim().toLowerCase(Locale.ROOT);
        String key = "gui.barotrauma.fabricator.category." + token;
        String translated = Language.getInstance().getOrDefault(key);
        return translated.equals(key) ? raw.split(",")[0].trim() : translated;
    }

    private List<String> categories() {
        return FabricatorData.catalog().categories();
    }

    /* ---------------------------------------------------------------- 生命周期 */

    @Override
    protected void init() {
        super.init();
        if (category == null) {
            category = "";
        }
        if (selectedId == null) {
            List<FabricatorCatalog.Recipe> recipes = visibleRecipes();
            if (!recipes.isEmpty()) {
                selectedId = recipes.get(0).id();
            }
        }
        filterBox = new EditBox(font, leftPos + LIST_X, topPos + 24, LIST_W, 14,
                Component.translatable("gui.barotrauma.fabricator.filter"));
        filterBox.setValue(search);
        filterBox.setResponder(text -> {
            search = text;
            scroll = 0;
            // 选中的配方可能已经不在筛选结果里：不换的话制作按钮还会发旧配方，
            // 材料齐全时服务端会照旧扣料开工。
            List<FabricatorCatalog.Recipe> recipes = visibleRecipes();
            boolean present = false;
            for (FabricatorCatalog.Recipe recipe : recipes) {
                if (recipe.id().equals(selectedId)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                selectedId = recipes.isEmpty() ? null : recipes.get(0).id();
            }
        });
        addRenderableWidget(filterBox);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (filterBox != null && filterBox.isFocused()) {
            if (keyCode == 256) {
                filterBox.setFocused(false);
                return true;
            }
            if (filterBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            // 聚焦在筛选框时吃掉其它按键，避免按 E 直接把界面关掉。
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (filterBox != null && filterBox.isFocused() && filterBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /* ---------------------------------------------------------------- 绘制 */

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, ACCENT, false);
        int working = menu.data().get(FabricatorMenu.DATA_WORKING);
        int power = menu.data().get(FabricatorMenu.DATA_POWER);
        int condition = menu.data().get(FabricatorMenu.DATA_CONDITION);
        String status = condition <= 0 ? "已损坏（需要扳手）"
                : power == 0 ? (working != 0 ? "供电不足，制作暂停" : "供电不足（未通电）")
                : "状态：可用";
        int color = condition <= 0 ? RED : power == 0 ? 0xFFD8B44A : DIM;
        graphics.drawString(font, status, W - 8 - font.width(status), 6, color, false);
        graphics.drawString(font, "输入", LIST_X, 202, DIM, false);
        graphics.drawString(font, "输出", 206, 202, DIM, false);


        FabricatorCatalog.Recipe recipe = selected();
        if (recipe == null) {
            return;
        }
        ItemStack result = FabricatorData.result(recipe);
        graphics.drawString(font, result.getHoverName(), 228, 28, TEXT, false);
        graphics.drawString(font, "所需材料", 206, 46, DIM, false);
        int y = 60;
        for (FabricatorCatalog.Ingredient ingredient : recipe.ingredients()) {
            int have = available(ingredient.itemId());
            ItemStack stack = new ItemStack(FabricatorData.item(ingredient.itemId()));
            graphics.renderItem(stack, 206, y - 2);
            int need = ingredient.count() * amount;
            graphics.drawString(font, stack.getHoverName().getString() + "  " + have + "/" + need,
                    226, y + 1, have >= need ? GREEN : RED, false);
            y += 16;
        }
        graphics.drawString(font, String.format("%.0f 秒 · %.0f kW", recipe.time(), 500.0D),
                206, 132, DIM, false);
        String amountText = String.valueOf(amount);
        graphics.drawString(font, amountText, 246 - font.width(amountText) / 2, 152, TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + W, y + H, BG);
        graphics.fill(x, y, x + W, y + 1, EDGE);
        graphics.fill(x, y + H - 1, x + W, y + H, EDGE);
        graphics.fill(x, y, x + 1, y + H, EDGE);
        graphics.fill(x + W - 1, y, x + W, y + H, EDGE);
        // 中缝：左列表 / 右详情（原作的 VerticalLine）
        graphics.fill(x + 200, y + 20, x + 201, y + 196, EDGE);

        // 左侧分类按钮（原作左侧竖排那一列）
        List<String> categories = categories();
        drawCategory(graphics, x, y, 0, categoryName("", true), category.isEmpty(), mouseX, mouseY);
        for (int i = 0; i < categories.size(); i++) {
            String entry = categories.get(i);
            drawCategory(graphics, x, y, i + 1, categoryName(entry, false),
                    entry.equals(category), mouseX, mouseY);
        }

        // 配方列表
        graphics.fill(x + LIST_X - 2, y + LIST_Y - 2, x + LIST_X + LIST_W + 2, y + 190, INNER);
        List<FabricatorCatalog.Recipe> recipes = visibleRecipes();
        scroll = Mth.clamp(scroll, 0, Math.max(0, recipes.size() - MAX_ROWS));
        int rows = Math.min(MAX_ROWS, Math.max(0, recipes.size() - scroll));
        for (int i = 0; i < rows; i++) {
            FabricatorCatalog.Recipe recipe = recipes.get(scroll + i);
            int rowY = y + LIST_Y + i * ROW;
            boolean chosen = recipe.id().equals(selectedId);
            boolean hovered = mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                    && mouseY >= rowY && mouseY < rowY + ROW;
            if (chosen || hovered) {
                graphics.fill(x + LIST_X, rowY, x + LIST_X + LIST_W, rowY + ROW,
                        chosen ? PANEL : 0xFF1A241D);
            }
            ItemStack result = FabricatorData.result(recipe);
            graphics.renderItem(result, x + LIST_X, rowY - 2);
            graphics.drawString(font, result.getHoverName(), x + LIST_X + 20, rowY + 1,
                    chosen ? TEXT : DIM, false);
        }
        if (recipes.isEmpty()) {
            graphics.drawString(font, "没有匹配的配方", x + LIST_X, y + LIST_Y, DIM, false);
        }

        // 右侧：成品预览、数量、按钮、进度
        FabricatorCatalog.Recipe recipe = selected();
        if (recipe != null) {
            graphics.renderItem(FabricatorData.result(recipe), x + 206, y + 26);
        }
        drawButton(graphics, x + 206, y + 148, 16, 16, "-", amount > 1, mouseX, mouseY);
        drawButton(graphics, x + 270, y + 148, 16, 16, "+", amount < 99, mouseX, mouseY);
        boolean working = menu.data().get(FabricatorMenu.DATA_WORKING) != 0;
        boolean canCraft = recipe != null && !working
                && menu.data().get(FabricatorMenu.DATA_CONDITION) > 0
                && menu.data().get(FabricatorMenu.DATA_POWER) != 0;
        drawButton(graphics, x + 296, y + 146, 136, 22,
                working ? "取消制作" : "制作", canCraft, mouseX, mouseY);

        int progress = menu.data().get(FabricatorMenu.DATA_PROGRESS);
        graphics.fill(x + 206, y + 178, x + 432, y + 188, TRACK);
        int filled = Math.round(226 * Mth.clamp(progress / 1000.0F, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.fill(x + 206, y + 178, x + 206 + filled, y + 188, GREEN);
        }
        String percent = (progress / 10) + "%";
        graphics.drawString(font, percent, x + 206 + (226 - font.width(percent)) / 2,
                y + 179, TEXT, false);

        // 底部槽位底
        for (int i = 0; i < FabricatorMenu.INPUT_SLOTS; i++) {
            slotBg(graphics, x + 46 + i * 20 - 1, y + 214 - 1);
        }
        slotBg(graphics, x + 206 - 1, y + 214 - 1);
        // 快捷栏格子底；上面那 27 格是"装备背包区"，由 BagOverlay 整块画。
        for (int col = 0; col < 9; col++) {
            slotBg(graphics, x + FabricatorMenu.INV_AREA_X + col * 18 - 1,
                    y + FabricatorMenu.INV_AREA_Y + 58 - 1);
        }
        if (minecraft != null && minecraft.player != null) {
            BagSlots.rebuildClientAt(menu, minecraft.player, FabricatorMenu.INV_START,
                    FabricatorMenu.INV_AREA_X, FabricatorMenu.INV_AREA_Y,
                    FabricatorMenu.INV_AREA_W, FabricatorMenu.INV_AREA_H);
            BagOverlay.renderIn(graphics, x, y, minecraft.player,
                    FabricatorMenu.INV_AREA_X, FabricatorMenu.INV_AREA_Y,
                    FabricatorMenu.INV_AREA_W, FabricatorMenu.INV_AREA_H);
            com.opamb.barotrauma.client.equipment.HandsSlotOverlay.renderIn(graphics, menu, x, y);
        }
    }

    private void drawCategory(GuiGraphics graphics, int x, int y, int index, String label,
                              boolean active, int mouseX, int mouseY) {
        int bx = x + 6;
        int by = y + 26 + index * 24;
        boolean hovered = mouseX >= bx && mouseX < bx + 34 && mouseY >= by && mouseY < by + 20;
        graphics.fill(bx, by, bx + 34, by + 20, active ? PANEL : (hovered ? 0xFF1A241D : 0xFF171F1A));
        graphics.fill(bx, by, bx + 34, by + 1, EDGE);
        graphics.drawString(font, label, bx + (34 - font.width(label)) / 2, by + 6,
                active ? ACCENT : DIM, false);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h, String label,
                            boolean active, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, active ? (hovered ? 0xFF2C4034 : PANEL) : 0xFF171F1A);
        graphics.fill(x, y, x + w, y + 1, EDGE);
        graphics.fill(x, y + h - 1, x + w, y + h, EDGE);
        graphics.drawString(font, label, x + (w - font.width(label)) / 2,
                y + (h - 8) / 2 + 1, active ? TEXT : DIM, false);
    }

    private void slotBg(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
    }

    /* ---------------------------------------------------------------- 交互 */

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (button == 0) {
            int x = leftPos;
            int y = topPos;
            // 分类按钮
            if (mouseX >= x + 6 && mouseX < x + 40) {
                for (int i = 0; i <= categories().size(); i++) {
                    int by = y + 26 + i * 24;
                    if (mouseY >= by && mouseY < by + 20) {
                        category = i == 0 ? "" : categories().get(i - 1);
                        scroll = 0;
                        List<FabricatorCatalog.Recipe> recipes = visibleRecipes();
                        selectedId = recipes.isEmpty() ? null : recipes.get(0).id();
                        return true;
                    }
                }
            }
            // 配方行
            List<FabricatorCatalog.Recipe> recipes = visibleRecipes();
            for (int i = 0; i < Math.min(MAX_ROWS, Math.max(0, recipes.size() - scroll)); i++) {
                int rowY = y + LIST_Y + i * ROW;
                if (mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_W
                        && mouseY >= rowY && mouseY < rowY + ROW) {
                    selectedId = recipes.get(scroll + i).id();
                    return true;
                }
            }
            // 数量 -/+
            if (inside(mouseX, mouseY, x + 206, y + 148, 16, 16)) {
                amount = Math.max(1, amount - 1);
                return true;
            }
            if (inside(mouseX, mouseY, x + 270, y + 148, 16, 16)) {
                amount = Math.min(99, amount + 1);
                return true;
            }
            // 制作 / 取消
            if (inside(mouseX, mouseY, x + 296, y + 146, 136, 22)) {
                boolean working = menu.data().get(FabricatorMenu.DATA_WORKING) != 0;
                if (working) {
                    ModNetwork.CHANNEL.sendToServer(new FabricatorActionPacket(
                            menu.pos(), FabricatorActionPacket.CANCEL, 0, 1));
                } else {
                    FabricatorCatalog.Recipe recipe = selected();
                    int index = recipe == null ? -1
                            : FabricatorData.catalog().recipes().indexOf(recipe);
                    if (index >= 0) {
                        // 原版按钮包只有 1 字节，装不下“配方下标 + 数量”，
                        // 改用独立包分开传，服务端再校验。
                        ModNetwork.CHANNEL.sendToServer(new FabricatorActionPacket(
                                menu.pos(), FabricatorActionPacket.START, index, amount));
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double delta) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (mouseX < leftPos + 200) {
            scroll = Math.max(0, scroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(screenX, screenY, delta);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
