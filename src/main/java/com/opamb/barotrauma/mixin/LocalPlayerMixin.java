package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Q 键丢弃改成"服务端说了算"。
 *
 * <p>原版 {@code LocalPlayer#drop} 会先在本地把当前快捷栏格清空再发包；抱着箱子时
 * 服务端丢的是副手箱子（{@code ServerPlayerMixin}），于是客户端那格被本地删掉、
 * 服务端又不会补发——看起来就是"快捷栏里的物品被丢掉了/消失了"。</p>
 *
 * <p>这里只发包、不动本地物品栏，等服务端删完再由槽位同步回来（差一个 tick，
 * 肉眼看不出来），两边永远一致。</p>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void barotrauma$serverAuthoritativeDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        self.connection.send(new ServerboundPlayerActionPacket(
                all ? ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                        : ServerboundPlayerActionPacket.Action.DROP_ITEM,
                BlockPos.ZERO, Direction.DOWN));
        // 只用来决定要不要播挥手动作。
        cir.setReturnValue(!self.getInventory().getSelected().isEmpty()
                || TwoHanded.is(self.getInventory().offhand.get(0)));
    }
}
