package com.opamb.barotrauma.world;

import com.opamb.barotrauma.world.level.LevelParams;
import com.opamb.barotrauma.world.level.LevelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 隧道网络：端点、随机上下/左右游走、椭圆剖面、侧隧道环路连通性、确定性。 */
class RoutePathTest {

    private static final long SEED = 0x5EED1234L;

    @Test
    @DisplayName("航道两端固定在站点，高度 -80")
    void endpointsStayOnSites() {
        LevelParams basic = LevelType.BASIC.params();
        RoutePath path = RoutePath.build(basic, SEED, 700.0D, 0.0D, 0.0D, 0.0D);
        RoutePath.Point first = path.pointAt(0.0D);
        RoutePath.Point last = path.pointAt(1.0D);
        assertEquals(700.0D, first.x(), 1.0E-6D);
        assertEquals(0.0D, first.z(), 1.0E-6D);
        assertEquals(0.0D, last.x(), 1.0E-6D);
        assertEquals(0.0D, last.z(), 1.0E-6D);
        assertEquals(-80.0D, first.y(), 1.0E-6D);
        assertEquals(-80.0D, last.y(), 1.0E-6D);
    }

    @Test
    @DisplayName("主航道像原作一样随机上下跳，同时左右摆动")
    void mainPathWandersUpDownAndSideways() {
        LevelParams basic = LevelType.BASIC.params();
        // 南北向航线：横向摆动落在 x 上。
        RoutePath path = RoutePath.build(basic, SEED, 0.0D, 0.0D, 0.0D, -700.0D);
        RoutePath.Point[] points = path.points();
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        for (RoutePath.Point point : points) {
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y());
            minX = Math.min(minX, point.x());
            maxX = Math.max(maxX, point.x());
        }
        assertTrue(maxY - minY > 100.0D,
                "主航道必须有明显的上下起伏，实际 " + (maxY - minY));
        assertTrue(maxX - minX > 30.0D,
                "主航道必须有明显的左右摆动，实际 " + (maxX - minX));
    }

    @Test
    @DisplayName("航道中心是大洞窟，远离隧道网络是岩体")
    void cavernIsOpenAtCenterAndSolidOutside() {
        LevelParams basic = LevelType.BASIC.params();
        RoutePath path = RoutePath.build(basic, SEED, 0.0D, 0.0D, 0.0D, -700.0D);
        RoutePath.Point mid = path.pointAt(0.5D);

        RoutePath.Open open = path.openAt(mid.x(), mid.z(), SEED);
        assertNotNull(open, "航道中心必须打开");
        assertTrue(open.top() - open.bottom() >= 300.0D,
                "中心洞高应该接近 440，实际 " + (open.top() - open.bottom()));
        assertNull(path.openAt(mid.x() + 600.0D, mid.z(), SEED), "远离隧道网络应是岩");
    }

    @Test
    @DisplayName("侧隧道首尾都接在父隧道上，而且整条中心线都挖通")
    void sideTunnelsAreConnectedLoops() {
        LevelParams maze = LevelType.MAZE.params();
        RoutePath path = RoutePath.build(maze, 42L, 0.0D, 0.0D, 0.0D, -1300.0D);
        assertEquals(maze.shape().sideTunnelMax(), path.branches().size());
        for (RoutePath.Tunnel branch : path.branches()) {
            RoutePath.Point[] points = branch.points();
            assertTrue(points.length >= 3, "侧隧道至少 3 个航点");
            // 首尾必须落在父隧道上：排除 branch 自己再测，否则端点到自身恒为 0。
            // 侧隧道可以挂主航道，也可以挂另一条侧隧道，所以测整个父网络。
            assertTrue(path.distanceToNetworkExcluding(points[0].x(), points[0].z(), branch)
                            < 1.0E-6D,
                    "侧隧道起点必须接在父网络上");
            assertTrue(path.distanceToNetworkExcluding(points[points.length - 1].x(),
                            points[points.length - 1].z(), branch) < 1.0E-6D,
                    "侧隧道终点必须接在父网络上");
            // 反例：branch 中段离父网络有距离，但仍属于隧道网络；
            // 若某个 branch 与父网络脱离，排除自己后的距离会一直 >0。
            RoutePath.Point branchMid = points[points.length / 2];
            assertEquals(0.0D,
                    path.distanceToNetwork(branchMid.x(), branchMid.z()), 1.0E-6D,
                    "branch 中段属于隧道网络");
            // 整条中心线都必须有洞，不能被岩壁截断。
            for (int i = 0; i < points.length - 1; i++) {
                RoutePath.Point a = points[i];
                RoutePath.Point b = points[i + 1];
                for (int step = 0; step <= 10; step++) {
                    double t = step / 10.0D;
                    double x = a.x() + (b.x() - a.x()) * t;
                    double z = a.z() + (b.z() - a.z()) * t;
                    assertNotNull(path.openAt(x, z, SEED),
                            "侧隧道中心线被岩壁截断 @" + Math.round(x) + "," + Math.round(z));
                }
            }
        }
    }

    @Test
    @DisplayName("同种子同端点生成同一条隧道网络")
    void deterministic() {
        LevelParams advanced = LevelType.ADVANCED.params();
        RoutePath a = RoutePath.build(advanced, 7L, 0.0D, 0.0D, 0.0D, -1500.0D);
        RoutePath b = RoutePath.build(advanced, 7L, 0.0D, 0.0D, 0.0D, -1500.0D);
        assertEquals(a.pointAt(0.37D), b.pointAt(0.37D));
        assertEquals(a.chambers(), b.chambers());
        assertEquals(a.branches().size(), b.branches().size());
        assertEquals(a.branches().get(0).points()[2], b.branches().get(0).points()[2]);
    }

    @Test
    @DisplayName("南北向长航道的所有隧道都留在 2048 槽位内")
    void longRouteStaysInSlot() {
        LevelParams advanced = LevelType.ADVANCED.params();
        RoutePath path = RoutePath.build(advanced, 99L, 0.0D, 0.0D, 0.0D, -1500.0D);
        for (RoutePath.Tunnel tunnel : path.tunnels()) {
            for (RoutePath.Point point : tunnel.points()) {
                assertTrue(Math.abs(point.x()) <= 1024.0D,
                        "隧道横向摆动越出槽位：" + point.x());
            }
        }
    }
}
