package com.opamb.barotrauma.world;

import com.opamb.barotrauma.world.level.LevelParams;
import com.opamb.barotrauma.world.level.LevelSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * 航行关卡的隧道网络。对照原作 {@code Level.GeneratePathNodes} / SidePath：
 *
 * <ul>
 *   <li><b>主航道</b>：沿航线轴逐节点前进，每个节点的垂直坐标在关卡高度内随机，
 *       再夹在上一节点 ±{@code 高度×variance×0.5} 内——所以它会像原作一样
 *       反复上下跳，而不是一条平滑的正弦。横向也做同样的随机游走，让潜艇左右绕。</li>
 *   <li><b>侧隧道</b>：从已有隧道（包括侧隧道自己）取前 1/3 的一个节点和后 1/3 的
 *       一个节点，在两点之间生成一条自己的随机游走路径。首尾都接在父隧道上，
 *       所以是真正的环路/岔路口，不是死胡同；侧隧道还可以继续挂侧隧道。</li>
 *   <li><b>洞室</b>：额外的大洞室挂在主航道旁，开口很宽。</li>
 * </ul>
 *
 * <p>横截面是椭圆（横向半宽、纵向半高），沿折线扫掠；多段取并集，弯道不会出尖角。
 * 纯数学、确定性、跨区块连续；世界生成 worker 线程只读。</p>
 */
public final class RoutePath {

    /** 主航道横向随机游走噪声。 */
    private static final long SALT_LATERAL = 0x2F6E2B1DL;
    /** 主航道垂直随机游走噪声。 */
    private static final long SALT_VERTICAL = 0x6A09E667L;
    /** 洞窟尺寸起伏噪声。 */
    private static final long SALT_RADIUS = 0xBB67AE85L;
    /** 岩壁粗糙度噪声。 */
    private static final long SALT_ROUGH = 0x3C6EF372L;

    /** 一个航点：椭圆横截面（横向半宽、纵向半高）。 */
    public record Point(double x, double y, double z, double halfWidth, double halfHeight) {
    }

    /** 一条隧道：主航道或侧隧道环路。 */
    public record Tunnel(boolean main, Point[] points) {
    }

    /** 独立的大洞室（挂在主航道旁，开口很宽，不是小洞）。 */
    public record Chamber(double x, double y, double z, double halfWidth, double halfHeight) {
    }

    /** 某一列上洞窟打开的垂直区间；{@code null} 表示这一列没有洞。 */
    public record Open(double top, double bottom) {
    }

    /** 最近点：水平距离、该处航道高度、沿线进度（0..1）。 */
    public record Nearest(double distance, double y, double along) {
    }

    private final Tunnel main;
    private final List<Tunnel> tunnels;
    private final List<Chamber> chambers;

    private RoutePath(Tunnel main, List<Tunnel> tunnels, List<Chamber> chambers) {
        this.main = main;
        this.tunnels = tunnels;
        this.chambers = chambers;
    }

    public static RoutePath build(LevelParams params, long seed,
                                  double startX, double startZ,
                                  double endX, double endZ) {
        double dx = endX - startX;
        double dz = endZ - startZ;
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-3D) {
            length = 1.0D;
        }
        double nx = -dz / length;
        double nz = dx / length;

        double hw = params.cavernHalfWidth();
        double hh = params.cavernHalfHeight();
        double minY = params.bandFloor() + hh + 10.0D;
        double maxY = LevelParams.BAND_CEILING_Y - 20.0D;
        double variance = Math.max(0.05D, params.shape().mainPathVariance());
        // 原作按整关高度算步长：pathBorders.Height * variance * 0.5。
        // 这里用 bandCeiling - bandFloor（不是洞窟内部可用范围），否则大洞窟会把
        // 上下起伏压得很小，看起来又像平直隧道。
        double bandHeight = LevelParams.BAND_CEILING_Y - params.bandFloor();
        double stepY = Math.max(40.0D, bandHeight * variance * 0.5D);
        // 横向随机游走：槽位半宽 1024，最大 ±250；主洞 600 宽也能大幅左右摆。
        double maxLat = Math.min(250.0D, Math.max(80.0D, 1024.0D - hw - 32.0D));
        double stepLat = Math.max(40.0D, params.averageWidth() * 0.20D);

