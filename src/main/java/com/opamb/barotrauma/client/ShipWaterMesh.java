package com.opamb.barotrauma.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.opamb.barotrauma.network.ShipWaterStatePacket.BOX_STRIDE;

/** 将装配坐标中的湿区间合成外表面，波浪、顶面和侧面的顶边共用同一组采样。 */
final class ShipWaterMesh {
    static final float MIN_DEPTH = 0.001F;
    private static final float EPSILON = 0.0001F;
    private static final float JOIN_HEIGHT = 0.125F;
    private static final float MAX_WAVE = 0.065F;
    private static final float WAVE_STEP = 1.0F / 60.0F;
    private static final float IMPULSE_INTERVAL = 0.14F;

    // 角点从西北开始，沿顶面外法线方向排序。边顺序为东、南、西、北。
    private static final int[] CORNER_X = {0, 0, 1, 1};
    private static final int[] CORNER_Z = {0, 1, 1, 0};
    private static final int[] STEP_X = {1, 0, -1, 0};
    private static final int[] STEP_Z = {0, 1, 0, -1};
    private static final int[] EDGE_START = {3, 2, 1, 0};
    private static final int[] EDGE_END = {2, 1, 0, 3};

    @FunctionalInterface
    interface Opening {
        boolean connects(int x, int z, int nextX, int nextZ, float low, float high);
    }

    @FunctionalInterface
    interface VertexSink {
        void put(float x, float y, float z, float belowSurface, float surfaceStrength);
    }

    private final Map<Long, Column> columns = new LinkedHashMap<>();
    private final float[] polygonT = new float[8];
    private final float[] polygonY = new float[8];
    private final float[] clippedT = new float[8];
    private final float[] clippedY = new float[8];
    private float[] layout = new float[0];
    private int frame;
    private float waveTime;
    private float untilImpulse;

    private record Source(int offset, float bottom, float ceiling) {
    }

    private static final class Column {
        final int x;
        final int z;
        final List<Source> sources = new ArrayList<>();
        final Column[] neighbors = new Column[4];
        Span[] wet;
        int count;

        Column(int x, int z) {
            this.x = x;
            this.z = z;
        }

        void update(float[] boxes, int frame) {
            count = 0;
            for (Source source : sources) {
                float top = Math.min(boxes[source.offset + 6], source.ceiling);
                if (!(top > source.bottom + MIN_DEPTH)) {
                    continue;
                }
                Span previous = count == 0 ? null : wet[count - 1];
                if (previous != null && source.bottom <= previous.top + EPSILON) {
                    previous.top = Math.max(previous.top, top);
                    previous.ceiling = Math.max(previous.ceiling, source.ceiling);
                } else {
                    wet[count++].set(source.bottom, top, source.ceiling, frame);
                }
            }
            for (int i = 0; i < count; i++) {
                Span span = wet[i];
                if (i + 1 < count) {
                    span.ceiling = Math.min(span.ceiling, wet[i + 1].bottom);
                }
                span.strength = clamp(Math.min(span.ceiling - span.top, span.top - span.bottom) / 0.14F,
                        0.0F, 1.0F);
            }
        }
    }

    private static final class Span {
        final Span[] links = new Span[4];
        final Corner[] corners = {new Corner(), new Corner(), new Corner(), new Corner()};
        float bottom;
        float top;
        float ceiling;
        float strength;
        float height;
        float velocity;
        float nextVelocity;
        int lastFrame;

        void set(float bottom, float top, float ceiling, int frame) {
            if (lastFrame != frame - 1 || this.bottom != bottom) {
                height = 0.0F;
                velocity = 0.0F;
            }
            this.bottom = bottom;
            this.top = top;
            this.ceiling = ceiling;
            lastFrame = frame;
            for (int edge = 0; edge < links.length; edge++) {
                links[edge] = null;
            }
        }
    }

    private static final class Corner {
        Corner parent;
        float floor;
        float ceiling;
        float sum;
        float strength;
        int samples;

        void reset(Span span, float surface) {
            parent = this;
            floor = span.strength == 0.0F ? span.top : span.bottom + EPSILON;
            ceiling = span.ceiling;
            sum = surface;
            strength = span.strength;
            samples = 1;
        }

        Corner root() {
            if (parent != this) {
                parent = parent.root();
            }
            return parent;
        }

