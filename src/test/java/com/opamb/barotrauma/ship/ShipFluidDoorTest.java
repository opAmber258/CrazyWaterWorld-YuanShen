package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.network.ShipWaterStatePacket;
import com.opamb.barotrauma.ship.water.ShipFluidField;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 门洞的两侧水体：开着的门由逐格场的压力边过水，关着的门完全阻断。
 */
class ShipFluidDoorTest {

    private static ShipState state(boolean open) {
        BlockPos door = new BlockPos(1, 0, 0);
        ShipGeometry.Compartment left = new ShipGeometry.Compartment(
                Set.of(new BlockPos(0, 0, 0), door), 0);
        ShipGeometry.Compartment right = new ShipGeometry.Compartment(
                Set.of(new BlockPos(2, 0, 0)), 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(left, right), List.of(), 0, 3,
                new ShipGeometry.Bounds(0, 0, 0, 2, 0, 0),
                Set.of(), List.of(new ShipGeometry.DoorLink(door, 0, 1, open)), Set.of());
        return new ShipState(geometry, MachineCount.EMPTY);
    }

    @Test
    @DisplayName("开着的门让水从高侧流到低侧，水量守恒")
    void openDoorPassesWater() {
        ShipState state = state(true);
        state.simulation().flood(0, 100.0D);
        for (int i = 0; i < 60; i++) {
            state.simulation().tickWaterFlow(0.1D);
        }
        assertTrue(state.simulation().waterUnits(1) > 20.0D, "开着的门应该让水过去");
        assertEquals(100.0D, state.simulation().waterUnits(0)
                + state.simulation().waterUnits(1), 1.0E-6D, "过水不能凭空增减");
    }

    @Test
    @DisplayName("关着的门完全阻断水流")
    void closedDoorBlocksWater() {
        ShipState state = state(false);
        state.simulation().flood(0, 100.0D);
        for (int i = 0; i < 60; i++) {
            state.simulation().tickWaterFlow(0.1D);
        }
        assertEquals(0.0D, state.simulation().waterUnits(1), 1.0E-6D);
        assertEquals(100.0D, state.simulation().waterUnits(0), 1.0E-6D);
        assertEquals(0, ShipWaterStateBuilder.build(state, 1, false, 0, 0, 0).sideFlows().length);
    }

    @Test
    @DisplayName("满舱通过低处开门向空舱补水，没过门洞后也继续均压")
    void submergedDoorStillEqualizesRooms() {
        BlockPos door = new BlockPos(1, 0, 0);
        var left = new ShipGeometry.Compartment(Set.of(door, new BlockPos(0, 0, 0),
                new BlockPos(0, 1, 0), new BlockPos(0, 2, 0), new BlockPos(0, 3, 0)), 0);
        var right = new ShipGeometry.Compartment(Set.of(new BlockPos(2, 0, 0),
                new BlockPos(2, 1, 0), new BlockPos(2, 2, 0), new BlockPos(2, 3, 0)), 0);
        var geometry = new ShipGeometry.Result(List.of(left, right), List.of(), 0, 9,
                new ShipGeometry.Bounds(0, 0, 0, 2, 3, 0), Set.of(),
                List.of(new ShipGeometry.DoorLink(door, 0, 1, true)), Set.of());
        var state = new ShipState(geometry, MachineCount.EMPTY);
        var simulation = state.simulation();
        simulation.flood(0, 500.0D);
        simulation.tickWaterFlow(0.01D);
        assertTrue(simulation.fluid().sideFlows().get(0).wetTop()
                > simulation.fluid().sideFlows().get(0).receivingSurface());
        boolean flowedUnderwater = false;

        for (int i = 0; i < 40; i++) {
            simulation.tickWaterFlow(0.1D);
            for (var flow : simulation.fluid().sideFlows()) {
                flowedUnderwater |= flow.receivingSurface() >= flow.wetTop();
            }
        }

        assertTrue(flowedUnderwater, "接收水面没过门洞后，应继续同步侧向流动供渲染气泡");
        assertTrue(simulation.waterUnits(1) > 190.0D,
                "接收舱应在门洞淹没后继续进水至两侧水头接近");
        assertEquals(500.0D, simulation.waterUnits(), 1.0E-6D);
    }

