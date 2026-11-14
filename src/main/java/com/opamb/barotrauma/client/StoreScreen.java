package com.opamb.barotrauma.client;

import com.opamb.barotrauma.equipment.CrateContainer;
import com.opamb.barotrauma.equipment.ItemSubSlot;
import com.opamb.barotrauma.item.CrateItem;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.StoreActionPacket;
import com.opamb.barotrauma.network.StoreStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商人商店界面（独立于任务/维修终端）。
 *
 * <p>照原作三个标签页：购买、出售、潜艇（{@code StoreTab.SellSub}，从潜艇货柜
 * 出售）。每行有数量选择（−/数量/+），选好的数量进购物车，底部显示总价并
 * 整单成交；服务端整单校验库存/余额/耐久。</p>
 */
public final class StoreScreen extends Screen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 252;
    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = 9;
    private static final int TAB_BUY = 0;
    private static final int TAB_SELL = 1;
    private static final int TAB_SUB = 2;

    private static final int BG = 0xF01B2320;
    private static final int EDGE = 0xFF3B463C;
    private static final int BOX = 0xFF232B25;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF8B9184;
    private static final int ACCENT = 0xFFE0762F;
    private static final int GREEN = 0xFF7BE38B;
    private static final int RED = 0xFFFF7A6A;
    private static final int HOVER = 0xFF39483A;

    private StoreStatePacket state;
    private int tab = TAB_BUY;
    private int scroll;
    private final Map<String, Integer> buyCart = new LinkedHashMap<>();
    private final Map<Integer, Integer> sellCart = new LinkedHashMap<>();
    private final Map<String, Integer> subCart = new LinkedHashMap<>();
    /** 照原作购买确认：先问"现在拿"还是"送到潜艇"。 */
    private boolean deliveryPrompt;

    private record SellRow(int slot, ItemStack stack, StoreStatePacket.Entry entry) {
    }

    private StoreScreen(StoreStatePacket state) {
        super(Component.literal("商人"));
        this.state = state;
    }

    /** 服务端发来商店快照：已经打开就刷新（并清空购物车），否则新开界面。 */
    public static void receive(StoreStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof StoreScreen screen) {
            screen.state = packet;
            screen.buyCart.clear();
            screen.sellCart.clear();
            screen.subCart.clear();
            screen.deliveryPrompt = false;
            screen.scroll = Math.max(0, Math.min(screen.scroll, screen.maxScroll()));
        } else {
            minecraft.setScreen(new StoreScreen(packet));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int leftPos() {
        return (width - PANEL_W) / 2;
    }

    private int topPos() {
        return (height - PANEL_H) / 2;
    }

    private int listTop() {
        return topPos() + 40;
    }

    private List<StoreStatePacket.Entry> buyEntries() {
        List<StoreStatePacket.Entry> out = new ArrayList<>();
        for (StoreStatePacket.Entry entry : state.entries()) {
            if (entry.buyable()) {
                out.add(entry);
            }
        }
        return out;
    }

    private List<SellRow> sellRows() {
        List<SellRow> out = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return out;
        }
        Map<String, StoreStatePacket.Entry> byId = new HashMap<>();
        for (StoreStatePacket.Entry entry : state.entries()) {
            byId.put(entry.itemId(), entry);
        }
        var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null) {
                continue;
            }
            StoreStatePacket.Entry entry = byId.get(key.toString());
            if (entry == null || !canSell(stack)) {
                continue;
            }
            out.add(new SellRow(slot, stack, entry));
        }
        return out;
    }

    private static boolean canSell(ItemStack stack) {
        if (ItemSubSlot.hasSubSlot(stack) && !ItemSubSlot.get(stack).isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof CrateItem crate
                && !new CrateContainer(stack, crate.capacity()).isEmpty()) {
            return false;
        }
        if (stack.isDamageableItem()) {
            double ratio = (stack.getMaxDamage() - stack.getDamageValue())
                    / (double) Math.max(1, stack.getMaxDamage());
            return ratio >= 0.9D;
        }
        return true;
    }

    private int rowCount() {
        return switch (tab) {
            case TAB_BUY -> buyEntries().size();
            case TAB_SELL -> sellRows().size();
            default -> state.subEntries().size();
        };
    }

    private int maxScroll() {
        return Math.max(0, rowCount() - VISIBLE_ROWS);
    }

    private int buyQuantity(String itemId) {
        return buyCart.getOrDefault(itemId, 0);
    }

    private int sellQuantity(int slot) {
        return sellCart.getOrDefault(slot, 0);
    }

    private int subQuantity(String itemId) {
        return subCart.getOrDefault(itemId, 0);
    }

    private int buyTotal() {
        int total = 0;
        for (Map.Entry<String, Integer> line : buyCart.entrySet()) {
            StoreStatePacket.Entry entry = entryById(line.getKey());
            if (entry != null) {
                total += entry.buyPrice() * line.getValue();
            }
        }
        return total;
    }

    private int sellTotal() {
        int total = 0;
        List<SellRow> rows = sellRows();
        for (Map.Entry<Integer, Integer> line : sellCart.entrySet()) {
            for (SellRow row : rows) {
                if (row.slot() == line.getKey()) {
                    total += row.entry().sellPrice() * line.getValue();
                    break;
                }
            }
        }
        return total;
    }

    private int subTotal() {
        int total = 0;
        for (Map.Entry<String, Integer> line : subCart.entrySet()) {
            StoreStatePacket.SubEntry entry = subEntryById(line.getKey());
            if (entry != null) {
                total += entry.sellPrice() * line.getValue();
            }
        }
        return total;
    }

    private int cartCount() {
        int count = 0;
        if (tab == TAB_BUY) {
            for (int value : buyCart.values()) {
                count += value;
            }
        } else if (tab == TAB_SELL) {
            for (int value : sellCart.values()) {
                count += value;
            }
        } else {
            for (int value : subCart.values()) {
                count += value;
            }
        }
        return count;
    }

    private StoreStatePacket.Entry entryById(String itemId) {
        for (StoreStatePacket.Entry entry : state.entries()) {
            if (entry.itemId().equals(itemId)) {
                return entry;
            }
        }
        return null;
    }

    private StoreStatePacket.SubEntry subEntryById(String itemId) {
        for (StoreStatePacket.SubEntry entry : state.subEntries()) {
            if (entry.itemId().equals(itemId)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = leftPos();
        int y = topPos();
        g.fill(x, y, x + PANEL_W, y + PANEL_H, BG);
        g.fill(x, y, x + PANEL_W, y + 1, EDGE);
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, EDGE);
        g.fill(x, y, x + 1, y + PANEL_H, EDGE);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, EDGE);

        g.drawString(font, "商人", x + 10, y + 6, ACCENT, false);
        String wallet = "钱包 " + state.wallet() + " mk";
        g.drawString(font, wallet, x + PANEL_W - 10 - font.width(wallet), y + 6, GREEN, false);
        String balance = "商人余额 " + state.merchantBalance() + " mk";
        g.drawString(font, balance, x + PANEL_W - 10 - font.width(balance), y + 18, DIM, false);

        drawTab(g, x + 10, y + 22, "购买", tab == TAB_BUY, mouseX, mouseY);
        drawTab(g, x + 70, y + 22, "出售", tab == TAB_SELL, mouseX, mouseY);
        drawTab(g, x + 130, y + 22, "潜艇", tab == TAB_SUB, mouseX, mouseY);
        g.fill(x + 8, listTop() - 3, x + PANEL_W - 8, listTop() - 2, EDGE);

        if (tab == TAB_BUY) {
            renderBuy(g, mouseX, mouseY);
        } else if (tab == TAB_SELL) {
            renderSell(g, mouseX, mouseY);
        } else {
            renderSub(g, mouseX, mouseY);
        }

        int total = tab == TAB_BUY ? buyTotal() : tab == TAB_SELL ? sellTotal() : subTotal();
        int cartY = y + PANEL_H - 38;
        String cartText = switch (tab) {
            case TAB_BUY -> "购物车 " + cartCount() + " 件 · 总价 " + total + " mk";
            case TAB_SELL -> "出售车 " + cartCount() + " 件 · 总价 " + total + " mk";
            default -> "从潜艇出售 " + cartCount() + " 件 · 总价 " + total + " mk";
        };
        g.drawString(font, cartText, x + 10, cartY + 5, total > state.wallet()
                && tab == TAB_BUY ? RED : TEXT, false);
        int buttonX = x + PANEL_W - 120;
        boolean can = cartCount() > 0;
        boolean hover = can && mouseX >= buttonX && mouseX < buttonX + 110
                && mouseY >= cartY && mouseY < cartY + 18;
        g.fill(buttonX, cartY, buttonX + 110, cartY + 18,
                can ? hover ? HOVER : 0xFF3A4A3C : BOX);
        g.fill(buttonX, cartY, buttonX + 110, cartY + 1, EDGE);
        g.drawCenteredString(font, tab == TAB_BUY ? "购买" : "出售",
                buttonX + 55, cartY + 5, can ? TEXT : DIM);

        g.drawString(font, "−/+ 调数量 · 左键行 +1 · 滚轮翻页 · Esc 关闭",
                x + 10, y + PANEL_H - 14, DIM, false);

        if (deliveryPrompt) {
            renderDeliveryPrompt(g, mouseX, mouseY);
        }
    }

    /** 购买确认弹窗：现在拿 / 送到潜艇（照原作两个交付选项）。 */
    private void renderDeliveryPrompt(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, width, height, 0xA0000000);
        int w = 260;
        int h = 88;
        int px = (width - w) / 2;
        int py = (height - h) / 2;
        g.fill(px, py, px + w, py + h, BG);
        g.fill(px, py, px + w, py + 1, EDGE);
        g.fill(px, py + h - 1, px + w, py + h, EDGE);
        g.fill(px, py, px + 1, py + h, EDGE);
        g.fill(px + w - 1, py, px + w, py + h, EDGE);
        g.drawString(font, "要立即获得已购买物品，还是在起航时送至潜艇？", px + 10, py + 8, TEXT, false);
        g.drawString(font, "立即获得：直接进背包", px + 10, py + 24, DIM, false);
        g.drawString(font, "送至潜艇：起航时放进船上货柜", px + 10, py + 36, DIM, false);
        int by = py + h - 26;
        drawPromptButton(g, "立即获得", px + 12, by, 110, mouseX, mouseY);
        drawPromptButton(g, "送至潜艇", px + 138, by, 110, mouseX, mouseY);
    }

    private void drawPromptButton(GuiGraphics g, String label, int x, int y, int w,
                                  int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
        g.fill(x, y, x + w, y + 18, hover ? HOVER : 0xFF3A4A3C);
        g.fill(x, y, x + w, y + 1, EDGE);
        g.drawCenteredString(font, label, x + w / 2, y + 5, TEXT);
    }

    private void drawTab(GuiGraphics g, int x, int y, String label, boolean selected,
                         int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + 56 && mouseY >= y && mouseY < y + 14;
        g.fill(x, y, x + 56, y + 14, selected ? ACCENT : hover ? HOVER : BOX);
        g.fill(x, y, x + 56, y + 1, EDGE);
        g.drawCenteredString(font, label, x + 28, y + 3, selected ? 0xFF201810 : TEXT);
    }

    private void renderBuy(GuiGraphics g, int mouseX, int mouseY) {
        List<StoreStatePacket.Entry> entries = buyEntries();
        int x = leftPos();
        int top = listTop();
        int shown = Math.min(VISIBLE_ROWS, Math.max(0, entries.size() - scroll));
        for (int i = 0; i < shown; i++) {
            StoreStatePacket.Entry entry = entries.get(scroll + i);
            int rowY = top + i * ROW_H;
            boolean hover = mouseX >= x + 8 && mouseX < x + PANEL_W - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            g.fill(x + 8, rowY, x + PANEL_W - 8, rowY + ROW_H, hover ? HOVER : BOX);
            ItemStack stack = stackFor(entry.itemId());
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 12, rowY + 1);
            }
            String name = stack.isEmpty() ? entry.itemId() : stack.getHoverName().getString();
            g.drawString(font, name, x + 34, rowY + 5, TEXT, false);
            g.drawString(font, "[" + entry.category() + "]", x + 196, rowY + 5, DIM, false);
            String stock = entry.stock() > 0 ? "库存 " + entry.stock() : "售罄";
            g.drawString(font, stock, x + 262, rowY + 5,
                    entry.stock() > 0 ? DIM : RED, false);
            drawQuantity(g, x, rowY, buyQuantity(entry.itemId()), entry.stock(), mouseX, mouseY);
            String price = entry.buyPrice() + " mk";
            g.drawString(font, price, x + PANEL_W - 14 - font.width(price), rowY + 5,
                    state.wallet() >= entry.buyPrice() ? GREEN : RED, false);
        }
        if (entries.isEmpty()) {
            g.drawString(font, "货架空了", x + 12, top + 6, DIM, false);
        }
    }

    private void renderSell(GuiGraphics g, int mouseX, int mouseY) {
        List<SellRow> rows = sellRows();
        int x = leftPos();
        int top = listTop();
        int shown = Math.min(VISIBLE_ROWS, Math.max(0, rows.size() - scroll));
        for (int i = 0; i < shown; i++) {
            SellRow row = rows.get(scroll + i);
            int rowY = top + i * ROW_H;
            boolean hover = mouseX >= x + 8 && mouseX < x + PANEL_W - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            g.fill(x + 8, rowY, x + PANEL_W - 8, rowY + ROW_H, hover ? HOVER : BOX);
            g.renderItem(row.stack(), x + 12, rowY + 1);
            g.drawString(font, row.stack().getHoverName().getString(), x + 34, rowY + 5, TEXT, false);
            g.drawString(font, "槽位 " + row.slot(), x + 196, rowY + 5, DIM, false);
            g.drawString(font, "×" + row.stack().getCount(), x + 262, rowY + 5, DIM, false);
            drawQuantity(g, x, rowY, sellQuantity(row.slot()), row.stack().getCount(), mouseX, mouseY);
            String price = row.entry().sellPrice() + " mk";
            g.drawString(font, price, x + PANEL_W - 14 - font.width(price), rowY + 5, GREEN, false);
        }
        if (rows.isEmpty()) {
            g.drawString(font, "背包里没有商店收购的物品", x + 12, top + 6, DIM, false);
        }
    }

    /** 潜艇页：按物品类型聚合船上货柜里的可出售物品。 */
    private void renderSub(GuiGraphics g, int mouseX, int mouseY) {
        List<StoreStatePacket.SubEntry> entries = state.subEntries();
        int x = leftPos();
        int top = listTop();
        int shown = Math.min(VISIBLE_ROWS, Math.max(0, entries.size() - scroll));
        for (int i = 0; i < shown; i++) {
            StoreStatePacket.SubEntry entry = entries.get(scroll + i);
            int rowY = top + i * ROW_H;
            boolean hover = mouseX >= x + 8 && mouseX < x + PANEL_W - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            g.fill(x + 8, rowY, x + PANEL_W - 8, rowY + ROW_H, hover ? HOVER : BOX);
            ItemStack stack = stackFor(entry.itemId());
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 12, rowY + 1);
            }
            String name = stack.isEmpty() ? entry.itemId() : stack.getHoverName().getString();
            g.drawString(font, name, x + 34, rowY + 5, TEXT, false);
            g.drawString(font, "船上 ×" + entry.quantity(), x + 196, rowY + 5, DIM, false);
            drawQuantity(g, x, rowY, subQuantity(entry.itemId()), entry.quantity(), mouseX, mouseY);
            String price = entry.sellPrice() + " mk";
            g.drawString(font, price, x + PANEL_W - 14 - font.width(price), rowY + 5, GREEN, false);
        }
        if (entries.isEmpty()) {
            g.drawString(font, "潜艇里没有商店收购的物品", x + 12, top + 6, DIM, false);
        }
    }

    /** 行内 − 数量 + 控件；命中区与 mouseClicked 保持一致。 */
    private void drawQuantity(GuiGraphics g, int x, int rowY, int quantity, int max,
                              int mouseX, int mouseY) {
        int minusX = x + 300;
        int plusX = x + 346;
        int qtyX = x + 316;
        boolean minusHover = mouseX >= minusX && mouseX < minusX + 14
                && mouseY >= rowY + 2 && mouseY < rowY + 16;
        boolean plusHover = mouseX >= plusX && mouseX < plusX + 14
                && mouseY >= rowY + 2 && mouseY < rowY + 16;
        g.fill(minusX, rowY + 2, minusX + 14, rowY + 16,
                quantity > 0 ? minusHover ? HOVER : 0xFF2E3A30 : BOX);
        g.drawCenteredString(font, "-", minusX + 7, rowY + 5, quantity > 0 ? TEXT : DIM);
        g.fill(plusX, rowY + 2, plusX + 14, rowY + 16,
                quantity < max ? plusHover ? HOVER : 0xFF2E3A30 : BOX);
        g.drawCenteredString(font, "+", plusX + 7, rowY + 5, quantity < max ? TEXT : DIM);
        g.drawCenteredString(font, String.valueOf(quantity), qtyX + 15, rowY + 5,
                quantity > 0 ? GREEN : DIM);
    }

    private static ItemStack stackFor(String itemId) {
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (deliveryPrompt) {
            return handleDeliveryPromptClick(mouseX, mouseY, button);
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int x = leftPos();
        int y = topPos();
        if (mouseX >= x + 10 && mouseX < x + 66 && mouseY >= y + 22 && mouseY < y + 36) {
            tab = TAB_BUY;
            scroll = 0;
            return true;
        }
        if (mouseX >= x + 70 && mouseX < x + 126 && mouseY >= y + 22 && mouseY < y + 36) {
            tab = TAB_SELL;
            scroll = 0;
            return true;
        }
        if (mouseX >= x + 130 && mouseX < x + 186 && mouseY >= y + 22 && mouseY < y + 36) {
            tab = TAB_SUB;
            scroll = 0;
            return true;
        }
        int top = listTop();
        if (mouseY >= top && mouseY < top + VISIBLE_ROWS * ROW_H
                && mouseX >= x + 8 && mouseX < x + PANEL_W - 8) {
            int index = scroll + (int) ((mouseY - top) / ROW_H);
            if (tab == TAB_BUY) {
                List<StoreStatePacket.Entry> entries = buyEntries();
                if (index < 0 || index >= entries.size()) {
                    return super.mouseClicked(mouseX, mouseY, button);
                }
                StoreStatePacket.Entry entry = entries.get(index);
                int current = buyQuantity(entry.itemId());
                if (mouseX >= x + 300 && mouseX < x + 314 && current > 0) {
                    setBuyQuantity(entry.itemId(), current - 1);
                    return true;
                }
                if (mouseX >= x + 346 && mouseX < x + 360 && current < entry.stock()) {
                    setBuyQuantity(entry.itemId(), current + 1);
                    return true;
                }
                if (current < entry.stock()) {
                    setBuyQuantity(entry.itemId(), current + 1);
                }
                return true;
            }
            if (tab == TAB_SELL) {
                List<SellRow> rows = sellRows();
                if (index < 0 || index >= rows.size()) {
                    return super.mouseClicked(mouseX, mouseY, button);
                }
                SellRow row = rows.get(index);
                int current = sellQuantity(row.slot());
                if (mouseX >= x + 300 && mouseX < x + 314 && current > 0) {
                    setSellQuantity(row.slot(), current - 1);
                    return true;
                }
                if (mouseX >= x + 346 && mouseX < x + 360 && current < row.stack().getCount()) {
                    setSellQuantity(row.slot(), current + 1);
                    return true;
                }
                if (current < row.stack().getCount()) {
                    setSellQuantity(row.slot(), current + 1);
                }
                return true;
            }
            List<StoreStatePacket.SubEntry> entries = state.subEntries();
            if (index < 0 || index >= entries.size()) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            StoreStatePacket.SubEntry entry = entries.get(index);
            int current = subQuantity(entry.itemId());
            if (mouseX >= x + 300 && mouseX < x + 314 && current > 0) {
                setSubQuantity(entry.itemId(), current - 1);
                return true;
            }
            if (mouseX >= x + 346 && mouseX < x + 360 && current < entry.quantity()) {
                setSubQuantity(entry.itemId(), current + 1);
                return true;
            }
            if (current < entry.quantity()) {
                setSubQuantity(entry.itemId(), current + 1);
            }
            return true;
        }
        int cartY = y + PANEL_H - 38;
        int buttonX = x + PANEL_W - 120;
        if (cartCount() > 0 && mouseX >= buttonX && mouseX < buttonX + 110
                && mouseY >= cartY && mouseY < cartY + 18) {
            if (tab == TAB_BUY) {
                // 原作购买确认：先选"现在拿"还是"送到潜艇"。
                deliveryPrompt = true;
            } else if (tab == TAB_SELL) {
                List<StoreActionPacket.Line> lines = new ArrayList<>();
                for (Map.Entry<Integer, Integer> line : sellCart.entrySet()) {
                    if (line.getValue() > 0) {
                        lines.add(new StoreActionPacket.Line("", line.getKey(), line.getValue()));
                    }
                }
                ModNetwork.sendToServer(new StoreActionPacket(StoreActionPacket.SELL, lines, false));
            } else {
                List<StoreActionPacket.Line> lines = new ArrayList<>();
                for (Map.Entry<String, Integer> line : subCart.entrySet()) {
                    if (line.getValue() > 0) {
                        lines.add(new StoreActionPacket.Line(line.getKey(), -1, line.getValue()));
                    }
                }
                ModNetwork.sendToServer(new StoreActionPacket(StoreActionPacket.SELL_SUB, lines, false));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 购买交付弹窗的点击：现在拿 / 送到潜艇；点空白或 Esc 取消。 */
    private boolean handleDeliveryPromptClick(double mouseX, double mouseY, int button) {
        int w = 260;
        int h = 88;
        int px = (width - w) / 2;
        int py = (height - h) / 2;
        int by = py + h - 26;
        if (button == 0) {
            if (mouseX >= px + 12 && mouseX < px + 122 && mouseY >= by && mouseY < by + 18) {
                deliveryPrompt = false;
                sendBuy(true);
                return true;
            }
            if (mouseX >= px + 138 && mouseX < px + 248 && mouseY >= by && mouseY < by + 18) {
                deliveryPrompt = false;
                sendBuy(false);
                return true;
            }
            if (mouseX < px || mouseX >= px + w || mouseY < py || mouseY >= py + h) {
                deliveryPrompt = false;
            }
        }
        return true;
    }

    private void sendBuy(boolean deliverImmediately) {
        List<StoreActionPacket.Line> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> line : buyCart.entrySet()) {
            if (line.getValue() > 0) {
                lines.add(new StoreActionPacket.Line(line.getKey(), -1, line.getValue()));
            }
        }
        ModNetwork.sendToServer(new StoreActionPacket(StoreActionPacket.BUY, lines,
                deliverImmediately));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (deliveryPrompt && keyCode == 256) {
            deliveryPrompt = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void setBuyQuantity(String itemId, int quantity) {
        if (quantity <= 0) {
            buyCart.remove(itemId);
        } else {
            buyCart.put(itemId, quantity);
        }
    }

    private void setSellQuantity(int slot, int quantity) {
        if (quantity <= 0) {
            sellCart.remove(slot);
        } else {
            sellCart.put(slot, quantity);
        }
    }

    private void setSubQuantity(String itemId, int quantity) {
        if (quantity <= 0) {
            subCart.remove(itemId);
        } else {
            subCart.put(itemId, quantity);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (delta > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
