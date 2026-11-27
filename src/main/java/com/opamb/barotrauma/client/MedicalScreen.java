package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.client.TreatmentRenderer.DisplayLine;
import com.opamb.barotrauma.client.TreatmentRenderer.Zone;
import com.opamb.barotrauma.medical.ModMedicalItems;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import com.opamb.barotrauma.network.MedicalRequestPacket;
import com.opamb.barotrauma.network.MedicalTreatPacket;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 原作式治疗面板：手持药品右键（自己）或左键队友后打开。
 *
 * <p>布局仿原作：左=身体图（**悬停部位才显示该部位伤情**，用户指令），
 * 右=悬停部位的伤情明细 + **对症药品图标（按原作 SuitableTreatment 排序，单击即用，
 * 从背包取药）**；用药后面板保持打开（用户指令）。点击身体部位 = 用主手药品。</p>
 */
public final class MedicalScreen extends ScaledScreen {

    private static final int PANEL_W = 316;
    private static final int PANEL_H = 208;
    private static final int LINE = 11;
    private static final int DETAIL_X = 101;
    private static final int DETAIL_WIDTH = PANEL_W - DETAIL_X - 10;

    /** 对症药品（按原作 SuitableTreatment 适配度降序；底表 §4）。 */
    private static final Map<AfflictionType, List<Supplier<? extends Item>>> SUITABLE = buildSuitable();

    private static Map<AfflictionType, List<Supplier<? extends Item>>> buildSuitable() {
        Map<AfflictionType, List<Supplier<? extends Item>>> map = new LinkedHashMap<>();
        map.put(AfflictionType.BLEEDING, List.of(ModMedicalItems.ANTIBIOTIC_GLUE, ModMedicalItems.BANDAGE));
        map.put(AfflictionType.BURN, List.of(ModMedicalItems.BANDAGE, ModMedicalItems.ANTIBIOTIC_GLUE));
        map.put(AfflictionType.BLOODLOSS, List.of(ModMedicalItems.BLOOD_PACK, ModMedicalItems.SALINE));
        map.put(AfflictionType.BLUNT_TRAUMA, List.of(ModMedicalItems.MORPHINE, ModMedicalItems.FENTANYL));
        map.put(AfflictionType.OPIATE_OVERDOSE, List.of(ModMedicalItems.NALOXONE));
        return map;
    }

    private record IconRect(Item item, int x, int y) {
    }

    private final UUID target;
    private final String targetName;
    private List<Zone> zones = List.of();
    /** 粘性选中部位：悬停即选中，鼠标移开（去看药品图标）后保持，右侧状态不再闪没（用户实测反馈 #4）。 */
    private Limb selectedLimb;
    private final List<IconRect> iconRects = new ArrayList<>();
    private int refreshTicks;
    /** 伤情明细区的滚动量（换行后的描述可能超过面板高度）。 */
    private int statusScroll;
    /** 上一帧算出的内容高度，用来夹滚动量。 */
    private int statusContentHeight;
    /** 上一帧的状态区视口（设计坐标），药品命中不能超出它。 */
    private int statusViewTop;
    private int statusViewBottom;

    public MedicalScreen(UUID target, String targetName) {
        super(Component.literal("医疗"), PANEL_W, PANEL_H);
        this.target = target;
        this.targetName = targetName;
    }

    @Override
    protected void init() {
        super.init();
        ModNetwork.sendToServer(new MedicalRequestPacket(target));
    }

