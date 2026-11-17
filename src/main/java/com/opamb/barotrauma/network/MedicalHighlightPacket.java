package com.opamb.barotrauma.network;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：请求给某个玩家挂/摘发光高光（H 键长按选队友时用）。
 *
 * <p><b>为什么必须走服务端</b>：1.20.1 的 {@code Entity.isCurrentlyGlowing()} 在客户端读的是
 * <b>shared flag 6</b>（{@code getSharedFlag(6)}），不是药水效果——客户端本地
 * {@code addEffect(GLOWING)} 既不会写这个 flag，也不会同步，所以**看不到描边**。
 * 只有服务端 {@code addEffect} 才会通过实体元数据把 flag 推到客户端。</p>
 *
 * <p>不带时长参数：服务端固定挂 {@link #DURATION_TICKS} tick，客户端按住期间每 tick
 * 续期即可（玩家松手后会自然过期，即使漏发一次移除请求也不会永久残留）。</p>
 */
public record MedicalHighlightPacket(java.util.UUID target, boolean on) {

    /** 一次续期管多久（tick）。客户端每 tick 会重发，留余量防丢包。 */
    private static final int DURATION_TICKS = 40;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(target);
        buf.writeBoolean(on);
    }

    public static MedicalHighlightPacket decode(FriendlyByteBuf buf) {
        return new MedicalHighlightPacket(buf.readUUID(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer requester = context.getSender();
            if (requester == null) {
                return;
            }
            ServerPlayer targetPlayer = requester.server.getPlayerList().getPlayer(target);
            if (targetPlayer == null) {
                return;
            }
            // 只允许在治疗距离内打高光：防止被用来隔墙标记远处玩家（原作没有这类能力）。
            if (requester != targetPlayer
                    && (targetPlayer.level() != requester.level()
                        || requester.distanceToSqr(targetPlayer)
                                > com.opamb.barotrauma.medical.MedicalTuning.TREAT_RANGE_SQR)) {
                return;
            }
            if (on) {
                targetPlayer.addEffect(new MobEffectInstance(
                        MobEffects.GLOWING, DURATION_TICKS, 0, true, false));
            } else {
                targetPlayer.removeEffect(MobEffects.GLOWING);
            }
        });
        context.setPacketHandled(true);
    }
}
