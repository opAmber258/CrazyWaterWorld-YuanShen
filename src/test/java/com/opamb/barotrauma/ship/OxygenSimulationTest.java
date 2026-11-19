package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 舱室氧气：制氧机 → 通风口 → 舱室；乘员呼吸只扣自己所在舱室。
 *
 * <p>数值来自原作 {@code OxygenGenerator}（400×100 原始单位/秒）与
 * {@code Hull.OxygenConsumptionSpeed=700}，按 1 格 = 95.64 原始单位折算成
 * {@link ShipSimulation#O2_GENERATOR_OUTPUT} / {@link ShipSimulation#O2_BREATH_RATE}。</p>
 */
class OxygenSimulationTest {

    /** 5×3×3 的封闭方盒：内部 3×1×1 = 3 格，一个舱室，满氧 300 单位。 */
    private static Set<BlockPos> cubeSolid() {
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    boolean shell = Math.abs(x) == 2 || Math.abs(y) == 1 || Math.abs(z) == 1;
                    if (shell) {
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return solid;
    }

    private static ShipSimulation singleCompartment() {
        return simulation(cubeSolid());
    }

    /**
     * 7×3×3 的方盒，内部用 x=1 的隔墙分成两间：
     * x∈[-2,0] 是 9 格的大舱，x=2 是 3 格的小舱。
     */
    private static ShipSimulation twoCompartments() {
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    boolean shell = Math.abs(x) == 3 || Math.abs(y) == 1 || Math.abs(z) == 1;
                    boolean wall = x == 1 && y == 0;
                    if (shell || wall) {
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return simulation(solid);
    }

    private static ShipGeometry.Result scan(Set<BlockPos> solid) {
        ShipGeometry.Probe probe = new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return solid.contains(pos);
            }

            @Override
            public boolean isWater(BlockPos pos) {
                return false;
            }
        };
        Predicate<BlockPos> isShipBlock = solid::contains;
        return ShipGeometry.scanShip(BlockPos.ZERO, 8, isShipBlock, probe);
    }

    private static ShipSimulation simulation(Set<BlockPos> solid) {
        return new ShipSimulation(scan(solid), MachineCount.EMPTY);
    }

    private static final double EPS = 0.05D;

    @Test
    void 制氧机只给有通风口的舱室补氧() {
        ShipSimulation sim = singleCompartment();
        int compartment = sim.bodyOf(new BlockPos(0, 0, 0));
        assertTrue(compartment >= 0, "方盒内部应该是一个封闭舱室");
        sim.breathe(compartment, 10.0D);
        double drained = sim.oxygenUnits(compartment);
        sim.reportOxygenSource(new BlockPos(1, 0, 1), ShipSimulation.O2_GENERATOR_OUTPUT);
        sim.reportVent(new BlockPos(0, 0, 0), compartment, true);
        sim.tick(0.1D);
        assertEquals(ShipSimulation.O2_GENERATOR_OUTPUT * 0.1D,
                sim.oxygenUnits(compartment) - drained, EPS);
    }

    @Test
    void 没有通风口时不产氧() {
        ShipSimulation sim = singleCompartment();
        int compartment = sim.bodyOf(new BlockPos(0, 0, 0));
        sim.breathe(compartment, 10.0D);
        double drained = sim.oxygenUnits(compartment);
        sim.reportOxygenSource(new BlockPos(1, 0, 1), ShipSimulation.O2_GENERATOR_OUTPUT);
        sim.tick(0.1D);
        // 原作：制氧机只通过通风口送氧；没有通风口就只有自然损耗
        assertEquals(-0.0031D * 0.1D, sim.oxygenUnits(compartment) - drained, 0.01D);
    }

    @Test
    void 有通风口时未通风的舱室拿不到氧() {
        ShipSimulation sim = twoCompartments();
        int big = sim.bodyOf(new BlockPos(-2, 0, 0));
        int small = sim.bodyOf(new BlockPos(2, 0, 0));
        sim.breathe(big, 10.0D);
        sim.breathe(small, 10.0D);
        double smallBefore = sim.oxygenUnits(small);
        sim.reportOxygenSource(new BlockPos(0, 0, 1), ShipSimulation.O2_GENERATOR_OUTPUT);
        // 只给大舱接通风口
        sim.reportVent(new BlockPos(-2, 0, 0), big, true);
        sim.tick(0.1D);
        assertEquals(smallBefore, sim.oxygenUnits(small), EPS);
    }

    @Test
    void 泡水的通风口不送氧() {
        ShipSimulation sim = singleCompartment();
        int compartment = sim.bodyOf(new BlockPos(0, 0, 0));
        sim.breathe(compartment, 10.0D);
        double drained = sim.oxygenUnits(compartment);
        sim.reportOxygenSource(new BlockPos(1, 0, 1), ShipSimulation.O2_GENERATOR_OUTPUT);
        sim.reportVent(new BlockPos(0, 0, 0), compartment, false);
        sim.tick(0.1D);
        assertEquals(0.0D, sim.oxygenUnits(compartment) - drained, EPS);
    }

    @Test
    void 多台制氧机流量相加() {
        ShipSimulation sim = singleCompartment();
        int compartment = sim.bodyOf(new BlockPos(0, 0, 0));
        sim.breathe(compartment, 10.0D);
        double drained = sim.oxygenUnits(compartment);
        sim.reportOxygenSource(new BlockPos(1, 0, 1), ShipSimulation.O2_GENERATOR_OUTPUT / 2.0D);
        sim.reportOxygenSource(new BlockPos(-1, 0, -1), ShipSimulation.O2_GENERATOR_OUTPUT / 2.0D);
        sim.reportVent(new BlockPos(0, 0, 0), compartment, true);
        sim.tick(0.1D);
        assertEquals(ShipSimulation.O2_GENERATOR_OUTPUT * 0.1D,
                sim.oxygenUnits(compartment) - drained, EPS);
    }

    @Test
    void 制氧按有通风口的舱室体积分配() {
        ShipSimulation sim = twoCompartments();
        int big = sim.bodyOf(new BlockPos(-2, 0, 0));
        int small = sim.bodyOf(new BlockPos(2, 0, 0));
        assertTrue(big >= 0 && small >= 0 && big != small, "隔墙应该分出两个舱室");
        sim.breathe(big, 10.0D);
        sim.breathe(small, 10.0D);
        double bigBefore = sim.oxygenUnits(big);
        double smallBefore = sim.oxygenUnits(small);
        sim.reportOxygenSource(new BlockPos(0, 0, 1), ShipSimulation.O2_GENERATOR_OUTPUT);
        sim.reportVent(new BlockPos(-2, 0, 0), big, true);
        sim.reportVent(new BlockPos(2, 0, 0), small, true);
        sim.tick(0.1D);
        double bigGain = sim.oxygenUnits(big) - bigBefore;
        double smallGain = sim.oxygenUnits(small) - smallBefore;
        // 9 格 : 3 格 = 3 : 1
        assertEquals(3.0D, bigGain / smallGain, 0.05D);
    }

    @Test
    void 呼吸只扣自己所在舱室() {
        ShipSimulation sim = twoCompartments();
        int big = sim.bodyOf(new BlockPos(-2, 0, 0));
        int small = sim.bodyOf(new BlockPos(2, 0, 0));
        double bigBefore = sim.oxygenUnits(big);
        double smallBefore = sim.oxygenUnits(small);
        sim.breathe(big, 10.0D);
        assertEquals(ShipSimulation.O2_BREATH_RATE * 10.0D, bigBefore - sim.oxygenUnits(big), 1.0E-6);
        assertEquals(smallBefore, sim.oxygenUnits(small), 1.0E-6);
    }

    @Test
    void 氧气不会扣成负数也不会超过舱容() {
        ShipSimulation sim = singleCompartment();
        int compartment = sim.bodyOf(new BlockPos(0, 0, 0));
        sim.breathe(compartment, 10000.0D);
        assertEquals(0.0D, sim.oxygenUnits(compartment), 1.0E-6);
        sim.reportOxygenSource(new BlockPos(1, 0, 1), 100000.0D);
        sim.reportVent(new BlockPos(0, 0, 0), compartment, true);
        sim.tick(1.0D);
        assertEquals(300.0D, sim.oxygenUnits(compartment), 1.0E-6);
    }

    @Test
    void 舱室氧气百分比() {
        ShipSimulation sim = singleCompartment();
        int compartment = sim.bodyOf(new BlockPos(0, 0, 0));
        assertEquals(100.0D, sim.oxygenPercent(compartment), 1.0E-6);
        sim.breathe(compartment, 10.0D);
        assertEquals((300.0D - 73.2D) / 300.0D * 100.0D, sim.oxygenPercent(compartment), 1.0E-6);
    }

    @Test
    void 开门重扫后氧气不回满() {
        Set<BlockPos> solid = cubeSolid();
        ShipState state = new ShipState(scan(solid), MachineCount.EMPTY);
        int compartment = state.compartmentAt(new BlockPos(0, 0, 0));
        assertTrue(compartment >= 0);
        state.simulation().breathe(compartment, 10.0D);
        double before = state.simulation().oxygenUnits(compartment);
        // 同几何重扫（开门/关门/粘方块都会走这条路）
        state.replaceGeometry(scan(solid));
        assertEquals(before, state.simulation().oxygenUnits(compartment), EPS);
    }
}