        int intervalMin = Math.max(24, params.shape().mainPathIntervalMin());
        int intervalMax = Math.max(intervalMin, params.shape().mainPathIntervalMax());

        List<Point> mainNodes = new ArrayList<>();
        mainNodes.add(new Point(startX, -80.0D, startZ, hw, hh));
        double along = 0.0D;
        double lat = 0.0D;
        double y = -80.0D;
        int node = 1;
        while (true) {
            int step = randInt(seed, intervalMin, intervalMax, node * 13 + 1);
            along += step;
            if (along >= length - intervalMin) {
                break;
            }
            double u = along / length;
            // 原作：先在整条 pathBorders 高度里随机，再夹到上一节点 ±height*variance*0.5。
            double candidateY = minY + rand01(seed, node * 17 + 3) * (maxY - minY);
            y = clamp(candidateY, y - stepY, y + stepY);
            y = clamp(y, minY, maxY);
            double candidateLat = -maxLat + rand01(seed, node * 29 + 5) * 2.0D * maxLat;
            lat = clamp(candidateLat, lat - stepLat, lat + stepLat);
            lat = clamp(lat, -maxLat, maxLat);
            double px = startX + dx * u + nx * lat;
            double pz = startZ + dz * u + nz * lat;
            double hwNode = hw * (1.0D + rand01(seed, node * 41 + 7) * 0.16D - 0.08D);
            double hhNode = hh * (1.0D + rand01(seed, node * 43 + 9) * 0.16D - 0.08D);
            mainNodes.add(new Point(px, y, pz, Math.max(40.0D, hwNode), Math.max(40.0D, hhNode)));
            node++;
        }
        mainNodes.add(new Point(endX, -80.0D, endZ, hw, hh));
        Tunnel main = new Tunnel(true, mainNodes.toArray(new Point[0]));

        List<Tunnel> tunnels = new ArrayList<>();
        tunnels.add(main);
        int sideCount = Math.max(0, params.shape().sideTunnelMax());
        for (int s = 0; s < sideCount; s++) {
            // 原作：父隧道可以是主航道，也可以是之前生成的侧隧道。
            Tunnel parent = tunnels.get((int) Math.floorMod(
                    LevelSelector.mix(seed, s * 97 + 13), tunnels.size()));
            Point[] parentPoints = parent.points();
            if (parentPoints.length < 4) {
                continue;
            }
            int firstThird = Math.max(1, parentPoints.length / 3);
            int aIndex = 1 + (int) Math.floorMod(
                    LevelSelector.mix(seed, s * 101 + 17), firstThird);
            aIndex = Math.min(aIndex, parentPoints.length - 3);
            int lastStart = Math.max(aIndex + 1, parentPoints.length * 2 / 3);
            int bIndex = lastStart + (int) Math.floorMod(
                    LevelSelector.mix(seed, s * 103 + 19),
                    Math.max(1, parentPoints.length - 1 - lastStart));
            bIndex = Math.min(bIndex, parentPoints.length - 2);
            if (bIndex <= aIndex) {
                continue;
            }
            Point a = parentPoints[aIndex];
            Point b = parentPoints[bIndex];
            Point[] side = buildSidePath(params, seed, s, a, b,
                    intervalMin, intervalMax, variance, minY, maxY);
            tunnels.add(new Tunnel(false, side));
        }