    @Test
    void reverseFlowAndFloodOverflowKeepTheirDirectionInSnapshot() {
        var state = state(true);
        var simulation = state.simulation();
        simulation.flood(1, 100.0D);
        // 右舱已满，继续注入的水和水头差造成的流量都沿同一扇门向左。
        simulation.fluid().injectFlood(1, 15.0D);
        simulation.tickWaterFlow(0.1D);
        var packet = ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0);
        assertEquals(ShipWaterStatePacket.SIDE_FLOW_STRIDE, packet.sideFlows().length);
        assertEquals(2.0F, packet.sideFlows()[0]);
        assertEquals(-1.0F, packet.sideFlows()[3]);
        assertEquals(0.0F, packet.sideFlows()[4]);
        assertEquals(simulation.bodyWater(0),
                packet.sideFlows()[7] * ShipFluidField.DOOR_FLOW_PER_CELL * 0.1D, 1.0E-5D);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            assertArrayEquals(packet.sideFlows(), ShipWaterStatePacket.decode(buffer).sideFlows());
        } finally {
            buffer.release();
        }
    }

    @Test
    void sideSeaDoorSendsInwardFlow() {
        BlockPos door = new BlockPos(2, 1, 2);
        var geometry = recessedDoor(door, true);
        assertTrue(geometry.bounds().contains(door.west()), "门外的海仍在整船包围盒内");
        var state = new ShipState(geometry, MachineCount.EMPTY);
        state.simulation().tickWaterFlow(0.02D);
        var packet = ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0);
        assertEquals(2 * ShipWaterStatePacket.SIDE_FLOW_STRIDE, packet.sideFlows().length);
        assertEquals(2.0F, packet.sideFlows()[0]);
        assertEquals(1.0F, packet.sideFlows()[3]);
        assertEquals(0.0F, packet.sideFlows()[4]);
        assertEquals(120.0D, state.simulation().bodyWater(0), 1.0E-6D);

        state.replaceGeometry(recessedDoor(door, false));
        state.simulation().tickWaterFlow(0.02D);
        assertTrue(state.simulation().fluid().sideFlows().isEmpty());
        assertEquals(120.0D, state.simulation().bodyWater(0), 1.0E-6D);
    }

    @Test
    void exteriorSearchDoesNotTurnKnownBreachedRoomIntoAnInfiniteWaterSource() {
        BlockPos door = new BlockPos(2, 1, 2);
        var field = ShipFluidField.fromGeometry(recessedDoor(door, true),
                Set.of(door.west(), door.above().west()));
        field.tick(0.05D);
        int inside = field.bodyOf(door.east());
        int outside = field.bodyOf(door.west());
        assertTrue(outside >= 0 && outside != inside);
        assertEquals(0.0D, field.bodyWater(inside));
        field.injectFlood(outside, 200.0D);
        field.tick(0.05D);
        assertTrue(field.bodyWater(inside) > 0.0D);
        assertEquals(200.0D, field.bodyWater(inside) + field.bodyWater(outside), 1.0E-6D);
    }

    private static ShipGeometry.Result recessedDoor(BlockPos door, boolean open) {
        var shell = new HashSet<BlockPos>();
        for (BlockPos cell : BlockPos.betweenClosed(2, 0, 0, 6, 3, 4)) {
            if (cell.getX() == 2 || cell.getX() == 6 || cell.getY() == 0 || cell.getY() == 3
                    || cell.getZ() == 0 || cell.getZ() == 4) {
                shell.add(cell.immutable());
            }
        }
        // 船身下面的装甲向西伸，舱门位于内缩的外墙上。
        shell.add(new BlockPos(0, 0, 2));
        shell.add(new BlockPos(1, 0, 2));
        return ShipGeometry.scanShip(door.east(2), 8, shell::contains, new ShipGeometry.Probe() {
            @Override public boolean isSolid(BlockPos cell) { return shell.contains(cell); }
            @Override public boolean isWater(BlockPos cell) { return false; }
            @Override public boolean isDoorway(BlockPos cell) { return cell.equals(door) || cell.equals(door.above()); }
            @Override public boolean isOpenDoorway(BlockPos cell) { return open && isDoorway(cell); }
            @Override public Direction.Axis doorwayAxis(BlockPos cell) { return Direction.Axis.X; }
        }, 0, shell::contains);
    }

    @Test
    @DisplayName("上舱过水时同步舱口落水和落点，关门后停止")
    void fallingWaterTracksActualTransfer() {
        BlockPos hatch = new BlockPos(0, 3, 0);
        var upper = new ShipGeometry.Compartment(Set.of(hatch, hatch.above()), 0);
        var lower = new ShipGeometry.Compartment(Set.of(hatch.below(), hatch.below(2),
                hatch.below(3)), 0);
        var bounds = new ShipGeometry.Bounds(0, 0, 0, 0, 4, 0);
        var geometry = new ShipGeometry.Result(List.of(upper, lower), List.of(), 0, 5,
                bounds, Set.of(), List.of(new ShipGeometry.DoorLink(hatch, 0, 1, true)), Set.of());
        var state = new ShipState(geometry, MachineCount.EMPTY);
        state.simulation().flood(0, 200.0D);
        state.simulation().tickWaterFlow(0.1D);

        var packet = ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0);
        assertEquals(ShipWaterStatePacket.STREAM_STRIDE, packet.streams().length);
        assertEquals(3.0F, packet.streams()[1]);
        assertEquals(state.simulation().fluid().bodyLevel(1), packet.streams()[4], 1.0E-6D);
        assertTrue(packet.streams()[6] > 0.0F);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            assertArrayEquals(packet.streams(), ShipWaterStatePacket.decode(buffer).streams());
        } finally {
            buffer.release();
        }

        state.replaceGeometry(new ShipGeometry.Result(List.of(upper, lower), List.of(), 0, 5,
                bounds, Set.of(), List.of(new ShipGeometry.DoorLink(hatch, 0, 1, false)), Set.of()));
        state.simulation().tickWaterFlow(0.1D);
        assertEquals(0, ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0).streams().length);
        assertEquals(200.0D, state.simulation().waterUnits(), 1.0E-6D);
    }
}
