package com.opamb.barotrauma.network;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：请求给某个玩家挂/续发光高光（H 键长按选队友时用）。
 *
 * <p><b>为什么必须走服务端</b>：1.20.1 的 {@code Entity.isCurrentlyGlowing()} 在客户端读的是
 * <b>shared flag 6</b>（{@code getSharedFlag(6)}），不是药水效果——客户端本地
 * {@code addEffect(GLOWING)} 既不会写这个 flag，也不会同步，所以**看不到描边**。
 * 只有服务端 {@code addEffect} 才会通过实体元数据把 flag 推到客户端。</p>
 *
 * <p>**只有"开"，没有"关"**：发光效果是共享的（倒地系统也在用同一个
 * {@code MobEffects.GLOWING}），无条件 {@code removeEffect} 会把别人的高光一起抹掉——
 * 比如两名救人者同时长按选中同一目标，其中一个松手就会清掉另一个的高光，也会打断
 * 倒地玩家的标记。所以这里**只续期不主动删**：客户端松手后不再请求，效果在
 * {@link #DURATION_TICKS} 内自然过期，语义上等价于"停止续期"。</p>
 */
public record MedicalHighlightPacket(java.util.UUID target) {

    /** 一次续期管多久（tick）。客户端每 tick 会重发，留余量防丢包。 */
    private static final int DURATION_TICKS = 40;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(target);
    }

    public static MedicalHighlightPacket decode(FriendlyByteBuf buf) {
        return new MedicalHighlightPacket(buf.readUUID());
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
            targetPlayer.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING, DURATION_TICKS, 0, true, false));
        });
        context.setPacketHandled(true);
    }
}
