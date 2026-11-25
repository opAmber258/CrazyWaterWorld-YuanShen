package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.LightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 灯具面板改颜色/开关。
 *
 * <p>照原作 {@code LightColor} 的 RGBA：RGB 是色相，A 是发光强度，
 * 服务端校验玩家距离后写进方块实体，再由它按电压重算方块光照等级。</p>
 */
public record LightColorPacket(BlockPos pos, int rgb, int alpha, boolean lit) {

    /** 面板打开时的最大交互距离（格）。 */
    private static final double MAX_DISTANCE_SQR = 64.0D;

    public static void encode(LightColorPacket message, FriendlyByteBuf buf) {
        buf.writeBlockPos(message.pos);
        buf.writeInt(message.rgb & 0xFFFFFF);
        buf.writeByte(message.alpha & 0xFF);
        buf.writeBoolean(message.lit);
    }

    public static LightColorPacket decode(FriendlyByteBuf buf) {
        return new LightColorPacket(buf.readBlockPos(), buf.readInt() & 0xFFFFFF,
                buf.readUnsignedByte(), buf.readBoolean());
    }

    public static void handle(LightColorPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.isSpectator()
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!(level.getBlockEntity(message.pos) instanceof LightBlockEntity light)) {
                return;
            }
            Vec3 center = RepairActionPacket.worldCenter(level, message.pos);
            if (center == null || player.distanceToSqr(center) > MAX_DISTANCE_SQR) {
                return;
            }
            light.applyColor(message.rgb, message.alpha, message.lit);
        });
        context.setPacketHandled(true);
    }
}