        void share(Corner other) {
            Corner a = root();
            Corner b = other.root();
            if (a == b) {
                return;
            }
            float floor = Math.max(a.floor, b.floor);
            float ceiling = Math.min(a.ceiling, b.ceiling);
            if (floor <= ceiling) {
                b.parent = a;
                a.floor = floor;
                a.ceiling = ceiling;
                a.sum += b.sum;
                a.samples += b.samples;
                a.strength = Math.min(a.strength, b.strength);
            }
        }

        float y() {
            Corner corner = root();
            return clamp(corner.sum / corner.samples, corner.floor, corner.ceiling);
        }
    }

    int update(float[] boxes, float dt, float seconds, float[] impacts, int impactEnd, Opening opening) {
        if (!sameLayout(boxes)) {
            setLayout(boxes);
        }
        int wetColumns = 0;
        frame++;
        for (Column column : columns.values()) {
            column.update(boxes, frame);
            if (column.count > 0) {
                wetColumns++;
            }
        }
        for (Column column : columns.values()) {
            link(column, 0, opening);
            link(column, 1, opening);
        }
        waveTime += dt;
        while (waveTime >= WAVE_STEP) {
            untilImpulse -= WAVE_STEP;
            if (untilImpulse <= 0.0F) {
                untilImpulse += IMPULSE_INTERVAL;
                disturb(impacts, impactEnd);
            }
            stepWaves();
            waveTime -= WAVE_STEP;
        }
        for (Column column : columns.values()) {
            for (int i = 0; i < column.count; i++) {
                Span span = column.wet[i];
                for (int corner = 0; corner < span.corners.length; corner++) {
                    int x = column.x + CORNER_X[corner];
                    int z = column.z + CORNER_Z[corner];
                    float ambient = 0.005F * (float) Math.sin(x * 1.19D + z * 0.63D + seconds * 1.55D)
                            + 0.003F * (float) Math.sin(z * 1.53D - x * 0.47D - seconds * 1.10D);
                    span.corners[corner].reset(span, span.top + (span.height + ambient) * span.strength);
                }
            }
        }
        for (Column column : columns.values()) {
            for (int i = 0; i < column.count; i++) {
                Span span = column.wet[i];
                for (int edge = 0; edge < 2; edge++) {
                    Span neighbor = span.links[edge];
                    if (neighbor != null) {
                        int opposite = edge + 2;
                        span.corners[EDGE_START[edge]].share(neighbor.corners[EDGE_END[opposite]]);
                        span.corners[EDGE_END[edge]].share(neighbor.corners[EDGE_START[opposite]]);
                    }
                }
            }
        }
        return wetColumns;
    }

