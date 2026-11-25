package com.opamb.barotrauma.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.opamb.barotrauma.sonar.ClientSonarCache;
import com.opamb.barotrauma.sonar.SonarPulse;
import com.opamb.barotrauma.sonar.SonarScan;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

/** Projection and visible returns for the 3D scope. Target picking reads the displayed returns. */
final class SonarScope {

    private static final int[] DEFAULT_GRADIENT = {0x00000000, 0xFF0032A0, 0xFF0085A6, 0xFF029F1E, 0xFFFFFFFF};
    private static final double NEAR = 2.0D;
    private final int field;
    private final float focal;
    private final Echo[] cells;
    private int[] lastHull;
    private Vec3 lastOffset;
    private Quaternionfc lastRotation;
    private float[] lastView;
    private double lastCamera;
    private List<Segment> outline = List.of();
    private static final class DrawBuffer {
        private static final BufferBuilder INSTANCE = new BufferBuilder(32_768);
    }

    private record Projected(float x, float y, double depth) {}

    private record Echo(BlockPos world, int type, float x, float y, float strength, double depth) {
        double priority() {
            return switch (type) {
                case SonarScan.TYPE_ORE -> 3.0 + strength;
                case SonarScan.TYPE_CONTACT -> 2.0 + strength;
                default -> strength;
            };
        }
    }

    SonarScope(int field) {
        this.field = field;
        this.focal = field * 0.95F / 0.57735F;
        this.cells = new Echo[(field + 1) * (field + 1)];
    }

    void clear() {
        Arrays.fill(cells, null);
    }

    private Projected project(double x, double y, double z, float[] basis, double camera) {
        double depth = camera + x * basis[0] + y * basis[1] + z * basis[2];
        if (depth < NEAR) {
            return null;
        }
        return new Projected((float) ((x * basis[3] + y * basis[4] + z * basis[5]) * focal / depth),
                (float) (-(x * basis[6] + y * basis[7] + z * basis[8]) * focal / depth),
                depth);
    }

    record NavMarker(int x, int y, boolean onRim) {}
    record LabelPosition(int x, int y) {}
    record Segment(int x1, int y1, int x2, int y2) {}
    private record Point(double x, double y) {}
    private record CameraPoint(double x, double y, double depth) {}

    NavMarker navigationMarker(Vec3 offset, float[] view, double camera, boolean beyondRange) {
        CameraPoint pos = cameraPoint(offset.x, offset.y, offset.z, view, camera);
        double length = Math.hypot(pos.x, pos.y);
        double radius = field - 7.0D;
        if (!beyondRange && pos.depth >= NEAR && length <= (field - 3.0D) * pos.depth / focal) {
            return new NavMarker((int) Math.round(pos.x * focal / pos.depth),
                    (int) Math.round(pos.y * focal / pos.depth), false);
        }
        if (length < 1.0E-5D) {
            return new NavMarker(0, (int) (pos.depth < NEAR ? radius : -radius), true);
        }
        return new NavMarker((int) Math.round(radius * pos.x / length),
                (int) Math.round(radius * pos.y / length), true);
    }

    /** Navigation destinations share the horizontal compass, independent of orbit pitch. */
    NavMarker stationMarker(Vec3 offset, double rightX, double rightZ, double range) {
        double side = offset.x * rightX + offset.z * rightZ;
        double ahead = offset.x * rightZ - offset.z * rightX;
        double length = Math.hypot(side, ahead);
        if (length < 1.0E-6D) {
            ahead = offset.y;
            length = Math.abs(ahead);
        }
        double distance = offset.length();
        double scale = (field - 7.0D) * Math.min(1.0D, distance / range) / Math.max(1.0E-6D, length);
        return new NavMarker((int) Math.round(side * scale), (int) Math.round(-ahead * scale), distance > range);
    }

