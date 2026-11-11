package com.opamb.barotrauma.client.equipment;

import com.opamb.barotrauma.equipment.ItemSubSlot;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 悬停子格：鼠标放在潜水服/焊接工具上时，<b>正上方</b>弹出一个 18×18 的小格，
 * 用来放氧气瓶 / 焊接油箱（原作容器槽就是从物品上方弹出的）。
 *
 * <p>弹出格不是真实 {@code Slot}，命中测试自己按坐标算；鼠标从宿主移到弹出格时
 * 带粘性容差，避免跨过 2px 间隙就消失。</p>
 *
 * <p>弹出/收回用位置 + 透明度动画：打开时从宿主格上沿滑出，收起时滑回宿主格；
 * 切换宿主（鼠标从潜水服移到焊接工具）时直接平滑滑向新位置，不重新播放。</p>
 */
public final class ItemSubSlotOverlay {

    private static final int SIZE = 18;
    private static final int GAP = 2;
    /** 鼠标离开宿主/弹出格多少像素内仍保持显示。 */
    private static final int KEEP_MARGIN = 8;
    private static final int BORDER = 0x373737;
    private static final int FILL = 0x8B8B8B;
    private static final int HINT = 0x5A5A5A;
    /** 展开/收回速度（progress 每秒变化量）。 */
    private static final float OPEN_SPEED = 1.0F / 0.12F;
    private static final float CLOSE_SPEED = 1.0F / 0.10F;
    /** 位置跟随速度（指数平滑系数）。 */
    private static final float FOLLOW = 22.0F;

    /** 当前显示中的宿主槽菜单下标；-1 = 没有。 */
    private static int pinnedSlot = -1;
    /** 当前界面；换界面（背包↔创造）时重置动画状态。 */
    private static AbstractContainerMenu pinnedMenu;
    /** 当前 Screen 实例：背包每次打开都是新实例，用来重置"上次没播完"的动画。 */
    private static Object pinnedScreen;
    /** 0 = 完全收回，1 = 完全弹出。 */
    private static float progress;
    private static float animX;
    private static float animY;
    private static long lastFrameMillis = -1L;
    /** 上一帧实际画出来的位置（点击命中用）。 */
    private static int drawnX;
    private static int drawnY;

    private ItemSubSlotOverlay() {
    }