    /** 面板打开期间每秒刷新一次目标快照；关闭后 tick 停止，请求自然结束。 */
    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            ModNetwork.sendToServer(new MedicalRequestPacket(target));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderDesign(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = 0;
        int y = 0;
        PanelStyle.frame(g, x, y, PANEL_W, PANEL_H, PanelStyle.MEDICAL);

        ClientMedicalCache.Snapshot snapshot = ClientMedicalCache.snapshot(target);
        g.drawString(font, "医疗 · " + targetName, 12, 9, PanelStyle.TEXT, false);
        PanelStyle.divider(g, 12, 23, PANEL_W - 24);

        if (targetDead()) {
            // 目标死亡后旧图标不能再可点。
            iconRects.clear();
            zones = List.of();
            g.fill(x + 11, y + 28, x + PANEL_W - 11, y + 44, 0x80FF5A5A);
            g.drawString(this.font, Component.literal("目标已死亡"),
                    x + 16, y + 32, 0xFFFFE0E0);
            return;
        }

        if (snapshot == null) {
            zones = List.of();
            iconRects.clear();
            g.drawString(font, "正在同步队友数据…", 12, 32, PanelStyle.DIM, false);
            return;
        }

        int vitalityColor = snapshot.downed() ? 0xFFBF5D50
                : snapshot.vitality() > 60 ? 0xFF568C75 : 0xFFC9A25C;
        String vitality = String.format(java.util.Locale.ROOT, "%.0f / 100", snapshot.vitality());
        g.drawString(font, snapshot.downed() ? "重伤倒地" : "活力", 12, 31,
                snapshot.downed() ? 0xFFFFA491 : PanelStyle.DIM, false);
        PanelStyle.meter(g, 66, 29, 184, 11, snapshot.vitality() / 100.0D, vitalityColor, 0);
        g.drawString(font, vitality, PANEL_W - 12 - font.width(vitality), 31, PanelStyle.TEXT, false);

        PanelStyle.well(g, 10, 48, 82, 138);
        int by = 68;
        zones = TreatmentRenderer.zones(51, by);
        TreatmentRenderer.draw(g, zones, snapshot.rows(), mouseX, mouseY, selectedLimb);
        String region = selectedLimb == null ? "选择部位" : TreatmentRenderer.limbName(selectedLimb);
        g.drawCenteredString(font, region, 51, 157, PanelStyle.MEDICAL);

        PanelStyle.well(g, DETAIL_X, 48, DETAIL_WIDTH, 138);
        int statusTop = 57;
        int statusBottom = 179;
        int viewHeight = Math.max(LINE * 2, statusBottom - statusTop);
        statusViewTop = statusTop;
        statusViewBottom = statusBottom;
        statusScroll = Mth.clamp(statusScroll, 0, Math.max(0, statusContentHeight - viewHeight));
        iconRects.clear();
        enableDesignScissor(g, DETAIL_X + 2, statusTop, PANEL_W - 16, statusBottom);
        g.pose().pushPose();
        g.pose().translate(0.0F, -statusScroll, 0.0F);
        int cy = statusTop;
        if (selectedLimb != null) {
            g.drawString(this.font,
                    Component.literal(TreatmentRenderer.limbName(selectedLimb) + "："), DETAIL_X + 8, cy, 0xFF7FB8E8);
            cy += LINE;
            for (AfflictionType type : AfflictionType.ALL) {
                if (!type.limbSpecific()) {
                    continue;
                }
                double strength = TreatmentRenderer.strengthOf(snapshot.rows(), type, selectedLimb);
                if (strength <= 0) {
                    continue;
                }
                double ratio = TreatmentRenderer.severityRatio(type, strength);
                g.drawString(this.font, Component.literal("· "
                        + TreatmentRenderer.label(type, strength)), DETAIL_X + 10, cy,
                        TreatmentRenderer.severityColor(ratio));
                cy += LINE;
                // 原作阶段文案（灰字，自动换行不超框）
                String desc = type.descriptionAt(strength);
                for (String part : TreatmentRenderer.wrap(this.font, desc, DETAIL_WIDTH - 24)) {
                    g.drawString(this.font, Component.literal(part), DETAIL_X + 16, cy, 0xFF8A8A8A);
                    cy += LINE;
                }
            }
            // 头部指示型全身病症（眩晕/阿片中毒/成瘾）显示在头部选中时
            if (selectedLimb == Limb.HEAD) {
                for (AfflictionType type : AfflictionType.ALL) {
                    if (type.limbSpecific() || !type.headIndicator()) {
                        continue;
                    }
                    double strength = TreatmentRenderer.strengthOf(snapshot.rows(), type, null);
                    if (strength < type.showThreshold()) {
                        continue;
                    }
                    double ratio = TreatmentRenderer.severityRatio(type, strength);
                    g.drawString(this.font, Component.literal("· "
                            + TreatmentRenderer.label(type, strength)), DETAIL_X + 10, cy,
                            TreatmentRenderer.severityColor(ratio));
                    cy += LINE;
                    String desc = type.descriptionAt(strength);
                    for (String part : TreatmentRenderer.wrap(this.font, desc, DETAIL_WIDTH - 24)) {
                        g.drawString(this.font, Component.literal(part), DETAIL_X + 16, cy, 0xFF8A8A8A);
                        cy += LINE;
                    }
                }
            }
            // 对症药品栏：并排小方框，悬停高亮，单击对该部位用药（只显示背包里真实拥有的药品）
            iconRects.clear();
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            List<Item> drugs = new ArrayList<>();
            for (var entry : SUITABLE.entrySet()) {
                if (TreatmentRenderer.strengthOf(snapshot.rows(), entry.getKey(), selectedLimb) > 0) {
                    for (Supplier<? extends Item> supplier : entry.getValue()) {
                        Item item = supplier.get();
                        if (!drugs.contains(item) && hasAnywhere(minecraft.player, item)) {
                            drugs.add(item);
                        }
                    }
                }
            }
            if (drugs.isEmpty()) {
                g.drawString(this.font, Component.literal("该部位无对症药品（或背包里没有）"), DETAIL_X + 10, cy + 2, 0xFF909098);
                cy += LINE;
            } else {
                int slotX = DETAIL_X + 10;
                for (Item drug : drugs) {
                    if (slotX + 18 > PANEL_W - 16) {
                        slotX = DETAIL_X + 10;
                        cy += 20;
                    }
                    // 图标画在 cy - statusScroll：悬停判断也得用可见坐标，
                    // 并且不能超出滚动视口（否则滚出去的行也会高亮）。
                    int rowY = cy - statusScroll;
                    boolean slotHover = mouseX >= slotX && mouseX < slotX + 18
                            && mouseY >= rowY && mouseY < rowY + 18
                            && rowY >= statusViewTop && rowY + 18 <= statusViewBottom;
                    PanelStyle.slot(g, slotX, cy);
                    if (slotHover) g.hLine(slotX, slotX + 17, cy, PanelStyle.MEDICAL);
                    g.renderItem(new ItemStack(drug), slotX + 1, cy + 1);
                    iconRects.add(new IconRect(drug, slotX + 1, cy - statusScroll + 1));
                    slotX += 20;
                }
                cy += 18;
            }
        } else {
            g.drawString(this.font, Component.literal("全身状态"), DETAIL_X + 8, cy, 0xFF7FB8E8);
            cy += LINE;
            List<DisplayLine> statusLines = new ArrayList<>(TreatmentRenderer.globalLines(snapshot.rows()));
            if (snapshot.downed()) {
                statusLines.add(new DisplayLine("⚠ 重伤倒地", 0xFFFF5A5A));
            }
            if (statusLines.isEmpty()) {
                statusLines.add(new DisplayLine("没有伤病", 0xFF55E05A));
            }
            for (DisplayLine line : statusLines) {
                g.drawString(this.font, Component.literal("· " + line.text()), DETAIL_X + 10, cy, line.color());
                cy += LINE;
            }
            g.drawString(this.font, Component.literal("左键点击身体选择部位"), DETAIL_X + 10, cy + 2, 0xFF909098);
        }
        g.pose().popPose();
        g.disableScissor();
        statusContentHeight = cy - statusTop;
        if (statusContentHeight > viewHeight) {
            // 右侧滚动条：告诉玩家下面还有内容。
            int barH = Math.max(8, viewHeight * viewHeight / statusContentHeight);
            int maxScroll = statusContentHeight - viewHeight;
            int barY = statusTop + (viewHeight - barH) * statusScroll / Math.max(1, maxScroll);
            g.fill(PANEL_W - 15, statusTop, PANEL_W - 13, statusBottom, 0x40FFFFFF);
            g.fill(PANEL_W - 15, barY, PANEL_W - 13, barY + barH, 0xFF7FB8E8);
        }

        g.drawString(font, "点击药品用药 · Esc 关闭", 12, PANEL_H - 15, PanelStyle.DIM, false);
    }

