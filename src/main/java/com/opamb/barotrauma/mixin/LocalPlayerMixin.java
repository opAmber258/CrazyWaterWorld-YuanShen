package com.opamb.barotrauma.mixin;

import com.mojang.authlib.GameProfile;
import com.opamb.barotrauma.client.TurretClient;
import com.opamb.barotrauma.client.UnderwaterMovement;
import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 本地玩家的物品丢弃和假水疾跑。
 *
 * <p>原版 {@code LocalPlayer#drop} 会先在本地把当前快捷栏格清空再发包；抱着箱子时
 * 服务端丢的是副手箱子（{@code ServerPlayerMixin}），于是客户端那格被本地删掉、
 * 服务端又不会补发——看起来就是"快捷栏里的物品被丢掉了/消失了"。</p>
 *
 * <p>这里只发包、不动本地物品栏，等服务端删完再由槽位同步回来（差一个 tick，
 * 肉眼看不出来），两边永远一致。</p>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {

    protected LocalPlayerMixin(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Override
    public void travel(Vec3 input) {
        if (UnderwaterMovement.swimTravel((LocalPlayer) (Object) this, input)) {
            calculateEntityAnimation(false);
        } else {
            super.travel(input);
        }
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V"))
    private void barotrauma$updateAirOceanInput(CallbackInfo ci) {
        UnderwaterMovement.updateSwimInput((LocalPlayer) (Object) this);
    }

    /** Shift 在假水里控制下潜，水平游速不受陆地潜行倍率影响。 */
    @Redirect(method = "aiStep",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isMovingSlowly()Z"))
    private boolean barotrauma$slowOnlyOnLand(LocalPlayer player) {
        return player.isMovingSlowly() && !UnderwaterMovement.hasAirOceanWater(player);
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void barotrauma$serverAuthoritativeDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
        // 炮塔里 Q 是射击模式切换键；顺手把原版丢弃整个挡掉，避免 Q 同时丢物品。
        if (TurretClient.isActive()) {
            cir.setReturnValue(false);
            return;
        }
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
