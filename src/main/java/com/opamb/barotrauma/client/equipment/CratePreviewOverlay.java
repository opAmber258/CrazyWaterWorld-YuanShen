package com.opamb.barotrauma.client.equipment;

import com.opamb.barotrauma.equipment.CrateContainer;
import com.opamb.barotrauma.equipment.HandsSlot;
import com.opamb.barotrauma.item.CrateItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 拿在手上的货运箱悬停弹窗（原作 {@code keepopenwhenequipped}+{@code movableframe}：
 * 箱子在手上时容器窗口是开着的；放在容器里就看不到它的格子，所以这里只认双手格）。
 *
 * <p>布局照原作容器窗口（{@code Inventory.UpdateSubInventory}）：列数
 * {@code clamp(floor(sqrt(capacity)), 1, SlotsPerRow)}，行左对齐，整块以手上那格
 * 为中心；所以金属箱是 4 列 × 5 行。格子用原版 18px 小格。</p>
 *
 * <p>弹出/收起带滑动 + 淡入动画（和潜水服/焊接工具的子格一致）；鼠标从宿主格
 * 移到弹窗途中不会因为跨过间隙而收起。</p>
 */
public final class CratePreviewOverlay {

    private static final int CELL = 18;
    private static final int PITCH = CELL;
    private static final int SLOTS_PER_ROW = 5;
    private static final int PAD = 3;
    private static final int KEEP_MARGIN = 10;
    private static final int BORDER = 0x373737;
    private static final int FILL = 0x8B8B8B;
    private static final int PANEL = 0x1A1D20;
    private static final float OPEN_SPEED = 1.0F / 0.12F;
    private static final float CLOSE_SPEED = 1.0F / 0.10F;
    private static final float FOLLOW = 22.0F;

    private static int pinnedSlot = -1;
    private static AbstractContainerMenu pinnedMenu;
    private static Object pinnedScreen;
    private static float progress;
    private static float animX;
    private static float animY;
    private static long lastFrameMillis = -1L;
    private static int drawnX;
    private static int drawnY;
    private static int drawnColumns;
    private static int drawnRows;
    private static int drawnCapacity;

