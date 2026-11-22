package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.medical.MedicalDamageTypes;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.SwimStatePacket;
import com.opamb.barotrauma.world.AirOceanSwimmer;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 空气海域的玩家状态：游泳姿态同步、深水挤压、摔落豁免。
 *
 * <p>呼吸不再走原版空气值：{@link #onLivingBreathe} 把玩家的空气永远维持在满，
 * 原版氧气条/溺水伤害/受击晃屏因此都不会出现。真正的缺氧是医疗服务系统里的
 * {@code oxygenlow}，由 {@code MedicalEvents} 按原作 {@code CharacterHealth.UpdateOxygen}
 * 的 -5/+10 每秒增减。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UnderwaterSurvival {

    private UnderwaterSurvival() {
    }

    /** 深水挤压的节流计数（60 tick = 3 秒）。 */
    private static int pressureAccumulator;

    private static final Map<UUID, SwimSync> swimStates = new HashMap<>();

    private static final class SwimSync {
        boolean swimMovement;
        SwimStatePacket lastPacket;
        long lastLogTick = -10L;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        swimStates.remove(event.getEntity().getUUID());
    }

    /** 服务端每 tick：同步游泳姿态、恢复空气值并处理深水挤压。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel serverLevel = player.serverLevel();
        AirOceanSwimmer swimmer = (AirOceanSwimmer) player;
        SwimSync swim = swimStates.computeIfAbsent(player.getUUID(), id -> new SwimSync());
        if (event.phase == TickEvent.Phase.START) {
            // 在原版更新姿态之前判定能否游泳。浅水保持原来的走路和碰撞。
            swim.swimMovement = AirOceanSwimmer.canUseAirOceanMovement(player)
                    && AirOceanSwimmer.canSwimAtDepth(player, swim.swimMovement);
            swimmer.barotrauma$setAirOceanSwimState(swim.swimMovement, player.isSprinting());
            if (swim.swimMovement) {
                player.resetFallDistance();
            }
            return;
        }
        // 船下沉/移动时，游泳中的玩家不算"站在船上"，VS2 的 EntityDragger 不会
        // 拖他，船体就会从玩家身上扫过去（用户实测：贴着天花板游，结果穿到上层）。
        // 在舱内每 tick 标记为随船，并把这个船 id 下发给客户端，让客户端用同一个
        // id 标记本地玩家——两边各算一次会在贴墙/边界处一半拖一半不拖，导致回拉。
        long dragShipId = -1L;
        if (!player.isSpectator() && com.opamb.barotrauma.ship.vs.VsSupport.available()
                && com.opamb.barotrauma.ship.vs.VsServerDrag.isInsideCompartment(
                        serverLevel, player)) {
            Long activeShip = com.opamb.barotrauma.ship.vs.VsState
                    .queryActiveShipId(serverLevel);
            if (activeShip != null) {
                dragShipId = activeShip;
                com.opamb.barotrauma.ship.vs.VsServerDrag.keepDragged(
                        serverLevel, player, dragShipId);
            }
        }
        // 乘员吸气：没有潜水服就从所在舱室扣氧（原作 Hull.OxygenConsumptionSpeed）。
        com.opamb.barotrauma.ship.ShipBreathing.breathe(player);
        // 只同步变化。原版疾跑输入走 START/STOP_SPRINTING，游泳条件由服务端决定。
        SwimStatePacket packet = new SwimStatePacket(swim.swimMovement,
                swimmer.barotrauma$isAirOceanSwimming(), dragShipId);
        if (!packet.equals(swim.lastPacket)) {
            if (swim.lastPacket != null && swim.lastPacket.swimPose() != packet.swimPose()
                    && serverLevel.getGameTime() - swim.lastLogTick >= 10L) {
                swim.lastLogTick = serverLevel.getGameTime();
                BarotraumaMod.LOGGER.info("[vs] 疾游={} 可游={} 疾跑={} y={}",
                        packet.swimPose(), swim.swimMovement, player.isSprinting(), player.getY());
            }
            swim.lastPacket = packet;
            ModNetwork.sendToPlayer(player, packet);
        }
        if (player.isCreative() || player.isSpectator()
                || player.hasEffect(MobEffects.WATER_BREATHING)) {
            refill(player);
            return;
        }
        if (OceanEnvironment.isOceanWaterAt(player.level(), player.getEyePosition())) {
            // 空气海域没有真实水，玩家落底时不能吃摔落伤害；持续清零坠落距离。
            player.resetFallDistance();
            // P3：深水挤压——300m 以下裸潜每 3 秒 1 点（任务最深 600m，出舱拿黑匣子要快）
            double depth = -player.getEyePosition().y;
            pressureAccumulator++;
            if (depth > com.opamb.barotrauma.ship.ShipPressure.playerSafeDepth(player)
                    && pressureAccumulator >= 60) {
                pressureAccumulator = 0;
                var holder = player.level().registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(MedicalDamageTypes.PRESSURE);
                player.hurt(new DamageSource(holder),
                        com.opamb.barotrauma.ship.ShipPressure.PLAYER_DAMAGE_PER_SECOND * 3.0F);
            }
        } else {
            refill(player);
        }
    }

    /**
     * 原版空气值不再参与缺氧判定。
     *
     * <p>用户指令：删掉原版氧气条，缺氧只走医疗服务系统。这里把所有玩家的
     * {@code LivingBreatheEvent} 都改成"能呼吸、回满空气"——原版空气值永远满，
     * 于是不会出现氧气泡条、{@code LivingDrownEvent}、溺水扣血和受击晃屏。</p>
     *
     * <p>真正的氧气模型在 {@code MedicalEvents}：按原作
     * {@code CharacterHealth.UpdateOxygen} 的 -5 / +10 每秒增减 {@code oxygenlow}
     * 强度（潜水服没瓶、泡在海里、真实水里都算"无可用氧"）。</p>
     */
    @SubscribeEvent
    public static void onLivingBreathe(net.minecraftforge.event.entity.living.LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        event.setCanBreathe(true);
        event.setCanRefillAir(true);
        event.setConsumeAirAmount(0);
    }

    /** 空气海域里取消原版摔落伤害（玩家实际落点是海底）。 */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player
                && OceanEnvironment.isSubmerged(player.level(), player.position())) {
            event.setCanceled(true);
        }
    }

    /** 恢复氧气值（哨站、干舱或呼吸豁免时调用）。 */
    private static void refill(Player player) {
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 4));
        }
    }
}
