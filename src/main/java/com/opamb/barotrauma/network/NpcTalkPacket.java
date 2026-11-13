package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：对话里选择“有什么活儿”。
 *
 * <p>服务端校验玩家确实站在站务员旁边，再发任务并回一个
 * {@link NpcDialoguePacket}（带这次发出的任务 id）。</p>
 */
public record NpcTalkPacket(int action) {

    /** 问站务员要活儿。 */
    public static final int ASK_FOR_WORK = 0;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
    }

    public static NpcTalkPacket decode(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        if (action < 0 || action > 64) {
            throw new IllegalArgumentException("npc talk action");
        }
        return new NpcTalkPacket(action);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getSender() != null) {
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null && action == ASK_FOR_WORK) {
                    com.opamb.barotrauma.mission.MissionNpc.askForWork(player);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
