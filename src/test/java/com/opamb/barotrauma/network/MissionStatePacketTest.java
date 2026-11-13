package com.opamb.barotrauma.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 任务状态包（进行中 + 池 + 当前站报价）的编解码。 */
class MissionStatePacketTest {

    @Test
    void 任务状态包往返() {
        MissionStatePacket packet = new MissionStatePacket(
                1200, 4200L,
                new int[]{1}, new int[]{2}, new long[]{9000L}, new int[]{3}, new int[]{140},
                new int[]{4},
                new int[]{1, 2, 3}, new int[]{0, 2, 1}, new boolean[]{false, true, false},
                new int[]{1, 2}, new int[]{5, 6});
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            MissionStatePacket decoded = MissionStatePacket.decode(buf);
            assertEquals(packet.credits(), decoded.credits());
            assertEquals(packet.nowGameTime(), decoded.nowGameTime());
            assertArrayEquals(packet.activeIds(), decoded.activeIds());
            assertArrayEquals(packet.activeStates(), decoded.activeStates());
            assertArrayEquals(packet.activeDeadlineAt(), decoded.activeDeadlineAt());
            assertArrayEquals(packet.activeCargoTotal(), decoded.activeCargoTotal());
            assertArrayEquals(packet.activeCargoPerCrate(), decoded.activeCargoPerCrate());
            assertArrayEquals(packet.activeDest(), decoded.activeDest());
            assertArrayEquals(packet.poolIds(), decoded.poolIds());
            assertArrayEquals(packet.poolStates(), decoded.poolStates());
            assertArrayEquals(packet.poolTaken(), decoded.poolTaken());
            assertArrayEquals(packet.offerIds(), decoded.offerIds());
            assertArrayEquals(packet.offerDests(), decoded.offerDests());
        } finally {
            buf.release();
        }
    }

    @Test
    void 超长进行中列表被拒绝() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(0);
            buf.writeLong(0L);
            buf.writeVarInt(100000);
            assertThrows(IllegalArgumentException.class, () -> MissionStatePacket.decode(buf));
        } finally {
            buf.release();
        }
    }
}
