package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.client.MedicalBodyRenderer.DisplayLine;
import com.opamb.barotrauma.client.MedicalBodyRenderer.Zone;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import com.opamb.barotrauma.network.MedicalTreatPacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 自用医疗界面（用户指令 #7）：**继承背包界面**——打开健康栏目时物品栏完全可用。
 *
 * <p>交互三通道：① 左键点身体部位 = 选中并锁定；② 拖动背包里的药品到身体部位上松开 =
 * 对该部位用药；③ 选中部位后**双击**物品栏里的药品 = 快速用药。
 * 面板画在人物模型的正下方（用户指令），含伤情明细 + 药品栏（背包没有的药品灰暗、
 * 悬停显示药名）。</p>
 */
public final class MedicalInventoryScreen extends InventoryScreen {

    private static final int PANEL_W = 176;
    /** 面板高度：略微超过背包栏（166）——用户指令 #1。 */
    private static final int PANEL_H = 176;
    /** 医疗侧栏宽度（辅助小面板）。 */
    private static final int SIDEBAR_W = 62;
    private static final int LINE = 11;

    private final UUID target;
    private final String targetName;
    private List<Zone> zones = List.of();
    private Limb selectedLimb;
    private long lastSlotClickAt;
    private int lastSlotClickIndex = -1;
    /** 快捷合成栏遮盖区域（渲染时按槽位实际坐标计算，点击时用于吞掉点击）。 */
    private int[] craftCoverRect;

    public MedicalInventoryScreen(Player player) {
        super(player);
        this.target = player.getUUID();
        this.targetName = player.getName().getString();
    }

