package com.opamb.barotrauma.client;

import com.opamb.barotrauma.cthulhu.CthulhuPreset;
import com.opamb.barotrauma.network.CthulhuGazePacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;

/**
 * 克苏鲁的注视——预设 × 档位 × 时长选择面板（开发者触发器）。
 *
 * <p>顶部选持续时长（3/6/12 秒），下面每行一个预设，右侧三档 弱/中/强。
 * 所有组合都从这一个触发器里选，不为档位单独做物品。</p>
 *
 * <p>布局按 440×300 设计；窗口太小（高 GUI 缩放）时整块等比缩小，
 * 避免像早先那样上下被裁掉。鼠标坐标也按同一比例换算。</p>
 */
public final class CthulhuGazeScreen extends Screen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 272;
    private static final int ROW_H = 24;

    private static final int BG = 0xF0161218;
    private static final int EDGE = 0xFF4A3B55;
    private static final int BOX = 0xFF221A2A;
    private static final int TEXT = 0xFFE4D8F0;
    private static final int DIM = 0xFF9C8FA8;
    private static final int ACCENT = 0xFFB77BE8;
    private static final int HOVER = 0xFF3A2B47;

    private static final int TIER_W = 38;
    private static final int TIER_H = 16;
    private static final int TIER_GAP = 4;

    private static final int DUR_W = 44;
    private static final int DUR_H = 16;
    private static final int DUR_GAP = 4;

    /** 当前选中的时长下标（默认 6 秒）。 */
    private int durationIndex = 1;

    /** 等比缩放：窗口装不下 440×300 时缩小。 */
    private float uiScale = 1.0F;
    private float originX;
    private float originY;

    public CthulhuGazeScreen() {
        super(Component.literal("克苏鲁的注视"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.setScreen(new CthulhuGazeScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        updateLayout();

        float mx = (mouseX - originX) / uiScale;
        float my = (mouseY - originY) / uiScale;

        g.pose().pushPose();
        g.pose().translate(originX, originY, 0.0F);
        g.pose().scale(uiScale, uiScale, 1.0F);

        g.fill(0, 0, PANEL_W, PANEL_H, BG);
        g.fill(0, 0, PANEL_W, 1, EDGE);
        g.fill(0, PANEL_H - 1, PANEL_W, PANEL_H, EDGE);
        g.fill(0, 0, 1, PANEL_H, EDGE);
        g.fill(PANEL_W - 1, 0, PANEL_W, PANEL_H, EDGE);

        g.drawString(font, "克苏鲁的注视（预设 × 档位 × 时长）", 10, 8, ACCENT, false);
        String hint = "点击档位应用 · ESC 关闭";
        g.drawString(font, hint, PANEL_W - 8 - font.width(hint), 8, DIM, false);

        // 时长选择
        g.drawString(font, "持续", 10, 26, TEXT, false);
        for (int i = 0; i < CthulhuPreset.DURATION_SECONDS.length; i++) {
            int bx = durationX(i);
            boolean selected = i == durationIndex;
            boolean hover = in(mx, my, bx, 22, DUR_W, DUR_H);
            g.fill(bx, 22, bx + DUR_W, 22 + DUR_H, selected ? HOVER : BOX);
            g.fill(bx, 22, bx + DUR_W, 23, selected ? ACCENT : EDGE);
            g.drawCenteredString(font, CthulhuPreset.DURATION_SECONDS[i] + " 秒",
                    bx + DUR_W / 2, 22 + (DUR_H - 8) / 2, selected || hover ? TEXT : DIM);
        }

        CthulhuPreset[] presets = CthulhuPreset.values();
        for (int i = 0; i < presets.length; i++) {
            int ry = rowY(i);
            int tierCount = presets[i].maxTier() + 1;
            g.fill(8, ry, PANEL_W - 8, ry + ROW_H - 2, BOX);
            g.fill(8, ry, PANEL_W - 8, ry + 1, EDGE);
            TextureAtlasSprite icon = Minecraft.getInstance().getMobEffectTextures()
                    .get(presets[i].effect());
            g.blit(12, ry + 3, 0, 18, 18, icon);
            g.drawString(font, presets[i].displayName(), 34, ry + 7, TEXT, false);
            int descX = 90;
            int descWidth = Math.max(0, tierX(0, tierCount) - 6 - descX);
            String desc = font.plainSubstrByWidth(presets[i].description(), descWidth);
            g.drawString(font, desc, descX, ry + 7, DIM, false);
            for (int tier = 0; tier < tierCount; tier++) {
                int bx = tierX(tier, tierCount);
                boolean hover = in(mx, my, bx, ry + 4, TIER_W, TIER_H);
                int color = tierColor(tier);
                g.fill(bx, ry + 4, bx + TIER_W, ry + 4 + TIER_H, hover ? HOVER : 0xFF2C2235);
                g.fill(bx, ry + 4, bx + TIER_W, ry + 5, color);
                g.drawCenteredString(font, CthulhuPreset.tierName(tier),
                        bx + TIER_W / 2, ry + 4 + (TIER_H - 8) / 2, color);
            }
        }
        g.drawString(font, "同一次只能生效一个注视；重新触发会覆盖当前预设/档位/时长",
                10, PANEL_H - 14, DIM, false);

        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        updateLayout();
        double mx = (mouseX - originX) / uiScale;
        double my = (mouseY - originY) / uiScale;

        for (int i = 0; i < CthulhuPreset.DURATION_SECONDS.length; i++) {
            if (in(mx, my, durationX(i), 22, DUR_W, DUR_H)) {
                durationIndex = i;
                return true;
            }
        }

        CthulhuPreset[] presets = CthulhuPreset.values();
        for (int i = 0; i < presets.length; i++) {
            int ry = rowY(i);
            int tierCount = presets[i].maxTier() + 1;
            for (int tier = 0; tier < tierCount; tier++) {
                if (in(mx, my, tierX(tier, tierCount), ry + 4, TIER_W, TIER_H)) {
                    ModNetwork.sendToServer(new CthulhuGazePacket(
                            i, tier, CthulhuPreset.DURATION_SECONDS[durationIndex]));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 窗口装不下就整体等比缩小并居中。 */
    private void updateLayout() {
        uiScale = Math.min(1.0F, Math.min(
                (width - 8.0F) / PANEL_W,
                (height - 8.0F) / PANEL_H));
        if (uiScale <= 0.0F) {
            uiScale = 0.1F;
        }
        originX = (width - PANEL_W * uiScale) / 2.0F;
        originY = (height - PANEL_H * uiScale) / 2.0F;
    }

    private static int rowY(int index) {
        return 46 + index * ROW_H;
    }

    private static int tierX(int tier, int count) {
        int start = PANEL_W - 8 - (TIER_W * count + TIER_GAP * (count - 1));
        return start + tier * (TIER_W + TIER_GAP);
    }

    private static int durationX(int index) {
        return 48 + index * (DUR_W + DUR_GAP);
    }

    private static int tierColor(int tier) {
        return switch (tier) {
            case 0 -> 0xFF6FBF73;
            case 1 -> 0xFFD9A64A;
            case 2 -> 0xFFD95C5C;
            default -> 0xFFB15CE8;
        };
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
