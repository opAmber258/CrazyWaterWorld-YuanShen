package com.opamb.barotrauma.client;

import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 原作风格身体图渲染器（背包面板和治疗面板共用）：
 * 六部位人形图，按部位最高严重度着色；**部位伤情文字只在鼠标悬停时显示**（用户指令）。
 */
public final class TreatmentRenderer {

    public record Zone(Limb limb, int x, int y, int w, int h) {
    }

    public record DisplayLine(String text, int color) {
    }

    public static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("blunttrauma", "钝器伤"),
            Map.entry("bleeding", "出血"),
            Map.entry("burn", "烧伤"),
            Map.entry("bloodloss", "失血"),
            Map.entry("oxygenlow", "缺氧"),
            Map.entry("stun", "眩晕"),
            Map.entry("opiateoverdose", "阿片类药物中毒"),
            Map.entry("opiateaddiction", "阿片类药物成瘾"),
            Map.entry("opiatewithdrawal", "阿片类药物戒断反应"),
            Map.entry("organdamage", "器官损伤"),
            Map.entry("radiationsickness", "辐射病"));

    /** 原作式严重度显示：强度占上限的百分比，整数无小数（用户指令 #5）。 */
    public static String label(AfflictionType type, double strength) {
        int percent = (int) Math.round(strength / type.maxStrength() * 100.0);
        return DISPLAY_NAMES.getOrDefault(type.id(), type.id()) + " " + percent + "%";
    }

    private static final int HEALTHY = 0xFF5A6472;
    private static final int HOVER_BORDER = 0xFFFFFFFF;

    /** 身体图总高度（头部 14 + 躯干 30 + 间隙 + 腿 26 ≈ 74）。 */
    public static final int BODY_HEIGHT = 74;

    private TreatmentRenderer() {
    }

    /** 身体图布局：cx = 身体中轴 x，by = 头顶 y。 */
    public static List<Zone> zones(int cx, int by) {
        return List.of(
                new Zone(Limb.HEAD, cx - 8, by, 16, 14),
                new Zone(Limb.TORSO, cx - 9, by + 16, 18, 30),
                new Zone(Limb.LEFT_ARM, cx - 22, by + 16, 11, 28),
                new Zone(Limb.RIGHT_ARM, cx + 11, by + 16, 11, 28),
                new Zone(Limb.LEFT_LEG, cx - 9, by + 48, 8, 26),
                new Zone(Limb.RIGHT_LEG, cx + 1, by + 48, 8, 26));
    }

    /** 画身体图；mouseX/Y 为屏幕坐标（-1 表示无鼠标，如面板渲染在固定位置时）。
     *  头部着色包含头部指示型全身病症（阿片中毒会让头部变色——用户指令）。 */
    public static void draw(GuiGraphics g, List<Zone> zones, List<String> rows,
                            double mouseX, double mouseY) {
        for (Zone zone : zones) {
            double worst = worstRatio(rows, zone.limb());
            if (zone.limb() == Limb.HEAD) {
                // 头部指示型全身病症（眩晕/阿片中毒等）也参与头部着色
                for (AfflictionType type : AfflictionType.ALL) {
                    if (type.headIndicator() && !type.limbSpecific()) {
                        double strength = strengthOf(rows, type, null);
                        if (strength >= type.showThreshold()) {
                            worst = Math.max(worst, severityRatio(type, strength));
                        }
                    }
                }
            }
            int fill = worst > 0 ? severityColor(worst) : HEALTHY;
            g.fill(zone.x(), zone.y(), zone.x() + zone.w(), zone.y() + zone.h(), fill);
            boolean hover = mouseX >= zone.x() && mouseX < zone.x() + zone.w()
                    && mouseY >= zone.y() && mouseY < zone.y() + zone.h();
            if (hover) {
                g.fill(zone.x() - 1, zone.y() - 1, zone.x() + zone.w() + 1, zone.y(), HOVER_BORDER);
                g.fill(zone.x() - 1, zone.y() + zone.h(), zone.x() + zone.w() + 1, zone.y() + zone.h() + 1, HOVER_BORDER);
                g.fill(zone.x() - 1, zone.y(), zone.x(), zone.y() + zone.h(), HOVER_BORDER);
                g.fill(zone.x() + zone.w(), zone.y(), zone.x() + zone.w() + 1, zone.y() + zone.h(), HOVER_BORDER);
            }
        }
    }

    /**
     * 严重度比例（0–1）——身体图着色与文案配色都用它。
     *
     * <p>**优先按实际 vitality 消耗占比**（消耗点数/100），这样"出血 5 = 半条血"才会显示
     * 黄色而不是误导性的绿色（用户实测反馈 #3）。</p>
     *
     * <p>消耗为 0 的病症（辐射病各段、缺氧 50 以下预警段）退回"强度占上限的比例"——
     * 否则它们在面板里永远是 0、显示成健康绿，胸口不变色（CodeRabbit 指出）。
     * 规则与 {@link MedicalBodyRenderer#severityRatio} 一致，两处不要各写一套。</p>
     *
     * <p>注意 `vitalityDecrease` 返回的是**每 100 点活力的消耗**（有的类型已乘上限、
     * 有的是原始点数），所以必须**先除以 100 再截断**——先 min 会让 5 点变成满格红。</p>
     */
    public static double severityRatio(AfflictionType type, double strength) {
        double drain = type.vitalityDecrease(strength, 100.0);
        if (drain > 0.0) {
            return Math.min(1.0, drain / 100.0);
        }
        double max = type.maxStrength();
        return max <= 0.0 ? 0.0 : Math.min(1.0, Math.max(0.0, strength / max));
    }

    /**
     * 悬停部位的伤情文字：该部位的分部位病症 + **指示在本部位上的全身病症**。
     *
     * <p>原作每个全身病症都有 `indicatorlimb`（缺氧/失血/辐射病/器官损伤 → 胸口；
     * 眩晕/阿片系 → 头部）。以前只实现了头部，导致点开胸口看不到缺氧和辐射病——
     * 玩家的直觉正是"去胸口看"，所以必须按指示部位挂。</p>
     */
    public static List<DisplayLine> hoverLines(List<Zone> zones, List<String> rows,
                                               double mouseX, double mouseY) {
        for (Zone zone : zones) {
            if (mouseX >= zone.x() && mouseX < zone.x() + zone.w()
                    && mouseY >= zone.y() && mouseY < zone.y() + zone.h()) {
                return linesForLimb(rows, zone.limb());
            }
        }
        return List.of();
    }

    /** 某个部位上要显示的全部病症行（分部位 + 指示在该部位的全身病症）。 */
    public static List<DisplayLine> linesForLimb(List<String> rows, Limb limb) {
        List<DisplayLine> lines = new ArrayList<>();
        // 1) 该部位自身的病症
        for (AfflictionType type : AfflictionType.ALL) {
            if (!type.limbSpecific()) {
                continue;
            }
            double strength = strengthOf(rows, type, limb);
            if (strength > 0) {
                double ratio = severityRatio(type, strength);
                lines.add(new DisplayLine(label(type, strength), severityColor(ratio)));
            }
        }
        // 2) 指示在本部位的全身病症（原作 indicatorlimb）
        for (AfflictionType type : AfflictionType.ALL) {
            if (type.limbSpecific() || type.indicatorLimb() != limb) {
                continue;
            }
            double strength = strengthOf(rows, type, null);
            if (strength >= type.showThreshold() && strength > 0) {
                lines.add(new DisplayLine(label(type, strength),
                        severityColor(severityRatio(type, strength))));
            }
        }
        if (lines.isEmpty()) {
            lines.add(new DisplayLine(limbName(limb) + "：无伤", 0xFF9BE870));
        }
        return lines;
    }

    /**
     * 该部位的最高严重度（用于身体图着色）：分部位病症 + 指示在本部位的全身病症。
     * 缺氧/辐射病挂在胸口时，胸口会跟着变色——玩家一眼能看出"中招了"。
     */
    public static double worstRatio(List<String> rows, Limb limb) {
        double worst = 0;
        for (AfflictionType type : AfflictionType.ALL) {
            if (type.limbSpecific()) {
                worst = Math.max(worst, severityRatio(type, strengthOf(rows, type, limb)));
            } else if (type.indicatorLimb() == limb) {
                double strength = strengthOf(rows, type, null);
                if (strength >= type.showThreshold()) {
                    worst = Math.max(worst, severityRatio(type, strength));
                }
            }
        }
        return worst;
    }

    /**
     * 全身病症常驻行：**全部全身病症**（失血/缺氧/器官损伤/辐射病/眩晕/阿片系…）。
     *
     * <p>用户指令：全身性的失血/缺氧要常驻显示，不能只藏在悬停里。
     * 同时它们也会出现在各自 `indicatorLimb` 的部位悬停里（原作语义），
     * 两处并存是刻意的——常驻行保证"一直看得见"，部位行保证"符合直觉地能查"。</p>
     */
    public static List<DisplayLine> globalLines(List<String> rows) {
        List<DisplayLine> lines = new ArrayList<>();
        for (AfflictionType type : AfflictionType.ALL) {
            if (type.limbSpecific()) {
                continue;
            }
            double strength = strengthOf(rows, type, null);
            if (strength > 0 && strength >= type.showThreshold()) {
                double ratio = severityRatio(type, strength);
                String warn = type == AfflictionType.OXYGEN_LOW && strength < type.maxStrength() * 0.25 ? "（预警）" : "";
                lines.add(new DisplayLine(label(type, strength) + warn, severityColor(ratio)));
            }
        }
        return lines;
    }

    public static double strengthOf(List<String> rows, AfflictionType type, Limb limb) {
        double total = 0;
        for (String row : rows) {
            if (row.startsWith("t|")) {
                continue;
            }
            String[] parts = row.split("\\|");
            if (parts.length < 2 || !parts[0].equals(type.id())) {
                continue;
            }
            if (type.limbSpecific()) {
                if (parts.length >= 3 && parts[1].equals(limb.name())) {
                    total += parse(parts[2]);
                }
            } else {
                total += parse(parts[1]);
            }
        }
        return total;
    }

    /**
     * 严重度颜色：连续渐变（用户指令：各阶段平滑过渡，色卡参照原版潜渊症
     * 健康界面的 绿→黄→橙→红 序列）。
     */
    public static int severityColor(double ratio) {
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        // 原版色卡近似锚点：绿 (110,220,110) → 黄 (240,220,70) → 橙 (255,150,50) → 红 (235,55,55)
        double[][] anchors = {
                {0.00, 110, 220, 110},
                {0.40, 240, 220, 70},
                {0.70, 255, 150, 50},
                {1.00, 235, 55, 55}
        };
        for (int i = 0; i < anchors.length - 1; i++) {
            if (ratio <= anchors[i + 1][0]) {
                double span = anchors[i + 1][0] - anchors[i][0];
                double t = span <= 0 ? 0 : (ratio - anchors[i][0]) / span;
                int r = (int) (anchors[i][1] + (anchors[i + 1][1] - anchors[i][1]) * t);
                int gr = (int) (anchors[i][2] + (anchors[i + 1][2] - anchors[i][2]) * t);
                int b = (int) (anchors[i][3] + (anchors[i + 1][3] - anchors[i][3]) * t);
                return 0xFF000000 | (r << 16) | (gr << 8) | b;
            }
        }
        return 0xFFEB3737;
    }

    public static String limbName(Limb limb) {
        return switch (limb) {
            case HEAD -> "头部";
            case TORSO -> "胸部";
            case LEFT_ARM -> "左臂";
            case RIGHT_ARM -> "右臂";
            case LEFT_LEG -> "左腿";
            case RIGHT_LEG -> "右腿";
        };
    }

    public static String fmt(double value) {
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    /** 中文按字符换行（无空格分词），保证不超出面板宽度。 */
    public static List<String> wrap(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return List.of(text);
        }
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(current.toString() + c) > maxWidth && current.length() > 0) {
                out.add(current.toString());
                current = new StringBuilder();
            }
            current.append(c);
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    private static double parse(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
