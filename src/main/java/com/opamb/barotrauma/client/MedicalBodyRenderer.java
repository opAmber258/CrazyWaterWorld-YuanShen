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
public final class MedicalBodyRenderer {

    public record Zone(Limb limb, int x, int y, int w, int h) {
    }

    public record DisplayLine(String text, int color) {
    }

    public static final Map<String, String> DISPLAY_NAMES = Map.of(
            "blunttrauma", "钝器伤",
            "bleeding", "出血",
            "burn", "烧伤",
            "bloodloss", "失血",
            "oxygenlow", "缺氧",
            "stun", "眩晕",
            "opiateoverdose", "阿片类药物中毒",
            "opiateaddiction", "阿片类药物成瘾",
            "opiatewithdrawal", "阿片类药物戒断反应",
            "organdamage", "器官损伤");

    /** 原作式严重度显示：强度占上限的百分比，整数无小数（用户指令 #5）。 */
    public static String label(AfflictionType type, double strength) {
        int percent = (int) Math.round(strength / type.maxStrength() * 100.0);
        return DISPLAY_NAMES.getOrDefault(type.id(), type.id()) + " " + percent + "%";
    }

    private static final int HEALTHY = 0xFF5A6472;
    private static final int HOVER_BORDER = 0xFFFFFFFF;

    /** 身体图总高度（头部 14 + 躯干 30 + 间隙 + 腿 26 ≈ 74）。 */
    public static final int BODY_HEIGHT = 74;

    private MedicalBodyRenderer() {
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

    /** 严重度比例：**按实际 vitality 消耗占比**（消耗点数/100），而非强度/上限——
     *  这样"出血 5 = 半条血"才会显示黄色而不是误导性的绿色（用户实测反馈 #3）。 */
    public static double severityRatio(AfflictionType type, double strength) {
        return Math.min(1.0, type.vitalityDecrease(strength, 100.0) / 100.0);
    }

    /** 悬停部位的伤情文字（含该部位全部病症 + 头部指示型全身病症）；无悬停返回空列表。 */
    public static List<DisplayLine> hoverLines(List<Zone> zones, List<String> rows,
                                               double mouseX, double mouseY) {
        for (Zone zone : zones) {
            if (mouseX >= zone.x() && mouseX < zone.x() + zone.w()
                    && mouseY >= zone.y() && mouseY < zone.y() + zone.h()) {
                List<DisplayLine> lines = new ArrayList<>();
                for (AfflictionType type : AfflictionType.ALL) {
                    if (!type.limbSpecific()) {
                        continue;
                    }
                    double strength = strengthOf(rows, type, zone.limb());
                    if (strength > 0) {
                        double ratio = severityRatio(type, strength);
                        lines.add(new DisplayLine(label(type, strength), severityColor(ratio)));
                    }
                }
                // 头部指示的全身病症（眩晕/阿片中毒等）显示在头部悬停里
                if (zone.limb() == Limb.HEAD) {
                    for (AfflictionType type : AfflictionType.ALL) {
                        if (!type.headIndicator() || type.limbSpecific()) {
                            continue;
                        }
                        double strength = strengthOf(rows, type, null);
                        if (strength >= type.showThreshold()) {
                            lines.add(new DisplayLine(label(type, strength),
                                    severityColor(severityRatio(type, strength))));
                        }
                    }
                }
                if (lines.isEmpty()) {
                    lines.add(new DisplayLine(limbName(zone.limb()) + "：无伤", 0xFF9BE870));
                }
                return lines;
            }
        }
        return List.of();
    }

    public static double worstRatio(List<String> rows, Limb limb) {
        double worst = 0;
        for (AfflictionType type : AfflictionType.ALL) {
            if (!type.limbSpecific()) {
                continue;
            }
            worst = Math.max(worst, severityRatio(type, strengthOf(rows, type, limb)));
        }
        return worst;
    }

    /** 全身病症行（失血/缺氧——头部指示型如眩晕/阿片中毒只在头部悬停显示）。 */
    public static List<DisplayLine> globalLines(List<String> rows) {
        List<DisplayLine> lines = new ArrayList<>();
        for (AfflictionType type : new AfflictionType[]{AfflictionType.BLOODLOSS, AfflictionType.OXYGEN_LOW}) {
            double strength = strengthOf(rows, type, null);
            if (strength > 0) {
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