    /** 该部位对症药品的图标点击（缺省部位=null → 全身/最强优先）。 */
    private record IconClick(Item item, Limb limb) {
    }

    /** 目标死亡后容器会被清空，快照变成“活力 100”；用客户端实体状态覆盖这段空窗。 */
    private boolean targetDead() {
        if (minecraft == null || minecraft.level == null) {
            return false;
        }
        var targetPlayer = minecraft.level.getPlayerByUUID(target);
        return targetPlayer != null
                && (targetPlayer.isDeadOrDying() || targetPlayer.getHealth() <= 0.0F);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double delta) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (delta != 0.0D && mouseX >= DETAIL_X && mouseX < PANEL_W
                && mouseY >= statusViewTop && mouseY < statusViewBottom) {
            statusScroll -= (int) Math.signum(delta) * LINE * 2;
            return true;
        }
        return super.mouseScrolled(screenX, screenY, delta);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (button == 0) {
            // 1) 对症药品图标 = 用该药（作用于当前选中部位）
            for (IconRect rect : iconRects) {
                // 命中限制在状态区视口内：滚出视口的图标不能隔着文字/面板外点到。
                int hitTop = Math.max(rect.y(), statusViewTop);
                int hitBottom = Math.min(rect.y() + 16, statusViewBottom);
                if (mouseX >= rect.x() && mouseX < rect.x() + 16
                        && mouseY >= hitTop && mouseY < hitBottom) {
                    String itemId = String.valueOf(
                            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(rect.item()));
                    ModNetwork.sendToServer(new MedicalTreatPacket(target,
                            selectedLimb == null ? "-" : selectedLimb.name(), itemId, false));
                    return true; // 不关闭面板（用户指令 #11）
                }
            }
            // 2) 身体部位 = 仅选中（用药一律走下方药品栏——用户指令）
            for (Zone zone : zones) {
                if (mouseX >= zone.x() && mouseX < zone.x() + zone.w()
                        && mouseY >= zone.y() && mouseY < zone.y() + zone.h()) {
                    selectedLimb = selectedLimb == zone.limb() ? null : zone.limb();
                    statusScroll = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    private static Limb findHoveredLimb(List<Zone> zones, double mouseX, double mouseY) {
        for (Zone zone : zones) {
            if (mouseX >= zone.x() && mouseX < zone.x() + zone.w()
                    && mouseY >= zone.y() && mouseY < zone.y() + zone.h()) {
                return zone.limb();
            }
        }
        return null;
    }

    /** 悬停部位的病症行（含头部指示型，如眩晕/阿片中毒）；严重度按实际掉血占比。 */
    private static List<DisplayLine> limbStatusLines(ClientMedicalCache.Snapshot snapshot, Limb limb) {
        List<DisplayLine> lines = new ArrayList<>();
        for (AfflictionType type : AfflictionType.ALL) {
            if (!type.limbSpecific()) {
                continue;
            }
            double strength = TreatmentRenderer.strengthOf(snapshot.rows(), type, limb);
            if (strength > 0) {
                double ratio = TreatmentRenderer.severityRatio(type, strength);
                lines.add(new DisplayLine(
                        TreatmentRenderer.DISPLAY_NAMES.getOrDefault(type.id(), type.id())
                                + " " + TreatmentRenderer.fmt(strength),
                        TreatmentRenderer.severityColor(ratio)));
            }
        }
        if (limb == Limb.HEAD) {
            for (AfflictionType type : AfflictionType.ALL) {
                if (type.limbSpecific() || !type.headIndicator()) {
                    continue;
                }
                double strength = TreatmentRenderer.strengthOf(snapshot.rows(), type, null);
                if (strength >= type.showThreshold()) {
                    lines.add(new DisplayLine(
                            TreatmentRenderer.DISPLAY_NAMES.getOrDefault(type.id(), type.id())
                                    + " " + TreatmentRenderer.fmt(strength),
                            TreatmentRenderer.severityColor(
                                    TreatmentRenderer.severityRatio(type, strength))));
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add(new DisplayLine("无伤", 0xFF55E05A));
        }
        return lines;
    }

    /** 从显示行里猜病症类型（用于对症药品提示；无伤行返回 null）。 */
    private static AfflictionType typeOf(DisplayLine line) {
        for (AfflictionType type : AfflictionType.ALL) {
            String name = TreatmentRenderer.DISPLAY_NAMES.getOrDefault(type.id(), type.id());
            if (line.text().startsWith(name + " ")) {
                return type;
            }
        }
        return null;
    }

    /** 背包 + 装备背包里是否有这件药品（装备背包内容不在 player inventory 里）。 */
    private static boolean hasAnywhere(net.minecraft.world.entity.player.Player player, Item item) {
        if (player.getInventory().hasAnyOf(java.util.Set.of(item))) {
            return true;
        }
        for (ItemStack stack : com.opamb.barotrauma.equipment.BagManager.contents(player)) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }
}
