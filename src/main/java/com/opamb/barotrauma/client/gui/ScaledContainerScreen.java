package com.opamb.barotrauma.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 带物品槽的模组界面基类：照 {@link GuiScale} 的规范整体缩放。
 *
 * <p>子类只在<b>设计坐标</b>里画东西（{@code leftPos}/{@code topPos} 恒为 0，
 * 也就是界面左上角），槽位坐标、按钮坐标、文字坐标全部用设计坐标；缩放、
 * 居中、鼠标换算都在这里做。子类不要再重写 {@code render} 做缩放。</p>
 *
 * <p>注意：{@code tooltip} 用真实屏幕坐标在缩放之外画，保持原版字号。</p>
 */
public abstract class ScaledContainerScreen<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T> {

    /** 当前缩放倍率。 */
    protected float uiScale = 1.0F;
    /** 面板左上角的屏幕坐标。 */
    protected int panelX;
    protected int panelY;

    protected ScaledContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // 面板左侧可能放着槽位（比如双手格，坐标是负数）：给它们留出设计坐标空间，
        // 否则缩放到小窗口时负坐标会跑到屏幕外。
        int gutter = designLeftGutter();
        int designWidth = this.imageWidth + gutter;
        this.uiScale = GuiScale.factor(this.width, this.height, designWidth, this.imageHeight);
        this.panelX = GuiScale.offsetX(this.width, designWidth, this.uiScale)
                + Math.round(gutter * this.uiScale);
        this.panelY = GuiScale.offsetY(this.height, this.imageHeight, this.uiScale);
        // 设计坐标从 (0,0) 起，整块面板由 render 统一平移+缩放。
        this.leftPos = 0;
        this.topPos = 0;
    }

    /**
     * 面板左侧要预留的设计宽度：扫描菜单里所有<b>可见</b>的槽位，
     * 有负 x 的（双手格画在面板左边）就把左边留出来。
     *
     * <p>禁用占位/未装备的背包格在屏幕外（-10000），它们 {@code isActive()} 为 false，不参与。</p>
     */
    protected int designLeftGutter() {
        int minX = 0;
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                minX = Math.min(minX, slot.x);
            }
        }
        return minX < 0 ? -minX + 6 : 0;
    }

    /** 给 {@code AbstractContainerScreenMixin} 用：面板左侧预留的设计宽度。 */
    public int leftGutter() {
        return designLeftGutter();
    }

    /**
     * 按设计坐标开裁剪（和 {@link ScaledScreen} 同一套换算）。
     *
     * <p>原版 {@code enableScissor} 不认 PoseStack 的缩放，容器界面里的
     * 滚动列表也要先把设计矩形换算成屏幕像素。</p>
     */
    protected void enableDesignScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.enableScissor(
                Math.round(panelX + x1 * uiScale),
                Math.round(panelY + y1 * uiScale),
                Math.round(panelX + x2 * uiScale),
                Math.round(panelY + y2 * uiScale));
    }

    /**
     * 双手格箱子弹窗的展开方向，照原作 {@code CharacterInventory.CreateSlots}：
     * {@code SubInventoryDir = Math.Sign(screenH/2 - slotCenterY)}——槽位在屏幕下半边
     * 就向上弹（-1），在上半边就向下弹（+1）。
     */
    protected int subInventoryDir() {
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            if (slot instanceof com.opamb.barotrauma.equipment.HandsSlot) {
                double centerY = this.panelY + (slot.y + 8) * this.uiScale;
                return centerY > this.height / 2.0 ? -1 : 1;
            }
        }
        return -1;
    }

    /* ------------------------------------------------------------- 坐标换算 */

    /** 屏幕 X → 设计 X（浮点，给精确命中测试用）。 */
    protected double designX(double screenX) {
        return GuiScale.toDesignX(screenX, panelX, uiScale);
    }

    protected double designY(double screenY) {
        return GuiScale.toDesignY(screenY, panelY, uiScale);
    }

    /** 屏幕 X → 设计 X（整数，给绘制/命中测试用）。 */
    protected int designXi(double screenX) {
        return (int) Math.floor(designX(screenX));
    }

    protected int designYi(double screenY) {
        return (int) Math.floor(designY(screenY));
    }

    /* ---------------------------------------------------------------- 渲染 */

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 全屏压暗不参与缩放。
        renderBackground(graphics);
        int dx = designXi(mouseX);
        int dy = designYi(mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0.0F);
        graphics.pose().scale(uiScale, uiScale, 1.0F);
        renderScaled(graphics, dx, dy, partialTick);
        // 缩放空间里的叠层：双手格上箱子的内容弹窗（和背包界面同款）。
        // 原作 VisualSlot.SubInventoryDir：槽位在屏幕下半边 → 弹窗向上，上半边 → 向下。
        int dir = subInventoryDir();
        // 把整块屏幕换算成设计坐标，弹窗像原作一样贴着屏幕边夹住即可——
        // 这样它会以双手格为中心往上弹，而不是被面板左边缘顶到右边。
        int boundX = (int) Math.floor(-panelX / uiScale);
        int boundY = (int) Math.floor(-panelY / uiScale);
        int boundW = (int) Math.ceil((this.width - panelX) / uiScale) - boundX;
        int boundH = (int) Math.ceil((this.height - panelY) / uiScale) - boundY;
        com.opamb.barotrauma.client.equipment.CratePreviewOverlay.render(
                graphics, this.menu, 0, 0, dx, dy, dir, boundX, boundY, boundW, boundH);
        graphics.pose().popPose();
        // tooltip 不缩放：用真实屏幕坐标、原版字号。
        // 双手格的箱子弹窗展开时不再画物品 tooltip，否则会盖在弹窗上。
        if (!(com.opamb.barotrauma.client.equipment.CratePreviewOverlay.isOpen()
                && this.hoveredSlot instanceof com.opamb.barotrauma.equipment.HandsSlot)) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    /**
     * 缩放之后的整块绘制。默认就是原版流程（背景、槽位、标签）；
     * 完全自绘的界面（比如声纳台）可以覆盖它，用自己的顺序画。
     */
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /* ---------------------------------------------------------------- 输入 */

    // 屏幕坐标先换算回设计坐标，再交给原版逻辑：槽位命中、拖拽分堆、
    // Shift 点击、"点在外面丢出" 全部按设计空间算。

    /** 本次拖动属于哪个控件（点击时记录）；点的是物品槽则为 null。 */
    private GuiEventListener dragOwner;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double dx = designX(mouseX);
        double dy = designY(mouseY);
        dragOwner = null;
        for (GuiEventListener child : children()) {
            if (child.isMouseOver(dx, dy)) {
                dragOwner = child;
                break;
            }
        }
        // 双手格里的箱子：悬停弹窗里的格子（左键整堆、右键 1 个）。弹窗画在缩放空间里，
        // 命中也要用设计坐标。
        int[] crateCell = com.opamb.barotrauma.client.equipment.CratePreviewOverlay.hitTest(
                this.menu, 0, 0, dx, dy);
        if (crateCell != null && (button == 0 || button == 1)) {
            com.opamb.barotrauma.network.ModNetwork.sendToServer(
                    new com.opamb.barotrauma.network.CrateSlotPacket(
                            crateCell[0], crateCell[1], button));
            return true;
        }
        return super.mouseClicked(dx, dy, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragOwner = null;
        return super.mouseReleased(designX(mouseX), designY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        double dx = designX(mouseX);
        double dy = designY(mouseY);
        double sx = dragX / uiScale;
        double sy = dragY / uiScale;
        // 原版 AbstractContainerScreen.mouseDragged 只处理槽位拖拽、不转发给控件，
        // 所以容器界面里的滑条（电池充电速率等）点了没反应。只交给开始本次拖动的
        // 控件：遍历所有子控件会让电池滑条在拖物品槽时也改充电速率。
        if (dragOwner != null) {
            return dragOwner.mouseDragged(dx, dy, button, sx, sy);
        }
        return super.mouseDragged(dx, dy, button, sx, sy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(designX(mouseX), designY(mouseY), delta);
    }
}