    private CratePreviewOverlay() {
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
            reset();
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
        if (!(slot instanceof HandsSlot) || !slot.isActive() || !slot.hasItem()
                || !(slot.getItem().getItem() instanceof CrateItem crate) || crate.capacity() <= 0) {
            reset();
            return;
        }
        int capacity = crate.capacity();
        int columns = columnsFor(capacity);
        int rows = (capacity + columns - 1) / columns;
        int width = columns * CELL + PAD * 2;
        int height = rows * CELL + PAD * 2;
        int sx = left + slot.x;
        int sy = top + slot.y;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int[] open = place(sx, sy, width, height, screenW, screenH);

        // 保持判定用"展开后的目标位置"，不用动画中的当前位置：否则弹窗滑动时
        // 命中区域每帧变化，鼠标在边角会来回闪。
        boolean keep = hovered >= 0
                || inside(sx - KEEP_MARGIN, sy - KEEP_MARGIN, mouseX, mouseY, 16 + KEEP_MARGIN * 2)
                || inside(open[0] - KEEP_MARGIN, open[1] - KEEP_MARGIN, mouseX, mouseY,
                width + KEEP_MARGIN * 2)
                || insideCorridor(sx, sy, open[0], open[1], width, height, mouseX, mouseY);
        if (progress <= 0.0F && keep) {
            // 从宿主格长出来。
            animX = sx;
            animY = sy;
        }
        float targetX = keep ? open[0] : sx;
        float targetY = keep ? open[1] : sy;
        float follow = 1.0F - (float) Math.exp(-dt * FOLLOW);
        animX += (targetX - animX) * follow;
        animY += (targetY - animY) * follow;
        progress = Mth.clamp(progress + (keep ? OPEN_SPEED : -CLOSE_SPEED) * dt, 0.0F, 1.0F);
        if (progress <= 0.0F) {
            reset();
            return;
        }
        drawnX = Math.round(animX);
        drawnY = Math.round(animY);
        pinnedMenu = menu;
        drawnColumns = columns;
        drawnRows = rows;
        drawnCapacity = capacity;

        int alpha = Math.round(progress * 255.0F);
        graphics.fill(drawnX, drawnY, drawnX + width, drawnY + height, (alpha << 24) | PANEL);
        if (progress < 0.45F) {
            return;
        }
        CrateContainer contents = new CrateContainer(slot.getItem(), capacity);
        for (int i = 0; i < capacity; i++) {
            int cx = drawnX + PAD + (i % columns) * PITCH;
            int cy = drawnY + PAD + (i / columns) * PITCH;
            graphics.fill(cx - 1, cy - 1, cx + CELL - 1, cy + CELL - 1, (alpha << 24) | BORDER);
            graphics.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, (alpha << 24) | FILL);
            ItemStack stack = contents.getItem(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, cx, cy);
                graphics.renderItemDecorations(Minecraft.getInstance().font, stack, cx, cy);
            }
        }
    }

    /**
     * 点击命中的弹窗格子；返回 {@code {背包槽下标, 箱子格子}}，没命中返回 null。
     * 只在基本展开后接受点击。
     */
    @Nullable
    public static int[] hitTest(AbstractContainerMenu menu, int left, int top,
                                double mouseX, double mouseY) {
        if (menu != pinnedMenu || pinnedSlot < 0 || pinnedSlot >= menu.slots.size()
                || drawnColumns <= 0 || progress < 0.55F) {
            return null;
        }
        int gridX = drawnX + PAD;
        int gridY = drawnY + PAD;
        int column = (int) ((mouseX - gridX) / PITCH);
        int row = (int) ((mouseY - gridY) / PITCH);
        if (column < 0 || column >= drawnColumns || row < 0 || row >= drawnRows) {
            return null;
        }
        int cellX = gridX + column * PITCH;
        int cellY = gridY + row * PITCH;
        if (mouseX < cellX || mouseX >= cellX + CELL || mouseY < cellY || mouseY >= cellY + CELL) {
            return null;
        }
        int crateSlot = row * drawnColumns + column;
        if (crateSlot >= drawnCapacity) {
            return null;
        }
        Slot slot = menu.slots.get(pinnedSlot);
        if (!(slot.getItem().getItem() instanceof CrateItem)) {
            return null;
        }
        return new int[]{pinnedSlot, crateSlot};
    }

    /**
     * 鼠标是否落在已经展开的弹窗矩形里。
     *
     * <p>弹窗画在原版面板外面，松手时原版 {@code hasClickedOutside} 会把这里算成
     * -999，而光标上正好是刚从箱子里点出来的物品 → 服务端直接把它丢到地上。
     * 面板外的弹窗区域必须算"界内"，这样松手只是什么都不做。</p>
     */
    public static boolean isOverPopup(double mouseX, double mouseY) {
        if (progress < 0.2F || drawnColumns <= 0 || drawnRows <= 0) {
            return false;
        }
        int width = drawnColumns * CELL + PAD * 2;
        int height = drawnRows * CELL + PAD * 2;
        return mouseX >= drawnX && mouseX < drawnX + width
                && mouseY >= drawnY && mouseY < drawnY + height;
    }

    /** 鼠标下拿在手上的箱子槽（只认双手格）。 */
    private static int hostIndex(AbstractContainerMenu menu, int left, int top,
                                 double mouseX, double mouseY) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot instanceof HandsSlot) || !slot.isActive() || !slot.hasItem()) {
                continue;
            }
            if (!(slot.getItem().getItem() instanceof CrateItem crate) || crate.capacity() <= 0) {
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

    /** 列数：原作 {@code clamp(floor(sqrt(capacity)), 1, SlotsPerRow)}。 */
    private static int columnsFor(int capacity) {
        if (capacity <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(SLOTS_PER_ROW, (int) Math.floor(Math.sqrt(capacity))));
    }

    /** 弹窗位置：默认在手上那格正上方，水平以格子中心对齐；放不下就翻到下方/贴边。 */
    private static int[] place(int sx, int sy, int width, int height, int screenW, int screenH) {
        int x = sx + 8 - width / 2;
        int y = sy - height - 4;
        if (y < 2) {
            int below = sy + 18 + 2;
            if (below + height <= screenH - 2) {
                y = below;
            } else {
                y = Math.max(2, Math.min(sy, screenH - height - 2));
            }
        }
        x = Math.max(2, Math.min(x, screenW - width - 2));
        y = Math.max(2, Math.min(y, screenH - height - 2));
        return new int[]{x, y};
    }

    private static void reset() {
        pinnedSlot = -1;
        progress = 0.0F;
        drawnColumns = 0;
        drawnRows = 0;
        drawnCapacity = 0;
    }

    private static boolean inside(int x, int y, double mouseX, double mouseY, int size) {
        return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
    }

    /** 宿主格与弹窗之间的整块区域（含间隙）：鼠标横穿过去时不收起。 */
    private static boolean insideCorridor(int sx, int sy, int popupX, int popupY,
                                          int width, int height,
                                          double mouseX, double mouseY) {
        int x0 = Math.min(sx, popupX) - KEEP_MARGIN;
        int y0 = Math.min(sy, popupY) - KEEP_MARGIN;
        int x1 = Math.max(sx + 16, popupX + width) + KEEP_MARGIN;
        int y1 = Math.max(sy + 16, popupY + height) + KEEP_MARGIN;
        return mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
    }
}
