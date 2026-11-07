package com.opamb.barotrauma.network;

import com.opamb.barotrauma.client.ClientMedicalCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：某玩家的完整病症行 + 未钳制 vitality（可为负）+ 倒地标志。
 *
 * <p>target 通常是接收者自己（每秒变化同步）；治疗面板打开时也可以是队友的快照
 * （由 {@link MedicalRequestPacket} 请求）。行格式与 {@code AfflictionContainer.rows()}
 * 一致（id|LIMB|strength、t|... 治疗行）。服务端在变化时才发送。</p>
 */
public record MedicalSyncPacket(UUID target, String[] rows, float vitality, boolean downed) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(target);
        buf.writeVarInt(rows.length);
        for (String row : rows) {
            buf.writeUtf(row, 256);
        }
        buf.writeFloat(vitality);
        buf.writeBoolean(downed);
    }

    public static MedicalSyncPacket decode(FriendlyByteBuf buf) {
        UUID target = buf.readUUID();
        int count = buf.readVarInt();
        String[] rows = new String[count];
        for (int i = 0; i < count; i++) {
            rows[i] = buf.readUtf(256);
        }
        float vitality = buf.readFloat();
        boolean downed = buf.readBoolean();
        return new MedicalSyncPacket(target, rows, vitality, downed);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientMedicalCache.accept(target, rows, vitality, downed));
        context.setPacketHandled(true);
    }

    /** 服务端辅助：把 target 的医疗快照定向发给 viewer（治疗面板请求的应答）。 */
    public static void respondSnapshot(ServerPlayer viewer, ServerPlayer target) {
        com.opamb.barotrauma.medical.affliction.AfflictionContainer container =
                com.opamb.barotrauma.medical.MedicalCapabilities.container(target);
        if (container == null) {
            return;
        }
        float raw = (float) container.rawVitality(
                com.opamb.barotrauma.medical.bridge.MedicalEvents.MAX_VITALITY);
        ModNetwork.sendToPlayer(viewer, new MedicalSyncPacket(target.getUUID(),
                container.rows().toArray(String[]::new), raw, raw <= 0));
    }
}
