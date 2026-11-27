package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.ClientConfig;
import com.opamb.barotrauma.block.CoilgunBlock;
import com.opamb.barotrauma.block.FreeAimTurretBlock;
import com.opamb.barotrauma.entity.CoilgunBolt;
import com.opamb.barotrauma.ship.vs.VsClientTurret;
import com.opamb.barotrauma.block.HullBlock;
import com.opamb.barotrauma.block.TrackingTurretBlock;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.TurretAimPacket;
import com.opamb.barotrauma.network.TurretExitPacket;
import com.opamb.barotrauma.network.TurretFirePacket;
import com.opamb.barotrauma.network.TurretModePacket;
import com.opamb.barotrauma.network.CoilgunShotPacket;
import com.opamb.barotrauma.ship.CoilgunTrigger;
import com.opamb.barotrauma.network.TurretStatePacket;
import com.opamb.barotrauma.registry.ModParticles;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 炮塔视角的客户端状态机（原型）。
 *
 * <p>进入/退出完全由服务端的 {@link TurretStatePacket} 驱动；本地只负责：</p>
 * <ul>
 *   <li>把鼠标增量转成炮塔角度，并按每秒最大转角截断（“拖拽速度上限”）；</li>
 *   <li>接管相机位置与角度（{@link com.opamb.barotrauma.mixin.TurretCameraMixin}）；</li>
 *   <li>隐藏原版准星/热键栏/经验条，改画炮塔 HUD（{@link TurretHud}）；</li>
 *   <li>右键瞄准（FOV 缩放）、左键开火、左 Shift 退出。</li>
 * </ul>
 *
 * <p>两种炮塔共用服务端逻辑，区别只在鼠标输入：普通炮塔鼠标直接转视角；
 * 自由瞄准炮塔（{@link FreeAimTurretBlock}）先用鼠标推动一个可以拖到屏幕
 * 任意位置的光标，视角再以有限角速度跟随它。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TurretClient {

    /** 炮塔里按 Q 切换单发/全自动。默认 Q 与原版丢弃冲突，炮塔模式下由 mixin 拦掉丢弃。 */
    public static final KeyMapping TOGGLE_FIRE_MODE = new KeyMapping(
            "key.barotrauma.turret_fire_mode", GLFW.GLFW_KEY_Q, "key.categories.barotrauma");

    /** 本地防连点等待；真实射速以服务端 cooldown 为准（每 tick 回传）。 */
    private static final int LOCAL_COOLDOWN = 2;

    /** 自由瞄准：跟随角速度上限（度/秒）。 */
    private static final float FREE_AIM_MAX_SPEED = 80.0F;

    /** 光标靠近中心时线性减速的角度范围；避免最后几度还在硬转。 */
    private static final float FREE_AIM_EASE_DEGREES = 16.0F;

    /** 跟随速度的低通系数（1/s）：起步和收尾都不突变。 */
    private static final float FREE_AIM_ACCEL_PER_SECOND = 12.0F;

    /**
     * 鼠标移动 1 像素时光标在屏幕上移动的像素数。1.0 就是普通鼠标指针的跟手程度；
     * 按屏幕像素而不是原版角度灵敏度换算，宽屏/大 FOV 才不会把准心放大。
     */
    private static final float FREE_AIM_CURSOR_PIXELS = 1.0F;

    /** 剩余偏移小于这个角度直接归零，避免视角在中心附近一直微微蹭。 */
    private static final float FREE_AIM_STOP_DEGREES = 0.35F;

    /** 自由光标离屏幕边缘的留白（度），避免准心画到边框外。 */
    private static final float CURSOR_EDGE_MARGIN = 2.5F;

    /** 跟随炮台（第三种原型）：炮塔准心追赶视角中心的最大角速度（度/秒）。 */
    private static final float TRACKING_MAX_SPEED = 60.0F;

    /** 右键瞄准：FOV 缩到原来的 55%，普通炮塔鼠标灵敏度降到 35%，过渡速度 12/s。 */
    private static final float AIM_FOV_SCALE = 0.55F;
    private static final float AIM_SENSITIVITY = 0.35F;
    private static final float AIM_BLEND_PER_SECOND = 12.0F;

    /**
     * 同时在飞的炮弹特效槽位数：正常帧率下射程 48 格 / 24 格每秒 = 2 秒飞行。
     * 全自动 5 发/秒最多约 10 发并发，单发最多 4 发；留到 16 个槽位兜底，
     * 避免低帧率或连发时覆盖旧弹道，把它的命中特效一起吞掉。
     */
    private static final int SHOT_SLOTS = 16;


    private static boolean active;
    @Nullable
    private static BlockPos activePos;
    private static final Set<BlockPos> awaitingExit = new HashSet<>();
    private static boolean blockHeldUse;
    /** 通过潜望镜进入时的镜头锚点；直接操作炮塔时为 null（镜头在炮塔枢轴）。 */
    @Nullable
    private static BlockPos activeAnchor;
    /**
     * 服务端算好的世界镜头位置。物理船上的炮塔/潜望镜方块坐标在船坞里，
     * 不能直接当镜头位置（否则画面只剩船坞虚空里的深海背景）。
     */
    @Nullable
    private static Vec3 activeCamera;
    private static float aimYaw;
    private static float aimPitch;
    private static float lastSentYaw;
    private static float lastSentPitch;
    private static final TurretShotFx[] SHOTS = new TurretShotFx[SHOT_SLOTS];
    private static int nextShotSlot;
    /** 命中示意指针：视觉持续 7 tick（350ms），和测试台原值一致。 */
    public static final int HIT_INDICATOR_TICKS = 7;
    /** 双击中判定窗口：与视觉时长解耦，覆盖原版 10 tick 受伤无敌帧。 */
    public static final int HIT_COMBO_WINDOW_MILLIS = 1000;
    /** 命中播放期间再次命中时，两条平行线的间距（GUI px）。 */
    public static final float HIT_DOUBLE_LINE_GAP = 1.5F;
    /** 击杀示意：黄色 X 从准星向外扩散后淡出；313ms ≈ 6.25 tick（比 5 tick 长 25%）。 */
    public static final int KILL_INDICATOR_MILLIS = 313;
    /** 击杀 X 的固定旋转角；普通命中避让时按它计算夹角。 */
    public static final float KILL_INDICATOR_ANGLE_DEGREES = 45.0F;
    /** 击杀存在时，普通命中与 X 臂线至少差这么多度。 */
    public static final float HIT_AVOID_DEGREES = 15.0F;
    /** 双重击杀时两条平行线的间距（GUI px）。 */
    public static final float KILL_DOUBLE_LINE_GAP = 1.5F;
    /** 命中指针的预设旋转角（弧度）：每次命中随机挑一个，连续两次不重复。 */
    private static final float[] HIT_INDICATOR_ANGLES = {
            (float) Math.toRadians(10.0D),
            (float) Math.toRadians(24.0D),
            (float) Math.toRadians(38.0D),
            (float) Math.toRadians(52.0D),
            (float) Math.toRadians(66.0D),
            (float) Math.toRadians(80.0D)
    };

    /** 命中示意指针剩余 tick；每次命中重置，旧指针不做叠加。 */
    private static int hitIndicatorTicks;
    /** 本轮命中连段数：1=单线，2=双重线（封顶）。 */
    private static int hitChainCount;
    /** 上一次确认命中的 nanoTime；是否有值看 {@link #hasHitConfirm}。 */
    private static long lastHitConfirmNanos;
    /**
     * 连段窗口是否有效。{@code System.nanoTime()} 允许返回负数，不能用
     * {@code lastHitConfirmNanos > 0} 当“有没有命中过”的判据。
     */
    private static boolean hasHitConfirm;
    /** 击杀示意开始时间（nanoTime）；是否在播看 {@link #killIndicatorRunning}。 */
    private static long killIndicatorStartNanos;
    private static boolean killIndicatorRunning;
    /** 本轮击杀连段数：1=单线，2=双重线（封顶）。 */
    private static int killChainCount;
    /** 当前使用的预设角下标；-1 表示还没命中过。 */
    private static int hitIndicatorAngleIndex = -1;
    private static int cooldown;
    /** 服务端同步的武器状态：决定 HUD 怎么画、空弹/无电时是否发开火包。 */
    private static boolean syncedUsesAmmo;
    private static float chargeFraction;
    private static float minimumChargeFraction;
    private static boolean loaderBroken;
    private static boolean boxPresent;
    private static float shipYaw;
    private static int noPowerFlash;
    private static int loaderFlash;
    private static float actualYaw;
    private static float actualPitch;

    public static float chargeFraction() { return chargeFraction; }
    public static float minimumChargeFraction() { return minimumChargeFraction; }
    public static boolean loaderBroken() { return loaderBroken; }
    public static boolean boxPresent() { return boxPresent; }
    public static int noPowerFlash() { return noPowerFlash; }
    public static int loaderFlash() { return loaderFlash; }

    private static int syncedAmmoShots;
    private static boolean syncedPowered = true;
    /** 服务端下发的射击模式：true = 全自动（5 发/秒）。 */
    private static boolean autoFire;
    /** 上一次真正发出开火包的客户端 tick：全自动下 startAttack 与轮询可能同 tick 双触发。 */
    private static long lastFireTick = Long.MIN_VALUE;
    private static boolean coilgunHeld;
    private static long nextCoilgunRefresh;
    /** 右键瞄准：按住生效；进入炮塔的那次右键要先松开，避免一进来就缩放。 */
    private static boolean aiming;
    private static boolean aimArmed;
    /** 0…1 的瞄准过渡量，驱动 FOV、灵敏度和准心收拢。 */
    private static float aimBlend;
    private static long lastAimNanos = System.nanoTime();
    /** 本帧真正提交给渲染的 FOV；HUD 投影读它，保证和画面一致。 */
    private static float renderedFov = Float.NaN;
    private static boolean shiftWasDown;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static long lastTurnNanos = System.nanoTime();

    /** 当前炮塔是否是自由瞄准型。 */
    private static boolean freeAim;
    /** 当前炮塔是否是跟随型：视角自由移动，炮塔准心限速追视角中心。 */
    private static boolean tracking;
    /** 是否从瞄准站遥控接管：此时操作者本体在站台，命中预判把自己算作合法目标。 */
    private static boolean fromStation;
    /** 自由光标相对视角中心的角度偏移（度）：x 右为正，y 下为正。 */
    private static float cursorYaw;
    private static float cursorPitch;
    /** 当前跟随角速度（度/秒），做过低通，避免起步/收尾突变。 */
    private static float followSpeed;
    private static long lastFollowNanos = System.nanoTime();
    /** 跟随模式下相机（=玩家视角）的实时角度，HUD 用它投影炮塔准心。 */
    private static float viewYaw;
    private static float viewPitch;
    private static long lastTrackNanos = System.nanoTime();

    static {
        for (int i = 0; i < SHOT_SLOTS; i++) {
            SHOTS[i] = new TurretShotFx();
        }
    }

    private TurretClient() {
    }

    // ---- 状态查询（mixin / HUD 用） ----

    public static boolean isActive() {
        return active;
    }

    public static boolean blocksUse() {
        return active || blockHeldUse;
    }

    public static boolean isFreeAim() {
        return active && freeAim;
    }

    public static boolean isTracking() {
        return active && tracking;
    }

    /** 本地玩家是否正在操作这座炮塔（渲染器用它决定是否直接用本地瞄准角）。 */
    public static boolean isOperating(BlockPos pos) {
        return active && activePos != null && activePos.equals(pos);
    }

    @Nullable
    public static BlockPos activePos() {
        return activePos;
    }

    public static float aimYaw() {
        return aimYaw;
    }

    public static float aimPitch() {
        return aimPitch;
    }

    /** 0…1 的瞄准过渡量：HUD 用它收拢准心，FOV/灵敏度也按它插值。 */
    public static float aimBlend() {
        return active ? aimBlend : 0.0F;
    }

    /**
     * 瞄准时的鼠标角度增益（1 = 原样，0.35 = 瞄准最灵敏度的下限）。
     * 普通炮塔直接乘在角度增量上；跟随炮塔在 {@code TurretMouseMixin} 里乘到
     * 原版累积增量上，让三种模式的 ADS 手感一致。
     */
    public static double mouseAimScale() {
        return 1.0D - (1.0D - AIM_SENSITIVITY) * (active ? aimBlend : 0.0F);
    }

    /**
     * 当前实际生效的 FOV（度）。瞄准会缩放镜头，HUD 的世界点投影和自由光标
     * 换算必须用这个值，否则括号/光标会和画面错位。
     */
    public static float effectiveFov() {
        if (active && renderedFov > 1.0F) {
            return renderedFov;
        }
        return fovForBlend(active ? aimBlend : 0.0F);
    }

    private static float fovForBlend(float blend) {
        return Minecraft.getInstance().options.fov().get()
                * (1.0F - (1.0F - AIM_FOV_SCALE) * blend);
    }

    public static int cooldown() {
        return cooldown;
    }

    public static boolean syncedUsesAmmo() {
        return syncedUsesAmmo;
    }

    public static int syncedAmmoShots() {
        return syncedAmmoShots;
    }

    public static boolean syncedPowered() {
        return syncedPowered;
    }

    /** 当前模式的完整射击间隔，HUD 装填条按它算进度。 */
    public static int fireCooldownTicks() {
        return autoFire
                ? TurretBlockEntity.AUTO_FIRE_COOLDOWN_TICKS
                : TurretBlockEntity.FIRE_COOLDOWN_TICKS;
    }

    public static boolean isAutoFire() {
        return autoFire;
    }

    /** 跟随模式下相机（玩家视角）的水平角，和 {@link #aimYaw()} 的差就是炮塔准心偏移。 */
    public static float viewYaw() {
        return viewYaw;
    }

    public static float viewPitch() {
        return viewPitch;
    }

    /** 自由光标相对视角中心的水平偏移（度，右正）。 */
    public static float cursorYawOffset() {
        return cursorYaw;
    }

    /** 自由光标相对视角中心的垂直偏移（度，下正）。 */
    public static float cursorPitchOffset() {
        return cursorPitch;
    }

    /**
     * 相机位置：枢轴正上方（吊顶在下方），yaw/pitch 直接使用炮塔瞄准角。
     * 三种挂载共用同一套操作；安装面只改底座外观和相机高度。
     */
    @Nullable
    public static Vec3 cameraPosition() {
        if (!active || activePos == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        BlockState state = minecraft.level.getBlockState(activePos);
        if (state.getBlock() instanceof CoilgunBlock) {
            Vec3 sight = CoilgunBlock.sight(state, activePos, aimYaw, aimPitch);
            return VsClientTurret.toWorld(minecraft.level, activePos, sight);
        }
        Vec3 local = TurretBlockEntity.cameraPosition(state, activePos);
        Vec3 rendered = VsClientTurret.toWorld(minecraft.level, activePos, local);
        return rendered != local || activeCamera == null ? rendered : activeCamera;
    }

    /** 世界瞄准方向；安装面只影响放置外观，不改变操作。 */
    private static Vec3 aimDirection() {
        return lookVector(aimYaw, aimPitch);
    }

    public record CameraAngles(float yaw, float pitch) {}

    public static CameraAngles cameraAngles() {
        Minecraft mc = Minecraft.getInstance();
        if (syncedUsesAmmo && mc.level != null && activePos != null) {
            Vec3 direction = VsClientTurret.directionToWorld(mc.level, activePos, aimDirection());
            return new CameraAngles((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)),
                    (float) Math.toDegrees(Math.atan2(-direction.y, Math.hypot(direction.x, direction.z))));
        }
        return new CameraAngles(aimYaw + shipYaw, aimPitch);
    }

    @Nullable
    public static Vec3 coilgunImpact() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || activePos == null) return null;
        Vec3 muzzle = TurretBlockEntity.muzzlePosition(mc.level.getBlockState(activePos), activePos,
                actualYaw, actualPitch);
        Vec3 origin = VsClientTurret.toWorld(mc.level, activePos, muzzle);
        Vec3 direction = VsClientTurret.directionToWorld(mc.level, activePos, lookVector(actualYaw, actualPitch));
        Vec3 end = origin.add(direction.scale(CoilgunBolt.RANGE));
        var hit = mc.level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 raw = hit.getLocation();
            Vec3 world = VsClientTurret.toWorld(mc.level, hit.getBlockPos(), raw);
            if (world.distanceToSqr(origin) < raw.distanceToSqr(origin)) return world;
        }
        return hit.getLocation();
    }

    private static float clampAimYaw(float yaw) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && activePos != null) {
            return TurretBlockEntity.clampYaw(minecraft.level.getBlockState(activePos), yaw);
        }
        return Mth.wrapDegrees(yaw);
    }

    private static float pitchMin() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && activePos != null) {
            return TurretBlockEntity.minPitch(minecraft.level.getBlockState(activePos));
        }
        return TurretBlockEntity.MIN_PITCH;
    }

    private static float pitchMax() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && activePos != null) {
            return TurretBlockEntity.maxPitch(minecraft.level.getBlockState(activePos));
        }
        return TurretBlockEntity.MAX_PITCH;
    }

    // ---- 服务端状态 ----

    public static void onState(TurretStatePacket state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.level.getBlockEntity(state.pos()) instanceof TurretBlockEntity turret) {
            turret.applyClientState(state.yaw(), state.pitch(), state.cooldown(),
                    state.operatorEntityId(), state.autoFire());
        }
        if (minecraft.player == null) {
            return;
        }
        boolean mine = state.operatorEntityId() == minecraft.player.getId();
        // Server broadcasts in flight before the exit must not reactivate the camera.
        if (awaitingExit.contains(state.pos())) {
            if (!mine) awaitingExit.remove(state.pos());
            return;
        }
        if (mine && !minecraft.player.isSpectator() && !minecraft.player.isDeadOrDying()) {
            syncedUsesAmmo = state.usesAmmo();
            syncedAmmoShots = state.ammoShots();
            syncedPowered = state.powered();
            chargeFraction = state.chargeFraction();
            minimumChargeFraction = state.minimumChargeFraction();
            loaderBroken = state.loaderBroken();
            boxPresent = state.boxPresent();
            shipYaw = state.shipYaw();
            actualYaw = state.yaw();
            actualPitch = state.pitch();
            // 站台绑定距离不限：炮塔区块没加载时客户端没有方块状态/地形可渲染，
            // 不能把相机丢进虚空。直接回退出包，服务端会清占用。
            if (minecraft.level == null || !minecraft.level.hasChunkAt(state.pos())) {
                ModNetwork.sendToServer(new TurretExitPacket(state.pos()));
                return;
            }
            if (!active || activePos == null || !state.pos().equals(activePos)) {
                activate(state.pos(), state.yaw(), state.pitch(), state.fromStation(),
                        state.controllerPos(), state.cameraAnchor());
                // 进入时只取一次服务端剩余冷却；之后由本地门控，避免延迟到达的旧快照
                // 把正在递减的冷却重新拉长（CodeRabbit review on PR #82）。
                cooldown = state.cooldown();
                autoFire = state.autoFire();
            } else {
                boolean modeChanged = autoFire != state.autoFire();
                autoFire = state.autoFire();
                if (modeChanged) {
                    // 切模式时按服务端同样的规则收紧/补足冷却，不靠状态包逐 tick 覆盖。
                    cooldown = autoFire
                            ? Math.min(cooldown, TurretBlockEntity.AUTO_FIRE_COOLDOWN_TICKS)
                            : Math.max(cooldown, TurretBlockEntity.FIRE_COOLDOWN_TICKS);
                }
                fromStation = state.fromStation();
                activeAnchor = state.controllerPos() == null ? null
                        : state.controllerPos().immutable();
                activeCamera = state.cameraAnchor();
            }
            if (syncedUsesAmmo) cooldown = state.cooldown();
        } else if (active && state.pos().equals(activePos)) {
            deactivate(true);
        }
    }

    private static void activate(BlockPos pos, float yaw, float pitch, boolean remoteStation,
                                 @Nullable BlockPos anchor, @Nullable Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        active = true;
        activePos = pos;
        fromStation = remoteStation;
        activeAnchor = anchor == null ? null : anchor.immutable();
        activeCamera = camera;
        aimYaw = yaw;
        aimPitch = pitch;
        lastSentYaw = yaw;
        lastSentPitch = pitch;
        cooldown = 0;
        coilgunHeld = false;
        autoFire = false;
        hitIndicatorTicks = 0;
        hitChainCount = 0;
        lastHitConfirmNanos = 0L;
        hasHitConfirm = false;
        killIndicatorRunning = false;
        killChainCount = 0;
        hitIndicatorAngleIndex = -1;
        // 进入炮塔用的那次右键还按着：先要求松开，避免一进来就直接进瞄准。
        aimArmed = !minecraft.options.keyUse.isDown();
        aiming = false;
        aimBlend = 0.0F;
        renderedFov = Float.NaN;
        lastAimNanos = System.nanoTime();
        shiftWasDown = minecraft.options.keyShift.isDown();
        lastTurnNanos = System.nanoTime();
        // 三种炮塔共用方块实体，模式由方块类型决定（客户端此时已经加载了这一格）。
        Block block = minecraft.level != null ? minecraft.level.getBlockState(pos).getBlock() : null;
        freeAim = block instanceof FreeAimTurretBlock;
        tracking = block instanceof TrackingTurretBlock;
        cursorYaw = 0.0F;
        cursorPitch = 0.0F;
        followSpeed = 0.0F;
        lastFollowNanos = System.nanoTime();
        // 跟随模式的相机就是玩家自己的视角；炮塔准心（aimYaw/Pitch）从服务端角度追上来。
        viewYaw = minecraft.player != null ? minecraft.player.getYRot() : yaw;
        viewPitch = minecraft.player != null ? minecraft.player.getXRot() : pitch;
        lastTrackNanos = System.nanoTime();
        previousCameraType = minecraft.options.getCameraType();
        // 炮塔相机是遥控视角：强制第三人称，让玩家实体继续留在原地并可见。
        // 进入第一人称时本机不会渲染自己的模型，看起来就像“人消失了”。
        if (previousCameraType != CameraType.THIRD_PERSON_BACK) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private static void deactivate(boolean snapPlayerRotation) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 跟随模式下视角是玩家自己的，退出时不要把它拽回炮塔朝向；
        // 潜望镜会话也一样——玩家进潜望镜前的视角要保留。
        if (snapPlayerRotation && activeAnchor == null && !tracking && minecraft.player != null) {
            minecraft.player.setYRot(aimYaw);
            minecraft.player.setXRot(aimPitch);
            minecraft.player.yRotO = aimYaw;
            minecraft.player.xRotO = aimPitch;
        }
        minecraft.options.setCameraType(previousCameraType);
        active = false;
        noPowerFlash = 0;
        loaderFlash = 0;
        shipYaw = 0;
        activePos = null;
        activeAnchor = null;
        activeCamera = null;
        cooldown = 0;
        coilgunHeld = false;
        aiming = false;
        aimArmed = false;
        aimBlend = 0.0F;
        renderedFov = Float.NaN;
        // 不要清掉在飞的炮弹：退出炮塔后它们还要继续飞完并结算命中特效。
        hitIndicatorTicks = 0;
        hitChainCount = 0;
        lastHitConfirmNanos = 0L;
        hasHitConfirm = false;
        killIndicatorRunning = false;
        killChainCount = 0;
        hitIndicatorAngleIndex = -1;
        autoFire = false;
        freeAim = false;
        tracking = false;
        fromStation = false;
        cursorYaw = 0.0F;
        cursorPitch = 0.0F;
        followSpeed = 0.0F;
        viewYaw = 0.0F;
        viewPitch = 0.0F;
    }

    private static void requestExit() {
        if (!active || activePos == null) {
            return;
        }
        awaitingExit.add(activePos);
        blockHeldUse = Minecraft.getInstance().options.keyUse.isDown();
        ModNetwork.sendToServer(new TurretExitPacket(activePos));
        deactivate(true);
    }

    /** 由 {@code Minecraft#startAttack} 的 mixin 调用（左键开火）。 */
    public static void requestFire() {
        if (!active || activePos == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (syncedUsesAmmo) {
            if (minecraft.level != null) syncCoilgunTrigger(true, minecraft.level.getGameTime());
            return;
        }
        if (cooldown > 0) return;
        long tick = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        // startAttack 与全自动轮询可能在同一 tick 各调一次；只允许真正发出一包。
        if (autoFire && tick == lastFireTick) {
            return;
        }
        lastFireTick = tick;
        ModNetwork.sendToServer(new TurretFirePacket(activePos, true));
        cooldown = autoFire ? TurretBlockEntity.AUTO_FIRE_COOLDOWN_TICKS : LOCAL_COOLDOWN;
        startBeam();
    }

    private static void syncCoilgunTrigger(boolean held, long tick) {
        if (held) {
            if (!syncedPowered && noPowerFlash == 0) noPowerFlash = 20;
            else if (syncedPowered && (syncedAmmoShots <= 0 || loaderBroken) && loaderFlash == 0) loaderFlash = 20;
            if (coilgunHeld && tick < nextCoilgunRefresh) return;
            sendAimIfChanged();
            nextCoilgunRefresh = tick + CoilgunTrigger.REFRESH_TICKS;
        } else if (!coilgunHeld) {
            return;
        }
        coilgunHeld = held;
        ModNetwork.sendToServer(new TurretFirePacket(activePos, held));
    }

    /** 由 {@code MouseHandler#turnPlayer} 的 mixin 调用。 */
    public static void onMouseTurn(double deltaX, double deltaY) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();

        double turretSensitivity = ClientConfig.TURRET_SENSITIVITY.get() / 100.0;
        if (freeAim) {
            // 自由瞄准：鼠标只推光标，视角在渲染帧里限速追它（见 stepFreeAim）。
            // 光标按“屏幕像素”移动（像普通鼠标指针），再用当前 FOV 换算成角度；
            // 不能直接套原版角度灵敏度——宽屏上那会让准心以 2~3 倍指针速度飞。
            float pixelYaw = (float) (deltaX * turretSensitivity);
            float pixelPitch = (float) (deltaY * turretSensitivity);
            if (minecraft.options.invertYMouse().get()) {
                pixelPitch = -pixelPitch;
            }
            // 瞄准缩放后每像素对应的角度更小，光标自然变得更好微调。
            float halfFovY = effectiveFov() * 0.5F;
            int windowWidth = minecraft.getWindow().getWidth();
            int windowHeight = minecraft.getWindow().getHeight();
            float aspect = windowHeight > 0 ? windowWidth / (float) windowHeight : 16.0F / 9.0F;
            float halfFovX = (float) Math.toDegrees(Math.atan(
                    Math.tan(Math.toRadians(halfFovY)) * aspect));
            float degreesPerPixelX = halfFovX / Math.max(1.0F, windowWidth * 0.5F);
            float degreesPerPixelY = halfFovY / Math.max(1.0F, windowHeight * 0.5F);
            cursorYaw += pixelYaw * FREE_AIM_CURSOR_PIXELS * degreesPerPixelX;
            cursorPitch += pixelPitch * FREE_AIM_CURSOR_PIXELS * degreesPerPixelY;
            clampCursor();
            return;
        }

        double sensitivity = minecraft.options.sensitivity().get() * 0.6D + 0.2D;
        double scale = sensitivity * sensitivity * sensitivity * 8.0D * 0.15D;
        // 瞄准时降低角度增益：同样的鼠标移动转得更少，配合 FOV 缩放做微调。
        scale *= mouseAimScale() * turretSensitivity;
        float rawYaw = (float) (deltaX * scale);
        float rawPitch = (float) (deltaY * scale);
        if (minecraft.options.invertYMouse().get()) {
            rawPitch = -rawPitch;
        }

        long now = System.nanoTime();
        float deltaSeconds = Mth.clamp((now - lastTurnNanos) / 1.0E9F, 0.001F, 0.05F);
        lastTurnNanos = now;
        float maxDelta = TurretBlockEntity.MAX_TURN_PER_SECOND * deltaSeconds;

        // For coilguns the camera follows input immediately; the server still limits barrel speed.
        float maxInputStep = syncedUsesAmmo ? 180.0F : maxDelta;
        aimYaw = clampAimYaw(aimYaw + Mth.clamp(rawYaw, -maxInputStep, maxInputStep));
        aimPitch = Mth.clamp(aimPitch + Mth.clamp(rawPitch, -maxInputStep, maxInputStep),
                pitchMin(), pitchMax());
    }

    /**
     * 把自由光标夹在屏幕内（按当前 FOV 换算成角度），并保证光标指向的绝对角度
     * 不超出炮塔的俯仰限位。
     */
    private static void clampCursor() {
        Minecraft minecraft = Minecraft.getInstance();
        float halfFovY = effectiveFov() * 0.5F;
        int windowWidth = minecraft.getWindow().getWidth();
        int windowHeight = minecraft.getWindow().getHeight();
        // 窗口最小化时尺寸可能是 0，别让 atan/NaN 污染瞄准角。
        float aspect = windowHeight > 0 ? windowWidth / (float) windowHeight : 16.0F / 9.0F;
        float halfFovX = (float) Math.toDegrees(Math.atan(
                Math.tan(Math.toRadians(halfFovY)) * aspect));
        float maxYaw = Math.max(1.0F, halfFovX - CURSOR_EDGE_MARGIN);
        float maxPitch = Math.max(1.0F, halfFovY - CURSOR_EDGE_MARGIN);
        cursorYaw = Mth.clamp(cursorYaw, -maxYaw, maxYaw);
        cursorPitch = Mth.clamp(cursorPitch, -maxPitch, maxPitch);
        cursorPitch = Mth.clamp(cursorPitch,
                pitchMin() - aimPitch,
                pitchMax() - aimPitch);
    }

    /**
     * 把视角朝自由光标转一点，并把转过的角度从光标偏移里扣掉。
     *
     * <p>由 {@link #onCameraAngles} 在每次相机结算（每帧一次）时调用，和这一帧的
     * 鼠标增量共用同一个 dt、同一时刻生效——这是避免准心在屏幕上轻微抽搐的
     * 关键：输入和跟随不再各自在帧内不同时刻更新。</p>
     *
     * <p>转速不用“偏移 × 增益”，而是先按偏移方向求出目标速度：远离中心时就是
     * 恒定上限 {@link #FREE_AIM_MAX_SPEED}，最后 {@link #FREE_AIM_EASE_DEGREES}
     * 度线性减速。再对速度做一次低通（{@link #FREE_AIM_ACCEL_PER_SECOND}），
     * 起步、收尾、鼠标忽快忽慢时都不会出现一顿一顿的台阶感。</p>
     */
    private static void stepFreeAim() {
        long now = System.nanoTime();
        float deltaSeconds = Mth.clamp((now - lastFollowNanos) / 1.0E9F, 0.0F, 0.05F);
        lastFollowNanos = now;
        if (deltaSeconds <= 0.0F) {
            return;
        }

        float distance = Mth.sqrt(cursorYaw * cursorYaw + cursorPitch * cursorPitch);
        if (distance <= FREE_AIM_STOP_DEGREES) {
            cursorYaw = 0.0F;
            cursorPitch = 0.0F;
            followSpeed = 0.0F;
            return;
        }

        float targetSpeed = FREE_AIM_MAX_SPEED
                * Mth.clamp(distance / FREE_AIM_EASE_DEGREES, 0.0F, 1.0F);
        followSpeed += (targetSpeed - followSpeed)
                * Mth.clamp(deltaSeconds * FREE_AIM_ACCEL_PER_SECOND, 0.0F, 1.0F);
        followSpeed = Mth.clamp(followSpeed, 0.0F, FREE_AIM_MAX_SPEED);

        float step = Math.min(distance, followSpeed * deltaSeconds);
        float scale = step / distance;
        float stepYaw = cursorYaw * scale;
        float stepPitch = cursorPitch * scale;

        aimYaw = clampAimYaw(aimYaw + stepYaw);
        float newPitch = Mth.clamp(aimPitch + stepPitch, pitchMin(), pitchMax());
        float appliedPitch = newPitch - aimPitch;
        aimPitch = newPitch;

        cursorYaw -= stepYaw;
        cursorPitch -= appliedPitch;

        clampCursor();
    }

    /**
     * 开火粒子束：一条极细的发光粒子流，从**炮口**笔直飞向落点，方便观察弹道。
     *
     * <p>起点不是相机（在炮口上方，粒子会糊在眼前挡住视线），而是
     * {@link TurretBlockEntity#muzzlePosition(net.minecraft.core.BlockPos, Vec3)} 给出的
     * 炮口位置；落点与落点预判共用同一次 {@code raycastImpact}。</p>
     */
    private static void startBeam() {
        Vec3 direction = aimDirection();
        Vec3 origin = turretMuzzle(direction);
        ImpactPrediction impact = origin == null ? null : raycastImpact(origin, direction);
        if (origin == null || impact == null) {
            return;
        }
        float length = (float) Math.max(0.5D, origin.distanceTo(impact.point()));
        acquireShot().start(origin, direction, length, impact);
        spawnMuzzleEffects(origin, direction);
    }

    public static void onCoilgunShot(CoilgunShotPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (minecraft.level.hasChunkAt(packet.turret())
                && minecraft.level.getBlockEntity(packet.turret()) instanceof TurretBlockEntity turret) {
            turret.animateCoilgunShot();
        }
        spawnFlash(packet.muzzle(), 6, 0.23);
        spawnMuzzleEffects(packet.muzzle(), packet.direction());
    }

    /** 取一个空闲槽位；理论并发不会占满，真占满就覆盖最早的一发。 */
    private static TurretShotFx acquireShot() {
        for (int i = 0; i < SHOT_SLOTS; i++) {
            int index = (nextShotSlot + i) % SHOT_SLOTS;
            if (!SHOTS[index].isActive()) {
                nextShotSlot = (index + 1) % SHOT_SLOTS;
                return SHOTS[index];
            }
        }
        TurretShotFx fallback = SHOTS[nextShotSlot];
        nextShotSlot = (nextShotSlot + 1) % SHOT_SLOTS;
        return fallback;
    }

    /** 这个点是否按“泡在水里”处理；非深海维度恒为 false。 */
    static boolean isUnderwater(Vec3 point) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && OceanEnvironment.isSubmerged(minecraft.level, point);
    }

    /**
     * 枪口特效：仅炮口在水里时生成气泡锥、少量泥沙和一小簇白蓝闪光。
     * 干舱里开火不生成，保持原来的细粒子束。
     */
    private static void spawnMuzzleEffects(Vec3 muzzle, Vec3 direction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !isUnderwater(muzzle)) {
            return;
        }
        RandomSource random = minecraft.level.random;
        for (int i = 0; i < 10; i++) {
            double spreadX = (random.nextDouble() - 0.5D) * 0.30D;
            double spreadY = (random.nextDouble() - 0.5D) * 0.30D;
            double spreadZ = (random.nextDouble() - 0.5D) * 0.30D;
            double forward = 1.8D + random.nextDouble() * 2.6D;
            minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                    muzzle.x + spreadX * 0.5D, muzzle.y + spreadY * 0.5D, muzzle.z + spreadZ * 0.5D,
                    direction.x * forward + spreadX, direction.y * forward + spreadY,
                    direction.z * forward + spreadZ);
        }
        for (int i = 0; i < 3; i++) {
            minecraft.level.addParticle(ModParticles.TURRET_SEDIMENT.get(),
                    muzzle.x, muzzle.y, muzzle.z,
                    (random.nextDouble() - 0.5D) * 0.5D,
                    0.1D + random.nextDouble() * 0.2D,
                    (random.nextDouble() - 0.5D) * 0.5D);
        }
        spawnFlash(muzzle, 6, 0.08D);
    }

    /** 白蓝能量闪光：一簇短命 tracer，空气和水里都可见。 */
    private static void spawnFlash(Vec3 point, int count, double spread) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        RandomSource random = minecraft.level.random;
        for (int i = 0; i < count; i++) {
            minecraft.level.addParticle(ModParticles.TURRET_TRACER.get(),
                    point.x + (random.nextDouble() - 0.5D) * spread,
                    point.y + (random.nextDouble() - 0.5D) * spread,
                    point.z + (random.nextDouble() - 0.5D) * spread,
                    0.0D, 0.0D, 0.0D);
        }
    }

    /** 炮口世界坐标：与服务端 {@code TurretBlockEntity#muzzlePosition} 同一套公式。 */
    @Nullable
    private static Vec3 turretMuzzle(Vec3 direction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (activePos == null || minecraft.level == null) {
            return null;
        }
        return TurretBlockEntity.muzzlePosition(
                minecraft.level.getBlockState(activePos), activePos, aimYaw, aimPitch);
    }

    /** 每帧推进所有在飞的炮弹特效。 */
    private static void tickBeam() {
        for (TurretShotFx shot : SHOTS) {
            shot.step();
        }
    }

    /** 弹道穿过水面时在水线处补一圈小气泡，作为“空气→水”的过渡提示。 */
    static void spawnSurfaceCrossing(Vec3 point) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        RandomSource random = minecraft.level.random;
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double speed = 0.35D + random.nextDouble() * 0.5D;
            minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                    point.x, point.y, point.z,
                    Math.cos(angle) * speed, 0.25D + random.nextDouble() * 0.35D,
                    Math.sin(angle) * speed);
        }
    }

    /** 命中总入口：先打闪光，再按命中类型派发。空气命中同样有反馈。 */
    static void spawnImpactEffects(ImpactPrediction impact, Vec3 direction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || impact == null) {
            return;
        }
        Vec3 point = impact.point();
        boolean underwater = isUnderwater(point);
        spawnFlash(point, 7, 0.06D);
        switch (impact.kind()) {
            case CREATURE -> spawnCreatureImpact(minecraft, point, underwater);
            case HULL_INTACT -> spawnHullImpact(minecraft, point, underwater);
            case HULL_BREACHED -> spawnBreachImpact(minecraft, point, direction);
            case GLASS -> spawnGlassImpact(minecraft, point, underwater);
            case STRUCTURE -> spawnStructureImpact(minecraft, point, underwater);
            case SEABED -> spawnSeabedImpact(minecraft, point, direction, underwater);
            case MISS -> spawnMissImpact(minecraft, point, underwater);
        }
    }

    /** 生物：血雾（原版伤害指示粒子）+ 水下小气泡环。 */
    private static void spawnCreatureImpact(Minecraft minecraft, Vec3 point, boolean underwater) {
        RandomSource random = minecraft.level.random;
        for (int i = 0; i < 12; i++) {
            minecraft.level.addParticle(ParticleTypes.DAMAGE_INDICATOR,
                    point.x, point.y, point.z,
                    (random.nextDouble() - 0.5D) * 0.18D,
                    0.05D + random.nextDouble() * 0.12D,
                    (random.nextDouble() - 0.5D) * 0.18D);
        }
        if (underwater) {
            for (int i = 0; i < 8; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double speed = 1.0D + random.nextDouble() * 0.6D;
                minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                        point.x, point.y, point.z,
                        Math.cos(angle) * speed, 0.10D + random.nextDouble() * 0.20D,
                        Math.sin(angle) * speed);
            }
        }
    }

    /** 金属船壳：白亮碎屑 + 低沉金属声；水下再叠气泡。 */
    private static void spawnHullImpact(Minecraft minecraft, Vec3 point, boolean underwater) {
        RandomSource random = minecraft.level.random;
        for (int i = 0; i < 9; i++) {
            minecraft.level.addParticle(ParticleTypes.CRIT,
                    point.x, point.y, point.z,
                    (random.nextDouble() - 0.5D) * 2.4D,
                    0.6D + random.nextDouble() * 1.4D,
                    (random.nextDouble() - 0.5D) * 2.4D);
        }
        if (underwater) {
            for (int i = 0; i < 6; i++) {
                minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                        point.x, point.y, point.z,
                        (random.nextDouble() - 0.5D) * 0.6D,
                        0.15D + random.nextDouble() * 0.25D,
                        (random.nextDouble() - 0.5D) * 0.6D);
            }
        }
        minecraft.level.playLocalSound(point.x, point.y, point.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS,
                underwater ? 0.30F : 0.45F, underwater ? 0.70F : 1.35F, false);
    }

    /** 船上非船壳结构（门、泵、柜子等）：少量碎屑和气泡，不触发海床地波。 */
    private static void spawnStructureImpact(Minecraft minecraft, Vec3 point, boolean underwater) {
        RandomSource random = minecraft.level.random;
        for (int i = 0; i < 6; i++) {
            minecraft.level.addParticle(ParticleTypes.CRIT,
                    point.x, point.y, point.z,
                    (random.nextDouble() - 0.5D) * 1.8D,
                    0.5D + random.nextDouble() * 1.0D,
                    (random.nextDouble() - 0.5D) * 1.8D);
        }
        if (underwater) {
            for (int i = 0; i < 4; i++) {
                minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                        point.x, point.y, point.z,
                        (random.nextDouble() - 0.5D) * 0.5D,
                        0.12D + random.nextDouble() * 0.2D,
                        (random.nextDouble() - 0.5D) * 0.5D);
            }
        }
    }

    /** 破口：弹丸穿洞，只留少量被吸进舱内的气泡，不做冲击环。 */
    private static void spawnBreachImpact(Minecraft minecraft, Vec3 point, Vec3 direction) {
        RandomSource random = minecraft.level.random;
        for (int i = 0; i < 6; i++) {
            minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                    point.x, point.y, point.z,
                    -direction.x * 0.4D + (random.nextDouble() - 0.5D) * 0.15D,
                    -direction.y * 0.4D + (random.nextDouble() - 0.5D) * 0.15D,
                    -direction.z * 0.4D + (random.nextDouble() - 0.5D) * 0.15D);
        }
    }

    /** 舷窗：尖锐白蓝碎片 + 玻璃碎裂声。 */
    private static void spawnGlassImpact(Minecraft minecraft, Vec3 point, boolean underwater) {
        RandomSource random = minecraft.level.random;
        for (int i = 0; i < 12; i++) {
            minecraft.level.addParticle(ParticleTypes.CRIT,
                    point.x, point.y, point.z,
                    (random.nextDouble() - 0.5D) * 3.6D,
                    0.8D + random.nextDouble() * 2.0D,
                    (random.nextDouble() - 0.5D) * 3.6D);
        }
        if (underwater) {
            for (int i = 0; i < 6; i++) {
                minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                        point.x, point.y, point.z,
                        (random.nextDouble() - 0.5D) * 0.7D,
                        0.2D + random.nextDouble() * 0.3D,
                        (random.nextDouble() - 0.5D) * 0.7D);
            }
        }
        minecraft.level.playLocalSound(point.x, point.y, point.z,
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS,
                underwater ? 0.60F : 0.80F, underwater ? 0.90F : 1.30F, false);
    }

    /** 海床：垂直于弹道的冲击环 + 空化气泡 + 泥沙 + 贴地地波；空气中只留碎屑。 */
    private static void spawnSeabedImpact(Minecraft minecraft, Vec3 point, Vec3 direction,
                                          boolean underwater) {
        RandomSource random = minecraft.level.random;
        if (!underwater) {
            for (int i = 0; i < 8; i++) {
                minecraft.level.addParticle(ParticleTypes.CRIT,
                        point.x, point.y, point.z,
                        (random.nextDouble() - 0.5D) * 1.6D,
                        0.4D + random.nextDouble() * 1.0D,
                        (random.nextDouble() - 0.5D) * 1.6D);
            }
            return;
        }
        Vec3 normal = direction.normalize();
        Vec3 helper = Math.abs(normal.y) > 0.9D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = normal.cross(helper).normalize();
        Vec3 v = normal.cross(u).normalize();

        int ring = 24;
        for (int i = 0; i < ring; i++) {
            double angle = Math.PI * 2.0D * i / ring + random.nextDouble() * 0.12D;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double speed = 1.4D + random.nextDouble() * 1.8D;
            double radius = 0.10D;
            minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                    point.x + (u.x * cos + v.x * sin) * radius,
                    point.y + (u.y * cos + v.y * sin) * radius,
                    point.z + (u.z * cos + v.z * sin) * radius,
                    (u.x * cos + v.x * sin) * speed + normal.x * 0.15D,
                    (u.y * cos + v.y * sin) * speed + normal.y * 0.15D,
                    (u.z * cos + v.z * sin) * speed + normal.z * 0.15D);
        }
        for (int i = 0; i < 10; i++) {
            minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                    point.x, point.y, point.z,
                    (random.nextDouble() - 0.5D) * 0.6D,
                    0.2D + random.nextDouble() * 0.4D,
                    (random.nextDouble() - 0.5D) * 0.6D);
        }
        for (int i = 0; i < 16; i++) {
            minecraft.level.addParticle(ModParticles.TURRET_SEDIMENT.get(),
                    point.x + (random.nextDouble() - 0.5D) * 0.5D,
                    point.y,
                    point.z + (random.nextDouble() - 0.5D) * 0.5D,
                    (random.nextDouble() - 0.5D) * 0.8D,
                    0.1D + random.nextDouble() * 0.2D,
                    (random.nextDouble() - 0.5D) * 0.8D);
        }
        // 贴着海床的命中再叠一圈“地波”：气泡和泥沙沿地形向外扩散。
        TurretGroundWave.spawnFromImpact(point);
    }

    /** 打空：深海里只在射程端点散几颗气泡；空气中什么都没有。 */
    private static void spawnMissImpact(Minecraft minecraft, Vec3 point, boolean underwater) {
        if (!underwater) {
            return;
        }
        RandomSource random = minecraft.level.random;
        for (int i = 0; i < 5; i++) {
            minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                    point.x, point.y, point.z,
                    (random.nextDouble() - 0.5D) * 0.25D,
                    0.05D + random.nextDouble() * 0.15D,
                    (random.nextDouble() - 0.5D) * 0.25D);
        }
    }

    /**
     * 服务端确认这一发确实造成了伤害后才调用（{@link com.opamb.barotrauma.network.TurretHitPacket}）。
     * 普通命中闪白色随机角指针；击杀改闪黄色 X 扩散特效。
     */
    public static void onHitConfirmed(BlockPos pos, boolean killed) {
        if (!active || activePos == null || !activePos.equals(pos)) {
            return;
        }
        if (killed) {
            // 独立图层：击杀不再清掉普通命中；连段第二杀升级为双重线，封顶双重。
            int chain = killIndicatorActive() ? killChainCount + 1 : 1;
            killChainCount = Math.min(2, chain);
            killIndicatorStartNanos = System.nanoTime();
            killIndicatorRunning = true;
            // 击杀 X 固定 45°：普通指针还在显示且离 X 臂线太近时重挑一个角，
            // 保留它的剩余时间和连段计数（全自动下两次命中可能只隔几 tick）。
            if (hitIndicatorTicks > 0 && hitIndicatorAngleIndex >= 0
                    && angleSeparation(
                    (float) Math.toDegrees(HIT_INDICATOR_ANGLES[hitIndicatorAngleIndex]),
                    KILL_INDICATOR_ANGLE_DEGREES) < HIT_AVOID_DEGREES) {
                hitIndicatorAngleIndex = pickHitAngle(true);
            }
            return;
        }
        long now = System.nanoTime();
        boolean combo = hitIndicatorTicks > 0
                || (hasHitConfirm
                && now - lastHitConfirmNanos <= HIT_COMBO_WINDOW_MILLIS * 1_000_000L);
        hitChainCount = Math.min(2, combo ? hitChainCount + 1 : 1);
        lastHitConfirmNanos = now;
        hasHitConfirm = true;
        hitIndicatorAngleIndex = pickHitAngle(killIndicatorActive());
        hitIndicatorTicks = HIT_INDICATOR_TICKS;
    }

    /** 两个四臂十字的夹角（模 90°），返回 0…45。 */
    private static float angleSeparation(float a, float b) {
        float d = Math.abs(((a - b) % 90.0F + 90.0F) % 90.0F);
        return Math.min(d, 90.0F - d);
    }

    /**
     * 普通命中的预设角。击杀 X 存在时，优先挑与 45° 臂线夹角 ≥ {@link #HIT_AVOID_DEGREES}
     * 的角度；如果全都不够远，就挑离臂线最远的那一档，保证命中始终可见。
     */
    private static int pickHitAngle(boolean avoidKill) {
        int length = HIT_INDICATOR_ANGLES.length;
        int[] candidates = new int[length];
        int count = 0;
        if (avoidKill) {
            float best = 0.0F;
            for (float angle : HIT_INDICATOR_ANGLES) {
                best = Math.max(best, angleSeparation(
                        (float) Math.toDegrees(angle), KILL_INDICATOR_ANGLE_DEGREES));
            }
            float threshold = best >= HIT_AVOID_DEGREES ? HIT_AVOID_DEGREES : best;
            for (int i = 0; i < length; i++) {
                if (angleSeparation((float) Math.toDegrees(HIT_INDICATOR_ANGLES[i]),
                        KILL_INDICATOR_ANGLE_DEGREES) >= threshold) {
                    candidates[count++] = i;
                }
            }
        } else {
            for (int i = 0; i < length; i++) {
                candidates[count++] = i;
            }
        }
        int start = ThreadLocalRandom.current().nextInt(count);
        int chosen = candidates[start];
        if (count > 1 && chosen == hitIndicatorAngleIndex) {
            chosen = candidates[(start + 1) % count];
        }
        return chosen;
    }

    public static int hitIndicatorTicks() {
        return hitIndicatorTicks;
    }

    /** 当前击杀连段是否已经到双重线。 */
    public static boolean killDoubleLines() {
        return killChainCount >= 2;
    }

    /** 当前命中连段是否已经到双重线。 */
    public static boolean hitDoubleLines() {
        return hitChainCount >= 2;
    }

    /** 击杀特效 0…1 的进度；没有特效时返回 1。 */
    public static float killIndicatorProgress() {
        if (!killIndicatorRunning) {
            return 1.0F;
        }
        // nanoTime 可能是负数，只用差值算进度，负值不代表“没有开始”。
        float elapsedMs = (System.nanoTime() - killIndicatorStartNanos) / 1.0E6F;
        return Mth.clamp(elapsedMs / KILL_INDICATOR_MILLIS, 0.0F, 1.0F);
    }

    public static boolean killIndicatorActive() {
        return killIndicatorRunning && killIndicatorProgress() < 1.0F;
    }

    /** 当前示意指针的旋转角（弧度）。 */
    public static float hitIndicatorAngle() {
        return hitIndicatorAngleIndex < 0
                ? HIT_INDICATOR_ANGLES[0]
                : HIT_INDICATOR_ANGLES[hitIndicatorAngleIndex];
    }

    static Vec3 lookVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad));
    }

    // ---- 命中预测（跟随炮塔的落点准心） ----

    /** 一次命中/预判的类型：决定客户端特效分支，也决定 HUD 落点括号的颜色。 */
    public enum HitKind {
        CREATURE,
        HULL_INTACT,
        HULL_BREACHED,
        GLASS,
        /** 船上的非船壳结构：门、泵、柜子、声纳等，不触发海床地波。 */
        STRUCTURE,
        SEABED,
        MISS
    }

    /**
     * 一次落点预判的结果：命中点、被命中物体的半宽/半高（格）与命中类型。HUD 用
     * 前两个尺寸算出投影大小，再决定方括号的长短；特效按 {@link #kind()} 分支。
     */
    public record ImpactPrediction(Vec3 point, double halfWidth, double halfHeight, HitKind kind) {
    }

    /**
     * 跟随炮塔的落点预判：以**炮口**为起点、用本地炮塔瞄准角做一次和服务端
     * {@code fire} 相同的射线，返回命中点与被命中物体的尺寸。
     *
     * <p>服务端 {@code fire} 也用 {@link TurretBlockEntity#muzzlePosition()}，两边同源；
     * 客户端实体位置是插值后的，仍可能有一 tick 误差。</p>
     */
    @Nullable
    public static ImpactPrediction predictedImpact() {
        if (!active) {
            return null;
        }
        Vec3 direction = aimDirection();
        Vec3 origin = turretMuzzle(direction);
        if (origin == null) {
            return null;
        }
        return raycastImpact(origin, direction);
    }

    /** 从给定起点沿方向做射线：最近实体优先，其次方块；都没中则取最大射程端点。 */
    @Nullable
    private static ImpactPrediction raycastImpact(Vec3 origin, Vec3 direction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        Vec3 maxEnd = origin.add(direction.scale(TurretBlockEntity.RANGE));
        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(origin, maxEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        Vec3 blockEnd = blockHit.getType() != HitResult.Type.MISS
                ? blockHit.getLocation()
                : maxEnd;

        // 破口方块碰撞为空，COLLIDER 射线会直接穿过去；HullBlock.getShape 在 breached
        // 时仍返回整格，所以用 OUTLINE 再扫一次，才能进入“打中破口”分支。
        // 注意：这里只用于“特效分类”，不能拿 breachPoint 截短实体搜索——服务端 fire()
        // 用的是 COLLIDER，子弹会穿过破口继续打中后面的生物。
        BlockHitResult outlineHit = minecraft.level.clip(new ClipContext(origin, blockEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        Vec3 breachPoint = null;
        if (outlineHit.getType() != HitResult.Type.MISS) {
            BlockState outlineState = minecraft.level.getBlockState(outlineHit.getBlockPos());
            if (outlineState.getBlock() instanceof HullBlock && HullBlock.isBreached(outlineState)) {
                breachPoint = outlineHit.getLocation();
            }
        }

        LivingEntity target = findClientTarget(minecraft, origin, blockEnd);
        if (target != null) {
            Optional<Vec3> hit = target.getBoundingBox().inflate(0.25D).clip(origin, blockEnd);
            if (hit.isPresent()) {
                AABB box = target.getBoundingBox();
                double halfWidth = Math.max(box.getXsize(), box.getZsize()) * 0.5D;
                return new ImpactPrediction(hit.get(), halfWidth, box.getYsize() * 0.5D,
                        HitKind.CREATURE);
            }
        }
        if (breachPoint != null) {
            return new ImpactPrediction(breachPoint, 0.5D, 0.5D, HitKind.HULL_BREACHED);
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            HitKind kind = classifyBlock(minecraft.level, blockHit.getBlockPos());
            return new ImpactPrediction(blockEnd, 0.5D, 0.5D, kind);
        }
        // 方块按 1×1 半格处理；打空也按这个尺寸，准心不会缩没。
        return new ImpactPrediction(blockEnd, 0.5D, 0.5D, HitKind.MISS);
    }

    /** 方块分类：舷窗 > 船壳 > 其他地形（海床/岩壁）。 */
    private static HitKind classifyBlock(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModShipBlocks.HULL_GLASS.get()) || state.is(ModShipBlocks.TEST_HULL.get())) {
            return HitKind.GLASS;
        }
        if (state.getBlock() instanceof HullBlock) {
            return HitKind.HULL_INTACT;
        }
        if (isStructure(state)) {
            return HitKind.STRUCTURE;
        }
        return HitKind.SEABED;
    }

    /** 船上的设备/结构；炮塔方块不在 `isShipBlock` 里，单独补上。 */
    private static boolean isStructure(BlockState state) {
        return state.is(com.opamb.barotrauma.registry.ModBlocks.TURRET.get())
                || state.is(com.opamb.barotrauma.registry.ModBlocks.FREE_AIM_TURRET.get())
                || state.is(com.opamb.barotrauma.registry.ModBlocks.TRACKING_TURRET.get())
                || ModShipBlocks.isShipBlock(state);
    }

    @Nullable
    private static LivingEntity findClientTarget(Minecraft minecraft, Vec3 origin, Vec3 end) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        AABB search = new AABB(origin, end).inflate(1.0D);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        // 直连炮塔时操作者站在炮座旁，炮口可能插进自己身体：和服务端一样排除自己。
        // 站台接管时身体在别处，是合法目标。
        final boolean excludeSelf = !fromStation;
        for (LivingEntity candidate : minecraft.level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> (!excludeSelf || entity != minecraft.player)
                        && entity.isAlive() && !entity.isSpectator())) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.25D).clip(origin, end);
            if (hit.isPresent()) {
                double distance = origin.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return best;
    }

    // ---- 每 tick ----

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.isDown()) blockHeldUse = false;
        if (minecraft.level == null) {
            awaitingExit.clear();
        } else {
            awaitingExit.removeIf(pos -> !minecraft.level.hasChunkAt(pos));
        }
        // 即使不在炮塔里也要清空按键队列，避免进入炮塔后补触发一次切换。
        while (TOGGLE_FIRE_MODE.consumeClick()) {
            if (active && activePos != null && !syncedUsesAmmo) {
                ModNetwork.sendToServer(new TurretModePacket(activePos));
            }
        }
        if (!active) {
            return;
        }
        if (!killIndicatorActive()) {
            killChainCount = 0;
        }
        if (hitIndicatorTicks > 0) {
            hitIndicatorTicks--;
        }
        if (hitChainCount > 0 && hasHitConfirm
                && System.nanoTime() - lastHitConfirmNanos
                > HIT_COMBO_WINDOW_MILLIS * 1_000_000L) {
            hitChainCount = 0;
            hasHitConfirm = false;
            lastHitConfirmNanos = 0L;
        }
        if (minecraft.player == null || minecraft.level == null || activePos == null) {
            deactivate(false);
            return;
        }
        if (minecraft.screen != null) {
            requestExit();
            return;
        }
        if (!(minecraft.level.getBlockEntity(activePos) instanceof TurretBlockEntity)) {
            requestExit();
            return;
        }
        if (activeAnchor != null && (!minecraft.level.hasChunkAt(activeAnchor)
                || !(minecraft.level.getBlockState(activeAnchor).getBlock()
                instanceof com.opamb.barotrauma.block.PeriscopeBlock))) {
            requestExit();
            return;
        }
        if (minecraft.player.isDeadOrDying() || minecraft.player.isSpectator()) {
            requestExit();
            return;
        }

        boolean shift = minecraft.options.keyShift.isDown();
        if (shift && !shiftWasDown) {
            shiftWasDown = true;
            requestExit();
            return;
        }
        shiftWasDown = shift;

        // 右键瞄准：按住生效；进入炮塔时按着的那次右键先松开才算数。
        boolean useDown = minecraft.options.keyUse.isDown();
        if (!useDown) {
            aimArmed = true;
        }
        aiming = !syncedUsesAmmo && aimArmed && useDown;
        if (noPowerFlash > 0) noPowerFlash--;
        if (loaderFlash > 0) loaderFlash--;
        if (!syncedUsesAmmo && cooldown > 0) {
            cooldown--;
        }
        // 全自动：按住左键，冷却归零就补一发；单发只走 startAttack。
        if (syncedUsesAmmo) {
            syncCoilgunTrigger(minecraft.options.keyAttack.isDown(), minecraft.level.getGameTime());
        } else if (autoFire && minecraft.options.keyAttack.isDown()) {
            requestFire();
        }
        // F5 不能切回第一人称：否则本机模型被剔除，玩家会以为人消失了。
        if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        sendAimIfChanged();
    }

    private static void sendAimIfChanged() {
        if (Math.abs(Mth.wrapDegrees(aimYaw - lastSentYaw)) > 0.05F
                || Math.abs(aimPitch - lastSentPitch) > 0.05F) {
            ModNetwork.sendToServer(new TurretAimPacket(activePos, aimYaw, aimPitch));
            lastSentYaw = aimYaw;
            lastSentPitch = aimPitch;
        }
    }

    // ---- 渲染 / 输入接管 ----

    /** 炮塔里站着别动：清空移动输入（但保留 Shift 键映射本身给退出用）。 */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // 客户端不再渲染旧世界，地波不会自己 tick 到清理；这里显式释放引用。
        TurretGroundWave.clear();
        deactivate(false);
        awaitingExit.clear();
        blockHeldUse = false;
    }

    /** 瞄准缩放：FOV 按 aimBlend 平滑收拢，松开右键再平滑恢复。 */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) {
            // 手部/其他用途的 FOV 调用不要污染 HUD 的缓存。
            return;
        }
        if (!active) {
            renderedFov = (float) event.getFOV();
            return;
        }
        float fov = (float) (event.getFOV()
                * (1.0F - (1.0F - AIM_FOV_SCALE) * aimBlend));
        event.setFOV(fov);
        // 缓存真正提交的 FOV：ComputeFov 在 ComputeCameraAngles 之前执行，
        // HUD 若自己再算一遍会跟画面差一帧。
        renderedFov = fov;
    }

    private static void updateAimBlend() {
        long now = System.nanoTime();
        float deltaSeconds = Mth.clamp((now - lastAimNanos) / 1.0E9F, 0.0F, 0.1F);
        lastAimNanos = now;
        float target = aiming ? 1.0F : 0.0F;
        aimBlend += (target - aimBlend)
                * Mth.clamp(deltaSeconds * AIM_BLEND_PER_SECOND, 0.0F, 1.0F);
        if (Math.abs(target - aimBlend) < 0.01F) {
            aimBlend = target;
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        // 地波和在飞的炮弹都独立于炮塔状态推进：退出炮塔后剩下的半圈/半发也要飞完。
        TurretGroundWave.tickFrame();
        tickBeam();
        if (!active) {
            return;
        }
        float previousBlend = aimBlend;
        updateAimBlend();
        if (freeAim && aimBlend != previousBlend) {
            // 光标偏移是角度，屏幕位置 = offset / tan(halfFov)。FOV 变化时按 tan 比例
            // 缩放偏移，光标才会留在鼠标原来的屏幕位置，而不是向外跳。
            double oldTan = Math.tan(Math.toRadians(fovForBlend(previousBlend) * 0.5D));
            double newTan = Math.tan(Math.toRadians(fovForBlend(aimBlend) * 0.5D));
            float ratio = (float) (newTan / oldTan);
            cursorYaw *= ratio;
            cursorPitch *= ratio;
            clampCursor();
        }
        if (tracking) {
            // 视角就是玩家自己的（原版鼠标视角），这里只让炮塔准心限速追过去。
            stepTracking(event.getYaw(), event.getPitch());
            viewYaw = event.getYaw();
            viewPitch = event.getPitch();
            return;
        }
        if (freeAim) {
            stepFreeAim();
        }
        CameraAngles angles = cameraAngles();
        float camYaw = angles.yaw();
        float camPitch = angles.pitch();
        viewYaw = camYaw;
        viewPitch = camPitch;
        event.setYaw(camYaw);
        event.setPitch(camPitch);
        event.setRoll(0.0F);
    }

    /** 跟随模式：炮塔准心朝视角中心转，单帧转角受 {@link #TRACKING_MAX_SPEED} 限制。 */
    private static void stepTracking(float targetYaw, float targetPitch) {
        long now = System.nanoTime();
        float deltaSeconds = Mth.clamp((now - lastTrackNanos) / 1.0E9F, 0.0F, 0.05F);
        lastTrackNanos = now;
        if (deltaSeconds <= 0.0F) {
            return;
        }
        float maxStep = TRACKING_MAX_SPEED * deltaSeconds;
        float stepYaw = Mth.clamp(Mth.wrapDegrees(targetYaw - aimYaw), -maxStep, maxStep);
        float stepPitch = Mth.clamp(
                Mth.clamp(targetPitch, pitchMin(), pitchMax()) - aimPitch,
                -maxStep, maxStep);
        aimYaw = clampAimYaw(aimYaw + stepYaw);
        aimPitch = Mth.clamp(aimPitch + stepPitch, pitchMin(), pitchMax());
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!active) {
            return;
        }
        if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()
                || event.getOverlay() == VanillaGuiOverlay.VIGNETTE.type()
                || event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()
                || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
            event.setCanceled(true);
        }
    }
}