    /** Place the whole label inside the circle, moving it away from the nearest edge. */
    LabelPosition waypointLabel(NavMarker marker, int width, int height) {
        int radius = field - 3;
        int halfWidth = (width + 1) / 2;
        int yLimit = (int) Math.sqrt(radius * radius - halfWidth * halfWidth);
        int top = marker.y() <= 0 ? marker.y() + 5 : marker.y() - 5 - height;
        top = Math.max(-yLimit, Math.min(yLimit - height, top));
        int outerY = Math.max(Math.abs(top), Math.abs(top + height));
        int halfSpan = (int) Math.sqrt(radius * radius - outerY * outerY);
        int left = Math.max(-halfSpan, Math.min(halfSpan - width, marker.x() - width / 2));
        return new LabelPosition(left, top);
    }

    /** Draw only the boundary of the projected hull blocks, preserving gaps and steps. */
    List<Segment> hullEdges(int[] points, Vec3 offset, Quaternionfc rotation, float[] view, double camera) {
        if (camera == lastCamera && offset.equals(lastOffset) && rotation.equals(lastRotation)
                && Arrays.equals(view, lastView) && Arrays.equals(points, lastHull)) {
            return outline;
        }
        lastHull = points;
        lastOffset = offset;
        lastRotation = rotation;
        lastView = view;
        lastCamera = camera;

        int size = 2 * field;
        BitSet occupied = new BitSet(size * size);
        List<Point> projected = new ArrayList<>(20);
        List<Point> blockOutline = new ArrayList<>(20);
        CameraPoint[] corners = new CameraPoint[8];
        CameraPoint[] cornerOffsets = new CameraPoint[8];
        Vector3f rotated = new Vector3f();
        for (int i = 0; i < corners.length; i++) {
            rotated.set(((i & 1) == 0 ? -0.5F : 0.5F),
                    ((i & 2) == 0 ? -0.5F : 0.5F), ((i & 4) == 0 ? -0.5F : 0.5F));
            rotation.transform(rotated);
            cornerOffsets[i] = cameraPoint(rotated.x, rotated.y, rotated.z, view, 0.0D);
        }
        for (int block : points) {
            projected.clear();
            rotated.set(SonarScan.x(block), SonarScan.y(block), SonarScan.z(block));
            rotation.transform(rotated);
            CameraPoint center = cameraPoint(rotated.x + offset.x, rotated.y + offset.y,
                    rotated.z + offset.z, view, camera);
            double front = Double.NEGATIVE_INFINITY;
            double back = Double.POSITIVE_INFINITY;
            for (int i = 0; i < corners.length; i++) {
                CameraPoint delta = cornerOffsets[i];
                CameraPoint corner = new CameraPoint(center.x + delta.x, center.y + delta.y,
                        center.depth + delta.depth);
                corners[i] = corner;
                front = Math.max(front, corner.depth);
                back = Math.min(back, corner.depth);
                if (corner.depth >= NEAR) {
                    projected.add(toScreen(corner));
                }
            }
            if (back < NEAR && front >= NEAR) {
                // Intersect the 12 voxel edges with the camera near plane.
                for (int i = 0; i < corners.length; i++) {
                    for (int axis = 1; axis <= 4; axis <<= 1) {
                        if ((i & axis) == 0) {
                            CameraPoint a = corners[i];
                            CameraPoint b = corners[i | axis];
                            if ((a.depth < NEAR) != (b.depth < NEAR)) {
                                double t = (NEAR - a.depth) / (b.depth - a.depth);
                                projected.add(toScreen(new CameraPoint(a.x + (b.x - a.x) * t,
                                        a.y + (b.y - a.y) * t, NEAR)));
                            }
                        }
                    }
                }
            }
            if (projected.size() >= 3) {
                blockOutline.clear();
                traceBlockOutline(projected, blockOutline);
                coverProjection(blockOutline, occupied, size);
            }
        }
        List<Segment> edges = new ArrayList<>();
        traceMaskEdges(occupied, size, true, edges);
        traceMaskEdges(occupied, size, false, edges);
        outline = List.copyOf(edges);
        return outline;
    }

