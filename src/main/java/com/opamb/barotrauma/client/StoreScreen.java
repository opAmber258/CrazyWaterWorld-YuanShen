package com.opamb.barotrauma.client;

import com.opamb.barotrauma.equipment.CrateContainer;
import com.opamb.barotrauma.equipment.ItemSubSlot;
import com.opamb.barotrauma.item.CrateItem;
import com.opamb.barotrauma.mission.MissionCargo;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.StoreActionPacket;
import com.opamb.barotrauma.network.StoreStatePacket;
import net.minecraft.client.Minecraft;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledScreen;
import net.minecraft.core.registries.BuiltInRegistries;
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
public final class StoreScreen extends ScaledScreen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 324;
    private static final int ROW_H = 30;
    private static final int VISIBLE_ROWS = 7;
    /** 单行数量上限，和服务端 StoreService.MAX_LINE_QUANTITY 保持一致。 */
    private static final int MAX_SUB_QUANTITY = 64;
    private static final int TAB_BUY = 0;
    private static final int TAB_SELL = 1;
    private static final int TAB_SUB = 2;

    /** 购买交付弹窗（照原作 GUIMessageBox：标题 + 一句提问 + 两个按钮）。 */
    private static final int PROMPT_W = 300;
    private static final int PROMPT_H = 86;
    private static final int PROMPT_BTN_W = 132;

    private static final int EDGE = PanelStyle.EDGE;
    private static final int TEXT = PanelStyle.TEXT;
    private static final int DIM = PanelStyle.DIM;
    private static final int ACCENT = PanelStyle.SERVICE;
    private static final int GREEN = 0xFF7BE38B;
    private static final int RED = 0xFFFF7A6A;
    private static final int HOVER = PanelStyle.SELECTED;

    private StoreStatePacket state;
    private int tab = TAB_BUY;
    private int scroll;
    private final Map<String, Integer> buyCart = new LinkedHashMap<>();
    private final Map<Integer, Integer> sellCart = new LinkedHashMap<>();
    /** 出售行选中的物品 ID：服务端用它校验槽位内容没被换过。 */
    private final Map<Integer, String> sellItemIds = new LinkedHashMap<>();
    private final Map<String, Integer> subCart = new LinkedHashMap<>();
    /** 已提交、等服务端快照返回：期间禁止再次提交，避免重复扣物品/扣钱。 */
    private boolean awaitingState;
    /** 提交时刻；服务端没回包时显示"结果未知"，并继续保持锁定避免重复扣款。 */
    private long awaitingSince;
    private boolean submitUnknown;
    /** 照原作购买确认：先问"现在拿"还是"送到潜艇"。 */
    private boolean deliveryPrompt;
    /** 键盘焦点：0 标签 / 1 商品行 / 2 提交按钮；纯键盘玩家靠 Tab 切换。 */
    private int focusArea;
    private int focusRow = -1;
    /** 购买确认弹窗的键盘选择：0 立即获得 / 1 送至潜艇。 */
    private int promptChoice;

    private record SellRow(int slot, ItemStack stack, StoreStatePacket.Entry entry) {
    }

    private StoreScreen(StoreStatePacket state) {
        super(Component.translatable("store.title"), PANEL_W, PANEL_H);
        this.state = state;
    }

    /** 服务端发来商店快照：已经打开就刷新（并清空购物车），否则新开界面。 */
    public static void receive(StoreStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof StoreScreen screen) {
            screen.state = packet;
            screen.buyCart.clear();
            screen.sellCart.clear();
            screen.sellItemIds.clear();
            screen.subCart.clear();
            screen.deliveryPrompt = false;
            screen.awaitingState = false;
            screen.submitUnknown = false;
            screen.scroll = Math.max(0, Math.min(screen.scroll, screen.maxScroll()));
        } else if (packet.open()) {
            // 交易响应是刷新包：玩家按 Esc 关掉后不能把商店重新弹出来。
            minecraft.setScreen(new StoreScreen(packet));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 设计坐标原点：缩放由 ScaledScreen 处理，这里恒为 0。 */
    private int leftPos() {
        return 0;
    }

    private int topPos() {
        return 0;
    }

    /** 面板按设计坐标绘制，缩放/居中由 {@link ScaledScreen} 统一处理。 */
    private int panelW() {
        return PANEL_W;
    }

    private int panelH() {
        return PANEL_H;
    }

    /** 可见行数由设计高度决定，其余靠滚动。 */
    private int visibleRows() {
        return VISIBLE_ROWS;
    }

    private int listTop() {
        return topPos() + 63;
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
        for (int slot = 0; slot < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; slot++) {
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
        // 任务箱即使被搬空也带着任务标记，服务端整单会拒绝；这里先过滤，
        // 否则购物车里混进任务箱后连普通商品一起卖不出去。
        if (MissionCargo.isMissionCrate(stack)) {
            return false;
        }
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
        return Math.max(0, rowCount() - visibleRows());
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
    protected void renderDesign(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (awaitingState && System.currentTimeMillis() - awaitingSince > 3000L) {
            // 没有订单 ID 回执，超时不能视为失败：继续锁定，提示"结果未知"，
            // 让玩家关掉界面重开确认，避免重复提交再卖一次。
            submitUnknown = true;
        }
        int x = leftPos();
        int y = topPos();
        PanelStyle.frame(g, x, y, panelW(), panelH(), PanelStyle.SERVICE);

        g.drawString(font, Component.translatable("store.title"), x + 10, y + 9, TEXT, false);
        String wallet = Component.translatable("store.wallet", state.wallet()).getString();
        g.drawString(font, wallet, x + panelW() - 10 - font.width(wallet), y + 9, GREEN, false);
        String balance = Component.translatable("store.merchant_balance",
                state.merchantBalance()).getString();
        g.drawString(font, balance, x + panelW() - 10 - font.width(balance), y + 22, DIM, false);

        drawTab(g, x + 10, y + 39, Component.translatable("store.tab.buy").getString(),
                tab == TAB_BUY, mouseX, mouseY);
        drawTab(g, x + 110, y + 39, Component.translatable("store.tab.sell").getString(),
                tab == TAB_SELL, mouseX, mouseY);
        drawTab(g, x + 210, y + 39, Component.translatable("store.tab.sub").getString(),
                tab == TAB_SUB, mouseX, mouseY);
        if (focusArea == 0) {
            // 键盘焦点在标签栏：给当前标签描一圈，旁白已经读过名字。
            int tabX = x + 10 + tab * 100;
            g.fill(tabX - 1, y + 38, tabX + 97, y + 39, ACCENT);
            g.fill(tabX - 1, y + 53, tabX + 97, y + 54, ACCENT);
            g.fill(tabX - 1, y + 38, tabX, y + 54, ACCENT);
            g.fill(tabX + 96, y + 38, tabX + 97, y + 54, ACCENT);
        }
        PanelStyle.well(g, x + 8, listTop() - 1, panelW() - 16, visibleRows() * ROW_H + 2);

        // 交付弹窗是模态的：打开期间不画列表行，避免行内的数量 −/0/+ 跟弹窗叠在一起。
        if (!deliveryPrompt) {
            if (tab == TAB_BUY) {
                renderBuy(g, mouseX, mouseY);
            } else if (tab == TAB_SELL) {
                renderSell(g, mouseX, mouseY);
            } else {
                renderSub(g, mouseX, mouseY);
            }
        }

        int total = tab == TAB_BUY ? buyTotal() : tab == TAB_SELL ? sellTotal() : subTotal();
        int cartY = y + panelH() - 38;
        String cartText = switch (tab) {
            case TAB_BUY -> Component.translatable("store.cart.buy",
                    cartCount(), total).getString();
            case TAB_SELL -> Component.translatable("store.cart.sell",
                    cartCount(), total).getString();
            default -> Component.translatable("store.cart.sub",
                    cartCount(), total).getString();
        };
        g.drawString(font, cartText, x + 10, cartY + 5, total > state.wallet()
                && tab == TAB_BUY ? RED : TEXT, false);
        int buttonX = x + panelW() - 120;
        boolean can = cartCount() > 0;
        boolean buttonFocused = focusArea == 2 && can;
        boolean hover = can && mouseX >= buttonX && mouseX < buttonX + 110
                && mouseY >= cartY && mouseY < cartY + 18;
        PanelStyle.control(g, buttonX, cartY, 110, 18, can, hover || buttonFocused);
        g.drawCenteredString(font, Component.translatable(
                        tab == TAB_BUY ? "store.tab.buy" : "store.tab.sell"),
                buttonX + 55, cartY + 5, can ? TEXT : DIM);

        if (submitUnknown) {
            g.drawCenteredString(font, Component.translatable("store.result_unknown"),
                    x + panelW() / 2, y + panelH() - 27, RED);
        }
        g.drawString(font, Component.translatable("store.hint"),
                x + 10, y + panelH() - 14, DIM, false);

        if (deliveryPrompt) {
            renderDeliveryPrompt(g, mouseX, mouseY);
        }
    }

    private int promptX() {
        return (designWidth() - PROMPT_W) / 2;
    }

    private int promptY() {
        return (designHeight() - PROMPT_H) / 2;
    }

    /**
     * 购买交付弹窗，照原作 {@code Store.BuyItems} 里的 {@code GUIMessageBox}：
     * 标题「新补给品」+ 一句提问 + 两个按钮（立即获得 / 送至潜艇），没有选项行。
     *
     * <p>用设计坐标（{@code designWidth}/{@code designHeight}）居中，跟着界面一起缩放；
     * 面板不透明，不会透出下面的商店列表。</p>
     */
    private void renderDeliveryPrompt(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, designWidth(), designHeight(), 0xA0000000);
        int px = promptX();
        int py = promptY();
        PanelStyle.frame(g, px, py, PROMPT_W, PROMPT_H, PanelStyle.SERVICE);
        PanelStyle.divider(g, px + 9, py + 19, PROMPT_W - 18);
        g.drawString(font, Component.translatable("store.delivery.title"),
                px + 10, py + 6, ACCENT, false);
        g.drawString(font, Component.translatable("store.delivery.immediate_desc"),
                px + 10, py + 26, DIM, false);
        g.drawString(font, Component.translatable("store.delivery.sub_desc"),
                px + 10, py + 38, DIM, false);
        int by = py + PROMPT_H - 28;
        drawPromptButton(g, Component.translatable("store.delivery.immediate").getString(),
                px + 12, by, PROMPT_BTN_W, mouseX, mouseY, promptChoice == 0);
        drawPromptButton(g, Component.translatable("store.delivery.sub").getString(),
                px + 156, by, PROMPT_BTN_W, mouseX, mouseY, promptChoice == 1);
    }

    private void drawPromptButton(GuiGraphics g, String label, int x, int y, int w,
                                  int mouseX, int mouseY, boolean selected) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
        PanelStyle.control(g, x, y, w, 18, true, hover || selected);
        g.drawCenteredString(font, label, x + w / 2, y + 5, TEXT);
    }

    private void drawTab(GuiGraphics g, int x, int y, String label, boolean selected,
                         int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + 96 && mouseY >= y && mouseY < y + 14;
        PanelStyle.control(g, x, y, 96, 14, true, selected || hover);
        g.drawCenteredString(font, label, x + 48, y + 3, selected ? TEXT : DIM);
    }

    private void renderBuy(GuiGraphics g, int mouseX, int mouseY) {
        List<StoreStatePacket.Entry> entries = buyEntries();
        int x = leftPos();
        int top = listTop();
        int shown = Math.min(visibleRows(), Math.max(0, entries.size() - scroll));
        for (int i = 0; i < shown; i++) {
            StoreStatePacket.Entry entry = entries.get(scroll + i);
            int rowY = top + i * ROW_H;
            boolean focused = focusArea == 1 && focusRow == scroll + i;
            boolean hover = mouseX >= x + 8 && mouseX < x + panelW() - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hover || focused) g.fill(x + 9, rowY, x + panelW() - 9, rowY + ROW_H, HOVER);
            PanelStyle.divider(g, x + 10, rowY + ROW_H - 1, panelW() - 20);
            if (focused) {
                g.fill(x + 8, rowY, x + 10, rowY + ROW_H, ACCENT);
            }
            ItemStack stack = stackFor(entry.itemId());
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 16, rowY + 7);
            }
            String name = stack.isEmpty() ? entry.itemId() : stack.getHoverName().getString();
            g.drawString(font, clipName(name, nameWidth(x)),
                    x + 40, rowY + 5, TEXT, false);
            if (showsSecondaryColumns()) {
                g.drawString(font, "[" + categoryLabel(entry.category()) + "]",
                        x + 40, rowY + 17, DIM, false);
                String stock = entry.stock() > 0
                        ? Component.translatable("store.stock", entry.stock()).getString()
                        : Component.translatable("store.sold_out").getString();
                g.drawString(font, stock, x + 177, rowY + 17,
                        entry.stock() > 0 ? DIM : RED, false);
            }
            drawQuantity(g, x, rowY, buyQuantity(entry.itemId()), entry.stock(), mouseX, mouseY);
            String price = entry.buyPrice() + " mk";
            g.drawString(font, price, x + panelW() - 18 - font.width(price), rowY + 11,
                    state.wallet() >= entry.buyPrice() ? GREEN : RED, false);
        }
        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("store.empty_shelf"),
                    x + 12, top + 6, DIM, false);
        }
    }

    /** 分类以稳定 id 下发，客户端按语言键显示；缺键时退回原 id。 */
    private static String categoryLabel(String category) {
        if (category == null || category.isEmpty()) {
            return "";
        }
        String key = "store.category." + category;
        String text = Component.translatable(key).getString();
        return key.equals(text) ? category : text;
    }

    private void renderSell(GuiGraphics g, int mouseX, int mouseY) {
        List<SellRow> rows = sellRows();
        int x = leftPos();
        int top = listTop();
        int shown = Math.min(visibleRows(), Math.max(0, rows.size() - scroll));
        for (int i = 0; i < shown; i++) {
            SellRow row = rows.get(scroll + i);
            int rowY = top + i * ROW_H;
            boolean focused = focusArea == 1 && focusRow == scroll + i;
            boolean hover = mouseX >= x + 8 && mouseX < x + panelW() - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hover || focused) g.fill(x + 9, rowY, x + panelW() - 9, rowY + ROW_H, HOVER);
            PanelStyle.divider(g, x + 10, rowY + ROW_H - 1, panelW() - 20);
            if (focused) {
                g.fill(x + 8, rowY, x + 10, rowY + ROW_H, ACCENT);
            }
            g.renderItem(row.stack(), x + 16, rowY + 7);
            g.drawString(font, clipName(row.stack().getHoverName().getString(),
                            nameWidth(x)),
                    x + 40, rowY + 5, TEXT, false);
            if (showsSecondaryColumns()) {
                g.drawString(font, Component.translatable("store.slot", row.slot()),
                        x + 40, rowY + 17, DIM, false);
                g.drawString(font, "×" + row.stack().getCount(), x + 177, rowY + 17, DIM, false);
            }
            drawQuantity(g, x, rowY, sellQuantity(row.slot()), row.stack().getCount(), mouseX, mouseY);
            String price = row.entry().sellPrice() + " mk";
            g.drawString(font, price, x + panelW() - 18 - font.width(price), rowY + 11, GREEN, false);
        }
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("store.empty_bag"),
                    x + 12, top + 6, DIM, false);
        }
    }

    /** 潜艇页：按物品类型聚合船上货柜里的可出售物品。 */
    private void renderSub(GuiGraphics g, int mouseX, int mouseY) {
        List<StoreStatePacket.SubEntry> entries = state.subEntries();
        int x = leftPos();
        int top = listTop();
        int shown = Math.min(visibleRows(), Math.max(0, entries.size() - scroll));
        for (int i = 0; i < shown; i++) {
            StoreStatePacket.SubEntry entry = entries.get(scroll + i);
            int rowY = top + i * ROW_H;
            boolean focused = focusArea == 1 && focusRow == scroll + i;
            boolean hover = mouseX >= x + 8 && mouseX < x + panelW() - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hover || focused) g.fill(x + 9, rowY, x + panelW() - 9, rowY + ROW_H, HOVER);
            PanelStyle.divider(g, x + 10, rowY + ROW_H - 1, panelW() - 20);
            if (focused) {
                g.fill(x + 8, rowY, x + 10, rowY + ROW_H, ACCENT);
            }
            ItemStack stack = stackFor(entry.itemId());
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 16, rowY + 7);
            }
            String name = stack.isEmpty() ? entry.itemId() : stack.getHoverName().getString();
            g.drawString(font, clipName(name, nameWidth(x)),
                    x + 40, rowY + 5, TEXT, false);
            if (showsSecondaryColumns()) {
                g.drawString(font, Component.translatable("store.on_ship", entry.quantity()),
                        x + 40, rowY + 17, DIM, false);
            }
            drawQuantity(g, x, rowY, subQuantity(entry.itemId()), entry.quantity(), mouseX, mouseY);
            String price = entry.sellPrice() + " mk";
            g.drawString(font, price, x + panelW() - 18 - font.width(price), rowY + 11, GREEN, false);
        }
        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("store.empty_sub"),
                    x + 12, top + 6, DIM, false);
        }
    }

    /** 行内数量控件的 −/+ 左边界：按面板右边对齐，窄面板时不会跑到面板外。 */
    /** 物品名最多画多宽：有后续列时到第一列（x+196）前为止，否则到数量控件前。 */
    private int nameWidth(int x) {
        return quantityMinusX(x) - x - 46;
    }

    private int quantityMinusX(int x) {
        return x + panelW() - 140;
    }

    private int quantityPlusX(int x) {
        return x + panelW() - 94;
    }

    /**
     * 分类/库存/槽位这些次要列只够放在原始宽度里；面板一收窄就会和数量控件、
     * 右对齐价格叠在一起，直接隐藏（数量控件本身仍显示已选数量）。
     */
    private boolean showsSecondaryColumns() {
        return panelW() >= PANEL_W;
    }

    /** 商品名按可用宽度截断，避免压到数量控件。 */
    private String clipName(String name, int maxWidth) {
        if (name == null || font.width(name) <= maxWidth) {
            return name;
        }
        int ellipsis = font.width("…");
        return font.plainSubstrByWidth(name, Math.max(0, maxWidth - ellipsis)) + "…";
    }

    /** 行内 − 数量 + 控件；命中区与 mouseClicked 保持一致。 */
    private void drawQuantity(GuiGraphics g, int x, int rowY, int quantity, int max,
                              int mouseX, int mouseY) {
        int minusX = quantityMinusX(x);
        int plusX = quantityPlusX(x);
        int qtyX = (minusX + 14 + plusX) / 2;
        boolean minusHover = mouseX >= minusX && mouseX < minusX + 14
                && mouseY >= rowY && mouseY < rowY + ROW_H;
        boolean plusHover = mouseX >= plusX && mouseX < plusX + 14
                && mouseY >= rowY && mouseY < rowY + ROW_H;
        PanelStyle.control(g, minusX, rowY + 7, 14, 16, quantity > 0, quantity > 0 && minusHover);
        PanelStyle.control(g, plusX, rowY + 7, 14, 16, quantity < max, quantity < max && plusHover);
        g.drawCenteredString(font, "−", minusX + 7, rowY + 11, quantity > 0 ? TEXT : DIM);
        g.drawCenteredString(font, "+", plusX + 7, rowY + 11, quantity < max ? TEXT : DIM);
        g.drawCenteredString(font, String.valueOf(quantity), qtyX, rowY + 11, quantity > 0 ? GREEN : DIM);
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
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (awaitingState) {
            // 上一次交易还在等服务端快照：先消费点击，避免连点重复提交。
            return true;
        }
        if (deliveryPrompt) {
            return handleDeliveryPromptClick(mouseX, mouseY, button);
        }
        if (button != 0) {
            return super.mouseClicked(screenX, screenY, button);
        }
        int x = leftPos();
        int y = topPos();
        if (mouseX >= x + 10 && mouseX < x + 106 && mouseY >= y + 39 && mouseY < y + 53) {
            selectTab(TAB_BUY);
            return true;
        }
        if (mouseX >= x + 110 && mouseX < x + 206 && mouseY >= y + 39 && mouseY < y + 53) {
            selectTab(TAB_SELL);
            return true;
        }
        if (mouseX >= x + 210 && mouseX < x + 306 && mouseY >= y + 39 && mouseY < y + 53) {
            selectTab(TAB_SUB);
            return true;
        }
        int top = listTop();
        if (mouseY >= top && mouseY < top + visibleRows() * ROW_H
                && mouseX >= x + 8 && mouseX < x + panelW() - 8) {
            int index = scroll + (int) ((mouseY - top) / ROW_H);
            if (tab == TAB_BUY) {
                List<StoreStatePacket.Entry> entries = buyEntries();
                if (index < 0 || index >= entries.size()) {
                    return super.mouseClicked(screenX, screenY, button);
                }
                focusArea = 1;
                focusRow = index;
                StoreStatePacket.Entry entry = entries.get(index);
                int current = buyQuantity(entry.itemId());
                if (mouseX >= quantityMinusX(x) && mouseX < quantityMinusX(x) + 14) {
                    // 数量为 0 时也消费减号点击，别继续落到"整行 +1"。
                    if (current > 0) {
                        setBuyQuantity(entry.itemId(), current - 1);
                    }
                    return true;
                }
                if (mouseX >= quantityPlusX(x) && mouseX < quantityPlusX(x) + 14 && current < entry.stock()) {
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
                    return super.mouseClicked(screenX, screenY, button);
                }
                focusArea = 1;
                focusRow = index;
                SellRow row = rows.get(index);
                int current = sellQuantity(row.slot());
                if (mouseX >= quantityMinusX(x) && mouseX < quantityMinusX(x) + 14) {
                    if (current > 0) {
                        setSellQuantity(row.slot(), current - 1, itemIdOf(row.stack()));
                    }
                    return true;
                }
                if (mouseX >= quantityPlusX(x) && mouseX < quantityPlusX(x) + 14 && current < row.stack().getCount()) {
                    setSellQuantity(row.slot(), current + 1, itemIdOf(row.stack()));
                    return true;
                }
                if (current < row.stack().getCount()) {
                    setSellQuantity(row.slot(), current + 1, itemIdOf(row.stack()));
                }
                return true;
            }
            List<StoreStatePacket.SubEntry> entries = state.subEntries();
            if (index < 0 || index >= entries.size()) {
                return super.mouseClicked(screenX, screenY, button);
            }
            focusArea = 1;
            focusRow = index;
            StoreStatePacket.SubEntry entry = entries.get(index);
            int current = subQuantity(entry.itemId());
            if (mouseX >= quantityMinusX(x) && mouseX < quantityMinusX(x) + 14) {
                if (current > 0) {
                    setSubQuantity(entry.itemId(), current - 1);
                }
                return true;
            }
            if (mouseX >= quantityPlusX(x) && mouseX < quantityPlusX(x) + 14
                    && current < Math.min(entry.quantity(), MAX_SUB_QUANTITY)) {
                setSubQuantity(entry.itemId(), current + 1);
                return true;
            }
            if (current < Math.min(entry.quantity(), MAX_SUB_QUANTITY)) {
                setSubQuantity(entry.itemId(), current + 1);
            }
            return true;
        }
        int cartY = y + panelH() - 38;
        int buttonX = x + panelW() - 120;
        if (cartCount() > 0 && mouseX >= buttonX && mouseX < buttonX + 110
                && mouseY >= cartY && mouseY < cartY + 18) {
            submitCart();
            return true;
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    /** 购买交付弹窗的点击：现在拿 / 送到潜艇；点面板外或 Esc 取消。 */
    private boolean handleDeliveryPromptClick(double mouseX, double mouseY, int button) {
        int px = promptX();
        int py = promptY();
        int by = py + PROMPT_H - 28;
        if (button == 0) {
            if (mouseX >= px + 12 && mouseX < px + 12 + PROMPT_BTN_W
                    && mouseY >= by && mouseY < by + 18) {
                deliveryPrompt = false;
                sendBuy(true);
                return true;
            }
            if (mouseX >= px + 156 && mouseX < px + 156 + PROMPT_BTN_W
                    && mouseY >= by && mouseY < by + 18) {
                deliveryPrompt = false;
                sendBuy(false);
                return true;
            }
            if (mouseX < px || mouseX >= px + PROMPT_W || mouseY < py || mouseY >= py + PROMPT_H) {
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
        sendLines(StoreActionPacket.BUY, lines, deliverImmediately);
    }

    /**
     * 提交购物车；行数上限和服务端解码用同一个常量。
     * 超上限时服务端会直接丢包，所以这里先在客户端拦下并提示。
     */
    private void sendLines(int action, List<StoreActionPacket.Line> lines, boolean deliver) {
        if (awaitingState) {
            // 上一单还在等服务端快照：键盘连按 Enter 也不能再发一份相同订单。
            return;
        }
        if (lines.size() > StoreActionPacket.MAX_LINES) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "store.too_many_lines", StoreActionPacket.MAX_LINES), true);
            }
            return;
        }
        awaitingState = true;
        submitUnknown = false;
        awaitingSince = System.currentTimeMillis();
        ModNetwork.sendToServer(new StoreActionPacket(action, lines, deliver));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (awaitingState && keyCode != 256) {
            // 上一单还在等服务端快照：键盘也不许改购物车（响应到达时 receive() 会清空
            // 这些选择，玩家看到的选择和实际成交对不上）。Esc 仍放行，可以关界面。
            return true;
        }
        if (deliveryPrompt) {
            if (keyCode == 256) {
                deliveryPrompt = false;
                return true;
            }
            if (keyCode == 263 || keyCode == 262) {
                promptChoice = 1 - promptChoice;
                narrate(Component.translatable(promptChoice == 0
                        ? "store.delivery.immediate" : "store.delivery.sub"));
                return true;
            }
            if (keyCode == 257 || keyCode == 335 || keyCode == 32) {
                deliveryPrompt = false;
                sendBuy(promptChoice == 0);
                return true;
            }
            return true;
        }
        boolean shift = (modifiers & 1) != 0;
        if (keyCode == 258) { // Tab / Shift+Tab
            focusArea = Math.floorMod(focusArea + (shift ? 2 : 1), 3);
            narrate(Component.translatable(switch (focusArea) {
                case 0 -> "store.focus.tabs";
                case 1 -> "store.focus.list";
                default -> "store.focus.submit";
            }));
            return true;
        }
        if (keyCode == 265 || keyCode == 264) { // 上/下
            if (focusArea == 0) {
                selectTab(tab + (keyCode == 265 ? -1 : 1));
                return true;
            }
            if (focusArea == 1) {
                moveFocus(keyCode == 265 ? -1 : 1);
                return true;
            }
            return true;
        }
        if (keyCode == 263 || keyCode == 262) { // 左/右
            if (focusArea == 0) {
                selectTab(tab + (keyCode == 263 ? -1 : 1));
                return true;
            }
            if (focusArea == 1) {
                adjustFocusedQuantity(keyCode == 263 ? -1 : 1);
                return true;
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335 || keyCode == 32) { // Enter / Space
            if (focusArea == 1) {
                adjustFocusedQuantity(1);
            } else if (focusArea == 2) {
                submitCart();
            } else {
                selectTab(tab + 1);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 切换标签：清掉键盘行焦点，滚回顶部。 */
    private void selectTab(int next) {
        tab = Math.floorMod(next, 3);
        scroll = 0;
        focusRow = -1;
        narrate(Component.translatable(switch (tab) {
            case TAB_BUY -> "store.tab.buy";
            case TAB_SELL -> "store.tab.sell";
            default -> "store.tab.sub";
        }));
    }

    /** 键盘上下移动行焦点，并保证焦点行在可视区内。 */
    private void moveFocus(int delta) {
        int rows = rowCount();
        if (rows <= 0) {
            focusRow = -1;
            return;
        }
        int next = focusRow < 0 ? 0 : Math.max(0, Math.min(focusRow + delta, rows - 1));
        focusRow = next;
        if (next < scroll) {
            scroll = next;
        } else if (next >= scroll + visibleRows()) {
            scroll = next - visibleRows() + 1;
        }
        narrate(rowNarration(next));
    }

    /** 左/右或回车调整焦点行数量；旁白读名字、数量和单价。 */
    private void adjustFocusedQuantity(int delta) {
        int rows = rowCount();
        if (rows <= 0) {
            return;
        }
        int index = Math.max(0, Math.min(focusRow < 0 ? 0 : focusRow, rows - 1));
        focusRow = index;
        if (tab == TAB_BUY) {
            StoreStatePacket.Entry entry = buyEntries().get(index);
            int next = Math.max(0, Math.min(buyQuantity(entry.itemId()) + delta, entry.stock()));
            setBuyQuantity(entry.itemId(), next);
            narrate(quantityNarration(displayName(entry.itemId()), next, entry.buyPrice()));
        } else if (tab == TAB_SELL) {
            SellRow row = sellRows().get(index);
            int next = Math.max(0, Math.min(sellQuantity(row.slot()) + delta,
                    row.stack().getCount()));
            setSellQuantity(row.slot(), next, itemIdOf(row.stack()));
            narrate(quantityNarration(row.stack().getHoverName().getString(), next,
                    row.entry().sellPrice()));
        } else {
            StoreStatePacket.SubEntry entry = state.subEntries().get(index);
            int max = Math.min(entry.quantity(), MAX_SUB_QUANTITY);
            int next = Math.max(0, Math.min(subQuantity(entry.itemId()) + delta, max));
            setSubQuantity(entry.itemId(), next);
            narrate(quantityNarration(displayName(entry.itemId()), next, entry.sellPrice()));
        }
    }

    /** 朗读焦点行的名字/单价；数量由 adjustFocusedQuantity 单独读。 */
    private Component rowNarration(int index) {
        if (tab == TAB_BUY) {
            StoreStatePacket.Entry entry = buyEntries().get(index);
            return quantityNarration(displayName(entry.itemId()),
                    buyQuantity(entry.itemId()), entry.buyPrice());
        }
        if (tab == TAB_SELL) {
            SellRow row = sellRows().get(index);
            return quantityNarration(row.stack().getHoverName().getString(),
                    sellQuantity(row.slot()), row.entry().sellPrice());
        }
        StoreStatePacket.SubEntry entry = state.subEntries().get(index);
        return quantityNarration(displayName(entry.itemId()),
                subQuantity(entry.itemId()), entry.sellPrice());
    }

    private static Component quantityNarration(String name, int quantity, int price) {
        return Component.literal(name + " × " + quantity + " · " + price + " mk");
    }

    private static String displayName(String itemId) {
        ItemStack stack = stackFor(itemId);
        return stack.isEmpty() ? itemId : stack.getHoverName().getString();
    }

    /** 鼠标点击提交按钮和键盘 Enter 共用：购买弹确认，出售直接发。 */
    private void submitCart() {
        if (awaitingState) {
            // 和鼠标入口一致：等待服务端快照期间不许再提交（键盘 Enter 也走这里）。
            return;
        }
        if (cartCount() <= 0) {
            narrate(Component.translatable("store.cart_empty"));
            return;
        }
        if (tab == TAB_BUY) {
            // 原作购买确认：先选"现在拿"还是"送到潜艇"。
            deliveryPrompt = true;
            promptChoice = 0;
            narrate(Component.translatable("store.delivery.title"));
        } else if (tab == TAB_SELL) {
            List<StoreActionPacket.Line> lines = new ArrayList<>();
            for (Map.Entry<Integer, Integer> line : sellCart.entrySet()) {
                if (line.getValue() > 0) {
                    lines.add(new StoreActionPacket.Line(
                            sellItemIds.getOrDefault(line.getKey(), ""),
                            line.getKey(), line.getValue()));
                }
            }
            sendLines(StoreActionPacket.SELL, lines, false);
        } else {
            List<StoreActionPacket.Line> lines = new ArrayList<>();
            for (Map.Entry<String, Integer> line : subCart.entrySet()) {
                if (line.getValue() > 0) {
                    lines.add(new StoreActionPacket.Line(line.getKey(), -1, line.getValue()));
                }
            }
            sendLines(StoreActionPacket.SELL_SUB, lines, false);
        }
    }

    /** 屏幕阅读器旁白；没有旁白模块时静默。 */
    private void narrate(Component text) {
        if (minecraft != null) {
            minecraft.getNarrator().sayNow(text);
        }
    }

    private void setBuyQuantity(String itemId, int quantity) {
        if (quantity <= 0) {
            buyCart.remove(itemId);
        } else {
            buyCart.put(itemId, quantity);
        }
    }

    private void setSellQuantity(int slot, int quantity, String itemId) {
        if (quantity <= 0) {
            sellCart.remove(slot);
            sellItemIds.remove(slot);
        } else {
            sellCart.put(slot, quantity);
            sellItemIds.put(slot, itemId);
        }
    }

    /** 物品注册名；服务端用它确认出售时槽位里的东西没被换过。 */
    private static String itemIdOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private void setSubQuantity(String itemId, int quantity) {
        if (quantity <= 0) {
            subCart.remove(itemId);
        } else {
            subCart.put(itemId, quantity);
        }
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double delta) {
        // 弹窗打开时滚轮不再滚后面的列表。
        if (deliveryPrompt) {
            return true;
        }
        if (delta != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (delta > 0 ? -1 : 1)));
            // 滚轮把焦点行滚出可视区后，回车不能再去改看不见的商品数量。
            if (focusRow >= 0) {
                focusRow = Math.max(scroll,
                        Math.min(focusRow, scroll + visibleRows() - 1));
            }
            return true;
        }
        return super.mouseScrolled(screenX, screenY, delta);
    }
}
