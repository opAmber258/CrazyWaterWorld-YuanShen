package com.opamb.barotrauma.world.shipyard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipyardShapesTest {

    @Test
    void solidFillAndEstimateMatch() {
        BlockPos a = new BlockPos(2, 5, 10);
        BlockPos b = new BlockPos(-1, 3, 12);
        long estimate = ShipyardShapes.estimateCount(a, b, ShipyardShapes.MODE_FILL_SOLID);
        List<BlockPos> cells = ShipyardShapes.collectCells(a, b, ShipyardShapes.MODE_FILL_SOLID, 4096);
        assertEquals(4 * 3 * 3, estimate);
        assertEquals(estimate, cells.size());
        assertEquals(cells.size(), new HashSet<>(cells).size());
    }

    @Test
    void hollowShellExcludesInteriorAndHandlesThinBoxes() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(3, 3, 3);
        long estimate = ShipyardShapes.estimateCount(a, b, ShipyardShapes.MODE_FILL_HOLLOW);
        List<BlockPos> cells = ShipyardShapes.collectCells(a, b, ShipyardShapes.MODE_FILL_HOLLOW, 4096);
        assertEquals(64 - 8, estimate);
        assertEquals(56, cells.size());
        assertTrue(!cells.contains(new BlockPos(1, 1, 1)));
        assertTrue(!cells.contains(new BlockPos(2, 2, 2)));
        assertTrue(cells.contains(new BlockPos(0, 1, 2)));

        BlockPos thinA = new BlockPos(0, 0, 0);
        BlockPos thinB = new BlockPos(4, 1, 4);
        assertEquals(5 * 2 * 5, ShipyardShapes.estimateCount(thinA, thinB, ShipyardShapes.MODE_FILL_HOLLOW));
        assertEquals(50, ShipyardShapes.collectCells(thinA, thinB, ShipyardShapes.MODE_FILL_HOLLOW, 4096).size());
    }

    @Test
    void wallsModeLeavesTopAndBottomOpen() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(3, 4, 3);
        long estimate = ShipyardShapes.estimateCount(a, b, ShipyardShapes.MODE_FILL_WALLS);
        List<BlockPos> cells = ShipyardShapes.collectCells(a, b, ShipyardShapes.MODE_FILL_WALLS, 4096);
        assertEquals((16 - 4) * 5, estimate);
        assertEquals(estimate, cells.size());
        assertTrue(!cells.contains(new BlockPos(1, 0, 1)));
        assertTrue(!cells.contains(new BlockPos(1, 4, 2)));
        assertTrue(cells.contains(new BlockPos(0, 2, 1)));
    }

    @Test
    void line3dConnectsExactEndpoints() {
        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos b = new BlockPos(6, 5, 1);
        List<BlockPos> cells = ShipyardShapes.collectCells(a, b, ShipyardShapes.MODE_LINE, 4096);
        assertEquals(a, cells.get(0));
        assertEquals(b, cells.get(cells.size() - 1));
        assertEquals(ShipyardShapes.estimateCount(a, b, ShipyardShapes.MODE_LINE), cells.size());

        Set<BlockPos> unique = new HashSet<>(cells);
        assertEquals(cells.size(), unique.size());
        for (int i = 1; i < cells.size(); i++) {
            BlockPos prev = cells.get(i - 1);
            BlockPos curr = cells.get(i);
            assertTrue(Math.abs(curr.getX() - prev.getX()) <= 1);
            assertTrue(Math.abs(curr.getY() - prev.getY()) <= 1);
            assertTrue(Math.abs(curr.getZ() - prev.getZ()) <= 1);
        }
    }

    @Test
    void mirrorPosIsInvolutiveAndCenterlineIdempotent() {
        BlockPos pos = new BlockPos(1000485, 65, 12);
        BlockPos mirroredX = ShipyardShapes.mirrorPos(pos, ShipyardShapes.MIRROR_X, 1000480);
        assertEquals(new BlockPos(1000475, 65, 12), mirroredX);
        assertEquals(pos, ShipyardShapes.mirrorPos(mirroredX, ShipyardShapes.MIRROR_X, 1000480));

        BlockPos onAxisX = new BlockPos(1000480, 65, 12);
        assertEquals(onAxisX, ShipyardShapes.mirrorPos(onAxisX, ShipyardShapes.MIRROR_X, 1000480));

        BlockPos mirroredZ = ShipyardShapes.mirrorPos(pos, ShipyardShapes.MIRROR_Z, 0);
        assertEquals(new BlockPos(1000485, 65, -12), mirroredZ);
        assertEquals(pos, ShipyardShapes.mirrorPos(mirroredZ, ShipyardShapes.MIRROR_Z, 0));
    }

    @Test
    void mirrorForAxisMapsXToFrontBackAndZToLeftRight() {
        assertEquals(Mirror.FRONT_BACK, ShipyardShapes.mirrorForAxis(ShipyardShapes.MIRROR_X));
        assertEquals(Mirror.LEFT_RIGHT, ShipyardShapes.mirrorForAxis(ShipyardShapes.MIRROR_Z));
        assertEquals(Mirror.NONE, ShipyardShapes.mirrorForAxis(ShipyardShapes.MIRROR_NONE));
    }

    @Test
    void intersectGridPlaneHitsYPlaneAndRejectsParallelOrBehind() {
        Vec3 eye = new Vec3(10.0D, 80.0D, 10.0D);
        Vec3 downForward = new Vec3(1.0D, -1.0D, 0.5D).normalize();
        BlockPos hit = ShipyardShapes.intersectGridPlane(eye, downForward, Direction.Axis.Y, 64.0D, 512.0D);
        assertEquals(new BlockPos(26, 64, 18), hit);

        Vec3 parallel = new Vec3(1.0D, 0.0D, 0.0D);
        assertNull(ShipyardShapes.intersectGridPlane(eye, parallel, Direction.Axis.Y, 64.0D, 512.0D));

        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        assertNull(ShipyardShapes.intersectGridPlane(eye, up, Direction.Axis.Y, 64.0D, 512.0D));
    }

    @Test
    void collectCellsRespectsMaxLimit() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(20, 20, 20);
        assertTrue(ShipyardShapes.collectCells(a, b, ShipyardShapes.MODE_FILL_SOLID, 4096).isEmpty());
    }

    @Test
    void footprintOffsetsMatchMultiblockDimensions() {
        List<BlockPos> reactor = ShipyardShapes.footprintOffsets("reactor", Direction.NORTH);
        assertEquals(18, reactor.size());
        assertTrue(reactor.contains(new BlockPos(0, 0, 0)));
        assertTrue(reactor.contains(new BlockPos(1, 2, 1)));
        assertTrue(reactor.contains(new BlockPos(-1, 0, 0)));

        List<BlockPos> engine = ShipyardShapes.footprintOffsets("engine", Direction.EAST);
        assertEquals(18, engine.size());
        assertTrue(engine.contains(new BlockPos(-2, 1, 1)));

        assertEquals(9, ShipyardShapes.footprintOffsets("navigation_terminal", Direction.SOUTH).size());
        assertEquals(6, ShipyardShapes.footprintOffsets("ballast_pump", Direction.WEST).size());
        assertEquals(6, ShipyardShapes.footprintOffsets("fabricator", Direction.NORTH).size());
        assertEquals(3, ShipyardShapes.footprintOffsets("hatch", Direction.NORTH).size());
        assertEquals(2, ShipyardShapes.footprintOffsets("bulkhead_door", Direction.NORTH).size());
        assertEquals(1, ShipyardShapes.footprintOffsets("submarine_plating", Direction.NORTH).size());
    }

    @Test
    void rotateOffsetFourQuarterTurnsReturnsOriginal() {
        BlockPos p = new BlockPos(3, 2, -1);
        BlockPos r1 = ShipyardShapes.rotateOffset(p, 1);
        BlockPos r2 = ShipyardShapes.rotateOffset(r1, 1);
        BlockPos r3 = ShipyardShapes.rotateOffset(r2, 1);
        BlockPos r4 = ShipyardShapes.rotateOffset(r3, 1);
        assertEquals(new BlockPos(1, 2, 3), r1);
        assertEquals(new BlockPos(-3, 2, 1), r2);
        assertEquals(p, r4);
    }

    @Test
    void ellipsoidShellIsHollowAndExcludesCorners() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(8, 6, 8);
        List<BlockPos> shell = ShipyardShapes.collectCells(a, b, ShipyardShapes.MODE_ELLIPSOID, 4096);
        assertTrue(!shell.isEmpty());
        // 中心应为空腔，四角应被切圆。
        assertTrue(!shell.contains(new BlockPos(4, 3, 4)));
        assertTrue(!shell.contains(new BlockPos(0, 0, 0)));
        assertTrue(shell.contains(new BlockPos(4, 0, 4)));
        assertTrue(shell.contains(new BlockPos(0, 3, 4)));
    }
}
