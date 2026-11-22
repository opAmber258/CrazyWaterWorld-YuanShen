package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.item.TwoHanded;
import com.opamb.barotrauma.world.AirOceanSwimmer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
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

    @Shadow
    private boolean clientIsFloating;

    /** 普通游可在空气海域悬停。用服务端水位判断这次移动是否属于水中移动。 */
    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void barotrauma$acceptWaterMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (clientIsFloating && AirOceanSwimmer.canUseAirOceanMovement(player)
                && AirOceanSwimmer.canSwimAtDepth(player, ((AirOceanSwimmer) player).barotrauma$isInAirOceanWater())) {
            clientIsFloating = false;
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void barotrauma$blockOffhandSwap(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && TwoHanded.isCarrying(player)) {
            ci.cancel();
        }
    }
}