    private boolean sameLayout(float[] boxes) {
        if (layout.length != boxes.length) {
            return false;
        }
        for (int row = 0; row < boxes.length; row += BOX_STRIDE) {
            for (int i = 0; i < 6; i++) {
                if (layout[row + i] != boxes[row + i]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void setLayout(float[] boxes) {
        layout = boxes.clone();
        columns.clear();
        for (int row = 0; row < boxes.length; row += BOX_STRIDE) {
            Source source = new Source(row, boxes[row + 1], boxes[row + 4] + 1.0F);
            int xMax = (int) boxes[row + 3];
            int zMax = (int) boxes[row + 5];
            for (int x = (int) boxes[row]; x <= xMax; x++) {
                for (int z = (int) boxes[row + 2]; z <= zMax; z++) {
                    long key = columnKey(x, z);
                    Column column = columns.get(key);
                    if (column == null) {
                        column = new Column(x, z);
                        columns.put(key, column);
                    }
                    column.sources.add(source);
                }
            }
        }
        for (Column column : columns.values()) {
            column.sources.sort(Comparator.comparingDouble(Source::bottom));
            column.wet = new Span[column.sources.size()];
            for (int i = 0; i < column.wet.length; i++) {
                column.wet[i] = new Span();
            }
            for (int edge = 0; edge < column.neighbors.length; edge++) {
                column.neighbors[edge] = columns.get(columnKey(column.x + STEP_X[edge], column.z + STEP_Z[edge]));
            }
        }
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static void link(Column column, int edge, Opening opening) {
        Column neighbor = column.neighbors[edge];
        if (neighbor == null) {
            return;
        }
        int opposite = edge + 2;
        for (int i = 0; i < column.count; i++) {
            Span a = column.wet[i];
            for (int j = 0; j < neighbor.count; j++) {
                Span b = neighbor.wet[j];
                float low = Math.min(a.top, b.top);
                float high = Math.max(a.top, b.top);
                if (b.links[opposite] == null && high - low <= JOIN_HEIGHT
                        && low > Math.max(a.bottom, b.bottom) + EPSILON
                        && high <= Math.min(a.ceiling, b.ceiling)
                        && opening.connects(column.x, column.z, neighbor.x, neighbor.z, low, high)) {
                    a.links[edge] = b;
                    b.links[opposite] = a;
                    break;
                }
            }
        }
    }

    private void disturb(float[] impacts, int end) {
        for (int i = 0; i < end; i += 4) {
            Column column = columns.get(columnKey((int) Math.floor(impacts[i]), (int) Math.floor(impacts[i + 2])));
            if (column == null) {
                continue;
            }
            float nearest = 0.20F;
            Span target = null;
            for (int j = 0; j < column.count; j++) {
                Span span = column.wet[j];
                float distance = Math.abs(span.top - impacts[i + 1]);
                if (span.strength > 0.0F && distance < nearest) {
                    target = span;
                    nearest = distance;
                }
            }
            if (target != null) {
                target.velocity = Math.max(-0.02F, target.velocity - impacts[i + 3] * 0.20F);
            }
        }
    }

    private void stepWaves() {
        for (Column column : columns.values()) {
            for (int i = 0; i < column.count; i++) {
                Span span = column.wet[i];
                float acceleration = -0.023F * span.height - 0.070F * span.velocity;
                for (Span neighbor : span.links) {
                    if (neighbor != null) {
                        acceleration += 0.11F * (neighbor.height * neighbor.strength - span.height);
                    }
                }
                span.nextVelocity = span.velocity + acceleration;
            }
        }
        for (Column column : columns.values()) {
            for (int i = 0; i < column.count; i++) {
                Span span = column.wet[i];
                float height = span.height + span.nextVelocity;
                span.height = clamp(height, -MAX_WAVE, MAX_WAVE);
                span.velocity = span.height == height ? span.nextVelocity : span.nextVelocity * -0.35F;
            }
        }
    }

    boolean contains(double x, double y, double z) {
        Column column = columns.get(columnKey((int) Math.floor(x), (int) Math.floor(z)));
        if (column != null) {
            for (int i = 0; i < column.count; i++) {
                Span span = column.wet[i];
                if (y >= span.bottom && y < visualHeight(column, span, x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    float surfaceNear(double x, double z, double expected, double range) {
        Column column = columns.get(columnKey((int) Math.floor(x), (int) Math.floor(z)));
        float closest = Float.NaN;
        if (column != null) {
            for (int i = 0; i < column.count; i++) {
                float height = (float) visualHeight(column, column.wet[i], x, z);
                double distance = Math.abs(height - expected);
                if (distance < range) {
                    range = distance;
                    closest = height;
                }
            }
        }
        return closest;
    }

    /** Use the same diagonal and heights as the two rendered top triangles. */
    private static double visualHeight(Column column, Span span, double x, double z) {
        double s = x - column.x;
        double t = z - column.z;
        double first = span.corners[0].y();
        double opposite = span.corners[2].y();
        return t >= s ? first * (1.0D - t) + span.corners[1].y() * (t - s) + opposite * s
                : first * (1.0D - s) + span.corners[3].y() * (s - t) + opposite * t;
    }

    /** 每三次 put 为一个三角形，外法线方向一致。 */
    int emit(VertexSink sink) {
        int triangles = 0;
        for (Column column : columns.values()) {
            for (int i = 0; i < column.count; i++) {
                Span span = column.wet[i];
                horizontal(sink, column, span, true, 0, 1, 2);
                horizontal(sink, column, span, true, 0, 2, 3);
                horizontal(sink, column, span, false, 0, 2, 1);
                horizontal(sink, column, span, false, 0, 3, 2);
                triangles += 4;
                for (int edge = 0; edge < 4; edge++) {
                    triangles += sides(sink, column, span, edge);
                }
            }
        }
        return triangles;
    }

    private static void horizontal(VertexSink sink, Column column, Span span, boolean top, int a, int b, int c) {
        horizontalVertex(sink, column, span, top, a);
        horizontalVertex(sink, column, span, top, b);
        horizontalVertex(sink, column, span, top, c);
    }

    private static void horizontalVertex(VertexSink sink, Column column, Span span, boolean top, int index) {
        Corner corner = span.corners[index];
        float surface = corner.y();
        float y = top ? surface : span.bottom;
        sink.put(column.x + CORNER_X[index], y, column.z + CORNER_Z[index],
                surface - y, corner.root().strength);
    }

    private int sides(VertexSink sink, Column column, Span span, int edge) {
        Column neighbor = column.neighbors[edge];
        int first = EDGE_START[edge];
        int last = EDGE_END[edge];
        int opposite = (edge + 2) % 4;
        float top0 = span.corners[first].y();
        float top1 = span.corners[last].y();
        float lower0 = span.bottom;
        float lower1 = lower0;
        int triangles = 0;
        if (neighbor != null) {
            // 每个邻列只覆盖一格边长。求它的空气区间与本列水柱侧面的交集。
            for (int i = 0; i < neighbor.count; i++) {
                Span wet = neighbor.wet[i];
                triangles += wall(sink, column, span, first, last, top0, top1, lower0, lower1, wet.bottom);
                lower0 = wet.corners[EDGE_END[opposite]].y();
                lower1 = wet.corners[EDGE_START[opposite]].y();
            }
        }
        return triangles + wall(sink, column, span, first, last, top0, top1, lower0, lower1,
                Math.max(top0, top1));
    }

    private int wall(VertexSink sink, Column column, Span span, int first, int last,
                     float top0, float top1, float lower0, float lower1, float upper) {
        if (upper <= span.bottom || Math.max(top0, top1) <= Math.min(lower0, lower1)) {
            return 0;
        }
        polygonT[0] = polygonT[1] = 0.0F;
        polygonT[2] = polygonT[3] = 1.0F;
        polygonY[0] = polygonY[3] = span.bottom;
        polygonY[1] = top0;
        polygonY[2] = top1;
        int count = clip(4, polygonT, polygonY, clippedT, clippedY, lower0, lower1, 1.0F);
        count = clip(count, clippedT, clippedY, polygonT, polygonY, upper, upper, -1.0F);
        int triangles = 0;
        for (int i = 1; i < count - 1; i++) {
            float area = (polygonT[i] - polygonT[0]) * (polygonY[i + 1] - polygonY[0])
                    - (polygonT[i + 1] - polygonT[0]) * (polygonY[i] - polygonY[0]);
            if (Math.abs(area) > EPSILON * EPSILON) {
                wallVertex(sink, column, span, first, last, top0, top1, 0);
                wallVertex(sink, column, span, first, last, top0, top1, i);
                wallVertex(sink, column, span, first, last, top0, top1, i + 1);
                triangles++;
            }
        }
        return triangles;
    }

    private static int clip(int count, float[] inT, float[] inY, float[] outT, float[] outY,
                            float line0, float line1, float sign) {
        int output = 0;
        for (int current = 0; current < count; current++) {
            int previous = (current + count - 1) % count;
            float a = sign * (inY[previous] - mix(line0, line1, inT[previous]));
            float b = sign * (inY[current] - mix(line0, line1, inT[current]));
            if (a * b < 0.0F) {
                float t = mix(inT[previous], inT[current], a / (a - b));
                outT[output] = t;
                outY[output++] = mix(line0, line1, t);
            }
            if (b >= 0.0F) {
                outT[output] = inT[current];
                outY[output++] = inY[current];
            }
        }
        return output;
    }

    private void wallVertex(VertexSink sink, Column column, Span span, int first, int last,
                            float top0, float top1, int index) {
        float t = polygonT[index];
        float y = polygonY[index];
        sink.put(column.x + mix(CORNER_X[first], CORNER_X[last], t), y,
                column.z + mix(CORNER_Z[first], CORNER_Z[last], t), Math.max(0.0F, mix(top0, top1, t) - y),
                mix(span.corners[first].root().strength, span.corners[last].root().strength, t));
    }

    private static float mix(float a, float b, float t) {
        return (1.0F - t) * a + t * b;
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