    /** Cover pixel centers in the projection of this single block. Shared coverage creates no seam. */
    private void coverProjection(List<Point> polygon, BitSet occupied, int size) {
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Point point : polygon) {
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }
        int fromRow = Math.max(0, (int) Math.ceil(minY + field - 0.5D));
        int toRow = Math.min(size, (int) Math.ceil(maxY + field - 0.5D));
        for (int row = fromRow; row < toRow; row++) {
            double y = row - field + 0.5D;
            double left = Double.POSITIVE_INFINITY;
            double right = Double.NEGATIVE_INFINITY;
            Point a = polygon.get(polygon.size() - 1);
            for (Point b : polygon) {
                if ((y >= a.y) != (y >= b.y)) {
                    double x = a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
                a = b;
            }
            int from = Math.max(0, (int) Math.ceil(left + field - 0.5D));
            int to = Math.min(size, (int) Math.ceil(right + field - 0.5D));
            if (from < to) {
                occupied.set(row * size + from, row * size + to);
            }
        }
    }

    /** Merge adjacent pixel edges and then clip the resulting lines to the scope. */
    private void traceMaskEdges(BitSet occupied, int size, boolean horizontal, List<Segment> edges) {
        for (int across = 1; across < size; across++) {
            int start = -1;
            for (int along = 0; along <= size; along++) {
                boolean boundary = false;
                if (along < size) {
                    int index = horizontal ? across * size + along : along * size + across;
                    boundary = occupied.get(index) != occupied.get(index - (horizontal ? size : 1));
                }
                if (boundary && start < 0) {
                    start = along;
                } else if (!boundary && start >= 0) {
                    Point a = horizontal ? new Point(start - field, across - field)
                            : new Point(across - field, start - field);
                    Point b = horizontal ? new Point(along - field, across - field)
                            : new Point(across - field, along - field);
                    Segment segment = clipEdge(a, b, field - 2);
                    if (segment != null) {
                        edges.add(segment);
                    }
                    start = -1;
                }
            }
        }
    }

    private static CameraPoint cameraPoint(double x, double y, double z, float[] view, double camera) {
        return new CameraPoint(x * view[3] + y * view[4] + z * view[5],
                -(x * view[6] + y * view[7] + z * view[8]),
                camera + x * view[0] + y * view[1] + z * view[2]);
    }

    private Point toScreen(CameraPoint point) {
        return new Point(point.x * focal / point.depth, point.y * focal / point.depth);
    }