    public static void render(GuiGraphics graphics, AbstractContainerMenu menu, int left, int top,
                              int mouseX, int mouseY) {
        long now = Util.getMillis();
        float dt = lastFrameMillis < 0L ? 0.0F : Math.min(0.1F, (now - lastFrameMillis) / 1000.0F);
        lastFrameMillis = now;
        Object screen = Minecraft.getInstance().screen;
        if (menu != pinnedMenu || screen != pinnedScreen) {
            pinnedMenu = menu;
            pinnedScreen = screen;
            pinnedSlot = -1;
            progress = 0.0F;
        }

        int hovered = hostIndex(menu, left, top, mouseX, mouseY);
        if (hovered >= 0) {
            pinnedSlot = hovered;
        }
        if (pinnedSlot < 0 || pinnedSlot >= menu.slots.size()) {
            reset();
            return;
        }
        Slot slot = menu.slots.get(pinnedSlot);
        if (!slot.isActive() || !slot.hasItem() || !ItemSubSlot.hasSubSlot(slot.getItem())) {
            // 宿主物品被拿走/换掉：直接收掉，不做动画。
            reset();
            return;
        }

        int[] open = openPosition(slot, left, top);
        int startX = Mth.clamp(left + slot.x, 1, Math.max(1, screenWidth() - SIZE - 1));
        int startY = top + slot.y - 2;
        boolean keep = hovered >= 0
                || insideInflated(left + slot.x, top + slot.y, mouseX, mouseY, 16, KEEP_MARGIN)
                || insideInflated(drawnX, drawnY, mouseX, mouseY, SIZE - 2, KEEP_MARGIN);

        // 刚打开：从宿主格上沿开始滑出。
        if (progress <= 0.0F && keep) {
            animX = startX;
            animY = startY;
        }
        float targetX = keep ? open[0] : startX;
        float targetY = keep ? open[1] : startY;
        float follow = 1.0F - (float) Math.exp(-dt * FOLLOW);
        animX += (targetX - animX) * follow;
        animY += (targetY - animY) * follow;
        progress = Mth.clamp(progress + (keep ? OPEN_SPEED : -CLOSE_SPEED) * dt, 0.0F, 1.0F);
        if (progress <= 0.0F) {
            reset();
            return;
        }

        int x = Math.round(animX);
        int y = Math.round(animY);
        drawnX = x;
        drawnY = y;
        int alpha = Math.round(progress * 255.0F);
        if (alpha <= 2) {
            return;
        }
        graphics.fill(x - 1, y - 1, x + SIZE - 1, y + SIZE - 1, (alpha << 24) | BORDER);
        graphics.fill(x, y, x + SIZE - 2, y + SIZE - 2, (alpha << 24) | FILL);

        // 图标等弹出一半再画，避免从宿主格里"长"出来时压住宿主物品。
        if (progress < 0.45F) {
            return;
        }
        ItemStack stack = slot.getItem();
        ItemStack contained = ItemSubSlot.get(stack);
        if (contained.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            String hint = ItemSubSlot.hint(stack);
            graphics.drawString(font, hint, x + 8 - font.width(hint) / 2, y + 4,
                    (alpha << 24) | HINT, false);
        } else {
            graphics.renderItem(contained, x, y);
            graphics.renderItemDecorations(Minecraft.getInstance().font, contained, x, y);
        }
    }

    /**
     * 点击命中时返回菜单槽下标；否则 -1。
     * 只在弹出格**已经基本展开**时才接受点击，避免动画途中看不见/还没到位就点。
     */
    public static int hitTest(AbstractContainerMenu menu, int left, int top,
                              double mouseX, double mouseY) {
        if (menu != pinnedMenu || pinnedSlot < 0 || progress < 0.55F
                || pinnedSlot >= menu.slots.size()) {
            return -1;
        }
        Slot slot = menu.slots.get(pinnedSlot);
        if (!slot.isActive() || !slot.hasItem() || !ItemSubSlot.hasSubSlot(slot.getItem())) {
            return -1;
        }
        return inside(drawnX, drawnY, mouseX, mouseY, SIZE - 2) ? pinnedSlot : -1;
    }

    private static void reset() {
        pinnedSlot = -1;
        progress = 0.0F;
    }

    /** 鼠标下的"带子格"物品槽的**菜单下标**（列表位置；创造界面的包装槽 index 是 0，不能用它）。 */
    private static int hostIndex(AbstractContainerMenu menu, int left, int top,
                                 double mouseX, double mouseY) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.isActive() || !slot.hasItem() || !ItemSubSlot.hasSubSlot(slot.getItem())) {
                continue;
            }
            int x = left + slot.x;
            int y = top + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return i;
            }
        }
        return -1;
    }

    private static boolean inside(int x, int y, double mouseX, double mouseY, int size) {
        return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
    }

    private static boolean insideInflated(int x, int y, double mouseX, double mouseY,
                                          int size, int margin) {
        return mouseX >= x - margin && mouseX < x + size + margin
                && mouseY >= y - margin && mouseY < y + size + margin;
    }

    /** 弹出格完全展开后的位置：宿主格正上方（超出屏幕时贴边）。 */
    private static int[] openPosition(Slot slot, int left, int top) {
        int screenW = screenWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int x = Mth.clamp(left + slot.x, 1, Math.max(1, screenW - SIZE - 1));
        int y = Mth.clamp(top + slot.y - SIZE - GAP, 1, Math.max(1, screenH - SIZE - 1));
        return new int[]{x, y};
    }

    private static int screenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }
}
