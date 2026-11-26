package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：亮度调节器面板的“无限夜视”按钮。
 *
 * <p>服务端只认创造模式或权限 ≥ 2；当前已有无限夜视时再按一次移除，
 * 否则补一个不显示粒子的无限时长夜视。效果状态由服务端权威维护，
 * 面板按钮只根据客户端同步到的效果决定显示“给予 / 移除”。</p>
 */
public record NightVisionPacket() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static NightVisionPacket decode(FriendlyByteBuf buf) {
        return new NightVisionPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || (!player.isCreative() && !player.hasPermissions(2))) {
                return;
            }
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current != null && current.isInfiniteDuration()) {
                player.removeEffect(MobEffects.NIGHT_VISION);
                player.displayClientMessage(Component.translatable(
                        "brightness.night_vision.off"), true);
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                        MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
                player.displayClientMessage(Component.translatable(
                        "brightness.night_vision.on"), true);
            }
        });
        context.setPacketHandled(true);
    }
}
