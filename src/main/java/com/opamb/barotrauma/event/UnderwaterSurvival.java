package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 空气海域的呼吸规则：水下消耗氧气，回到哨站/未进水船舱后恢复。
 *
 * <p>因为海里没有真实水，原版不会自动扣氧气；这里补上同一套节奏
 * （约 15 秒耗尽，之后每约 1 秒掉 2 点伤害）。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UnderwaterSurvival {

    private UnderwaterSurvival() {
    }

    /** 深水挤压的节流计数（60 tick = 3 秒）。 */
    private static int pressureAccumulator;

    /** 本模组设置过游泳标志的玩家，避免清掉原版真实水中的游泳状态。 */
    private static final Set<UUID> modSwimming = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        // 疾跑 + 浸没 = 原版自由泳姿态（视情况同步给其他客户端；单人也是同一 JVM）。
        // 放在呼吸豁免之前：创造/水肺玩家同样要同步姿态，并清理本模组设置的游泳标志。
        // 判定用脚上方 0.4 格，和 UnderwaterMovement 保持一致。
        boolean submergedBody = OceanEnvironment.isSubmerged(player.level(),
                player.position().add(0.0D, 0.4D, 0.0D));
        boolean sprintSwim = submergedBody && player.isSprinting()
                && !player.isPassenger() && !player.getAbilities().flying
                && !player.isSpectator();
        if (sprintSwim) {
            player.setSwimming(true);
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            modSwimming.add(player.getUUID());
        } else if (modSwimming.remove(player.getUUID())) {
            // 只清除本模组设置的标志；原版真实水中的游泳状态不动。
            if (player.isSwimming()) {
                player.setSwimming(false);
            }
        }
        if (player.isCreative() || player.isSpectator()
                || player.hasEffect(MobEffects.WATER_BREATHING)) {
            refill(player);
            return;
        }
        boolean blockShipDry = OceanEnvironment.isInsideBlockShipDryZone(
                player.level(), player.getEyePosition());
        if (!blockShipDry && OceanEnvironment.isSubmerged(player.level(), player.getEyePosition())) {
            // 空气海域没有真实水，玩家落底时不能吃摔落伤害；持续清零坠落距离。
            player.resetFallDistance();
            player.setAirSupply(player.getAirSupply() - 1);
            if (player.getAirSupply() <= -20) {
                player.setAirSupply(0);
                player.hurt(player.damageSources().drown(), 2.0F);
            }
            // P3：深水挤压——300m 以下裸潜每 3 秒 1 点（任务最深 600m，出舱拿黑匣子要快）
            double depth = -player.getEyePosition().y;
            pressureAccumulator++;
            if (depth > com.opamb.barotrauma.ship.ShipPressure.PLAYER_SAFE_DEPTH
                    && pressureAccumulator >= 60) {
                pressureAccumulator = 0;
                player.hurt(player.damageSources().generic(), 
                        com.opamb.barotrauma.ship.ShipPressure.PLAYER_DAMAGE_PER_SECOND * 3.0F);
            }
        } else {
            refill(player);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player
                && OceanEnvironment.isSubmerged(player.level(), player.position())) {
            event.setCanceled(true);
        }
    }

    private static void refill(Player player) {
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 4));
        }
    }
}
