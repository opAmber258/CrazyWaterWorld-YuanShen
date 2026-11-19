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
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
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

    private static final int PANEL_W = 200;
    /**
     * 面板高度。
     *
     * <p>高度预算（自上而下）：标题 5、身体图 34..108、可滚动病症区 112..bottom-30、
     * 活力行底部 22。诊断区现在**可滚动**（上游实现），不再靠截断，所以高度主要影响
     * 一次能看到几行；220 在 GUI 缩放高度 240 下 y=10、底部 230，仍在屏内。</p>
     */
    private static final int PANEL_H = 220;
    private static final int LINE = 11;

    /** 对症药品（按原作 SuitableTreatment 适配度降序；底表 §4）。 */
    private static final Map<AfflictionType, List<Supplier<? extends Item>>> SUITABLE = buildSuitable();

    private static Map<AfflictionType, List<Supplier<? extends Item>>> buildSuitable() {
        Map<AfflictionType, List<Supplier<? extends Item>>> map = new LinkedHashMap<>();
        map.put(AfflictionType.BLEEDING, List.of(ModMedicalItems.ANTIBIOTIC_GLUE, ModMedicalItems.BANDAGE));
        map.put(AfflictionType.BURN, List.of(ModMedicalItems.BANDAGE, ModMedicalItems.ANTIBIOTIC_GLUE));
        map.put(AfflictionType.BLOODLOSS, List.of(ModMedicalItems.BLOOD_PACK, ModMedicalItems.SALINE));
        map.put(AfflictionType.BLUNT_TRAUMA, List.of(ModMedicalItems.MORPHINE, ModMedicalItems.FENTANYL));
        // 器官损伤：吗啡（损伤类 −2/s）与芬太尼（损伤类 −15/s）的 TreatmentSpec 都含
        // ORGAN_DAMAGE 条目，所以必须给这个映射——否则只有器官损伤时药品栏是空的，
        // 救人者手里有药也看不到可点（CodeRabbit 指出）。
        map.put(AfflictionType.ORGAN_DAMAGE, List.of(ModMedicalItems.MORPHINE, ModMedicalItems.FENTANYL));
        map.put(AfflictionType.OPIATE_OVERDOSE, List.of(ModMedicalItems.NALOXONE));
        // 原作 SuitableTreatment 对照（medical.xml）：纳洛酮 suitability 覆盖成瘾/戒断；
        // 消辐灵 100 → 辐射病；液态氧矿 30 → 缺氧（负 suitability 是器官损伤，不作为对症推荐）。
        map.put(AfflictionType.OPIATE_ADDICTION, List.of(ModMedicalItems.NALOXONE));
        map.put(AfflictionType.OPIATE_WITHDRAWAL, List.of(ModMedicalItems.NALOXONE));
        map.put(AfflictionType.RADIATION, List.of(ModMedicalItems.ANTIRAD));
        map.put(AfflictionType.OXYGEN_LOW, List.of(ModMedicalItems.LIQUID_OXYGENITE));
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
        g.fill(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, 0xFF4A4A5A);
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xE012121A);

        ClientMedicalCache.Snapshot snapshot = ClientMedicalCache.snapshot(target);
        g.drawString(this.font, Component.literal("医疗 · " + targetName), x + 6, y + 5, 0xFFFFC74A);

        if (targetDead()) {
            // 目标死亡后旧图标不能再可点。
            iconRects.clear();
            g.fill(x + 4, y + 18, x + PANEL_W - 4, y + 32, 0x80FF5A5A);
            g.drawString(this.font, Component.literal("目标已死亡"),
                    x + 7, y + 22, 0xFFFFE0E0);
            return;
        }

        if (targetOutOfRange()) {
            // 超距：服务端的快照请求会被静默丢弃，缓存里的是过期数据——不要再显示它，
            // 也不要把旧图标留成可点击（点了服务端同样拒绝）。
            iconRects.clear();
            g.fill(x + 4, y + 18, x + PANEL_W - 4, y + 32, 0x80FF5A5A);
            g.drawString(this.font, Component.literal("目标超出治疗范围（"
                            + (int) com.opamb.barotrauma.medical.MedicalTuning.TREAT_RANGE_BLOCKS + " 格内）"),
                    x + 7, y + 22, 0xFFFFE0E0);
            return;
        }

        if (snapshot == null) {
            // 数据还没到：面板留空即可（用户要求去掉操作提示类灰字）
            return;
        }

        if (snapshot.downed()) {
            g.fill(x + 4, y + 18, x + PANEL_W - 4, y + 30, 0x80FF5A5A);
            g.drawString(this.font, Component.literal("⚠ 重伤倒地 —— 治疗到活力转正即可救起"),
                    x + 7, y + 20, 0xFFFFE0E0);
        }

        // ---- 身体图（正中）：**左键点击选中并锁定**（鼠标挪到其他部位不会变——用户指令 #3）----
        int cx = x + PANEL_W / 2;
        int by = y + (snapshot.downed() ? 46 : 34);
        zones = TreatmentRenderer.zones(cx, by);
        TreatmentRenderer.draw(g, zones, snapshot.rows(), mouseX, mouseY);

        // ---- 身体图**正下方**：可滚动区域 ----
        // 伤情描述会按面板宽度换行，固定面板装不下；这里开裁剪 + 滚动，
        // 药品图标跟着滚动，命中坐标也用滚动后的位置。
        //（原来这里是"限高 + 截断"，和上游 main 独立落地的滚动方案撞车；
        //  滚动是更好的解法——不会再丢内容，所以采用滚动。）
        int statusTop = by + 78;
        int statusBottom = y + PANEL_H - 30;
        int viewHeight = Math.max(LINE * 2, statusBottom - statusTop);
        statusViewTop = statusTop;
        statusViewBottom = statusBottom;
        statusScroll = Mth.clamp(statusScroll, 0, Math.max(0, statusContentHeight - viewHeight));
        iconRects.clear();
        enableDesignScissor(g, x, statusTop, x + PANEL_W, statusBottom);
        g.pose().pushPose();
        g.pose().translate(0.0F, -statusScroll, 0.0F);
        int cy = statusTop;
        List<DisplayLine> statusLines;
        if (selectedLimb != null) {
            g.drawString(this.font,
                    Component.literal(TreatmentRenderer.limbName(selectedLimb) + "："), x + 8, cy, 0xFF7FB8E8);
            cy += LINE;
            // 该部位的病症 = 分部位病症 + 指示在该部位的全身病症（原作 indicatorlimb）。
            // 缺氧/失血/辐射病/器官损伤挂在胸口，眩晕/阿片系挂在头部——玩家的直觉就是
            // 去胸口看缺氧，所以这里必须按指示部位列，不能只看 limbSpecific。
            for (AfflictionType type : AfflictionType.ALL) {
                boolean shown;
                double strength;
                if (type.limbSpecific()) {
                    strength = TreatmentRenderer.strengthOf(snapshot.rows(), type, selectedLimb);
                    shown = strength > 0;
                } else {
                    if (type.indicatorLimb() != selectedLimb) {
                        continue;
                    }
                    strength = TreatmentRenderer.strengthOf(snapshot.rows(), type, null);
                    shown = strength > 0 && strength >= type.showThreshold();
                }
                if (!shown) {
                    continue;
                }
                double ratio = TreatmentRenderer.severityRatio(type, strength);
                g.drawString(this.font, Component.literal("· "
                        + TreatmentRenderer.label(type, strength)), x + 10, cy,
                        TreatmentRenderer.severityColor(ratio));
                cy += LINE;
                // 原作阶段文案（灰字，自动换行不超框）
                String desc = type.descriptionAt(strength);
                for (String part : TreatmentRenderer.wrap(this.font, desc, PANEL_W - 24)) {
                    g.drawString(this.font, Component.literal(part), x + 16, cy, 0xFF8A8A8A);
                    cy += LINE;
                }
            }
            // 对症药品栏：并排小方框，悬停高亮，单击对该部位用药（只显示背包里真实拥有的药品）
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            List<Item> drugs = new ArrayList<>();
            for (var entry : SUITABLE.entrySet()) {
                if (strengthAtLimb(snapshot.rows(), entry.getKey(), selectedLimb) > 0) {
                    for (Supplier<? extends Item> supplier : entry.getValue()) {
                        Item item = supplier.get();
                        if (!drugs.contains(item) && hasAnywhere(minecraft.player, item)) {
                            drugs.add(item);
                        }
                    }
                }
            }
            if (!drugs.isEmpty()) {
                int slotX = x + 10;
                for (Item drug : drugs) {
                    // 图标画在 cy - statusScroll：悬停判断也得用可见坐标，
                    // 并且不能超出滚动视口（否则滚出去的行也会高亮）。
                    int rowY = cy - statusScroll;
                    boolean slotHover = mouseX >= slotX && mouseX < slotX + 18
                            && mouseY >= rowY && mouseY < rowY + 18
                            && rowY >= statusViewTop && rowY + 18 <= statusViewBottom;
                    g.fill(slotX, cy, slotX + 18, cy + 18, 0xFF1E1E26);
                    g.fill(slotX, cy, slotX + 18, cy + 1, slotHover ? 0xFFFFFFFF : 0xFF5A5A6A);
                    g.fill(slotX, cy + 17, slotX + 18, cy + 18, slotHover ? 0xFFFFFFFF : 0xFF5A5A6A);
                    g.fill(slotX, cy, slotX + 1, cy + 18, slotHover ? 0xFFFFFFFF : 0xFF5A5A6A);
                    g.fill(slotX + 17, cy, slotX + 18, cy + 18, slotHover ? 0xFFFFFFFF : 0xFF5A5A6A);
                    g.renderItem(new ItemStack(drug), slotX + 1, cy + 1);
                    iconRects.add(new IconRect(drug, slotX + 1, cy - statusScroll + 1));
                    slotX += 20;
                }
                cy += 18;
            }
        } else {
            g.drawString(this.font, Component.literal("全身状态"), x + 8, cy, 0xFF7FB8E8);
            cy += LINE;
            List<DisplayLine> globalStatus = new ArrayList<>(
                    TreatmentRenderer.globalLines(snapshot.rows()));
            if (snapshot.downed()) {
                globalStatus.add(new DisplayLine("⚠ 重伤倒地", 0xFFFF5A5A));
            }
            if (globalStatus.isEmpty()) {
                globalStatus.add(new DisplayLine("没有伤病", 0xFF55E05A));
            }
            for (DisplayLine line : globalStatus) {
                g.drawString(this.font, Component.literal("· " + line.text()), x + 10, cy, line.color());
                cy += LINE;
            }
        }
        g.pose().popPose();
        g.disableScissor();
        statusContentHeight = cy - statusTop;
        if (statusContentHeight > viewHeight) {
            // 右侧滚动条：告诉玩家下面还有内容。
            int barH = Math.max(8, viewHeight * viewHeight / statusContentHeight);
            int maxScroll = statusContentHeight - viewHeight;
            int barY = statusTop + (viewHeight - barH) * statusScroll / Math.max(1, maxScroll);
            g.fill(x + PANEL_W - 3, statusTop, x + PANEL_W - 1, statusBottom, 0x40FFFFFF);
            g.fill(x + PANEL_W - 3, barY, x + PANEL_W - 1, barY + barH, 0xFF7FB8E8);
        }

        // ---- 底部：活力（操作提示按用户要求移除）----
        int vitalityY = y + PANEL_H - 22;
        int vitalityColor = snapshot.downed() ? 0xFFFF5A5A
                : snapshot.vitality() > 60 ? 0xFF55E05A
                : snapshot.vitality() > 25 ? 0xFFFFD75A : 0xFFFF5A5A;
        g.drawString(this.font, Component.literal(String.format("活力 %.0f/100%s",
                        snapshot.vitality(), snapshot.downed() ? "（倒地）" : "")),
                x + 6, vitalityY, vitalityColor);

        // ---- 悬停身体图某个部位 → 浮窗显示该部位**完整**伤情 ----
        // 滚动区解决"内容装不下"，这里解决"不用点选就能直接看某部位"：
        // 鼠标扫过身体图时立刻给出该部位全部病症（含强度标签），比先点选再读右侧更快。
        List<DisplayLine> hovered = TreatmentRenderer.hoverLines(zones, snapshot.rows(), mouseX, mouseY);
        if (!hovered.isEmpty()) {
            var tooltip = new ArrayList<Component>();
            for (DisplayLine line : hovered) {
                tooltip.add(Component.literal(line.text())
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(line.color()))));
            }
            g.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
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

    /**
     * 目标是否已超出治疗距离（或不在同一维度）。
     *
     * <p>服务端对超距的 {@link MedicalRequestPacket} **静默丢弃**，客户端于是继续画缓存里的
     * 旧快照——玩家走开后看到的是一直不更新的过期数据，还照样能点药品（点了也没用）。
     * 这里用与服务端同一个常数判断，超距时面板不再显示旧数据（CodeRabbit 指出）。</p>
     */
    private boolean targetOutOfRange() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return true;
        }
        var targetPlayer = minecraft.level.getPlayerByUUID(target);
        if (targetPlayer == null || targetPlayer.level() != minecraft.player.level()) {
            return true;
        }
        return minecraft.player.distanceToSqr(targetPlayer)
                > com.opamb.barotrauma.medical.MedicalTuning.TREAT_RANGE_SQR;
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double delta) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (delta != 0.0D && mouseX >= 0 && mouseX < PANEL_W
                && mouseY >= 0 && mouseY < PANEL_H) {
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
            // 超距/死亡时不给任何操作入口（图标已在 render 里清空，这里再兜一层，
            // 防止 render 尚未跑到的那一帧里点到过期图标）
            if (targetOutOfRange() || targetDead()) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
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
                    selectedLimb = zone.limb();
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

    /**
     * 某病症在这个部位上"生效"的强度：分部位病症取该部位实例；
     * 全身病症只有在其 `indicatorLimb` 等于该部位时才算（原作 indicatorlimb）。
     * 用于对症药品栏——点胸口要能对缺氧/辐射病用药，而不是因为它是全身类型就返回 0。
     */
    private static double strengthAtLimb(List<String> rows, AfflictionType type, Limb limb) {
        if (type.limbSpecific()) {
            return TreatmentRenderer.strengthOf(rows, type, limb);
        }
        if (type.indicatorLimb() != limb) {
            return 0.0;
        }
        double strength = TreatmentRenderer.strengthOf(rows, type, null);
        return strength >= type.showThreshold() ? strength : 0.0;
    }

    /**
     * 该部位上生效的病症行（分部位 + 指示在本部位的全身病症）。
     * 复用 {@link TreatmentRenderer#linesForLimb}，与侧栏/悬停保持同一套规则。
     */
    private static List<DisplayLine> limbStatusLines(ClientMedicalCache.Snapshot snapshot, Limb limb) {
        return TreatmentRenderer.linesForLimb(snapshot.rows(), limb);
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
