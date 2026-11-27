package com.opamb.barotrauma.world;

import com.opamb.barotrauma.world.level.LevelParams;
import com.opamb.barotrauma.world.level.LevelSelector;
import com.opamb.barotrauma.world.level.TerrainTemplate;

import java.util.ArrayList;
import java.util.List;

/** 航次的确定性地形。坐标沿两端哨站旋转，沿线距离和横向宽度互不混用。 */
final class RouteTerrain {
    private static final double EDGE = 64.0D;
    private static final double DOCK_CLEAR = 96.0D;
    private static final long FLOOR_SALT = 0x57A9AEB1L;
    private static final long ROOF_SALT = 0x10F02C4FL;

    /** 开阔带内允许有一段悬空岩体，上下仍各自保留水域。 */
    record Column(int ceiling, int floor, int rockBottom, int rockTop) {
        boolean hasRockAt(int y) {
            return y >= rockBottom && y <= rockTop;
        }
    }

    private record Reef(double along, double across, double radius, double rise, boolean suspended) {
    }

    private final TerrainTemplate template;
    private final long routeSeed;
    private final RoutePath path;
    private final double startX;
    private final double startZ;
    private final double ux;
    private final double uz;
    private final double length;
    private final double halfWidth;
    private final double endMargin;
    private final double height;
    private final double radius;
    private final int endOffset;
    private final double archAlong;
    private final List<Reef> reefs;

    RouteTerrain(MissionInstance.RouteLevel level, DockingBay.Site start,
                 MissionInstance.Anchor end, RoutePath path) {
        template = level.terrain();
        routeSeed = level.seed();
        this.path = path;
        startX = start.x();
        startZ = start.z();
        double dx = end.x() - startX;
        double dz = end.z() - startZ;
        length = Math.max(1.0D, Math.hypot(dx, dz));
        ux = dx / length;
        uz = dz / length;
        LevelParams params = level.type().params();
        halfWidth = params.terrainCrossWidth() * 0.5D;
        endMargin = Math.max(EDGE, (params.terrainWidth() - length) * 0.5D);
        height = params.terrainHeight();
        radius = params.terrainTunnelRadius();
        endOffset = MissionInstance.destinationY(template) - MissionInstance.ANCHOR_Y;
        archAlong = length * (0.32D + 0.34D * random(3));
        reefs = template == TerrainTemplate.OPEN_SEA ? createReefs() : List.of();
    }

    private List<Reef> createReefs() {
        List<Reef> result = new ArrayList<>();
        int count = Math.max(4, (int) Math.round(length / 110.0D));
        for (int i = 0; i < count; i++) {
            int salt = 31 + i * 5;
            double along = length * ((i + 0.25D + random(salt) * 0.5D) / count);
            double across = halfWidth * (0.39D + random(salt + 1) * 0.36D);
            if ((i & 1) != 0) {
                across = -across;
            }
            double reefRadius = 38.0D + 62.0D * random(salt + 2);
            double rise = height * (0.15D + 0.11D * random(salt + 3));
            result.add(new Reef(along, across, reefRadius, rise, i % 3 == 1));
        }
        return List.copyOf(result);
    }

    /** 模板影响范围外返回 null，区块生成接回全局海域。 */
    Column column(long worldSeed, int x, int z) {
        return column(worldSeed, x, z, false);
    }

