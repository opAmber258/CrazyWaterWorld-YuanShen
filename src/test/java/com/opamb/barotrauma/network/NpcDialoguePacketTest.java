package com.opamb.barotrauma.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 站务员对话包/要活儿包的编解码。 */
class NpcDialoguePacketTest {

    @Test
    void 对话包往返() {
        NpcDialoguePacket packet = new NpcDialoguePacket(
                NpcDialoguePacket.GRANTED, 3, new int[]{1, 2, 4});
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            NpcDialoguePacket decoded = NpcDialoguePacket.decode(buf);
            assertEquals(packet.phase(), decoded.phase());
            assertEquals(packet.node(), decoded.node());
            assertArrayEquals(packet.missionIds(), decoded.missionIds());
        } finally {
            buf.release();
        }
    }

    @Test
    void 要活儿包往返() {
        NpcTalkPacket packet = new NpcTalkPacket(NpcTalkPacket.ASK_FOR_WORK);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            assertEquals(packet, NpcTalkPacket.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void 超长任务列表被拒绝() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(NpcDialoguePacket.GRANTED);
            buf.writeInt(0);
            buf.writeVarInt(100000);
            assertThrows(IllegalArgumentException.class, () -> NpcDialoguePacket.decode(buf));
        } finally {
            buf.release();
        }
    }
}
