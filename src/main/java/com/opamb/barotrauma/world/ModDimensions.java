package com.opamb.barotrauma.world;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 深海维度的坐标常量与换算。
 *
 * <p>世界骨架（y 轴）：</p>
 * <pre>
 *   15 .. 0     冰盖（packed ice），不可穿透
 *   -1 .. -1000 水柱（正好 1000 格 = 1000 米）
 *   -1001..-1008 海床（深板岩）
 * </pre>
 */
public final class ModDimensions {

    public static final ResourceKey<Level> THE_DEEP = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(BarotraumaMod.MOD_ID, "the_deep"));

    /** 造船厂编辑维度：虚空 + 每玩家地块，不参与深海海压/任务。 */
    public static final ResourceKey<Level> SHIPYARD = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(BarotraumaMod.MOD_ID, "shipyard"));

    /** 造船厂试航维度：独立海域，只允许一艘测试船。 */
    public static final ResourceKey<Level> SHIPYARD_TEST = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(BarotraumaMod.MOD_ID, "shipyard_test"));

    // ---- 造船厂地块（远离深海站点/对接口坐标，避免 DockingBay 的无维度判定）----
    public static final int SHIPYARD_MIN_Y = -64;
    public static final int SHIPYARD_HEIGHT = 384;
    /**
     * 地块原点基准 X；每个玩家沿 X 轴按 PLOT_SPACING 排开。
     *
     * <p>余数取 32（mod 1024）：任务站点 x 为 20000 + slot*1024/2048，
     * 出发站再东西向偏移 80/125/192/200/207/280 格，且 {@code siteAt} 不判维度；
     * 原值 1000000（余数 576）正好距某轮站点 32 格（< SITE_RADIUS=40），
     * 新值与所有可能的站点 x 至少相隔 232 格。</p>
     */
    public static final int SHIPYARD_ORIGIN_X = 1_000_480;
    public static final int SHIPYARD_PLOT_SPACING = 1024;
    public static final int SHIPYARD_PLOT_Y = 64;
    /** 单地块可编辑半边长；与扫描安全上限保持一致。 */
    public static final int SHIPYARD_PLOT_RADIUS = 128;
    /** 新建船时核心相对地块原点的偏移（核心居中放置）。 */
    public static final int SHIPYARD_CORE_OFFSET = 16;

    // ---- 试航海域（首版 127 米水深，低于 150m 海压阈值）----
    public static final int SHIPYARD_TEST_MIN_Y = -128;
    public static final int SHIPYARD_TEST_HEIGHT = 384;
    public static final int SHIPYARD_TEST_WATER_TOP_Y = -1;
    public static final int SHIPYARD_TEST_WATER_BOTTOM_Y = -128;
    public static final int SHIPYARD_TEST_ORIGIN_X = 0;
    /** 远离任务锚点 z=0，避免 DockingBay 把试航海面误判成主站干燥区。 */
    public static final int SHIPYARD_TEST_ORIGIN_Z = 1_000_000;

    /** 世界最低 y。 */
    public static final int MIN_Y = -1008;
    /** 世界总高度（1.20.1 维度类型要求是 16 的倍数）。 */
    public static final int HEIGHT = 1024;

    public static final int CEILING_BOTTOM_Y = 0;
    public static final int WATER_TOP_Y = -1;
    public static final int WATER_BOTTOM_Y = -1000;
    public static final int SEABED_TOP_Y = -1001;

    // ---- 新手哨站（挂在第一层顶板岩壁下方，y 范围见 OutpostBuilder.MIN_Y/MAX_Y）----
    public static final int OUTPOST_X = 0;
    public static final int OUTPOST_Z = 0;
    public static final int OUTPOST_Y = -54;
    public static final int OUTPOST_SPAWN_Y = -62;

    /** 深度换算：1 格 = 1 米。 */
    public static int toMeters(int y) {
        return -y;
    }

    private ModDimensions() {
    }
}
