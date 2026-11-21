package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.medical.MedicalDamageTypes;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /** 本模组设置过游泳标志的玩家，避免清掉原版真实水中的游泳状态。 */
    private static final Set<UUID> modSwimming = new HashSet<>();
    /** 每个玩家上一次同步给客户端的“脚部浸没”状态（变化才发包）。 */
    private static final java.util.Map<UUID, Boolean> lastSwimSync = new java.util.HashMap<>();
    /** 上一次同步的权威泳姿（变化才发包）。 */
    private static final java.util.Map<UUID, Boolean> lastSwimPoseSync = new java.util.HashMap<>();
    /** 上一次同步的随船拖拽船 id（变化才发包）。 */
    private static final java.util.Map<UUID, Long> lastDragSync = new java.util.HashMap<>();
    /** 低顶泳姿锁存：低顶立即锁，恢复站立要连续若干 tick 有余量，避免墙角抖动。 */
    private static final java.util.Map<UUID, PoseLatch> poseLatches = new java.util.HashMap<>();
    /** 脚部浸没锁存：进水/出水各用不同高度，避免停在水面时每 tick 翻转泳姿。 */
    private static final java.util.Map<UUID, WaterLatch> waterLatches = new java.util.HashMap<>();

    private static final class PoseLatch {
        boolean low;
        int standTicks;
        int missingTicks;
        int floorTicks;
        int offFloorTicks;
        int missingFloorTicks;
        boolean hasFloorState;
        Boolean floorStand;
        long lastLogTick;
    }

    private static final class WaterLatch {
        boolean submerged;
        int dryTicks;
    }

    /**
     * 脚部是否浸没，带 0.3 格回差和 6 tick 出水确认。
     *
     * <p>玩家停在水面时，脚正好在"脚上 0.4 格"的判定线上，浮沉一两厘米就会让
     * {@code isOceanWaterAt} 每 tick 翻一次，泳姿跟着站/游来回切（用户实测
     * "我都动没动"）。这里改成：水到脚上 0.40 格才算进水；脚上 0.10 格都干了、
     * 且连续 6 tick 才算真正出水，中间保持上一状态。</p>
     */
    private static boolean submergedAtFeet(ServerLevel level, Player player) {
        WaterLatch latch = waterLatches.computeIfAbsent(player.getUUID(), key -> new WaterLatch());
        net.minecraft.world.phys.Vec3 feet = player.position();
        boolean enter = OceanEnvironment.isOceanWaterAt(level, feet.add(0.0D, 0.40D, 0.0D));
        boolean keep = OceanEnvironment.isOceanWaterAt(level, feet.add(0.0D, 0.10D, 0.0D));
        if (enter) {
            latch.submerged = true;
            latch.dryTicks = 0;
            return true;
        }
        if (!keep) {
            // 20 tick：船在水面上下浮动时，水线会短暂落到脚面上方 0.1 格以下；
            // 6 tick 太短，泳姿会按“站→游→站”循环切换（用户实测停住不动也在切）。
            if (latch.submerged && ++latch.dryTicks >= 20) {
                latch.submerged = false;
                latch.dryTicks = 0;
            }
        } else {
            latch.dryTicks = 0;
        }
        return latch.submerged;
    }

    /**
     * 水里只有落到舱底/平台顶（连续 4 tick 贴着）才允许站起来。
     *
     * <p>浮在水中间、贴墙、贴天花板都返回 false → 保持游泳碰撞箱。这样贴墙时
     * 不会因为水面盒判定的细微差异在站/游之间来回切（用户实测“视角卡卡的”）。</p>
     *
     * <p>离地要连续 6 tick 才切回游泳，船数据暂时缺失（几何重扫）时保持上一状态
     * 最多 2 秒，避免浮在水面/重扫瞬间姿态闪跳。</p>
     *
     * @return null = 不在物理船里；true = 已贴地可站；false = 还在水里/刚贴地
     */
    private static Boolean floorReady(ServerLevel level, Player player) {
        PoseLatch latch = poseLatches.computeIfAbsent(player.getUUID(), key -> new PoseLatch());
        if (!com.opamb.barotrauma.ship.vs.VsSupport.available()) {
            latch.floorTicks = 0;
            latch.offFloorTicks = 0;
            return null;
        }
        Boolean near = com.opamb.barotrauma.ship.vs.VsServerDrag.nearFloor(
                level, player, 0.35D);
        if (near == null) {
            // 不在任何水面盒里，或者船几何正在重扫。只有确认人还在船体几何内时才
            // 保留上一状态（防重扫闪切）；已经离开船（例如游到舱外/船边）就直接
            // 返回 null，否则会把“贴着舱底”的锁存带到海里，在水里站起来。
            latch.floorTicks = 0;
            latch.offFloorTicks = 0;
            Boolean inside = com.opamb.barotrauma.ship.vs.VsState
                    .queryInsideCompartment(level, player.position());
            if (++latch.missingFloorTicks > 40 || !Boolean.TRUE.equals(inside)) {
                latch.hasFloorState = false;
                latch.floorStand = null;
                latch.missingFloorTicks = 0;
                return null;
            }
            return latch.hasFloorState ? latch.floorStand : null;
        }
        latch.missingFloorTicks = 0;
        // 物理船在倾斜/滚动时，船体局部坐标会有几厘米抖动，单靠 water box 的
        // 0.35 格距离会在边界来回翻。玩家实际踩在方块上（onGround）也算贴底，
        // 这样站在舱底不动时不会被判成“离地”而切回游泳。
        boolean nearNow = Boolean.TRUE.equals(near) || player.onGround();
        if (nearNow) {
            latch.offFloorTicks = 0;
            if (latch.floorTicks < 100) {
                latch.floorTicks++;
            }
            if (latch.floorTicks >= 4) {
                latch.hasFloorState = true;
                latch.floorStand = Boolean.TRUE;
            }
        } else {
            latch.floorTicks = 0;
            if (latch.offFloorTicks < 100) {
                latch.offFloorTicks++;
            }
            // 离地确认 10 tick：船体倾斜/玩家小跳时不要立刻切游泳，否则站/游
            // 碰撞箱会反复改尺寸（视角卡、位置回拉）。
            if (!latch.hasFloorState || latch.offFloorTicks >= 10) {
                latch.hasFloorState = true;
                latch.floorStand = Boolean.FALSE;
            }
        }
        return latch.hasFloorState ? latch.floorStand : null;
    }

    /**
     * 舱顶是否低到不能站。带锁存和余量：
     * <ul>
     *   <li>净高连 1.78 格都没有 → 立即锁成游泳；</li>
     *   <li>净高有 1.92 格以上、连续 8 tick → 才解锁回站立；</li>
     *   <li>中间是死区，保持上一状态；船数据暂时缺失最多容忍 2 秒。</li>
     * </ul>
     * 这样贴着墙/天花板时不会因为一两 tick 的位置差反复切换碰撞箱。
     */
    private static boolean lowCeiling(ServerLevel level, Player player, boolean submergedBody) {
        UUID id = player.getUUID();
        PoseLatch latch = poseLatches.computeIfAbsent(id, key -> new PoseLatch());
        if (player.isPassenger() || player.getAbilities().flying || player.isSpectator()
                || player.onClimbable() || player.isFallFlying()
                || !com.opamb.barotrauma.ship.vs.VsSupport.available()) {
            latch.low = false;
            latch.standTicks = 0;
            latch.missingTicks = 0;
            return false;
        }
        if (!submergedBody && !latch.low) {
            latch.standTicks = 0;
            latch.missingTicks = 0;
            return false;
        }
        double height = player.getDimensions(net.minecraft.world.entity.Pose.STANDING).height;
        Boolean room = com.opamb.barotrauma.ship.vs.VsServerDrag.standingRoom(
                level, player, height + 0.12D);
        Boolean tight = com.opamb.barotrauma.ship.vs.VsServerDrag.standingRoom(
                level, player, height - 0.02D);
        if (Boolean.FALSE.equals(tight)) {
            latch.low = true;
            latch.standTicks = 0;
            latch.missingTicks = 0;
            return true;
        }
        if (Boolean.TRUE.equals(room)) {
            latch.missingTicks = 0;
            if (latch.low && ++latch.standTicks >= 8) {
                latch.low = false;
                latch.standTicks = 0;
            }
            return latch.low;
        }
        // 死区（1.78~1.92）或无船数据：保持锁存，不来回切。
        latch.standTicks = 0;
        if (room == null) {
            if (++latch.missingTicks > 40) {
                latch.low = false;
                latch.missingTicks = 0;
            }
        } else {
            latch.missingTicks = 0;
        }
        return latch.low;
    }

    /** 退出世界时清掉这个玩家的锁存/同步缓存：重进时第一 tick 才会重新下发权威状态。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        modSwimming.remove(id);
        lastSwimSync.remove(id);
        lastSwimPoseSync.remove(id);
        lastDragSync.remove(id);
        poseLatches.remove(id);
        waterLatches.remove(id);
    }

    /** 服务端每 tick：同步游泳姿态、恢复空气值并处理深水挤压。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        net.minecraft.server.level.ServerLevel serverLevel =
                (net.minecraft.server.level.ServerLevel) player.level();
        // 船下沉/移动时，游泳中的玩家不算"站在船上"，VS2 的 EntityDragger 不会
        // 拖他，船体就会从玩家身上扫过去（用户实测：贴着天花板游，结果穿到上层）。
        // 在舱内每 tick 标记为随船，并把这个船 id 下发给客户端，让客户端用同一个
        // id 标记本地玩家——两边各算一次会在贴墙/边界处一半拖一半不拖，导致回拉。
        long dragShipId = -1L;
        if (com.opamb.barotrauma.ship.vs.VsSupport.available()
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
        // 疾跑 + 浸没 = 原版自由泳姿态（视情况同步给其他客户端；单人也是同一 JVM）。
        // 放在呼吸豁免之前：创造/水肺玩家同样要同步姿态，并清理本模组设置的游泳标志。
        // 判定用脚上方 0.4 格，和 UnderwaterMovement 保持一致；外面再套一层回差锁存，
        // 停在水面时不会因为上下浮几厘米就在站/游之间来回切。
        boolean submergedBody = submergedAtFeet(serverLevel, player);
        // 只有两种情况保持游泳碰撞箱：疾跑游泳，或者舱顶净高放不下 1.8 格
        // 站立碰撞箱。后者就是贴着天花板时切回站立会被挤到上层的原因；
        // 正常层高的水里仍然让玩家站起来（用户明确不要"全程站不起来"）。
        boolean lowCeiling = lowCeiling(serverLevel, player, submergedBody);
        boolean sprintSwim = submergedBody && player.isSprinting()
                && !player.isPassenger() && !player.getAbilities().flying
                && !player.isSpectator();
        // 水里离地就保持游泳：浮在水中间/贴墙/贴天花板都不切站立。只有连续
        // 4 tick 贴着舱底/平台顶、且头顶放得下，才允许站起来。
        Boolean floor = submergedBody ? floorReady(serverLevel, player) : null;
        boolean inShip = floor != null;
        boolean floorStand = Boolean.TRUE.equals(floor);
        // 水里的判定不再要求“在船里”：游在舱外/舱口边时同样保持游泳碰撞箱。
        // 之前 inShip=false（水面盒没覆盖到）就允许站立，而站姿 1.8 格撞到舱顶
        // 会被原版挤下去，表现就是贴天花板时“被吸住、走不动”。
        boolean swimPose = sprintSwim
                || (submergedBody && !floorStand)
                || lowCeiling;
        // 诊断：姿态变化时打一行（最多每 10 tick 一行），复测时能直接看出是谁在翻。
        PoseLatch diag = poseLatches.computeIfAbsent(player.getUUID(), key -> new PoseLatch());
        Boolean lastPose = lastSwimPoseSync.get(player.getUUID());
        if (lastPose != null && lastPose != swimPose
                && serverLevel.getGameTime() - diag.lastLogTick >= 10L) {
            diag.lastLogTick = serverLevel.getGameTime();
            BarotraumaMod.LOGGER.info(
                    "[vs] 泳姿切换：{}（water={} inShip={} nearFloor={} low={} sprint={} y={}）",
                    swimPose ? "游泳" : "站立", submergedBody, inShip, floor, lowCeiling,
                    sprintSwim, String.format("%.2f", player.getY()));
        }
        if (swimPose) {
            player.setSwimming(true);
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            modSwimming.add(player.getUUID());
        } else if (modSwimming.remove(player.getUUID())) {
            // 只清除本模组设置的标志；原版真实水中的游泳状态不动。
            if (player.isSwimming()) {
                player.setSwimming(false);
            }
        }
        // 权威脚部浸没 + 泳姿同步：变化才发包，客户端只跟随，不自己再算一遍。
        Boolean previousWater = lastSwimSync.put(player.getUUID(), submergedBody);
        Boolean previousPose = lastSwimPoseSync.put(player.getUUID(), swimPose);
        Long previousDrag = lastDragSync.put(player.getUUID(), dragShipId);
        if (previousWater == null || previousWater != submergedBody
                || previousPose == null || previousPose != swimPose
                || previousDrag == null || previousDrag != dragShipId) {
            com.opamb.barotrauma.network.ModNetwork.sendToPlayer((ServerPlayer) player,
                    new com.opamb.barotrauma.network.SwimStatePacket(
                            submergedBody, swimPose, dragShipId));
        }
        if (player.isCreative() || player.isSpectator()
                || player.hasEffect(MobEffects.WATER_BREATHING)) {
            refill(player);
            return;
        }
        boolean blockShipDry = OceanEnvironment.isInsideBlockShipDryZone(
                player.level(), player.getEyePosition());
        if (!blockShipDry && OceanEnvironment.isOceanWaterAt(player.level(), player.getEyePosition())) {
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