    private static void traceBlockOutline(List<Point> points, List<Point> hull) {
        points.sort(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y));
        for (Point point : points) {
            appendCorner(hull, point, 0);
        }
        int lower = hull.size() - 1;
        for (int i = points.size() - 2; i >= 0; i--) {
            appendCorner(hull, points.get(i), lower);
        }
        hull.remove(hull.size() - 1);
    }

    private static void appendCorner(List<Point> hull, Point next, int start) {
        while (hull.size() >= start + 2) {
            Point a = hull.get(hull.size() - 2);
            Point b = hull.get(hull.size() - 1);
            double turn = (b.x - a.x) * (next.y - a.y) - (b.y - a.y) * (next.x - a.x);
            if (turn > 0.0D) {
                break;
            }
            hull.remove(hull.size() - 1);
        }
        hull.add(next);
    }

    private static Segment clipEdge(Point a, Point b, double radius) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 1.0E-10D) {
            return null;
        }
        // Find the interval on the line within the display circle, then intersect with the edge.
        double mid = -(a.x * dx + a.y * dy) / lengthSquared;
        double cross = a.x * dy - a.y * dx;
        double reach = radius * radius - cross * cross / lengthSquared;
        if (reach < 0.0D) {
            return null;
        }
        double half = Math.sqrt(reach / lengthSquared);
        double start = Math.max(0.0D, mid - half);
        double end = Math.min(1.0D, mid + half);
        if (start >= end) {
            return null;
        }
        return new Segment((int) Math.round(a.x + dx * start), (int) Math.round(a.y + dy * start),
                (int) Math.round(a.x + dx * end), (int) Math.round(a.y + dy * end));
    }

    void drawEchoes(GuiGraphics graphics, int cx, int cy, double camera, float[] basis, double now, Vec3 delta, float zoom) {
        if (!ClientSonarCache.online()) {
            clear();
            return;
        }
        if (!updateEchoes(ClientSonarCache.pulses(), ClientSonarCache.center(), camera, basis, now,
                delta, zoom, ClientSonarCache.distortion())) {
            return;
        }
        graphics.flush();
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder buffer = DrawBuffer.INSTANCE;
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (Echo echo : cells) {
            if (echo != null) {
                drawEcho(pose, cx, cy, echo, zoom, now);
            }
        }
        BufferBuilder.RenderedBuffer rendered = buffer.end();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(rendered);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    boolean updateEchoes(Iterable<SonarPulse> pulses, BlockPos center, double camera,
                         float[] basis, double now, Vec3 delta, float zoom, float distortion) {
        clear();
        boolean visible = false;
        for (SonarPulse pulse : pulses) {
            if (now - pulse.tick() >= SonarPulse.HISTORY_TICKS) {
                continue;
            }
            for (int packed : pulse.points()) {
                float timer = pulse.fadeTimer(packed, now, distortion);
                if (timer <= 0.0F) {
                    continue;
                }
                int x = pulse.origin().getX() + SonarScan.x(packed);
                int y = pulse.origin().getY() + SonarScan.y(packed);
                int z = pulse.origin().getZ() + SonarScan.z(packed);
                int type = SonarScan.type(packed);
                Projected pos = project(x - center.getX() - delta.x, y - center.getY() - delta.y,
                        z - center.getZ() - delta.z, basis, camera);
                if (pos == null || !inside(pos.x, pos.y, field - 4)) {
                    continue;
                }
                BlockPos world = new BlockPos(x, y, z);
                visible |= putEcho(world, type, pos.x, pos.y, timer, pos.depth);
                if (type != SonarScan.TYPE_TERRAIN) {
                    continue;
                }
                float initial = pulse.terrainSignal(packed);
                float radialFade = 1.0F - (float) (pulse.distance(packed) / pulse.range());
                float elapsed = initial * radialFade - timer;
                float tail = Math.min(initial - 0.5F, 1.5F);
                float radius = (float) Math.hypot(pos.x, pos.y);
                if (radius < 1.0F) {
                    continue;
                }
                // Use the voxel hit face as the line tangent for the original 3–6 px radial spacing.
                float unit = 2.0F * field / 700.0F / zoom;
                float step = 3.0F * (1.0F + wallTangentDot(packed)) * unit;
                for (float zStep = step; radius + zStep < field - 4 && tail > 0.0F; zStep += step) {
                    float remaining = tail * radialFade - elapsed;
                    if (remaining <= 0.0F) {
                        break;
                    }
                    float stretch = 1.0F + zStep / radius;
                    visible |= putEcho(world, type, pos.x * stretch, pos.y * stretch, remaining, pos.depth);
                    tail -= 0.1F;
                    step += 0.5F * unit;
                }
            }
        }
        return visible;
    }

    private static float wallTangentDot(int packed) {
        int x = SonarScan.x(packed), y = SonarScan.y(packed), z = SonarScan.z(packed);
        int perpendicular = switch (SonarScan.echoFaceAxis(packed)) {
            case 1 -> x;
            case 2 -> y;
            case 3 -> z;
            default -> Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        };
        double lengthSquared = x * x + y * y + z * z;
        return lengthSquared > 0.0D ? (float) Math.sqrt(1.0D - perpendicular * perpendicular / lengthSquared) : 0.0F;
    }

    private boolean putEcho(BlockPos world, int type, float x, float y, float timer, double depth) {
        float strength = Math.min(1.0F, timer * 0.5F);
        int col = (int) (x + field) / 2;
        int row = (int) (y + field) / 2;
        int index = row * (field + 1) + col;
        Echo echo = new Echo(world, type, x, y, strength, depth);
        Echo previous = cells[index];
        if (previous == null || echo.priority() > previous.priority()) {
            cells[index] = echo;
            return true;
        }
        return false;
    }

    static int blipColor(float strength) {
        float position = Math.max(0.0F, Math.min(1.0F, strength)) * (DEFAULT_GRADIENT.length - 1);
        int slot = Math.min((int) position, DEFAULT_GRADIENT.length - 2);
        int from = DEFAULT_GRADIENT[slot], to = DEFAULT_GRADIENT[slot + 1];
        float blend = position - slot;
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 255, b = (to >>> shift) & 255;
            result |= (int) (a + (b - a) * blend) << shift;
        }
        return result;
    }

    private void drawEcho(Matrix4f pose, int cx, int cy, Echo echo, float zoom, double now) {
        float x = cx + echo.x, y = cy + echo.y;
        if (echo.type == SonarScan.TYPE_ORE) {
            outline(pose, x, y, 3.0F, 255, 210, 95, Math.min(255, Math.round(255 * echo.strength) + 42));
            return;
        }
        int color = blipColor(echo.strength);
        int r = color >>> 16 & 255, g = color >>> 8 & 255, b = color & 255, alpha = color >>> 24;
        float radial = (float) Math.hypot(echo.x, echo.y);
        float ux = radial > 0.0F ? echo.x / radial : 1.0F;
        float uy = radial > 0.0F ? echo.y / radial : 0.0F;
        // Original brush is 64 px wide and is drawn at (strength+3) × scale × 0.08 × (rect/700).
        float scale = (echo.strength + 3.0F) * (1.0F + radial / field)
                * 0.08F * (float) Math.sqrt(zoom) * ((2.0F * field / 0.8F) / 700.0F);
        float radius = 32.0F * scale;
        drawBlipBrush(pose, x, y, ux * radius * 0.25F, uy * radius * 0.25F,
                -uy * radius * 0.5F, ux * radius * 0.5F, r, g, b, alpha);
        // The original adds a second, weaker brush with small lateral jitter.
        int seed = echo.world.hashCode() ^ (int) (now * 4.0D);
        float spread = ((seed * 0x9e3779b9 >>> 24) / 127.5F - 1.0F) * scale;
        drawBlipBrush(pose, x - uy * spread, y + ux * spread, radius, 0.0F,
                0.0F, radius, r, g, b, alpha / 2);
    }

    private void drawBlipBrush(Matrix4f pose, float x, float y, float ax, float ay, float bx, float by,
                               int r, int g, int b, int alpha) {
        softTriangle(pose, x, y, ax, ay, bx, by, r, g, b, alpha);
        softTriangle(pose, x, y, bx, by, -ax, -ay, r, g, b, alpha);
        softTriangle(pose, x, y, -ax, -ay, -bx, -by, r, g, b, alpha);
        softTriangle(pose, x, y, -bx, -by, ax, ay, r, g, b, alpha);
    }

    private void outline(Matrix4f pose, float x, float y, float size, int r, int g, int b, int a) {
        float[] xs = {-size, 0, size, 0};
        float[] ys = {0, -size, 0, size};
        float inner = 0.59F;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            vertex(pose, x + xs[i], y + ys[i], r, g, b, a);
            vertex(pose, x + xs[j], y + ys[j], r, g, b, a);
            vertex(pose, x + xs[j] * inner, y + ys[j] * inner, r, g, b, a);
            vertex(pose, x + xs[i], y + ys[i], r, g, b, a);
            vertex(pose, x + xs[j] * inner, y + ys[j] * inner, r, g, b, a);
            vertex(pose, x + xs[i] * inner, y + ys[i] * inner, r, g, b, a);
        }
    }

    private void softTriangle(Matrix4f pose, float x, float y, float ax, float ay, float bx, float by,
                              int r, int g, int b, int a) {
        vertex(pose, x, y, r, g, b, a);
        vertex(pose, x + ax, y + ay, r, g, b, 0);
        vertex(pose, x + bx, y + by, r, g, b, 0);
    }

    private void vertex(Matrix4f pose, float x, float y, int r, int g, int b, int a) {
        DrawBuffer.INSTANCE.vertex(pose, x, y, 0).color(r, g, b, a).endVertex();
    }

    BlockPos pick(double x, double y) {
        if (!inside(x, y, field)) {
            return null;
        }
        Echo best = null;
        double closest = 36.0D;
        int column = (int) (x + field) / 2;
        int row = (int) (y + field) / 2;
        for (int dy = Math.max(0, row - 3); dy <= Math.min(field, row + 3); dy++) {
            for (int dx = Math.max(0, column - 3); dx <= Math.min(field, column + 3); dx++) {
                Echo echo = cells[dy * (field + 1) + dx];
                if (echo == null || echo.type == SonarScan.TYPE_CONTACT) {
                    continue;
                }
                double ox = x - echo.x;
                double oy = y - echo.y;
                double distance = ox * ox + oy * oy;
                if (best != null && best.type == SonarScan.TYPE_ORE && echo.type != SonarScan.TYPE_ORE) {
                    continue;
                }
                if (distance < closest || (distance == closest && best != null && echo.depth < best.depth)
                        || (distance < 36.0D && echo.type == SonarScan.TYPE_ORE
                                && best != null && best.type != SonarScan.TYPE_ORE)) {
                    best = echo;
                    closest = distance;
                }
            }
        }
        return best == null ? null : best.world;
    }

    int[] marker(Vec3 relative, float[] basis, double camera) {
        Projected pos = project(relative.x, relative.y, relative.z, basis, camera);
        return pos == null ? null : new int[]{Math.round(pos.x), Math.round(pos.y)};
    }

    /** Aim and reticle use the same plane in front of the sonar origin. */
    Vec3 aimDirection(double x, double y, float[] view, double camera) {
        double reach = camera * 0.5D;
        double scale = (camera + reach) / (focal * reach);
        scale *= Math.min(1.0D, (field - 7.0D) / Math.max(1.0D, Math.hypot(x, y)));
        double u = x * scale;
        double v = -y * scale;
        return new Vec3(view[0] + u * view[3] + v * view[6],
                view[1] + u * view[4] + v * view[7],
                view[2] + u * view[5] + v * view[8]).normalize();
    }

    int[] aimMarker(Vec3 direction, float[] view, double camera) {
        double alignment = direction.x * view[0] + direction.y * view[1] + direction.z * view[2];
        return alignment <= 0.1D ? null : marker(direction.scale(camera * 0.5D / alignment), view, camera);
    }

    BlockPos dragTarget(BlockPos planePoint, BlockPos center, double x, double y,
                        float[] basis, double camera) {
        return dragTarget(planePoint, center, Vec3.ZERO, x, y, basis, camera);
    }

    BlockPos dragTarget(BlockPos planePoint, BlockPos center, Vec3 delta, double x, double y,
                        float[] basis, double camera) {
        double px = (double) planePoint.getX() - center.getX() - delta.x;
        double py = (double) planePoint.getY() - center.getY() - delta.y;
        double pz = (double) planePoint.getZ() - center.getZ() - delta.z;
        double plane = px * basis[0] + py * basis[1] + pz * basis[2];
        double depth = camera + plane;
        if (depth < NEAR) {
            return null;
        }
        double length = Math.hypot(x, y);
        double clip = Math.min(1.0D, (field - 6.0D) / Math.max(1.0D, length));
        double u = x * depth / focal * clip;
        double v = -y * depth / focal * clip;
        return center.offset((int) Math.round(delta.x + plane * basis[0] + u * basis[3] + v * basis[6]),
                (int) Math.round(delta.y + plane * basis[1] + u * basis[4] + v * basis[7]),
                (int) Math.round(delta.z + plane * basis[2] + u * basis[5] + v * basis[8]));
    }

    private static boolean inside(double x, double y, int radius) {
        return x * x + y * y <= radius * radius;
    }
}
