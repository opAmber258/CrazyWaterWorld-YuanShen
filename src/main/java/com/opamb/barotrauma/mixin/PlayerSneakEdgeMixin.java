package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 水下按住 Shift 不再触发原版的"潜行防掉边"。
 *
 * <p>原版 {@code Player#maybeBackOffFromEdge} 在潜行且"认为自己在地面上"时，会调用
 * {@code level.noCollision(...)} 判断往哪走会掉下边缘；判断为会掉下去就把水平位移
 * 按 0.05 一路减到 0。VS2 的船体方块对这个世界坐标查询不可见（实测
 * {@code getBlockCollisions} 返回 0 个形状），于是它认为四面八方都会掉下去，
 * 按住 Shift 时水平位移直接被清零——玩家"卡在原地动不了"，而垂直方向它其实没碰。</p>
 *
 * <p>这里只在水里关掉这个保护：陆地上潜行防掉边仍然照原版。</p>
 */
@Mixin(Player.class)
public abstract class PlayerSneakEdgeMixin {

    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void barotrauma$noSneakEdgeGuardInWater(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        net.minecraft.world.phys.Vec3 sample = self.position().add(0.0D, 0.4D, 0.0D);
        // 服务端也会走 Entity#move → maybeBackOffFromEdge，而服务端的 isSubmerged 读不到
        // 方块船的舱内水面（那要查船核心），干舱会被当成水下。这里按 UnderwaterSurvival
        // 的同一套判定先把方块船干舱排除掉，避免服务端和客户端对潜行防掉边不一致。
        // 客户端已经通过 ShipWaterRenderer 的干燥区处理过方块船，不必再查一遍船核心。
        if (!self.level().isClientSide()
                && OceanEnvironment.isInsideBlockShipDryZone(self.level(), sample)) {
            return;
        }
        if (OceanEnvironment.isSubmerged(self.level(), sample)) {
            cir.setReturnValue(false);
        }
    }
}