        List<Chamber> chambers = buildChambers(params, seed, mainNodes, nx, nz);
        return new RoutePath(main, List.copyOf(tunnels), List.copyOf(chambers));
    }

    /**
     * 侧隧道：从父隧道的节点 {@code a} 到节点 {@code b}，中间做自己的随机游走。
     * 首尾节点与父隧道重合，所以两端都是岔路口。
     */
    private static Point[] buildSidePath(LevelParams params, long seed, int salt,
                                         Point a, Point b,
                                         int intervalMin, int intervalMax,
                                         double variance, double minY, double maxY) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = Math.hypot(dx, dz);
        double bhw = params.branchHalfWidth();
        double bhh = params.branchHalfHeight();
        if (length < 1.0D) {
            return new Point[]{
                    new Point(a.x(), a.y(), a.z(), bhw, bhh),
                    new Point(b.x(), b.y(), b.z(), bhw, bhh)};
        }
        double nx = -dz / length;
        double nz = dx / length;
        double sideMinY = params.bandFloor() + bhh + 10.0D;
        double sideMaxY = LevelParams.BAND_CEILING_Y - 20.0D;
        double bandHeight = LevelParams.BAND_CEILING_Y - params.bandFloor();
        double stepY = Math.max(40.0D, bandHeight * variance * 0.5D);
        double maxLat = Math.min(200.0D, Math.max(60.0D, 1024.0D - bhw - 32.0D));
        double stepLat = Math.max(30.0D, params.averageWidth() * 0.12D);

        List<Point> nodes = new ArrayList<>();
        nodes.add(new Point(a.x(), a.y(), a.z(), bhw, bhh));
        double along = 0.0D;
        double lat = 0.0D;
        double y = a.y();
        int node = 1;
        while (true) {
            int step = randInt(seed, intervalMin, intervalMax, salt * 211 + node * 13);
            along += step;
            if (along >= length - intervalMin * 0.5D) {
                break;
            }
            double u = along / length;
            double candidateY = sideMinY + rand01(seed, salt * 223 + node * 17)
                    * (sideMaxY - sideMinY);
            y = clamp(candidateY, y - stepY, y + stepY);
            y = clamp(y, sideMinY, sideMaxY);
            double candidateLat = -maxLat + rand01(seed, salt * 227 + node * 29) * 2.0D * maxLat;
            lat = clamp(candidateLat, lat - stepLat, lat + stepLat);
            lat = clamp(lat, -maxLat, maxLat);
            nodes.add(new Point(
                    a.x() + dx * u + nx * lat,
                    y,
                    a.z() + dz * u + nz * lat,
                    bhw, bhh));
            node++;
        }
        nodes.add(new Point(b.x(), b.y(), b.z(), bhw, bhh));
        return nodes.toArray(new Point[0]);
    }

    /** 多出来的洞穴做成主航道旁边的大洞室，开口至少半个主洞宽。 */
    private static List<Chamber> buildChambers(LevelParams params, long seed,
                                               List<Point> mainNodes, double nx, double nz) {
        List<Chamber> chambers = new ArrayList<>();
        int extra = Math.max(0, params.shape().caveCount() - params.shape().sideTunnelMax());
        Point[] points = mainNodes.toArray(new Point[0]);
        for (int i = 0; i < extra; i++) {
            double u = (i + 0.6D) / (extra + 0.6D);
            Point p = pointAt(points, u);
            Point before = pointAt(points, Math.max(0.0D, u - 0.02D));
            Point after = pointAt(points, Math.min(1.0D, u + 0.02D));
            double tx = after.x() - before.x();
            double tz = after.z() - before.z();
            double tLen = Math.hypot(tx, tz);
            double chamberNx = tLen < 1.0E-6D ? nx : -tz / tLen;
            double chamberNz = tLen < 1.0E-6D ? nz : tx / tLen;
            long hash = LevelSelector.mix(seed, i * 57 + 29);
            double side = (hash & 1L) == 0L ? 1.0D : -1.0D;
            double offset = p.halfWidth();
            chambers.add(new Chamber(
                    p.x() + chamberNx * side * offset,
                    p.y(),
                    p.z() + chamberNz * side * offset,
                    p.halfWidth() * 0.70D,
                    p.halfHeight() * 0.90D));
        }
        return chambers;
    }

    /** 主航道沿线参数 {@code u∈[0,1]} 处的航点。 */
    public Point pointAt(double u) {
        return pointAt(main.points(), u);
    }

    private static Point pointAt(Point[] points, double u) {
        double t = Math.max(0.0D, Math.min(1.0D, u)) * (points.length - 1);
        int i = Math.min(points.length - 2, (int) Math.floor(t));
        double f = t - i;
        Point a = points[i];
        Point b = points[i + 1];
        return new Point(
                a.x() + (b.x() - a.x()) * f,
                a.y() + (b.y() - a.y()) * f,
                a.z() + (b.z() - a.z()) * f,
                a.halfWidth() + (b.halfWidth() - a.halfWidth()) * f,
                a.halfHeight() + (b.halfHeight() - a.halfHeight()) * f);
    }

    /** 到主航道中心线的水平距离（任务进度/矿脉用）。 */
    public double distanceTo(double x, double z) {
        return nearest(x, z).distance();
    }

    /** 到整个隧道网络（主航道 + 所有侧隧道）的最近水平距离。 */
    public double distanceToNetwork(double x, double z) {
        double best = Double.MAX_VALUE;
        for (Tunnel tunnel : tunnels) {
            Point[] points = tunnel.points();
            for (int i = 0; i < points.length - 1; i++) {
                double distance = distanceToSegment(x, z,
                        points[i].x(), points[i].z(), points[i + 1].x(), points[i + 1].z());
                if (distance < best) {
                    best = distance;
                }
            }
        }
        return best;
    }

    /** 主航道最近点：水平距离、该处航道高度、沿线进度。 */
    public Nearest nearest(double x, double z) {
        Point[] points = main.points();
        double bestDistance = Double.MAX_VALUE;
        double bestY = 0.0D;
        double bestAlong = 0.0D;
        for (int i = 0; i < points.length - 1; i++) {
            Point a = points[i];
            Point b = points[i + 1];
            double sdx = b.x() - a.x();
            double sdz = b.z() - a.z();
            double lenSq = sdx * sdx + sdz * sdz;
            double t = lenSq < 1.0E-9D ? 0.0D
                    : ((x - a.x()) * sdx + (z - a.z()) * sdz) / lenSq;
            t = Math.max(0.0D, Math.min(1.0D, t));
            double px = a.x() + sdx * t;
            double pz = a.z() + sdz * t;
            double distance = Math.hypot(x - px, z - pz);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestY = a.y() + (b.y() - a.y()) * t;
                bestAlong = (i + t) / (points.length - 1);
            }
        }
        return new Nearest(bestDistance, bestY, bestAlong);
    }

    /**
     * 这一列 (x,z) 上洞窟打开的垂直区间：主航道 + 侧隧道 + 洞室的椭圆并集。
     */
    public Open openAt(double x, double z, long roughnessSeed) {
        double top = Double.NEGATIVE_INFINITY;
        double bottom = Double.POSITIVE_INFINITY;
        boolean any = false;
        double rough = roughness(roughnessSeed, x, z);
        for (Tunnel tunnel : tunnels) {
            Point[] points = tunnel.points();
            for (int i = 0; i < points.length - 1; i++) {
                double[] union = ellipseAt(x, z, points[i], points[i + 1], rough);
                if (union != null) {
                    top = Math.max(top, union[0]);
                    bottom = Math.min(bottom, union[1]);
                    any = true;
                }
            }
        }
        for (Chamber chamber : chambers) {
            double dx = x - chamber.x();
            double dz = z - chamber.z();
            double distance = Math.hypot(dx, dz);
            double hw = chamber.halfWidth() * (1.0D + rough);
            double hh = chamber.halfHeight() * (1.0D + rough);
            if (hw <= 0.0D || distance >= hw) {
                continue;
            }
            double half = hh * Math.sqrt(1.0D - (distance / hw) * (distance / hw));
            top = Math.max(top, chamber.y() + half);
            bottom = Math.min(bottom, chamber.y() - half);
            any = true;
        }
        return any ? new Open(top, bottom) : null;
    }

    /** 一段椭圆洞窟在 (x,z) 列上的 [top,bottom]；不在这一段里返回 null。 */
    private static double[] ellipseAt(double x, double z, Point a, Point b, double rough) {
        double sdx = b.x() - a.x();
        double sdz = b.z() - a.z();
        double lenSq = sdx * sdx + sdz * sdz;
        double t = lenSq < 1.0E-9D ? 0.0D
                : ((x - a.x()) * sdx + (z - a.z()) * sdz) / lenSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        double px = a.x() + sdx * t;
        double pz = a.z() + sdz * t;
        double distance = Math.hypot(x - px, z - pz);
        double hw = (a.halfWidth() + (b.halfWidth() - a.halfWidth()) * t) * (1.0D + rough);
        double hh = (a.halfHeight() + (b.halfHeight() - a.halfHeight()) * t) * (1.0D + rough * 0.8D);
        if (hw <= 0.0D || distance >= hw) {
            return null;
        }
        double half = hh * Math.sqrt(1.0D - (distance / hw) * (distance / hw));
        double y = a.y() + (b.y() - a.y()) * t;
        return new double[]{y + half, y - half};
    }

    /** (x,z) 是否落在侧隧道或洞室里（不含主航道本身）。 */
    public boolean inChamber(double x, double z) {
        for (Tunnel tunnel : tunnels) {
            if (tunnel.main()) {
                continue;
            }
            Point[] points = tunnel.points();
            for (int i = 0; i < points.length - 1; i++) {
                if (distanceToSegment(x, z, points[i].x(), points[i].z(),
                        points[i + 1].x(), points[i + 1].z())
                        <= Math.max(points[i].halfWidth(), points[i + 1].halfWidth())) {
                    return true;
                }
            }
        }
        for (Chamber chamber : chambers) {
            if (Math.hypot(x - chamber.x(), z - chamber.z()) <= chamber.halfWidth()) {
                return true;
            }
        }
        return false;
    }

    /** 侧隧道（测试/调试用）。 */
    public List<Tunnel> branches() {
        List<Tunnel> out = new ArrayList<>();
        for (Tunnel tunnel : tunnels) {
            if (!tunnel.main()) {
                out.add(tunnel);
            }
        }
        return out;
    }

    /** 独立洞室（测试/调试用）。 */
    public List<Chamber> chambers() {
        return chambers;
    }

    /** 主航道航点（测试/调试用）。 */
    public Point[] points() {
        return main.points().clone();
    }

    /** 所有隧道（测试/调试用）。 */
    public List<Tunnel> tunnels() {
        return tunnels;
    }

    private static double roughness(long seed, double x, double z) {
        return DeepSeaNoise.fbm2(seed ^ SALT_ROUGH, x / 50.0D, z / 50.0D, 2) * 0.10D;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 确定性 [0,1) 随机。 */
    private static double rand01(long seed, int salt) {
        long hash = LevelSelector.mix(seed, salt);
        return ((hash >>> 11) & 0x1FFFFFFFFFFFFFL) / (double) (1L << 53);
    }

    /** 确定性 [min,max] 整数。 */
    private static int randInt(long seed, int min, int max, int salt) {
        if (max <= min) {
            return min;
        }
        return min + (int) Math.floorMod(LevelSelector.mix(seed, salt), (long) (max - min + 1));
    }

    private static double distanceToSegment(double px, double pz,
                                            double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSq = dx * dx + dz * dz;
        if (lengthSq <= 1.0E-6D) {
            return Math.hypot(px - ax, pz - az);
        }
        double t = ((px - ax) * dx + (pz - az) * dz) / lengthSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return Math.hypot(px - (ax + dx * t), pz - (az + dz * t));
    }
}
