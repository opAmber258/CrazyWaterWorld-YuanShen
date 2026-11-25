package com.opamb.barotrauma.client;

import com.opamb.barotrauma.sonar.SonarPulse;
import com.opamb.barotrauma.sonar.SonarScan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SonarScopeTest {
    private static final float[] FORWARD_Z = {0, 0, 1, 1, 0, 0, 0, 1, 0};
    private static final double CAMERA = 150;
    private static final BlockPos LAUNCH = new BlockPos(1000, -100, -40);

    @Test
    void visibleReturnPicksTheSameWorldBlockAfterTheShipMoves() {
        SonarScope scope = new SonarScope(88);
        SonarPulse pulse = pulse(SonarScan.pack(SonarScan.TYPE_ORE, 12, 0, 48));
        int[] initial = scope.marker(new Vec3(12, 0, 48), FORWARD_Z, CAMERA);
        assertFalse(scope.updateEchoes(List.of(pulse), LAUNCH, CAMERA, FORWARD_Z, 105, Vec3.ZERO, 1.0F, 0.0F));
        assertNull(scope.pick(initial[0], initial[1]));
        assertTrue(scope.updateEchoes(List.of(pulse), LAUNCH, CAMERA, FORWARD_Z, 121, Vec3.ZERO, 1.0F, 0.0F));
        assertEquals(LAUNCH.offset(12, 0, 48), scope.pick(initial[0], initial[1]));

        BlockPos moved = LAUNCH.offset(12, 0, 0);
        assertTrue(scope.updateEchoes(List.of(pulse), moved, CAMERA, FORWARD_Z, 122, Vec3.ZERO, 1.0F, 0.0F));
        assertEquals(LAUNCH.offset(12, 0, 48), scope.pick(0, 0));
        assertNull(scope.pick(initial[0], initial[1]));

        Vec3 liveDelta = new Vec3(7.25, -0.5, 0.0);
        int[] live = scope.marker(new Vec3(12, 0, 48).subtract(liveDelta), FORWARD_Z, CAMERA);
        assertTrue(scope.updateEchoes(List.of(pulse), LAUNCH, CAMERA, FORWARD_Z, 122,
                liveDelta, 1.0F, 0.0F));
        assertEquals(LAUNCH.offset(12, 0, 48), scope.pick(live[0], live[1]));
    }

    @Test
    void expiredAndOutOfScopeReturnsCannotBePicked() {
        SonarScope scope = new SonarScope(88);
        SonarPulse rear = pulse(SonarScan.pack(SonarScan.TYPE_ORE, 0, 0, -240));
        SonarPulse outside = pulse(SonarScan.pack(SonarScan.TYPE_ORE, 96, 0, 0));
        SonarPulse expired = pulse(SonarScan.pack(SonarScan.TYPE_ORE, 0, 0, 10));
        assertFalse(scope.updateEchoes(List.of(rear, outside, expired), LAUNCH, CAMERA, FORWARD_Z, 186,
                Vec3.ZERO, 1.0F, 0.0F));
        assertNull(scope.pick(0, 0));
        assertNull(scope.pick(90, 0));
    }

    @Test
    void draggingAMarkerKeepsItsWorldDepthEvenWhileTheShipMoves() {
        SonarScope scope = new SonarScope(102);
        BlockPos target = LAUNCH.offset(0, 0, 30);
        BlockPos moved = LAUNCH.offset(8, 5, 12);
        BlockPos first = scope.dragTarget(target, LAUNCH, 40, -18, FORWARD_Z, CAMERA);
        BlockPos afterMove = scope.dragTarget(target, moved, 40, -18, FORWARD_Z, CAMERA);
        assertNotNull(first);
        assertNotNull(afterMove);
        assertTrue(first.getX() > target.getX());
        assertTrue(first.getY() > target.getY());
        assertEquals(target.getZ(), first.getZ());
        assertEquals(target.getZ(), afterMove.getZ());
    }

    @Test
    void hullProjectionAppliesAssemblyRotationAndOffset() {
        SonarScope scope = new SonarScope(121);
        int[] hull = {SonarScan.pack(SonarScan.TYPE_HULL, 0, 0, 20)};
        List<SonarScope.Segment> unrotated = scope.hullEdges(hull, Vec3.ZERO,
                new Quaternionf(), FORWARD_Z, CAMERA);
        assertFalse(unrotated.isEmpty());
        assertTrue(unrotated.stream().allMatch(edge -> Math.abs(edge.x1()) <= 1));

        List<SonarScope.Segment> rotated = scope.hullEdges(hull, new Vec3(5, 7, 0),
                new Quaternionf().rotationY((float) (Math.PI / 2)), FORWARD_Z, CAMERA);
        assertFalse(rotated.isEmpty());
        assertTrue(rotated.stream().allMatch(edge -> edge.x1() > 28 && edge.y1() < -6));
    }

    @Test
    void hullEdgesCrossingTheScopeAreClippedEvenWithBothCornersOutside() {
        SonarScope scope = new SonarScope(60);
        int[] beam = new int[201];
        for (int i = 0; i < beam.length; i++) {
            beam[i] = SonarScan.pack(SonarScan.TYPE_HULL, i - 100, 0, 0);
        }
        List<SonarScope.Segment> edges = scope.hullEdges(beam, Vec3.ZERO,
                new Quaternionf(), FORWARD_Z, 30);
        assertTrue(edges.stream().anyMatch(edge -> Math.abs(edge.x2() - edge.x1()) >= 110));
        assertTrue(edges.stream().allMatch(edge -> Math.hypot(edge.x1(), edge.y1()) <= 59
                && Math.hypot(edge.x2(), edge.y2()) <= 59));
    }

    @Test
    void disconnectedHullBlocksAreNotJoinedAcrossEmptySpace() {
        SonarScope scope = new SonarScope(60);
        int[] hull = {SonarScan.pack(SonarScan.TYPE_HULL, -8, 0, 0),
                SonarScan.pack(SonarScan.TYPE_HULL, 8, 0, 0)};
        List<SonarScope.Segment> edges = scope.hullEdges(hull, Vec3.ZERO,
                new Quaternionf(), FORWARD_Z, 30);

        assertTrue(edges.stream().anyMatch(edge -> edge.x1() < -20));
        assertTrue(edges.stream().anyMatch(edge -> edge.x1() > 20));
        assertTrue(edges.stream().allMatch(edge -> Math.abs(edge.x1()) > 20 && Math.abs(edge.x2()) > 20
                && Integer.signum(edge.x1()) == Integer.signum(edge.x2())));
    }

    @Test
    void conningTowerKeepsItsRecessedDeckWithoutInternalBlockSeams() {
        SonarScope scope = new SonarScope(60);
        List<Integer> blocks = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            blocks.add(SonarScan.pack(SonarScan.TYPE_HULL, x, 0, 0));
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                blocks.add(SonarScan.pack(SonarScan.TYPE_HULL, x, y, 0));
            }
        }
        List<SonarScope.Segment> edges = scope.hullEdges(blocks.stream().mapToInt(Integer::intValue).toArray(),
                Vec3.ZERO, new Quaternionf(), FORWARD_Z, 30);

        assertTrue(edges.stream().anyMatch(edge -> edge.y1() == -2 && edge.y2() == -2
                && edge.x1() <= 8 && edge.x2() >= 12));
        assertTrue(edges.stream().anyMatch(edge -> edge.y1() <= -10 && edge.y2() <= -10));
        assertTrue(edges.stream().noneMatch(edge -> edge.x1() == 0 && edge.x2() == 0));
        assertTrue(edges.stream().noneMatch(edge -> edge.y1() == -2 && edge.y2() == -2
                && edge.x1() <= 0 && edge.x2() >= 0));
    }

    @Test
    void aMarkerBehindTheCameraKeepsItsScreenSideAtTheRim() {
        SonarScope scope = new SonarScope(121);
        SonarScope.NavMarker rear = scope.navigationMarker(new Vec3(18, 0, -180), FORWARD_Z, CAMERA, false);
        assertEquals(new SonarScope.NavMarker(114, 0, true), rear);
        SonarScope.NavMarker ahead = scope.navigationMarker(new Vec3(18, 0, 20), FORWARD_Z, CAMERA, false);
        assertFalse(ahead.onRim());
        assertEquals(scope.marker(new Vec3(18, 0, 20), FORWARD_Z, CAMERA)[0], ahead.x());
    }

    @Test
    void stationUsesHorizontalBearingInsteadOfOrbitProjection() {
        SonarScope scope = new SonarScope(121);
        Vec3 north = new Vec3(0, -72, 420);
        assertEquals(new SonarScope.NavMarker(0, -114, true), scope.stationMarker(north, -1, 0, 96));
        assertEquals(new SonarScope.NavMarker(0, 114, true), scope.stationMarker(north, 1, 0, 96));
        assertFalse(scope.stationMarker(new Vec3(0, -2, 20), -1, 0, 96).onRim());
    }

    @Test
    void blipGradientUsesTheOriginalColorStops() {
        assertEquals(0xFFFFFFFF, SonarScope.blipColor(1.0F));
        assertEquals(0xFF029F1E, SonarScope.blipColor(0.75F));
        assertEquals(0xFF0085A6, SonarScope.blipColor(0.5F));
        assertEquals(0xFF0032A0, SonarScope.blipColor(0.25F));
        assertEquals(0x00000000, SonarScope.blipColor(0.0F));
    }

    @Test
    void waypointNameFitsInsideTheCircleAtEveryBearing() {
        SonarScope scope = new SonarScope(121);
        for (int radius : new int[]{0, 114, 118}) {
            for (int degrees = 0; degrees < 360; degrees += 5) {
                double angle = Math.toRadians(degrees);
                int x = (int) Math.round(radius * Math.cos(angle));
                int y = (int) Math.round(radius * Math.sin(angle));
                SonarScope.LabelPosition label = scope.waypointLabel(new SonarScope.NavMarker(x, y, radius == 114),
                        125, 20);
                int outerX = Math.max(Math.abs(label.x()), Math.abs(label.x() + 125));
                int outerY = Math.max(Math.abs(label.y()), Math.abs(label.y() + 20));
                assertTrue(outerX * outerX + outerY * outerY <= 118 * 118);
                assertTrue(y <= 0 ? label.y() >= y + 5 : label.y() + 20 <= y - 5);
            }
        }
    }

    @Test
    void directionalReticleStaysUnderTheMouseWhenAimingOffCenter() {
        SonarScope scope = new SonarScope(121);
        Vec3 target = scope.aimDirection(80, -50, FORWARD_Z, CAMERA);
        assertArrayEquals(new int[]{80, -50}, scope.aimMarker(target, FORWARD_Z, CAMERA));
        assertArrayEquals(new int[]{80, -50}, scope.aimMarker(target, FORWARD_Z, CAMERA / 2));
    }

    private static SonarPulse pulse(int point) {
        return new SonarPulse(100, LAUNCH, 100, SonarScan.Mode.ACTIVE, new Vec3(0, 0, 1), new int[]{point});
    }
}
