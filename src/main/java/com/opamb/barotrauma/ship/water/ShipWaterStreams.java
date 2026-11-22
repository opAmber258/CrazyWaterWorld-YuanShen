package com.opamb.barotrauma.ship.water;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Visible routes for water accepted at openings. The fluid field still accounts for all water;
 * these paths show how it reaches the pool without drawing through decks.
 */
public final class ShipWaterStreams {

    private static final double FLOOR_CLEARANCE = 0.035D;
    private static final double WALL_OFFSET = 0.445D;
    private static final double MIN_LENGTH_SQUARED = 0.035D * 0.035D;

    public record Segment(Vec3 from, Vec3 to, double rate) {
    }

    public record SideFlow(Vec3 sill, int dx, int dz, double wetTop, double receivingSurface, double rate) {
    }

    private record Inlet(BlockPos opening, BlockPos receiver) {
    }

    private record SideTransfer(double sourceSurface, double amount) {
        SideTransfer merge(SideTransfer other) {
            return new SideTransfer(Math.max(sourceSurface, other.sourceSurface), amount + other.amount);
        }
    }

    private record Run(Vec3 from, Vec3 to) {
    }

    private final ShipFluidField field;
    private final Set<BlockPos> closedDoors;
    private final Set<BlockPos> hull;
    private final Map<Inlet, Double> pendingInflow = new LinkedHashMap<>();
    private final Map<Inlet, Double> active = new LinkedHashMap<>();
    private final Map<Inlet, SideTransfer> pendingSide = new LinkedHashMap<>();
    private final Map<Inlet, SideTransfer> activeSide = new LinkedHashMap<>();
    private final Map<Inlet, List<Vec3>> routes = new HashMap<>();
    private double stepSeconds;

    ShipWaterStreams(ShipFluidField field, Set<BlockPos> closedDoors, Set<BlockPos> hull) {
        this.field = field;
        this.closedDoors = Set.copyOf(closedDoors);
        this.hull = Set.copyOf(hull);
    }

    void recordInflow(BlockPos opening, BlockPos receiver, double amount) {
        // 舱底破口的水是“向上渗”的（opening 在接水格下方），也要记录：
        // 以前只收 opening.y >= receiver.y，这种漏点只看得到水位涨、看不到水流。
        if (receiver != null && amount > 0.0D) {
            pendingInflow.merge(new Inlet(opening.immutable(), receiver), amount, Double::sum);
        }
    }

    void beginStep(double dt) {
        active.clear();
        activeSide.clear();
        stepSeconds = dt;
        if (dt > 0.0D) {
            pendingInflow.forEach((inlet, amount) -> active.put(inlet, amount / dt));
            activeSide.putAll(pendingSide);
        }
        pendingInflow.clear();
        pendingSide.clear();
    }

    void opening(BlockPos opening, BlockPos receiver, double rate) {
        if (opening.getY() >= receiver.getY() && rate > 0.0D) {
            active.merge(new Inlet(opening, receiver), rate, Double::sum);
        }
    }

    void recordSideInflow(BlockPos from, BlockPos to, double sourceSurface, double amount) {
        pendingSide.merge(new Inlet(from, to), new SideTransfer(sourceSurface, amount), SideTransfer::merge);
    }

    void sideOpening(BlockPos from, BlockPos to, double sourceSurface, double amount) {
        activeSide.merge(new Inlet(from, to), new SideTransfer(sourceSurface, amount), SideTransfer::merge);
    }

    List<SideFlow> sideFlows() {
        List<SideFlow> flows = new ArrayList<>(activeSide.size());
        activeSide.forEach((inlet, transfer) -> {
            BlockPos to = inlet.receiver();
            int dx = to.getX() - inlet.opening().getX();
            int dz = to.getZ() - inlet.opening().getZ();
            Vec3 sill = Vec3.atBottomCenterOf(to).add(-0.5D * dx, 0, -0.5D * dz);
            double wetTop = Math.min(sill.y + 0.94D, transfer.sourceSurface());
            flows.add(new SideFlow(sill, dx, dz, wetTop, field.bodyLevel(field.bodyOf(to)),
                    transfer.amount() / stepSeconds));
        });
        return flows;
    }

    List<Segment> segments() {
        Map<Run, Double> visible = new LinkedHashMap<>();
        active.forEach((inlet, rate) -> {
            BlockPos receiver = inlet.receiver();
            int body = field.bodyOf(receiver);
            if (!canRunThrough(receiver, body)) {
                return;
            }
            if (inlet.opening().getY() < receiver.getY()) {
                // 舱底向上的渗漏：画一小段从洞口升到接水格的短流，不交给只向下寻路的 channel。
                Vec3 from = Vec3.atBottomCenterOf(inlet.opening()).add(0, 0.08D, 0);
                Vec3 to = Vec3.atBottomCenterOf(receiver).add(0, 0.34D, 0);
                if (from.distanceToSqr(to) > MIN_LENGTH_SQUARED) {
                    visible.merge(new Run(from, to), rate, Double::sum);
                }
                return;
            }
            double surface = field.bodyLevel(body);
            Vec3 previous = mouth(inlet);
            if (previous.y <= surface) {
                return;
            }
            for (Vec3 point : routes.computeIfAbsent(inlet, at -> channel(at, body))) {
                Vec3 next = point;
                boolean hitsPool = previous.y > surface && next.y <= surface;
                if (hitsPool) {
                    next = previous.lerp(next, (previous.y - surface) / (previous.y - next.y));
                }
                if (previous.distanceToSqr(next) > MIN_LENGTH_SQUARED) {
                    visible.merge(new Run(previous, next), rate, Double::sum);
                }
                if (hitsPool) {
                    break;
                }
                previous = next;
            }
        });
        List<Segment> segments = new ArrayList<>(visible.size());
        visible.forEach((run, rate) -> segments.add(new Segment(run.from(), run.to(), rate)));
        return segments;
    }

