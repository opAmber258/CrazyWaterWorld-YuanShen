package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M1–M4 显式 scan bounds 路径的纯逻辑回归（不需要 Level）。 */
class ShipScanBoundsTest {

    private static final BlockPos CORE = new BlockPos(0, 0, 0);

    private static ShipGeometry.Probe probe(Set<BlockPos> solid) {
        return new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return solid.contains(pos);
            }

            @Override
            public boolean isWater(BlockPos pos) {
                return false;
            }
        };
    }

    private static Set<BlockPos> hollowCube(int half) {
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -half; x <= half; x++) {
            for (int y = -half; y <= half; y++) {
                for (int z = -half; z <= half; z++) {
                    if (Math.abs(x) == half || Math.abs(y) == half || Math.abs(z) == half) {
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return solid;
    }

    private static ShipState stateWith(ShipGeometry.Bounds geometryBounds) {
        ShipGeometry.Result geometry = new ShipGeometry.Result(List.of(), List.of(), 0, 0,
                geometryBounds, Set.of(), List.of(), Set.of());
        return new ShipState(geometry, MachineCount.EMPTY);
    }

    @Test
    @DisplayName("显式 bounds 重载与等价的 radius 重载结果一致")
    void explicitBoundsOverloadMatchesRadiusOverload() {
        Set<BlockPos> solid = hollowCube(4);
        Predicate<BlockPos> isShipBlock = solid::contains;
        ShipGeometry.Probe probe = probe(solid);
        ShipGeometry.Bounds bounds = new ShipGeometry.Bounds(-4, -4, -4, 4, 4, 4);

        ShipGeometry.Result byRadius = ShipGeometry.scanShip(CORE, 4, isShipBlock, probe);
        ShipGeometry.Result byBounds = ShipGeometry.scanShip(bounds, CORE, isShipBlock, probe,
                0, null, pos -> false);

        assertEquals(byRadius.hull(), byBounds.hull());
        assertEquals(byRadius.bounds(), byBounds.bounds());
        assertEquals(byRadius.compartments().size(), byBounds.compartments().size());
        assertEquals(byRadius.totalVolume(), byBounds.totalVolume());
        assertEquals(byRadius.hullBlocks(), byBounds.hullBlocks());
    }

    @Test
    @DisplayName("rescan 的选路：显式 bounds 能扫到半径外的船壳")
    void scanGeometryUsesExplicitBounds() {
        Set<BlockPos> far = new HashSet<>();
        for (int x = 26; x <= 30; x++) {
            far.add(new BlockPos(x, 0, 0));
        }
        Predicate<BlockPos> isShipBlock = far::contains;
        ShipGeometry.Probe probe = probe(far);

        ShipGeometry.Result radiusOnly = ShipCoreBlockEntity.scanGeometry(
                null, CORE, isShipBlock, probe, 0, null, pos -> false);
        assertEquals(0, radiusOnly.hullBlocks(), "核心 ±24 扫不到 x=26..30");

        ShipGeometry.Bounds wide = new ShipGeometry.Bounds(-30, -2, -2, 30, 2, 2);
        ShipGeometry.Result withBounds = ShipCoreBlockEntity.scanGeometry(
                wide, CORE, isShipBlock, probe, 0, null, pos -> false);
        assertEquals(5, withBounds.hullBlocks());
        assertTrue(withBounds.hull().contains(new BlockPos(30, 0, 0)));
    }

    @Test
    @DisplayName("损坏 bounds / 搬船后的旧坐标不会启动逐格循环")
    void invalidBoundsNeverIterate() {
        Set<BlockPos> solid = hollowCube(2);
        AtomicBoolean queried = new AtomicBoolean();
        Predicate<BlockPos> counting = pos -> {
            queried.set(true);
            return solid.contains(pos);
        };

        ShipGeometry.Result corrupt = ShipGeometry.scanShip(
                new ShipGeometry.Bounds(Integer.MIN_VALUE, -1, -1, Integer.MAX_VALUE, 1, 1),
                CORE, counting, probe(solid), 0, null, pos -> false);
        assertEquals(0, corrupt.hullBlocks(), "min/max 溢出的 NBT 必须直接拒绝");
        assertFalse(queried.get(), "不能进入 collectHullWithEquipment 的 for(x++)");

        ShipGeometry.Result stale = ShipGeometry.scanShip(
                new ShipGeometry.Bounds(1_000_440, -5, -5, 1_000_520, 5, 5),
                CORE, counting, probe(solid), 0, null, pos -> false);
        assertEquals(0, stale.hullBlocks(), "与核心相隔百万格的旧坐标必须拒绝");
        assertFalse(queried.get());

        assertFalse(ShipGeometry.validBounds(new ShipGeometry.Bounds(0, 0, 0, -1, 0, 0)));
        assertFalse(ShipGeometry.validBounds(new ShipGeometry.Bounds(0, 0, 0, 1_000, 0, 0)));
        assertFalse(ShipGeometry.validScanArea(
                new ShipGeometry.Bounds(1_000_440, -5, -5, 1_000_520, 5, 5), CORE));

        // 走 rescan 的选路时，陈旧范围要被丢掉并回退到核心 ±RADIUS。
        ShipGeometry.Result fallback = ShipCoreBlockEntity.scanGeometry(
                new ShipGeometry.Bounds(1_000_440, -5, -5, 1_000_520, 5, 5),
                CORE, solid::contains, probe(solid), 0, null, pos -> false);
        assertEquals(solid.size(), fallback.hullBlocks(), "陈旧的显式范围应回退到默认扫描半径");
    }

    @Test
    @DisplayName("collectBlocks 的两参重载走 scanBounds，且四周各扩 1 格")
    void collectBlocksUsesScanBoundsAndPadsOneCell() {
        ShipGeometry.Bounds geometryBounds = new ShipGeometry.Bounds(-2, -2, -2, 2, 2, 2);
        ShipState state = stateWith(geometryBounds);

        assertEquals(geometryBounds, state.collectBounds());
        assertEquals(new ShipGeometry.Bounds(-3, -3, -3, 3, 3, 3),
                ShipState.collectionArea(state.collectBounds()),
                "几何包围盒路径也要保留原来的 +1");

        ShipGeometry.Bounds scan = new ShipGeometry.Bounds(-10, -2, -2, 10, 2, 2);
        state.setScanBounds(scan);
        assertEquals(scan, state.collectBounds(), "两参重载不能再用 geometry.bounds()");
        assertEquals(new ShipGeometry.Bounds(-11, -3, -3, 11, 3, 3),
                ShipState.collectionArea(state.collectBounds()));

        ShipState moved = state.shifted(100, 0, 0);
        assertEquals(scan.shifted(100, 0, 0), moved.scanBounds(), "平移时扫描范围必须跟着船走");
        assertEquals(new ShipGeometry.Bounds(89, -3, -3, 111, 3, 3),
                ShipState.collectionArea(moved.collectBounds()));
    }

    @Test
    @DisplayName("非法的显式收集范围直接拒绝，不会溢出成巨量迭代")
    void invalidCollectionBoundsRejected() {
        ShipState state = stateWith(new ShipGeometry.Bounds(-2, -2, -2, 2, 2, 2));
        state.setScanBounds(new ShipGeometry.Bounds(10, 0, 0, -10, 0, 0));
        assertNull(state.scanBounds());

        assertNull(ShipState.collectionArea(
                new ShipGeometry.Bounds(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0)));
        assertTrue(ShipState.collectionArea(null) == null);
    }
}
