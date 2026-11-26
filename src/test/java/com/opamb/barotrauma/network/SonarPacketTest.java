package com.opamb.barotrauma.network;

import com.opamb.barotrauma.sonar.SonarPulse;
import com.opamb.barotrauma.ship.NavigationMode;
import com.opamb.barotrauma.sonar.SonarScan;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.joml.Quaternionf;

import static org.junit.jupiter.api.Assertions.*;

class SonarPacketTest {
    @Test
    void statusRepliesCanOmitThePulse() {
        SonarDataPacket reply = roundTrip(null);
        assertNull(reply.pulse());
        assertEquals(69, reply.menuId());
        assertEquals(new BlockPos(105, -220, -80), reply.center());
        assertEquals(4, reply.settingsRevision());
        assertEquals(59L, reply.serverTick());
        assertEquals(NavigationMode.HOLD, NavigationMode.byId(reply.autopilot()));
        assertTrue(reply.online());
        assertEquals(128L, reply.shipId());
        assertEquals(new Vec3(-3.5, 1.5, 2.5), reply.shipAnchor());
        assertEquals(357, reply.waypointDistance());
        assertEquals(0.25F, reply.distortion());
        assertEquals(new Vec3(0.25, -0.375, 0.125), reply.hullOffset());
        assertEquals(new Quaternionf().rotationY(0.65F), reply.hullRotation());
    }

    @Test
    void aPulseCarriesLaunchPositionAndDirectionIndependentOfCurrentShipPose() {
        BlockPos launch = new BlockPos(99, -221, -72);
        int[] points = {SonarScan.pack(SonarScan.TYPE_ORE, -18, 0, 60)};
        SonarPulse pulse = new SonarPulse(30L, launch, 128, SonarScan.Mode.DIRECTIONAL,
                new Vec3(0, 0, 1), points);
        SonarDataPacket reply = roundTrip(pulse);
        assertEquals(launch, reply.pulse().origin());
        assertEquals(30L, reply.pulse().tick());
        assertEquals(128, reply.pulse().range());
        assertEquals(SonarScan.Mode.DIRECTIONAL, reply.pulse().mode());
        assertEquals(new Vec3(0, 0, 1), reply.pulse().direction());
        assertEquals(new Vec3(1, 0, 0), reply.direction());
        assertArrayEquals(points, reply.pulse().points());
    }

    private static SonarDataPacket roundTrip(SonarPulse pulse) {
        SonarDataPacket packet = new SonarDataPacket(96, new int[0], 100, 1, 0,
                SonarScan.Mode.DIRECTIONAL.id(), 220, 4, 2, NavigationMode.HOLD.ordinal(), false, 0, 0, 0,
                "", 0, -1, "", true, new Vec3(0.25, -0.375, 0.125), new Quaternionf().rotationY(0.65F),
                false, 0, 0, 0, "", 69, new BlockPos(105, -220, -80), 59, true,
                new Vec3(1, 0, 0), 4, pulse, 128L, new Vec3(-3.5, 1.5, 2.5), 357, 0.25F);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            SonarDataPacket result = SonarDataPacket.decode(buffer);
            assertFalse(buffer.isReadable());
            return result;
        } finally {
            buffer.release();
        }
    }
}
