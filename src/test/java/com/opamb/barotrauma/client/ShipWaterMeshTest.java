package com.opamb.barotrauma.client;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipWaterMeshTest {
    private static final ShipWaterMesh.Opening OPEN = (x, z, nextX, nextZ, low, high) -> true;

    @Test
    void longBoxSideIsClippedOnlyWhereTheShortNeighborTouches() {
        float[] boxes = {
                0, 0, 0, 2, 2, 0, 1.2F,
                0, 0, 1, 0, 2, 1, 1.2F
        };
        List<Vertex> vertices = mesh(boxes, OPEN, 0, new float[0]);
        int walls = 0;
        for (int i = 0; i < vertices.size(); i += 3) {
            List<Vertex> triangle = vertices.subList(i, i + 3);
            if (triangle.stream().allMatch(vertex -> vertex.point.z == 1.0F)) {
                assertTrue(triangle.stream().allMatch(vertex -> vertex.point.x >= 1.0F));
                walls++;
            }
        }
        assertEquals(4, walls);
    }

    @Test
    void rimOfTheWallUsesTheExactSurfaceVertices() {
        List<Vertex> vertices = mesh(new float[]{0, 0, 0, 0, 2, 0, 1.2F}, OPEN, 0, new float[0]);
        Set<Point> top = new HashSet<>();
        for (Vertex vertex : vertices.subList(0, 6)) {
            top.add(vertex.point);
        }
        Set<Point> sideRim = new HashSet<>();
        for (Vertex vertex : vertices.subList(12, vertices.size())) {
            if (vertex.belowSurface == 0.0F) {
                sideRim.add(vertex.point);
            }
        }
        assertEquals(4, top.size());
        assertEquals(top, sideRim);
    }

    @Test
    void verticallyTouchingBoxesHaveNoInteriorTopOrBottom() {
        float[] boxes = {
                0, 0, 0, 0, 0, 0, 1,
                0, 1, 0, 0, 3, 0, 2.2F
        };
        List<Vertex> vertices = mesh(boxes, OPEN, 0, new float[0]);
        assertEquals(12 * 3, vertices.size());
        assertFalse(vertices.stream().anyMatch(vertex -> vertex.point.y == 1.0F));
    }

    @Test
    void openingJoinsNearWaterlinesAndKeepsRealDrops() {
        float[] boxes = {
                0, 0, 0, 0, 3, 0, 1.0F,
                1, 0, 0, 1, 3, 0, 1.08F
        };
        assertEquals(2, doorRim(mesh(boxes, OPEN, 0, new float[0])).size());
        assertEquals(4, doorRim(mesh(boxes, (x, z, nx, nz, low, high) -> false, 0, new float[0])).size());
        boxes[13] = 1.75F;
        assertEquals(4, doorRim(mesh(boxes, OPEN, 0, new float[0])).size());
    }

    @Test
    void waterfallExcitesTheReceivingDeckOnly() {
        float[] boxes = {
                0, 0, 0, 0, 2, 0, 0.9F,
                0, 3, 0, 0, 5, 0, 4
        };
        float step = 1.0F / 60.0F;
        List<Vertex> calm = mesh(boxes, OPEN, step, new float[0]);
        List<Vertex> impact = mesh(boxes, OPEN, step, new float[]{0.5F, 0.9F, 0.5F, 0.08F});
        assertTrue(impact.get(0).point.y < calm.get(0).point.y);
        assertEquals(calm.stream().filter(vertex -> vertex.point.y >= 3).toList(),
                impact.stream().filter(vertex -> vertex.point.y >= 3).toList());
    }

    @Test
    void cameraContainmentTestsTheActualWaterColumnAndDeck() {
        ShipWaterMesh water = new ShipWaterMesh();
        water.update(new float[]{
                0, 0, 0, 0, 1, 0, 0.005F,
                0, 3, 0, 0, 4, 0, 3.8F
        }, 0.0F, 0.0F, new float[0], 0, OPEN);

        assertTrue(water.contains(0.5D, 0.002D, 0.5D));
        assertFalse(water.contains(0.5D, 0.25D, 0.5D));
        assertFalse(water.contains(0.5D, 2.9D, 0.5D));
        assertTrue(water.contains(0.5D, 3.1D, 0.5D));
        assertFalse(water.contains(1.0D, 3.1D, 0.5D));
        assertFalse(water.contains(0.5D, 4.0D, 0.5D));
    }

    @Test
    void openingFlowUsesTheVisualLandingHeightAndHidesSubmergedJets() {
        ShipWaterMesh water = new ShipWaterMesh();
        water.update(new float[]{0, 0, 0, 1, 2, 0, 0.45F}, 0.0F, 0.0F, new float[0], 0, OPEN);
        float surface = water.surfaceNear(0.5D, 0.5D, 0.55D, 0.55D);
        ShipWaterFlowMesh waterfall = new ShipWaterFlowMesh(water,
                new float[]{0.5F, 2.0F, 0.5F, 0.5F, 1.0F, 0.5F, 0.5F,
                        0.5F, 1.0F, 0.5F, 0.5F, 0.55F, 0.5F, 0.5F},
                new float[0], 0.0F, mouth -> Direction.Axis.X);
        List<Vertex> vertices = new ArrayList<>();
        int triangles = waterfall.emit((x, y, z, thickness, strength) ->
                vertices.add(new Vertex(new Point(x, y, z), thickness)));
        assertEquals(triangles * 3, vertices.size());
        assertTrue(vertices.stream().allMatch(vertex -> vertex.belowSurface < 0.0F));
        assertEquals(surface - 0.020F,
                vertices.stream().map(Vertex::point).map(Point::y).min(Float::compare).orElseThrow(), 0.0001F);

        ShipWaterFlowMesh submerged = new ShipWaterFlowMesh(water, new float[0],
                new float[]{1.0F, 0.0F, 0.5F, 1.0F, 0.0F, 0.5F, 0.6F, 0.4F}, 0.0F, mouth -> null);
        assertEquals(0, submerged.emit((x, y, z, thickness, strength) -> {
            throw new AssertionError("A flow between submerged cells has no exposed interface");
        }));
    }

    @Test
    void strongFallUsesTheHatchWidthAndHasMovingEdges() {
        float[] fall = {0.5F, 3.0F, 0.5F, 0.5F, 0.0F, 0.5F, 1.0F};
        List<Vertex> first = flow(fall, new float[0], 0.0F, Direction.Axis.Z);
        List<Vertex> mouth = first.stream().filter(v -> v.point.y == 3.0F).toList();
        assertTrue(mouth.stream().allMatch(v -> v.point.x == 0.5F));
        assertTrue(mouth.stream().anyMatch(v -> v.point.z < 0.10F));
        assertTrue(mouth.stream().anyMatch(v -> v.point.z > 0.90F));
        assertTrue(mouth.stream().anyMatch(v -> v.point.z == 0.5F));
        assertTrue(mouth.stream().anyMatch(v -> v.point.z == 0.5F && v.belowSurface < -0.20F));
        assertTrue(first.stream().anyMatch(v -> v.point.y == 0.0F));
        assertFalse(first.equals(flow(fall, new float[0], 0.16F, Direction.Axis.Z)));
        fall[6] = 0.06F;
        assertTrue(flow(fall, new float[0], 0.0F, Direction.Axis.Z).isEmpty());
    }

    @Test
    void sideOpeningSpreadsInFlowDirectionAndSettlesAtTheReceiver() {
        float[] side = {0.5F, 0.0F, 0.5F, 1.0F, 0.0F, 0.9F, 0.18F, 1.0F};
        List<Vertex> vertices = flow(new float[0], side, 0.0F, null);
        List<Vertex> mouth = vertices.stream().filter(v -> Math.abs(v.point.x - 0.34F) < 0.00001F).toList();
        assertFalse(mouth.isEmpty());
        assertTrue(mouth.stream().allMatch(v -> Math.abs(v.point.z - 0.5F) <= 0.38F));
        assertTrue(vertices.stream().anyMatch(v -> v.point.x > 1.2F
                && v.point.y < 0.23F && Math.abs(v.point.z - 0.5F) > 0.4F));
        assertTrue(vertices.stream().allMatch(v -> v.point.y > 0.17F));
    }

    private static List<Vertex> flow(float[] streams, float[] sideFlows, float time, Direction.Axis axis) {
        ShipWaterFlowMesh mesh = new ShipWaterFlowMesh(new ShipWaterMesh(), streams, sideFlows, time, mouth -> axis);
        List<Vertex> vertices = new ArrayList<>();
        int triangles = mesh.emit((x, y, z, thickness, strength) ->
                vertices.add(new Vertex(new Point(x, y, z), thickness)));
        assertEquals(triangles * 3, vertices.size());
        return vertices;
    }

    private static Set<Point> doorRim(List<Vertex> vertices) {
        Set<Point> points = new HashSet<>();
        for (Vertex vertex : vertices) {
            if (vertex.point.x == 1.0F && vertex.belowSurface == 0.0F) {
                points.add(vertex.point);
            }
        }
        return points;
    }

    private static List<Vertex> mesh(float[] boxes, ShipWaterMesh.Opening opening, float dt, float[] impacts) {
        ShipWaterMesh mesh = new ShipWaterMesh();
        mesh.update(boxes, dt, 0.0F, impacts, impacts.length, opening);
        List<Vertex> vertices = new ArrayList<>();
        int triangles = mesh.emit((x, y, z, below, strength) -> vertices.add(new Vertex(new Point(x, y, z), below)));
        assertEquals(triangles * 3, vertices.size());
        return vertices;
    }

    private record Point(float x, float y, float z) {
    }

    private record Vertex(Point point, float belowSurface) {
    }
}
