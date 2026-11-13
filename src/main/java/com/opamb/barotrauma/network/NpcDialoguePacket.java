package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：站务员对话。
 *
 * <p>{@code phase} 决定客户端显示哪段台词和按钮；{@code missionIds} 是对话里要列出的
 * 任务（提问后：本次新发的或当前站已有的报价）。任务名字/目的地客户端从
 * {@link MissionStatePacket} 的缓存里查。</p>
 */
public record NpcDialoguePacket(int phase, int node, int[] missionIds) {

    /** 刚右键站务员：只打招呼。 */
    public static final int GREETING = 0;
    /** 这次访问刚发了一批新任务。 */
    public static final int GRANTED = 1;
    /** 这一轮的活儿已经发过了，列当前站已有的报价。 */
    public static final int ALREADY = 2;
    /** 站里没有可接的活儿。 */
    public static final int NO_MISSIONS = 3;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(phase);
        buf.writeInt(node);
        buf.writeVarInt(missionIds.length);
        for (int id : missionIds) {
            buf.writeVarInt(id);
        }
    }

    public static NpcDialoguePacket decode(FriendlyByteBuf buf) {
        int phase = buf.readVarInt();
        if (phase < 0 || phase > 16) {
            throw new IllegalArgumentException("npc dialogue phase");
        }
        int node = buf.readInt();
        int count = buf.readVarInt();
        if (count < 0 || count > 64) {
            throw new IllegalArgumentException("npc dialogue mission count");
        }
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = buf.readVarInt();
        }
        return new NpcDialoguePacket(phase, node, ids);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.NpcDialogueScreen.receive(this)));
        context.setPacketHandled(true);
    }
}