    Column column(long worldSeed, int x, int z, boolean sealed) {
        double dx = x - startX;
        double dz = z - startZ;
        double along = dx * ux + dz * uz;
        double across = dz * ux - dx * uz;
        double outsideEnds = Math.max(-along, along - length);
        double edgeDistance = Math.min(halfWidth - Math.abs(across), endMargin - outsideEnds);
        if (edgeDistance <= 0.0D) {
            return null;
        }
        double weight = SeaTerrain.smooth(edgeDistance / EDGE);
        double dockDistance = Math.min(Math.hypot(along, across), Math.hypot(along - length, across));
        double outsideDock = SeaTerrain.smooth((dockDistance - DOCK_CLEAR) / 64.0D);
        RoutePath.Nearest nearest = path.nearest(x, z);
        double noiseX = x / LevelParams.TERRAIN_SCALE;
        double noiseZ = z / LevelParams.TERRAIN_SCALE;
        long seed = worldSeed ^ routeSeed;
        double rough = DeepSeaNoise.fbm2(seed ^ FLOOR_SALT, noiseX / 68.0D, noiseZ / 68.0D, 3);
        double roofNoise = DeepSeaNoise.fbm2(seed ^ ROOF_SALT, noiseX / 175.0D, noiseZ / 175.0D, 2);
        double endDock = 1.0D - SeaTerrain.smooth((Math.hypot(along - length, across) - DOCK_CLEAR) / 88.0D);
        double ceiling = LevelParams.BAND_CEILING_Y + endOffset * endDock
                - outsideDock * (10.0D + roofNoise * 8.0D);
        double bed;
        double shelfY = 0.0D;
        double shelfHalf = 0.0D;
        if (template == TerrainTemplate.CANYON) {
            ceiling = blend(ceiling, Math.min(ceiling, nearest.y() + Math.max(100.0D, height * 0.20D)),
                    outsideDock);
            double mainWall = SeaTerrain.smooth((nearest.distance() - radius) / 76.0D);
            double sideWall = SeaTerrain.smooth((path.distanceToNetwork(x, z) - radius * 0.52D) / 54.0D);
            double wall = Math.min(mainWall, sideWall) * outsideDock;
            double valley = nearest.y() - Math.max(112.0D, height * 0.18D) + rough * 25.0D;
            bed = blend(valley, ceiling - 18.0D - rough * 15.0D, wall);
            double archLength = 32.0D * LevelParams.TERRAIN_SCALE;
            double arch = 1.0D - square((along - archAlong) / archLength)
                    - square(nearest.distance() / (radius * 1.5D));
            if (arch > 0.0D) {
                shelfHalf = arch * 16.0D * weight * outsideDock;
                shelfY = nearest.y() + 108.0D - nearest.distance() * 0.23D + rough * 9.0D;
            }
        } else {
            double breadth = Math.min(1.0D, Math.abs(across) / halfWidth);
            double basin = SeaTerrain.smooth(Math.min(along, length - along) / Math.max(1.0D, length * 0.32D));
            // 大海盆上方冰盖顺深度缓降，横向整片仍保持敞开。
            ceiling -= height * 0.16D * basin * outsideDock;
            bed = LevelParams.BAND_CEILING_Y - height * (0.72D + 0.23D * basin - 0.32D * breadth * breadth)
                    + rough * 34.0D;
            double reefRise = 0.0D;
            for (Reef reef : reefs) {
                double relief = 1.0D - square((along - reef.along()) / reef.radius())
                        - square((across - reef.across()) / reef.radius()) + rough * 0.16D;
                if (relief <= 0.0D) {
                    continue;
                }
                reefRise = Math.max(reefRise, reef.rise() * relief * relief);
                if (reef.suspended() && relief > 0.35D && shelfHalf == 0.0D) {
                    shelfHalf = (relief - 0.35D) * 21.0D * weight * outsideDock;
                    shelfY = bed + height * 0.35D;
                }
            }
            bed += reefRise * outsideDock;
            // 横渡中心与码头接近区留出上下机动空间，远侧礁体仍能从不同方向绕过。
            double navigation = 1.0D - SeaTerrain.smooth((nearest.distance() - radius) / 70.0D);
            bed = blend(bed, Math.min(bed, nearest.y() - 88.0D), navigation);
            shelfHalf *= 1.0D - navigation;
        }
        bed = Math.min(bed, ceiling - 8.0D);
        bed = Math.max(bed, LevelParams.BAND_CEILING_Y - height);
        if (weight < 1.0D) {
            if (sealed) {
                double center = (ceiling + bed) * 0.5D;
                ceiling = blend(center, ceiling, weight);
                bed = blend(center, bed, weight);
            } else {
                ceiling = blend(SeaTerrain.baseCeilingY(worldSeed, x, z), ceiling, weight);
                bed = blend(SeaTerrain.baseFloorY(worldSeed, x, z), bed, weight);
            }
        }
        int top = (int) Math.round(ceiling);
        int floor = (int) Math.round(bed);
        int rockBottom = Math.max(floor + 1, (int) Math.ceil(shelfY - shelfHalf));
        int rockTop = Math.min(top - 1, (int) Math.floor(shelfY + shelfHalf));
        if (shelfHalf < 1.0D) {
            rockBottom = top;
            rockTop = floor;
        }
        return new Column(top, floor, rockBottom, rockTop);
    }

    private double random(int salt) {
        return (LevelSelector.mix(routeSeed, salt) >>> 11) * 0x1.0p-53;
    }

    private static double blend(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double square(double value) {
        return value * value;
    }
}