    @Override
    protected void init() {
        super.init();
        // 背包 + 健康侧栏**整体居中**（用户指令：整体挪到居中位置）
        this.leftPos = (this.width - (this.imageWidth + 6 + SIDEBAR_W)) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 只渲染容器本体，不画暗色遮罩——物品栏保持明亮可用
        super.render(g, mouseX, mouseY, partialTick);

        // 快捷合成栏遮盖（原作潜渊症不能原地搓东西——用户指令）：按菜单槽位实际坐标取包围盒
        int cMinX = Integer.MAX_VALUE, cMinY = Integer.MAX_VALUE;
        int cMaxX = Integer.MIN_VALUE, cMaxY = Integer.MIN_VALUE;
        for (int i = 0; i <= 4 && i < this.menu.slots.size(); i++) {
            var s = this.menu.slots.get(i);
            cMinX = Math.min(cMinX, s.x);
            cMinY = Math.min(cMinY, s.y);
            cMaxX = Math.max(cMaxX, s.x + 16);
            cMaxY = Math.max(cMaxY, s.y + 16);
        }
        craftCoverRect = new int[]{leftPos + cMinX - 4, topPos + cMinY - 4,
                leftPos + cMaxX + 4, topPos + cMaxY + 4};
        g.fill(craftCoverRect[0], craftCoverRect[1], craftCoverRect[2], craftCoverRect[3], 0xFFC6C6C6);

        // 医疗侧栏（**辅助性小面板**——背包是主角，用户指令）：窄条贴在背包右侧
        int mx = leftPos + imageWidth + 6;
        int my = topPos;
        int mw = SIDEBAR_W;
        int mh = imageHeight;
        g.fill(mx - 1, my - 1, mx + mw + 1, my + mh + 1, 0xFF4A4A5A);
        g.fill(mx, my, mx + mw, my + mh, 0xE012121A);
        g.drawString(this.font, Component.literal("状态"), mx + mw / 2 - this.font.width("状态") / 2, my + 4, 0xFFFFC74A);

        var snapshot = ClientMedicalCache.snapshot(target);
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        List<String> rows = snapshot == null ? List.of() : snapshot.rows();
        float rawVitality = snapshot == null ? 100.0f : snapshot.vitality();
        boolean downed = snapshot != null && snapshot.downed();

        // ---- 活力细条 ----
        int vitalityColor = downed ? 0xFFFF5A5A
                : rawVitality > 60 ? 0xFF55E05A : rawVitality > 25 ? 0xFFFFD75A : 0xFFFF5A5A;
        g.drawString(this.font,
                Component.literal(String.format("活力%.0f", rawVitality) + (downed ? "⚠" : "")),
                mx + 5, my + 16, vitalityColor);
        double vitalityRatio = Math.max(0.0, Math.min(1.0, rawVitality / 100.0));
        g.fill(mx + 5, my + 26, mx + mw - 5, my + 29, 0xFF26262E);
        if (vitalityRatio > 0) {
            g.fill(mx + 5, my + 26, mx + 5 + (int) ((mw - 10) * vitalityRatio), my + 29, vitalityColor);
        }

        // ---- 迷你身体图（悬停部位 → 详情浮窗）----
        int bodyCx = mx + mw / 2;
        int bodyBy = my + 34;
        zones = MedicalBodyRenderer.zones(bodyCx, bodyBy);
        MedicalBodyRenderer.draw(g, zones, rows, mouseX, mouseY);

        // ---- 全身病症（紧凑单行，短名）----
        int cy = bodyBy + MedicalBodyRenderer.BODY_HEIGHT + 2;
        for (DisplayLine line : MedicalBodyRenderer.globalLines(rows)) {
            g.drawString(this.font, Component.literal("· " + line.text()), mx + 4, cy, line.color());
            cy += 10;
        }
        if (rows.stream().anyMatch(r -> r.startsWith("t|"))) {
            g.drawString(this.font, Component.literal("◦ 治疗中"), mx + 4, Math.min(cy, my + mh - 12), 0xFF7FE87F);
        }

        // ---- 悬停身体部位 → 详情浮窗（伤情 + 原作文案；不在面板里排文字）----
        Limb hovered = null;
        for (Zone zone : zones) {
            if (mouseX >= zone.x() && mouseX < zone.x() + zone.w()
                    && mouseY >= zone.y() && mouseY < zone.y() + zone.h()) {
                hovered = zone.limb();
                break;
            }
        }
        if (hovered != null) {
            var lines = new java.util.ArrayList<Component>();
            lines.add(Component.literal(MedicalBodyRenderer.limbName(hovered)).withStyle(ChatFormatting.AQUA));
            for (AfflictionType type : AfflictionType.ALL) {
                if (!type.limbSpecific()) {
                    continue;
                }
                double strength = MedicalBodyRenderer.strengthOf(rows, type, hovered);
                if (strength > 0) {
                    double ratio = MedicalBodyRenderer.severityRatio(type, strength);
                    int color = MedicalBodyRenderer.severityColor(ratio);
                    lines.add(Component.literal(MedicalBodyRenderer.label(type, strength))
                            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
                    String desc = type.descriptionAt(strength);
                    if (!desc.isEmpty()) {
                        lines.add(Component.literal(desc).withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            if (lines.size() == 1) {
                lines.add(Component.literal("无伤").withStyle(ChatFormatting.GREEN));
            }
            g.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private String hoveredDrugName(Item item) {
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        if (key != null) {
            var name = Component.translatable(key.toLanguageKey("item"));
            return name.getString();
        }
        return item.toString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 0) 快捷合成栏区域：点击无效（原作不能原地搓东西——用户指令）
            if (craftCoverRect != null
                    && mouseX >= craftCoverRect[0] && mouseX < craftCoverRect[2]
                    && mouseY >= craftCoverRect[1] && mouseY < craftCoverRect[3]) {
                return true;
            }
            // 1) 身体部位 = 选中并锁定（不直接用药）
            for (Zone zone : zones) {
                if (mouseX >= zone.x() && mouseX < zone.x() + zone.w()
                        && mouseY >= zone.y() && mouseY < zone.y() + zone.h()) {
                    selectedLimb = zone.limb();
                    return true;
                }
            }
            // 2) 双击物品栏里的药品 = 快速用药（对选中部位；未选中则最强优先）
            if (this.hoveredSlot != null
                    && this.hoveredSlot.getItem().getItem() instanceof com.opamb.barotrauma.medical.MedicalItem) {
                long now = System.currentTimeMillis();
                if (lastSlotClickIndex == this.hoveredSlot.index
                        && now - lastSlotClickAt < 400) {
                    String itemId = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getKey(this.hoveredSlot.getItem().getItem()));
                    ModNetwork.sendToServer(new MedicalTreatPacket(target,
                            selectedLimb == null ? "-" : selectedLimb.name(), itemId, false));
                    lastSlotClickIndex = -1;
                    return true;
                }
                lastSlotClickAt = now;
                lastSlotClickIndex = this.hoveredSlot.index;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 拖动药品到身体部位上松开 = 对该部位用药（消耗背包/光标上的一件）
        if (button == 0 && this.menu.getCarried().getItem() instanceof com.opamb.barotrauma.medical.MedicalItem) {
            for (Zone zone : zones) {
                if (mouseX >= zone.x() && mouseX < zone.x() + zone.w()
                        && mouseY >= zone.y() && mouseY < zone.y() + zone.h()) {
                    String itemId = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getKey(this.menu.getCarried().getItem()));
                    ModNetwork.sendToServer(new MedicalTreatPacket(target, zone.limb().name(), itemId, true));
                    return true;
                }
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