    private static Vec3 mouth(Inlet inlet) {
        BlockPos opening = inlet.opening();
        BlockPos receiver = inlet.receiver();
        Vec3 center = Vec3.atBottomCenterOf(receiver);
        if (opening.getY() > receiver.getY()) {
            return center.add(0, 1, 0);
        }
        // 贴在内侧，避免破损后仍可见的船壳模型把水流挡住。
        return center.add((opening.getX() - receiver.getX()) * 0.46D, 0.36D,
                (opening.getZ() - receiver.getZ()) * 0.46D);
    }

    /** Keep the free path in its open cells, with falling or running water close to any continuous wall. */
    private List<Vec3> channel(Inlet inlet, int body) {
        List<Vec3> points = new ArrayList<>();
        BlockPos from = inlet.receiver();
        Vec3 previous = mouth(inlet);
        Direction preferred = inlet.opening().getY() == from.getY()
                ? Direction.fromDelta(inlet.opening().getX() - from.getX(), 0,
                inlet.opening().getZ() - from.getZ()) : null;
        for (BlockPos to : route(from, body)) {
            Vec3 next = Vec3.atBottomCenterOf(to).add(0, FLOOR_CLEARANCE, 0);
            Direction wall = wallAlong(from, to, preferred);
            if (wall != null) {
                next = next.add(wall.getStepX() * WALL_OFFSET, 0, wall.getStepZ() * WALL_OFFSET);
                double drop = previous.y - next.y;
                double across = wall.getAxis() == Direction.Axis.X ? next.x - previous.x : next.z - previous.z;
                if (drop > 0.55D && Math.abs(across) > 0.12D) {
                    // Take a short bend inside the opening before settling against the wall.
                    points.add(new Vec3(next.x, previous.y - Math.min(0.28D, drop * 0.35D), next.z));
                }
                preferred = wall;
            }
            points.add(next);
            from = to;
            previous = next;
        }
        return points;
    }

    private Direction wallAlong(BlockPos from, BlockPos to, Direction preferred) {
        if (preferred != null && supportsRun(from, to, preferred, true)) {
            return preferred;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (supportsRun(from, to, side, false)) {
                return side;
            }
        }
        return null;
    }

    private boolean supportsRun(BlockPos from, BlockPos to, Direction side, boolean inletWall) {
        if ((side.getAxis() == Direction.Axis.X && from.getX() != to.getX())
                || (side.getAxis() == Direction.Axis.Z && from.getZ() != to.getZ())) {
            return false;
        }
        if (from.equals(to) && inletWall && hull.contains(to.relative(side).below())) {
            return true;
        }
        int dx = Integer.compare(to.getX(), from.getX());
        int dy = Integer.compare(to.getY(), from.getY());
        int dz = Integer.compare(to.getZ(), from.getZ());
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1) {
            return false;
        }
        BlockPos.MutableBlockPos cursor = from.mutable();
        while (true) {
            BlockPos neighbor = cursor.relative(side);
            if ((!hull.contains(neighbor) || field.bodyOf(neighbor) >= 0)
                    && !(inletWall && cursor.equals(from) && !from.equals(to))) {
                return false;
            }
            if (cursor.equals(to)) {
                return true;
            }
            cursor.move(dx, dy, dz);
        }
    }

    private List<BlockPos> route(BlockPos receiver, int body) {
        List<BlockPos> route = new ArrayList<>();
        BlockPos cell = receiver;
        // 入水口下方有直通的落差时，把整段落水画成一条。
        if (!canRunThrough(cell.below(), body)) {
            route.add(cell);
        }
        while (true) {
            BlockPos below = cell.below();
            if (canRunThrough(below, body)) {
                do {
                    cell = below;
                    below = cell.below();
                } while (canRunThrough(below, body));
                route.add(cell);
            }
            List<BlockPos> across = pathToDrop(cell, body);
            if (across.isEmpty()) {
                return route;
            }
            route.addAll(across);
            cell = across.get(across.size() - 1);
        }
    }

    /** 沿同一层实际连通的格找最近的开口，同时保留拐角。 */
    private List<BlockPos> pathToDrop(BlockPos start, int body) {
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        ArrayDeque<BlockPos> search = new ArrayDeque<>();
        parents.put(start, start);
        search.add(start);
        while (!search.isEmpty()) {
            BlockPos cell = search.remove();
            if (canRunThrough(cell.below(), body)) {
                List<BlockPos> turns = new ArrayList<>();
                turns.add(cell);
                BlockPos direction = null;
                for (BlockPos at = cell; !at.equals(start); at = parents.get(at)) {
                    BlockPos step = at.subtract(parents.get(at));
                    if (direction != null && !direction.equals(step)) {
                        turns.add(at);
                    }
                    direction = step;
                }
                Collections.reverse(turns);
                return turns;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = cell.relative(direction);
                if (canRunThrough(next, body) && !parents.containsKey(next)) {
                    parents.put(next, cell);
                    search.add(next);
                }
            }
        }
        return List.of();
    }

    private boolean canRunThrough(BlockPos cell, int body) {
        return body >= 0 && field.bodyOf(cell) == body && !closedDoors.contains(cell);
    }
}
