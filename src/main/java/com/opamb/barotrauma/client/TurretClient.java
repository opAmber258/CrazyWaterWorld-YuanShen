package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.FreeAimTurretBlock;
import com.opamb.barotrauma.block.TrackingTurretBlock;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.TurretAimPacket;
import com.opamb.barotrauma.network.TurretExitPacket;
import com.opamb.barotrauma.network.TurretFirePacket;
import com.opamb.barotrauma.network.TurretStatePacket;
import com.opamb.barotrauma.registry.ModParticles;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 炮塔视角的客户端状态机（原型）。
 *
 * <p>进入/退出完全由服务端的 {@link TurretStatePacket} 驱动；本地只负责：</p>
 * <ul>
 *   <li>把鼠标增量转成炮塔角度，并按每秒最大转角截断（“拖拽速度上限”）；</li>
 *   <li>接管相机位置与角度（{@link com.opamb.barotrauma.mixin.TurretCameraMixin}）；</li>
 *   <li>隐藏原版准星/热键栏/经验条，改画炮塔 HUD（{@link TurretHud}）；</li>
 *   <li>右键开火、左 Shift 退出。</li>
 * </ul>
 *
 * <p>两种炮塔共用服务端逻辑，区别只在鼠标输入：普通炮塔鼠标直接转视角；
 * 自由瞄准炮塔（{@link FreeAimTurretBlock}）先用鼠标推动一个可以拖到屏幕
 * 任意位置的光标，视角再以有限角速度跟随它。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TurretClient {

    /** 本地预测冷却比服务端多 2 tick，避免包在途时被服务端拒绝。 */
    private static final int LOCAL_COOLDOWN = TurretBlockEntity.FIRE_COOLDOWN_TICKS + 2;

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

    /** 开火粒子束的飞行速度（格/秒）与粒子间距（格）。 */
    private static final float BEAM_SPEED = 24.0F;
    private static final float BEAM_DOT_SPACING = 0.12F;

    private static boolean active;
    @Nullable
    private static BlockPos activePos;
    private static float aimYaw;
    private static float aimPitch;
    private static float lastSentYaw;
    private static float lastSentPitch;
    @Nullable
    private static Vec3 beamOrigin;
    private static Vec3 beamDirection = Vec3.ZERO;
    private static float beamTravel;
    private static float beamLength;
    private static long beamLastNanos;
    private static int cooldown;
    /** 防止“进入炮塔的那次右键”立刻触发开火：必须先松手一次。 */
    private static boolean fireArmed;
    private static boolean shiftWasDown;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static long lastTurnNanos = System.nanoTime();

    /** 当前炮塔是否是自由瞄准型。 */
    private static boolean freeAim;
    /** 当前炮塔是否是跟随型：视角自由移动，炮塔准心限速追视角中心。 */
    private static boolean tracking;
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

    private TurretClient() {
    }

    // ---- 状态查询（mixin / HUD 用） ----

    public static boolean isActive() {
        return active;
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

    public static int cooldown() {
        return cooldown;
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

    /** 相机位置 = 服务端射线起点，保证准星中心就是弹道。 */
    @Nullable
    public static Vec3 cameraPosition() {
        if (!active || activePos == null) {
            return null;
        }
        return new Vec3(activePos.getX() + 0.5D,
                activePos.getY() + 0.5D + TurretBlockEntity.EYE_HEIGHT,
                activePos.getZ() + 0.5D);
    }

    // ---- 服务端状态 ----

    public static void onState(TurretStatePacket state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.level.getBlockEntity(state.pos()) instanceof TurretBlockEntity turret) {
            turret.applyClientState(state.yaw(), state.pitch(), state.cooldown(),
                    state.operatorEntityId());
        }
        if (minecraft.player == null) {
            return;
        }
        boolean mine = state.operatorEntityId() == minecraft.player.getId();
        if (mine) {
            if (!active || activePos == null || !state.pos().equals(activePos)) {
                activate(state.pos(), state.yaw(), state.pitch());
            } else {
                // 以服务端冷却为准，避免本地连发被拒。
                cooldown = Math.max(cooldown, state.cooldown());
            }
        } else if (active && state.pos().equals(activePos)) {
            deactivate(true);
        }
    }

    private static void activate(BlockPos pos, float yaw, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        active = true;
        activePos = pos;
        aimYaw = yaw;
        aimPitch = pitch;
        lastSentYaw = yaw;
        lastSentPitch = pitch;
        cooldown = 0;
        fireArmed = false;
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
        if (previousCameraType != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private static void deactivate(boolean snapPlayerRotation) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 跟随模式下视角是玩家自己的，退出时不要把它拽回炮塔朝向。
        if (snapPlayerRotation && !tracking && minecraft.player != null) {
            minecraft.player.setYRot(aimYaw);
            minecraft.player.setXRot(aimPitch);
            minecraft.player.yRotO = aimYaw;
            minecraft.player.xRotO = aimPitch;
        }
        minecraft.options.setCameraType(previousCameraType);
        active = false;
        activePos = null;
        cooldown = 0;
        fireArmed = false;
        freeAim = false;
        tracking = false;
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
        ModNetwork.sendToServer(new TurretExitPacket(activePos));
        deactivate(true);
    }

    /** 由 {@code Minecraft#startUseItem} 的 mixin 调用；松手一次后才会真正开火。 */
    public static void requestFire() {
        if (!active || activePos == null || cooldown > 0 || !fireArmed) {
            return;
        }
        ModNetwork.sendToServer(new TurretFirePacket(activePos));
        cooldown = LOCAL_COOLDOWN;
        startBeam();
    }

    /** 由 {@code MouseHandler#turnPlayer} 的 mixin 调用。 */
    public static void onMouseTurn(double deltaX, double deltaY) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();

        if (freeAim) {
            // 自由瞄准：鼠标只推光标，视角在渲染帧里限速追它（见 stepFreeAim）。
            // 光标按“屏幕像素”移动（像普通鼠标指针），再用当前 FOV 换算成角度；
            // 不能直接套原版角度灵敏度——宽屏上那会让准心以 2~3 倍指针速度飞。
            float pixelYaw = (float) deltaX;
            float pixelPitch = (float) deltaY;
            if (minecraft.options.invertYMouse().get()) {
                pixelPitch = -pixelPitch;
            }
            float halfFovY = minecraft.options.fov().get() * 0.5F;
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
        float rawYaw = (float) (deltaX * scale);
        float rawPitch = (float) (deltaY * scale);
        if (minecraft.options.invertYMouse().get()) {
            rawPitch = -rawPitch;
        }

        long now = System.nanoTime();
        float deltaSeconds = Mth.clamp((now - lastTurnNanos) / 1.0E9F, 0.001F, 0.05F);
        lastTurnNanos = now;
        float maxDelta = TurretBlockEntity.MAX_TURN_PER_SECOND * deltaSeconds;

        aimYaw = Mth.wrapDegrees(aimYaw + Mth.clamp(rawYaw, -maxDelta, maxDelta));
        aimPitch = Mth.clamp(aimPitch + Mth.clamp(rawPitch, -maxDelta, maxDelta),
                TurretBlockEntity.MIN_PITCH, TurretBlockEntity.MAX_PITCH);
    }

    /**
     * 把自由光标夹在屏幕内（按当前 FOV 换算成角度），并保证光标指向的绝对角度
     * 不超出炮塔的俯仰限位。
     */
    private static void clampCursor() {
        Minecraft minecraft = Minecraft.getInstance();
        float halfFovY = minecraft.options.fov().get() * 0.5F;
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
                TurretBlockEntity.MIN_PITCH - aimPitch,
                TurretBlockEntity.MAX_PITCH - aimPitch);
    }

    /**
     * 把视角朝自由光标转一点，并把转过的角度从光标偏移里扣掉。
     *
     * <p>由 {@link #onMouseTurn} 在每次鼠标结算（每帧一次）时调用，和这一帧的
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

        aimYaw = Mth.wrapDegrees(aimYaw + stepYaw);
        float newPitch = Mth.clamp(aimPitch + stepPitch,
                TurretBlockEntity.MIN_PITCH, TurretBlockEntity.MAX_PITCH);
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
        Vec3 direction = lookVector(aimYaw, aimPitch);
        Vec3 origin = turretMuzzle(direction);
        ImpactPrediction impact = origin == null ? null : raycastImpact(origin, direction);
        if (origin == null || impact == null) {
            beamOrigin = null;
            return;
        }
        beamOrigin = origin;
        beamDirection = direction;
        beamLength = (float) Math.max(0.5D, origin.distanceTo(impact.point()));
        beamTravel = 0.0F;
        beamLastNanos = System.nanoTime();
    }

    /** 炮口世界坐标：与服务端 {@code TurretBlockEntity#muzzlePosition} 同一套公式。 */
    @Nullable
    private static Vec3 turretMuzzle(Vec3 direction) {
        return activePos == null ? null : TurretBlockEntity.muzzlePosition(activePos, direction);
    }

    /** 每帧把粒子束的头部向前推一小段，沿新推进的区段撒点，看起来就是粒子在飞。 */
    private static void tickBeam() {
        if (beamOrigin == null) {
            return;
        }
        long now = System.nanoTime();
        float deltaSeconds = Mth.clamp((now - beamLastNanos) / 1.0E9F, 0.0F, 0.1F);
        beamLastNanos = now;

        float previous = beamTravel;
        beamTravel = Math.min(beamLength, beamTravel + BEAM_SPEED * deltaSeconds);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            int steps = Math.max(1, (int) Math.ceil((beamTravel - previous) / BEAM_DOT_SPACING));
            for (int i = 1; i <= steps; i++) {
                double distance = Mth.lerp(i / (double) steps, previous, beamTravel);
                Vec3 point = beamOrigin.add(beamDirection.scale(distance));
                // 不抖动、不给速度：整束是一条笔直的细线，点的位置完全由这里算。
                minecraft.level.addParticle(ModParticles.TURRET_TRACER.get(),
                        point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
            }
        }
        if (beamTravel >= beamLength) {
            beamOrigin = null;
        }
    }

    static Vec3 lookVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad));
    }

    // ---- 命中预测（跟随炮塔的落点准心） ----

    /**
     * 一次落点预判的结果：命中点，以及被命中物体的半宽/半高（格）。HUD 用这两个
     * 尺寸算出物体在屏幕上的投影大小，再决定方括号的长短——物体越大，括号越长。
     */
    public record ImpactPrediction(Vec3 point, double halfWidth, double halfHeight) {
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
        if (!active || !tracking) {
            return null;
        }
        Vec3 direction = lookVector(aimYaw, aimPitch);
        Vec3 origin = turretMuzzle(direction);
        if (origin == null) {
            return null;
        }
        return raycastImpact(origin, direction);
    }

    /** 从给定起点沿方向做射线：方块命中优先，其次最近实体；都没中则取最大射程端点。 */
    @Nullable
    private static ImpactPrediction raycastImpact(Vec3 origin, Vec3 direction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        Vec3 end = origin.add(direction.scale(TurretBlockEntity.RANGE));
        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }
        LivingEntity target = findClientTarget(minecraft, origin, end);
        if (target != null) {
            Optional<Vec3> hit = target.getBoundingBox().inflate(0.25D).clip(origin, end);
            if (hit.isPresent()) {
                AABB box = target.getBoundingBox();
                double halfWidth = Math.max(box.getXsize(), box.getZsize()) * 0.5D;
                return new ImpactPrediction(hit.get(), halfWidth, box.getYsize() * 0.5D);
            }
        }
        // 方块按 1×1 半格处理；打空也按这个尺寸，准心不会缩没。
        return new ImpactPrediction(end, 0.5D, 0.5D);
    }

    @Nullable
    private static LivingEntity findClientTarget(Minecraft minecraft, Vec3 origin, Vec3 end) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        AABB search = new AABB(origin, end).inflate(1.0D);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : minecraft.level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> entity != minecraft.player && entity.isAlive() && !entity.isSpectator())) {
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
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || activePos == null) {
            deactivate(false);
            return;
        }
        if (minecraft.screen != null) {
            requestExit();
            return;
        }
        if (!(minecraft.level.getBlockEntity(activePos) instanceof TurretBlockEntity)) {
            deactivate(true);
            return;
        }
        if (minecraft.player.isDeadOrDying()) {
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

        if (!minecraft.options.keyUse.isDown()) {
            fireArmed = true;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }

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
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active) {
            return;
        }
        tickBeam();
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
        event.setYaw(aimYaw);
        event.setPitch(aimPitch);
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
                Mth.clamp(targetPitch, TurretBlockEntity.MIN_PITCH, TurretBlockEntity.MAX_PITCH) - aimPitch,
                -maxStep, maxStep);
        aimYaw = Mth.wrapDegrees(aimYaw + stepYaw);
        aimPitch = Mth.clamp(aimPitch + stepPitch,
                TurretBlockEntity.MIN_PITCH, TurretBlockEntity.MAX_PITCH);
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
                || event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()
                || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
            event.setCanceled(true);
        }
    }
}
