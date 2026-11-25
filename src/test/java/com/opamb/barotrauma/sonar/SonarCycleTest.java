package com.opamb.barotrauma.sonar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class SonarCycleTest {
    @Test
    void activeReturnsArriveByWorldDistanceAndFade() {
        SonarPulse pulse = new SonarPulse(100, new BlockPos(400, -30, 80), 100,
                SonarScan.Mode.ACTIVE, Vec3.ZERO, new int[0]);
        int near = SonarScan.pack(SonarScan.TYPE_CONTACT, 0, 0, 25);
        int far = SonarScan.pack(SonarScan.TYPE_ORE, 0, 0, 100);

        assertEquals(0.0F, pulse.fadeTimer(near, 109.9, 0));
        assertEquals(2.0F, pulse.fadeTimer(near, 110, 0));
        assertEquals(1.55F, pulse.fadeTimer(near, 128, 0), 0.001F);
        assertEquals(0.0F, pulse.fadeTimer(far, 139.9, 0));
        assertEquals(2.0F, pulse.fadeTimer(far, 140, 0));
        assertEquals(0.0F, pulse.fadeTimer(far, 220, 0));
        assertEquals(0.5F, pulse.progress(120));
    }

    @Test
    void terrainEchoesHaveIndependentDistanceAndDamageBasedLifetimes() {
        int near = SonarScan.pack(SonarScan.TYPE_TERRAIN, 20, 0, 0);
        int far = SonarScan.pack(SonarScan.TYPE_TERRAIN, 75, 0, 0);
        SonarPulse pulse = new SonarPulse(100, BlockPos.ZERO, 100, SonarScan.Mode.ACTIVE,
                Vec3.ZERO, new int[]{near, far});
        assertTrue(pulse.fadeTimer(near, 148, 0) > pulse.fadeTimer(far, 148, 0));
        assertEquals(0.0F, pulse.fadeTimer(far, 180, 0));
        assertTrue(pulse.fadeTimer(near, 180, 0) > 0.0F);
        assertEquals(0.0F, pulse.fadeTimer(near, 180, 1));
    }

    @Test
    void sharedPollsDoNotRescanAndSettingsApplyToNextPulse() {
        SonarCycle cycle = new SonarCycle();
        AtomicInteger scans = new AtomicInteger();
        Supplier<SonarScan.Frame> scan = () -> {
            scans.incrementAndGet();
            return new SonarScan.Frame(cycle.range(), new int[0]);
        };
        BlockPos launch = new BlockPos(30, 20, 10);
        BlockPos moved = launch.east();
        SonarPulse first = cycle.update(true, 10, launch, scan);
        cycle.configure(SonarScan.Mode.DIRECTIONAL, 120, new Vec3(-2, 0, 0));
        assertSame(first, cycle.update(true, 10, moved, scan));
        assertSame(first, cycle.update(true, 49, moved, scan));
        assertEquals(launch, first.origin());
        assertEquals(SonarScan.Mode.ACTIVE, first.mode());
        assertEquals(1, scans.get());

        SonarPulse next = cycle.update(true, 50, moved, scan);
        assertEquals(moved, next.origin());
        assertEquals(SonarScan.Mode.DIRECTIONAL, next.mode());
        assertEquals(new Vec3(-1, 0, 0), next.direction());
        assertEquals(120, next.range());
        assertEquals(2, scans.get());
    }

    @Test
    void offlineStopsScanningAndPassiveUsesShorterCadenceAndLessPower() {
        SonarCycle cycle = new SonarCycle();
        Supplier<SonarScan.Frame> scan = () -> new SonarScan.Frame(cycle.range(), new int[0]);
        assertEquals(100.0, cycle.sonarPowerKw());
        cycle.update(true, 10, BlockPos.ZERO, scan);
        assertNull(cycle.update(false, 50, BlockPos.ZERO, () -> {
            fail("An offline terminal must not scan");
            return null;
        }));
        cycle.configure(SonarScan.Mode.PASSIVE, 96, new Vec3(Float.NaN, 0, 0));
        SonarPulse passive = cycle.update(true, 55, BlockPos.ZERO, scan);
        assertEquals(new Vec3(0, 0, 1), passive.direction());
        assertSame(passive, cycle.update(true, 74, BlockPos.ZERO, scan));
        assertNotSame(passive, cycle.update(true, 75, BlockPos.ZERO, scan));
        assertEquals(10.0, cycle.sonarPowerKw());
        int point = SonarScan.pack(SonarScan.TYPE_TERRAIN, 60, 0, 0);
        assertEquals(passive.terrainSignal(point) * (1.0F - 60.0F / passive.range()),
                passive.fadeTimer(point, 55, 0), 0.001F);
    }

    @Test
    void directionalConeRejectsRearAndOffAxisReturns() {
        assertTrue(SonarScan.inCone(4, 0, 20, 0, 0, 1));
        assertFalse(SonarScan.inCone(6, 0, 20, 0, 0, 1));
        assertFalse(SonarScan.inCone(0, 6, 20, 0, 0, 1));
        assertFalse(SonarScan.inCone(0, 0, -20, 0, 0, 1));
    }
}
