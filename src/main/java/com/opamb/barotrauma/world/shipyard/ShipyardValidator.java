package com.opamb.barotrauma.world.shipyard;

import com.opamb.barotrauma.blockentity.ReactorBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.blockentity.TurretStationBlockEntity;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipGeometry;
import com.opamb.barotrauma.ship.ShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 船厂校验：结构连通（硬）、水密（警告）与固定基础运行清单。
 */
public final class ShipyardValidator {

    public enum State {
        OK,
        WARN,
        MISSING
    }

    public record Requirement(String id, State state, String detail, BlockPos target) {
    }

    private static final int MAX_DIAGNOSTIC_POS = 128;
    private static final int MAX_FLOOR_CELLS = 256;
    private static final int MAX_WIRE_LINKS = 128;
    public static final byte WIRE_POWER = 0;
    public static final byte WIRE_TURRET = 1;

    public record WireLink(BlockPos from, BlockPos to, byte kind) {
    }

    public record Report(int blocks, int sizeX, int sizeY, int sizeZ,
                         int compartments, int openPockets, boolean connected, boolean sealed,
                         List<Requirement> requirements, long millis,
                         List<BlockPos> disconnectedPos, List<BlockPos> leakPos,
                         BlockPos minPos, BlockPos maxPos,
                         int totalInteriorVolume, int ballastVolume,
                         int powerGenKw, int powerLoadKw, int engineCount,
                         List<BlockPos> ballastFloorCells, List<BlockPos> dryFloorCells,
                         List<WireLink> wireLinks) {
        public Report(int blocks, int sizeX, int sizeY, int sizeZ,
                      int compartments, int openPockets, boolean connected, boolean sealed,
                      List<Requirement> requirements, long millis) {
            this(blocks, sizeX, sizeY, sizeZ, compartments, openPockets, connected, sealed,
                    requirements, millis, List.of(), List.of(), null, null,
                    0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        }
    }

    private ShipyardValidator() {
    }

    public static Report validate(ServerLevel level, ShipyardSavedData.Plot plot) {
        long start = System.currentTimeMillis();
        Set<BlockPos> shipBlocks = new HashSet<>();
        int reactor = 0;
        int reactorFuel = 0;
        int engine = 0;
        int pump = 0;
        int ballast = 0;
        int oxygen = 0;
        int vent = 0;
        int doors = 0;
        int battery = 0;
        BlockPos firstReactor = null;
        BlockPos firstEngine = null;
        BlockPos firstPump = null;
        BlockPos firstOxygen = null;
        BlockPos firstDoor = null;

        // 校验只读船核心已经扫描好的状态，不再遍历整个地块 bounds。
        BlockPos center = new BlockPos(plot.originX(), plot.originY(), plot.originZ());
        ShipCoreBlockEntity coreBe = null;
        double bestDistance = Double.MAX_VALUE;
        for (ShipCoreBlockEntity candidate : ShipCores.coresNear(level, center, 256.0D)) {
            double distance = candidate.getBlockPos().distSqr(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                coreBe = candidate;
            }
        }
        BlockPos core = coreBe == null ? null : coreBe.getBlockPos();
        int compartments = 0;
        int openPockets = 0;
        int totalInteriorVolume = 0;
        int ballastVolume = 0;
        List<BlockPos> leakPos = new ArrayList<>();
        List<BlockPos> ballastFloorCells = new ArrayList<>();
        List<BlockPos> dryFloorCells = new ArrayList<>();
        ShipGeometry.Result geometry = null;
        if (coreBe != null) {
            ShipState state = coreBe.state();
            if (state == null) {
                state = coreBe.rescan(level);
            }
            geometry = state.geometry();
            shipBlocks.addAll(geometry.hull());
            shipBlocks.addAll(geometry.equipment());
            shipBlocks.add(core);
            compartments = geometry.compartments().size();
            openPockets = (int) geometry.openPockets().stream()
                    .filter(pocket -> pocket.interiorVolume() > 0).count();
            collectLeakPoints(geometry, shipBlocks, leakPos);
        }

        double powerLoadSum = 0.0D;
        List<WireLink> wireLinks = new ArrayList<>();
        Set<BlockPos> ballastBlocks = new HashSet<>();

        for (BlockPos immutable : shipBlocks) {
            BlockState state = level.getBlockState(immutable);
            BlockEntity be = level.getBlockEntity(immutable);
            if (state.is(ModBlocks.REACTOR.get()) && be instanceof ReactorBlockEntity reactorBe) {
                reactor++;
                firstReactor = firstReactor == null ? immutable : firstReactor;
                if (reactorBe.availableFuel() > 1.0D) {
                    reactorFuel++;
                }
            } else if (state.is(ModBlocks.ENGINE.get()) && be != null) {
                engine++;
                firstEngine = firstEngine == null ? immutable : firstEngine;
            } else if ((state.is(ModBlocks.DRAIN_PUMP.get()) || state.is(ModBlocks.SMALL_PUMP.get()))
                    && be != null) {
                pump++;
                firstPump = firstPump == null ? immutable : firstPump;
            } else if (state.is(ModBlocks.BALLAST_PUMP.get())) {
                if (be != null) {
                    ballast++;
                }
                ballastBlocks.add(immutable);
            } else if (state.is(ModBlocks.OXYGEN_GENERATOR.get()) && be != null) {
                oxygen++;
                firstOxygen = firstOxygen == null ? immutable : firstOxygen;
            } else if (state.is(ModBlocks.VENT.get())) {
                vent++;
            } else if ((state.is(ModShipBlocks.BULKHEAD_DOOR.get())
                    || state.is(ModShipBlocks.HATCH.get())
                    || state.is(ModShipBlocks.HATCH_LADDER.get()))
                    && isMainDoorPart(state)) {
                doors++;
                firstDoor = firstDoor == null ? immutable : firstDoor;
            } else if ((state.is(ModBlocks.BATTERY.get()) || state.is(ModBlocks.SUPERCAPACITOR.get()))
                    && be != null) {
                battery++;
            }
            if (be instanceof PowerConsumer consumer) {
                powerLoadSum += Math.max(0.0D, consumer.powerDemandKw());
                if (consumer.powerBox() != null && wireLinks.size() < MAX_WIRE_LINKS) {
                    wireLinks.add(new WireLink(immutable, consumer.powerBox(), WIRE_POWER));
                }
            }
            if (be instanceof TurretStationBlockEntity station && station.isBound()
                    && wireLinks.size() < MAX_WIRE_LINKS) {
                wireLinks.add(new WireLink(immutable, station.turretPos(), WIRE_TURRET));
            }
        }

        if (geometry != null) {
            for (ShipGeometry.Compartment comp : geometry.compartments()) {
                totalInteriorVolume += comp.interiorVolume();
                boolean isBallast = isBallastCompartment(comp, ballastBlocks);
                if (isBallast) {
                    ballastVolume += comp.interiorVolume();
                }
                List<BlockPos> targetFloors = isBallast ? ballastFloorCells : dryFloorCells;
                if (targetFloors.size() < MAX_FLOOR_CELLS) {
                    for (BlockPos cell : comp.interiorCells()) {
                        if (!comp.cells().contains(cell.below())) {
                            targetFloors.add(cell);
                            if (targetFloors.size() >= MAX_FLOOR_CELLS) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        int powerGenKw = reactor * 3000;
        int powerLoadKw = (int) Math.round(powerLoadSum);

        int sizeX = 0;
        int sizeY = 0;
        int sizeZ = 0;
        BlockPos minPos = null;
        BlockPos maxPos = null;
        if (!shipBlocks.isEmpty()) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos block : shipBlocks) {
                minX = Math.min(minX, block.getX());
                minY = Math.min(minY, block.getY());
                minZ = Math.min(minZ, block.getZ());
                maxX = Math.max(maxX, block.getX());
                maxY = Math.max(maxY, block.getY());
                maxZ = Math.max(maxZ, block.getZ());
            }
            sizeX = maxX - minX + 1;
            sizeY = maxY - minY + 1;
            sizeZ = maxZ - minZ + 1;
            minPos = new BlockPos(minX, minY, minZ);
            maxPos = new BlockPos(maxX, maxY, maxZ);
        }
        List<BlockPos> disconnectedPos = shipBlocks.isEmpty()
                ? List.of() : collectDisconnected(shipBlocks, core);
        int disconnected = disconnectedPos.size();
        BlockPos structureTarget = disconnectedPos.isEmpty() ? core : disconnectedPos.get(0);
        BlockPos leakTarget = leakPos.isEmpty() ? core : leakPos.get(0);

        List<Requirement> req = new ArrayList<>();
        req.add(new Requirement("core", core != null ? State.OK : State.MISSING,
                core == null ? "缺少船核心/导航终端" : "船核心就位", core));
        req.add(new Requirement("structure", core != null && disconnected == 0 ? State.OK : State.MISSING,
                disconnected == 0 ? "全部船体方块与核心连通" : "有 " + disconnected + " 格船体未连通",
                structureTarget));
        req.add(new Requirement("sealed", core == null || disconnected > 0 ? State.WARN
                        : openPockets == 0 ? State.OK : State.WARN,
                openPockets == 0 ? "没有连海空腔" : "有 " + openPockets + " 个连海空腔", leakTarget));
        req.add(new Requirement("reactor", reactor == 0 ? State.MISSING : reactorFuel > 0 ? State.OK : State.WARN,
                reactor == 0 ? "缺少反应堆" : reactorFuel == 0 ? "反应堆没有可用燃料" : "反应堆 ×" + reactor,
                firstReactor));
        req.add(new Requirement("engine", engine == 0 ? State.MISSING : State.OK,
                engine == 0 ? "缺少引擎" : "引擎 ×" + engine, firstEngine));
        req.add(new Requirement("pump", pump == 0 ? State.MISSING : State.OK,
                pump == 0 ? "缺少排水/小泵" : "泵 ×" + pump, firstPump));
        req.add(new Requirement("ballast", ballast == 0 ? State.WARN : State.OK,
                ballast == 0 ? "缺少压载泵（无法自动压载）" : "压载泵 ×" + ballast, null));
        req.add(new Requirement("oxygen", oxygen > 0 && vent > 0 ? State.OK
                : oxygen > 0 || vent > 0 ? State.WARN : State.MISSING,
                oxygen == 0 ? "缺少制氧机" : vent == 0 ? "缺少通风口" : "制氧机 ×" + oxygen + "，通风口 ×" + vent,
                firstOxygen));
        req.add(new Requirement("doors", doors == 0 ? State.WARN : State.OK,
                doors == 0 ? "没有可关闭门/舱口" : "门/舱口 ×" + doors, firstDoor));
        req.add(new Requirement("power", reactor > 0 || battery > 0 ? State.OK : State.MISSING,
                reactor > 0 ? "反应堆供电" : battery > 0 ? "电池供电" : "没有反应堆/电池",
                firstReactor));
        req.add(new Requirement("helm", core != null ? State.OK : State.MISSING,
                core != null ? "驾驶终端可用" : "缺少驾驶终端", core));

        List<BlockPos> cappedDisconnected = disconnectedPos.size() > MAX_DIAGNOSTIC_POS
                ? List.copyOf(disconnectedPos.subList(0, MAX_DIAGNOSTIC_POS))
                : List.copyOf(disconnectedPos);
        return new Report(shipBlocks.size(), sizeX, sizeY, sizeZ,
                compartments, openPockets, core != null && disconnected == 0,
                core != null && disconnected == 0 && openPockets == 0,
                List.copyOf(req), System.currentTimeMillis() - start,
                cappedDisconnected, List.copyOf(leakPos), minPos, maxPos,
                totalInteriorVolume, ballastVolume, powerGenKw, powerLoadKw, engine,
                List.copyOf(ballastFloorCells), List.copyOf(dryFloorCells), List.copyOf(wireLinks));
    }

    private static boolean isMainDoorPart(BlockState state) {
        return !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
    }

    private static boolean isBallastCompartment(ShipGeometry.Compartment comp, Set<BlockPos> ballastBlocks) {
        if (ballastBlocks.isEmpty()) {
            return false;
        }
        for (BlockPos cell : comp.cells()) {
            if (ballastBlocks.contains(cell)) {
                return true;
            }
            for (Direction dir : Direction.values()) {
                if (ballastBlocks.contains(cell.relative(dir))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collectLeakPoints(ShipGeometry.Result geometry, Set<BlockPos> shipBlocks,
                                          List<BlockPos> out) {
        for (ShipGeometry.Compartment pocket : geometry.openPockets()) {
            if (pocket.interiorVolume() <= 0 || out.size() >= MAX_DIAGNOSTIC_POS) {
                continue;
            }
            Set<BlockPos> interior = pocket.interiorCells();
            int before = out.size();
            for (BlockPos cell : interior) {
                for (Direction dir : Direction.values()) {
                    BlockPos next = cell.relative(dir);
                    if (!interior.contains(next) && !shipBlocks.contains(next)) {
                        out.add(cell);
                        break;
                    }
                }
                if (out.size() >= MAX_DIAGNOSTIC_POS) {
                    return;
                }
            }
            if (out.size() == before) {
                for (BlockPos cell : interior) {
                    out.add(cell);
                    if (out.size() >= MAX_DIAGNOSTIC_POS) {
                        return;
                    }
                }
            }
        }
    }

    private static List<BlockPos> collectDisconnected(Set<BlockPos> blocks, BlockPos core) {
        if (blocks.isEmpty()) {
            return List.of();
        }
        if (core == null || !blocks.contains(core)) {
            return new ArrayList<>(blocks);
        }
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(core);
        queue.add(core);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (blocks.contains(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos block : blocks) {
            if (!seen.contains(block)) {
                missing.add(block);
            }
        }
        return missing;
    }

    private record Probe(ServerLevel level) implements ShipGeometry.Probe {
        @Override
        public boolean isSolid(BlockPos pos) {
            return ModShipBlocks.isSolidForShip(level.getBlockState(pos));
        }

        @Override
        public boolean isWater(BlockPos pos) {
            FluidState fluid = level.getFluidState(pos);
            return fluid.is(net.minecraft.tags.FluidTags.WATER);
        }

        @Override
        public boolean isDoorway(BlockPos pos) {
            return ModShipBlocks.isDoorway(level.getBlockState(pos));
        }

        @Override
        public boolean isOpenDoorway(BlockPos pos) {
            return ModShipBlocks.isOpenDoorway(level.getBlockState(pos));
        }

        @Override
        public Direction.Axis doorwayAxis(BlockPos pos) {
            return ModShipBlocks.doorwayAxis(level.getBlockState(pos));
        }
    }
}
