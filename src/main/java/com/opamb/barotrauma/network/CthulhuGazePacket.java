package com.opamb.barotrauma.network;

import com.opamb.barotrauma.cthulhu.CthulhuPreset;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：应用「克苏鲁的注视」的某个预设 + 档位（开发者触发器）。
 *
 * <p>tier 0/1/2 = 弱/中/强，落成 MobEffect 的 amplifier。服务端校验创造模式或
 * 权限 ≥ 2，并清掉其它预设避免叠加。视觉强度由客户端按 amplifier 换算。</p>
 */
public record CthulhuGazePacket(int preset, int tier, int durationSeconds) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(preset);
        buf.writeVarInt(tier);
        buf.writeVarInt(durationSeconds);
    }

    public static CthulhuGazePacket decode(FriendlyByteBuf buf) {
        return new CthulhuGazePacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer user = context.getSender();
            if (user == null) {
                return;
            }
            if (!canUse(user)) {
                user.displayClientMessage(Component.literal("克苏鲁的注视触发器仅限创造模式/管理员"), true);
                return;
            }
            CthulhuPreset chosen = CthulhuPreset.byIndex(preset);
            if (chosen != null) {
                apply(user, chosen, Math.max(0, Math.min(2, tier)),
                        CthulhuPreset.clampDurationSeconds(durationSeconds));
            }
        });
        context.setPacketHandled(true);
    }

    /** 创造模式或权限 ≥ 2（与医疗调试器同一套限制）。 */
    public static boolean canUse(ServerPlayer player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    /** 挂上预设效果（先清掉其它预设，避免叠加导致模式/强度互相打架）。 */
    public static void apply(ServerPlayer player, CthulhuPreset chosen, int tier, int durationSeconds) {
        for (CthulhuPreset other : CthulhuPreset.values()) {
            if (other != chosen) {
                player.removeEffect(other.effect());
            }
        }
        player.addEffect(new MobEffectInstance(chosen.effect(), durationSeconds * 20, tier, false, false));
    }
}
