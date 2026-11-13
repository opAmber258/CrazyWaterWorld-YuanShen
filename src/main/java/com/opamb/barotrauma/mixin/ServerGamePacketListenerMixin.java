package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 抱着双手持物品时禁止 F 键换副手（会把箱子挤掉）。
 *
 * <p>滚轮/数字键的封锁在客户端做（{@code MinecraftMixin}/{@code InventoryMixin}），
 * 不依赖服务端丢包。</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void barotrauma$blockOffhandSwap(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && TwoHanded.isCarrying(player)) {
            ci.cancel();
        }
    }
}
