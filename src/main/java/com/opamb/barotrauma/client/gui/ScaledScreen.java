package com.opamb.barotrauma.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 没有物品槽的模组界面基类：照 {@link GuiScale} 的规范整体缩放。
 *
 * <p>子类实现 {@link #renderDesign(GuiGraphics, int, int, float)}，在里面用
 * <b>设计坐标</b>画内容（左上角 (0,0)，范围 {@code designWidth × designHeight}），
 * 不要再自己算 {@code (width - W) / 2}。</p>
 */
public abstract class ScaledScreen extends Screen {

    private final int designWidth;
    private final int designHeight;

    protected float uiScale = 1.0F;
    protected int panelX;
    protected int panelY;

    protected ScaledScreen(Component title, int designWidth, int designHeight) {
        super(title);
        this.designWidth = designWidth;
        this.designHeight = designHeight;
    }

    /** 设计宽度（界面自身尺寸）。 */
    protected int designWidth() {
        return designWidth;
    }

    /** 设计高度（界面自身尺寸）。 */
    protected int designHeight() {
        return designHeight;
    }

    @Override
    protected void init() {
        super.init();
        this.uiScale = GuiScale.factor(this.width, this.height, designWidth, designHeight);
        this.panelX = GuiScale.offsetX(this.width, designWidth, uiScale);
        this.panelY = GuiScale.offsetY(this.height, designHeight, uiScale);
    }

    /* ------------------------------------------------------------- 坐标换算 */

    protected double designX(double screenX) {
        return GuiScale.toDesignX(screenX, panelX, uiScale);
    }

    protected double designY(double screenY) {
        return GuiScale.toDesignY(screenY, panelY, uiScale);
    }

    protected int designXi(double screenX) {
        return (int) Math.floor(designX(screenX));
    }

    protected int designYi(double screenY) {
        return (int) Math.floor(designY(screenY));
    }

    /* ---------------------------------------------------------------- 渲染 */

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int dx = designXi(mouseX);
        int dy = designYi(mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0.0F);
        graphics.pose().scale(uiScale, uiScale, 1.0F);
        renderDesign(graphics, dx, dy, partialTick);
        super.render(graphics, dx, dy, partialTick);
        graphics.pose().popPose();
        // 悬浮提示/徽标这类"跟着鼠标走"的内容用真实坐标画在缩放之外。
        renderOverlay(graphics, mouseX, mouseY);
    }

    /** 用设计坐标画界面内容（背景、列表、文字……）。 */
    protected abstract void renderDesign(GuiGraphics graphics, int mouseX, int mouseY,
                                         float partialTick);

    /** 缩放之外的叠加层（tooltip 等），用真实屏幕坐标。默认什么都不画。 */
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /**
     * 按设计坐标开裁剪。原版 {@code enableScissor} 不认 PoseStack 的缩放，
     * 所以这里先把设计矩形换算成屏幕像素，再交给它。
     */
    protected void enableDesignScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.enableScissor(
                Math.round(panelX + x1 * uiScale),
                Math.round(panelY + y1 * uiScale),
                Math.round(panelX + x2 * uiScale),
                Math.round(panelY + y2 * uiScale));
    }

    /* ---------------------------------------------------------------- 输入 */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(designX(mouseX), designY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(designX(mouseX), designY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        return super.mouseDragged(designX(mouseX), designY(mouseY), button,
                dragX / uiScale, dragY / uiScale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(designX(mouseX), designY(mouseY), delta);
    }
}
