package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.TurretBlock;
import com.opamb.barotrauma.block.TurretSpec;
import com.opamb.barotrauma.medical.bridge.MedicalEvents;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.TurretHitPacket;
import com.opamb.barotrauma.network.TurretStatePacket;
import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 舰载炮塔方块实体（原型）。
 *
 * <p>服务端权威：占用者、方位/俯仰角、射击冷却都在这里。客户端只发「我想转到
 * 这个角度」和「开火」，角度按每 tick 最大转速截断，射线命中也在服务端算。
 * 炮管渲染读同步过来的 yaw/pitch。</p>
 *
 * <p>相机在客户端 {@code TurretClient.cameraPosition()}，弹道起点是
 * {@link #muzzlePosition()}；两者不共线，HUD 落点括号负责显示真实弹道。</p>
 */
public class TurretBlockEntity extends BlockEntity {

    /** 单发间隔（tick）：0.5 秒，和原版炮塔的连射节奏接近。 */
    public static final int FIRE_COOLDOWN_TICKS = 10;

    /** 全自动间隔（tick）：4 tick = 5 发/秒。 */
    public static final int AUTO_FIRE_COOLDOWN_TICKS = 4;

    /** 射程（格）。 */
    public static final double RANGE = 48.0D;

    /** 服务端每 tick 允许的最大转动（度）；客户端用 120°/s 的同一上限。 */
    public static final float MAX_TURN_PER_TICK = 6.0F;

    /** 客户端每秒最大转动（度），供 HUD/输入层换算每帧上限。 */
    public static final float MAX_TURN_PER_SECOND = MAX_TURN_PER_TICK * 20.0F;

    /** 兜底值；实际每座炮塔按方块 {@link TurretSpec} 的挂载窗口。 */
    public static final float MIN_PITCH = -89.0F;
    public static final float MAX_PITCH = 89.0F;

    /** 操作者离炮塔的最远距离（格）。 */
    public static final double MAX_OPERATOR_DISTANCE = 6.0D;

    /** 每个玩家当前占用的炮塔，防止同时占用两台后旧的那台一直锁着。 */
    private static final Map<UUID, BlockPos> ACTIVE = new HashMap<>();

    private float yaw;
    private float pitch;
    private float prevYaw;
    private float prevPitch;
    private int cooldown;
    /** true = 全自动（按住左键每 4 tick 连发）；false = 单发。 */
    private boolean autoFire;
    /** 客户端最近一次请求的目标角：服务端每 tick 朝它转，丢包不会丢掉剩余角度。 */
    private float aimTargetYaw;
    private float aimTargetPitch;
    private boolean aimTargetSet;

    @Nullable
    private UUID operator;
    /** 同步给客户端的占用者实体 id（-1 = 空）。 */
    private int operatorEntityId = -1;
    /**
     * 从瞄准站接管时的锚点（站台坐标）；直接进炮塔时为 null。
     * 只存在于内存：重启后占用关系本来就不保留，不需要存档。
     */
    @Nullable
    private BlockPos operatorAnchor;
    /** 通过潜望镜进入时的控制器位置（世界坐标）；直接操作炮塔时为 null。 */
    @Nullable
    private BlockPos controllerPos;

    public TurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURRET.get(), pos, state);
        this.yaw = initialYaw(state);
        this.pitch = initialPitch(state);
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
    }

    /** 所有挂载都从水平中位起步，操作和落地炮塔一致。 */
    private static float initialPitch(BlockState state) {
        return 0.0F;
    }

    /** 安装面朝向；旧存档没有 MOUNT 时按落地处理。 */
    public static Direction mount(BlockState state) {
        return state.hasProperty(TurretBlock.MOUNT)
                ? state.getValue(TurretBlock.MOUNT)
                : Direction.UP;
    }

    /** 当前方块类型的几何/操作规格；换 2×2/3×3 时由方块覆盖 {@code spec()}。 */
    public static TurretSpec spec(BlockState state) {
        return TurretBlock.spec(state);
    }

    public static float minPitch(BlockState state) {
        return spec(state).minPitch(mount(state));
    }

    public static float maxPitch(BlockState state) {
        return spec(state).maxPitch(mount(state));
    }

    public float minPitch() {
        return minPitch(getBlockState());
    }

    public float maxPitch() {
        return maxPitch(getBlockState());
    }

    /** 转盘轴仍是世界 Y；yaw 和落地炮塔一样整圈。 */
    public static float clampYaw(BlockState state, float yaw) {
        return Mth.wrapDegrees(yaw);
    }


    private static float yawForDirection(Direction direction) {
        if (direction == Direction.SOUTH) {
            return 0.0F;
        }
        if (direction == Direction.WEST) {
            return 90.0F;
        }
        if (direction == Direction.NORTH) {
            return 180.0F;
        }
        if (direction == Direction.EAST) {
            return -90.0F;
        }
        return 0.0F;
    }

    private static float initialYaw(BlockState state) {
        if (!state.hasProperty(TurretBlock.FACING)) {
            return 0.0F;
        }
        // 用 if 链而不是 enum switch：switch 会生成 TurretBlockEntity$1 合成类，
        // 开发环境热替换/增量编译时曾出现合成类加载不到（NoClassDefFoundError）。
        return yawForDirection(state.getValue(TurretBlock.FACING));
    }

    // ---- 状态读取 ----

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float prevYaw() {
        return prevYaw;
    }

    public float prevPitch() {
        return prevPitch;
    }

    public int cooldown() {
        return cooldown;
    }

    public boolean autoFire() {
        return autoFire;
    }

    public int operatorEntityId() {
        return operatorEntityId;
    }

    /** 局部瞄准单位向量（yaw 0 = +Z，pitch 正 = -Y）。 */
    public static Vec3 localLookVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new Vec3(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad));
    }

    /** 世界瞄准方向：仍然是世界 yaw/pitch，安装面只影响放置外观。 */
    public Vec3 lookVector() {
        return localLookVector(yaw, pitch);
    }

    public static Vec3 lookVector(BlockState state, float yaw, float pitch) {
        return localLookVector(yaw, pitch);
    }

    /** 炮口世界坐标：枢轴 + 沿瞄准方向伸出炮管长度。 */
    public Vec3 muzzlePosition() {
        return muzzlePosition(getBlockState(), getBlockPos(), lookVector());
    }

    /** 轴心偏移由方块 spec 决定；所有挂载共用同一个轴心，操作/弹道一致。 */
    public static Vec3 pivotOffset(BlockState state) {
        return spec(state).pivotOffset();
    }

    public static Vec3 pivotPosition(BlockState state, BlockPos pos) {
        return spec(state).pivot(pos);
    }

    /** 供客户端按本地瞄准角算炮口的同一套公式（服务端/客户端弹道必须同源）。 */
    public static Vec3 muzzlePosition(BlockState state, BlockPos pos, Vec3 direction) {
        return spec(state).muzzle(pos, direction);
    }

    /** 相机位置也由 spec 提供；换炮塔尺寸/模型时客户端不需要改。 */
    public static Vec3 cameraPosition(BlockState state, BlockPos pos) {
        return spec(state).camera(pos, mount(state));
    }

    /**
     * 多块炮塔的主方块坐标。当前 1×1 原型就是自身；未来 2×2/3×3 由方块类
     * 覆盖这里（例如返回左下角主方块），站台/绑定器/网络包都按主方块工作。
     */
    public static BlockPos masterPos(BlockState state, BlockPos pos) {
        return pos;
    }

    /**
     * 从任意方块格解析炮塔 BE：当前 1×1 直接查自身；未来多块炮塔先走
     * {@link #masterPos}，站台和绑定器不需要知道“点到的是哪一块”。
     */
    @Nullable
    public static TurretBlockEntity at(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TurretBlock)) {
            return null;
        }
        BlockPos master = masterPos(state, pos);
        return level.getBlockEntity(master) instanceof TurretBlockEntity turret ? turret : null;
    }

    // ---- 进入 / 退出 ----

    public InteractionResult enter(Player player) {
        return enterInternal(player, null, null);
    }

    /**
     * 从瞄准站接管：站台必须仍然绑定本炮塔；操作距离不限，但站台本身由每 tick
     * 的 {@link #stationAnchorValid(ServerLevel)} 校验，站被拆/改绑会立刻退出。
     */
    public InteractionResult enterFromStation(Player player, BlockPos stationPos) {
        if (!(level instanceof ServerLevel server)
                || !(server.getBlockEntity(stationPos) instanceof TurretStationBlockEntity station)
                || !getBlockPos().equals(station.turretPos())) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("msg.barotrauma.turret.station.missing"), true);
            }
            return InteractionResult.FAIL;
        }
        return enterInternal(player, stationPos.immutable(), null);
    }

    /**
     * 通过潜望镜等控制器进入：占用距离和每 tick 的存活校验都按控制器的世界眼位算，
     * 而不是船坞里的炮塔方块坐标。
     */
    public InteractionResult enter(Player player, @Nullable BlockPos controllerPos) {
        return enterInternal(player, null,
                controllerPos == null ? null : controllerPos.immutable());
    }

    private InteractionResult enterInternal(Player player, @Nullable BlockPos stationPos,
                                            @Nullable BlockPos controllerPos) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (operator != null && !operator.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("msg.barotrauma.turret.occupied"), true);
            return InteractionResult.FAIL;
        }
        if (stationPos == null) {
            // 物理船上的炮塔/潜望镜都在船坞里，直接用船坞坐标比距离永远是 3e7 格。
            Vec3 anchor = interactionAnchor(controllerPos);
            if (player.distanceToSqr(anchor) > MAX_OPERATOR_DISTANCE * MAX_OPERATOR_DISTANCE) {
                return InteractionResult.FAIL;
            }
        }

        BlockPos previous = ACTIVE.put(player.getUUID(), getBlockPos());
        if (previous != null && !previous.equals(getBlockPos())
                && server.getBlockEntity(previous) instanceof TurretBlockEntity old
                // ACTIVE 只存坐标不存维度：只有旧炮塔确实归本人时才清，避免跨维度误踢别人。
                && player.getUUID().equals(old.operator)) {
            old.clearOperator(false);
        }

        this.controllerPos = controllerPos;
        operator = player.getUUID();
        operatorEntityId = player.getId();
        this.operatorAnchor = stationPos;
        // 冷却由射击与 serverTick 管理，换人不重置：接替者不能靠重新进入跳过后坐。
        player.displayClientMessage(Component.translatable(stationPos == null
                ? "msg.barotrauma.turret.entered"
                : "msg.barotrauma.turret.station.entered"), true);
        broadcastState();
        // 站台可能在 128 格广播半径之外：进入状态必须单独直发占用者，否则其客户端不会激活视角。
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendToPlayer(serverPlayer, statePacket());
        }
        return InteractionResult.CONSUME;
    }

    /** 主动退出（客户端按 Shift）。 */
    public void exit(ServerPlayer player) {
        if (operator != null && operator.equals(player.getUUID())) {
            clearOperator(true);
        }
    }

    private void clearOperator(boolean notify) {
        UUID old = operator;
        operator = null;
        operatorEntityId = -1;
        operatorAnchor = null;
        controllerPos = null;
        aimTargetSet = false;
        // 冷却跨占用者保留：退出再进入不能立刻连发。
        if (old != null) {
            ACTIVE.remove(old, getBlockPos());
            if (level instanceof ServerLevel server) {
                ServerPlayer player = server.getServer().getPlayerList().getPlayer(old);
                if (player != null) {
                    if (notify) {
                        player.displayClientMessage(
                                Component.translatable("msg.barotrauma.turret.exited"), true);
                    }
                    // 站台可能离炮塔很远，128 格广播够不到：强制退出必须直发本人。
                    ModNetwork.sendToPlayer(player, statePacket());
                }
            }
        }
        broadcastState();
    }

    /** 瞄准站被拆除/替换时由站台方块调用：只踢走以它为锚点的占用者。 */
    public void onStationRemoved(BlockPos stationPos) {
        if (operatorAnchor != null && operatorAnchor.equals(stationPos)) {
            clearOperator(true);
        }
    }

    // ---- 服务端 tick ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, TurretBlockEntity turret) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (turret.cooldown > 0) {
            turret.cooldown--;
        }
        if (turret.operator != null) {
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(turret.operator);
            boolean invalid = player == null
                    || player.level() != server
                    || player.isDeadOrDying()
                    || player.isSpectator();
            if (!invalid) {
                if (turret.operatorAnchor != null) {
                    // 站台接管：距离不限，但站台必须还在且仍指向本炮塔。
                    invalid = !turret.stationAnchorValid(server);
                } else {
                    if (turret.controllerPos != null) {
                        // 潜望镜：区块未加载时不要 getBlockEntity（会同步加载区块）。
                        invalid = !server.hasChunkAt(turret.controllerPos)
                                || !(server.getBlockEntity(turret.controllerPos)
                                instanceof PeriscopeBlockEntity)
                                || !com.opamb.barotrauma.block.PeriscopeBlock
                                        .hasStand(server, turret.controllerPos);
                    }
                    if (!invalid) {
                        invalid = !turret.withinReach(player);
                    }
                }
            }
            if (invalid) {
                turret.clearOperator(true);
            } else {
                if (turret.aimTargetSet) {
                    turret.stepAim();
                }
                // 同步冷却和已随船变换的炮塔镜头位置。
                turret.broadcastState();
            }
        }
    }

    /**
     * 客户端 tick：把插值起点收敛到当前角，否则观察者收到的 54°→60° 会一直重复插值，
     * 鼠标停下后炮管仍在每 tick 抖一段。
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, TurretBlockEntity turret) {
        turret.prevYaw = turret.yaw;
        turret.prevPitch = turret.pitch;
    }

    // ---- 瞄准 / 射击 ----

    /**
     * 客户端每次鼠标移动后发绝对目标角。这里只记录目标，实际转角放到
     * {@link #serverTick} 里按每 tick 预算推进：同一 tick 收到多个包不会叠加成
     * 一次转 12°，超过预算的剩余角度也不会被丢掉。
     */
    public void aim(ServerPlayer player, float targetYaw, float targetPitch) {
        if (!Float.isFinite(targetYaw) || !Float.isFinite(targetPitch)
                || !isOperator(player) || !withinReach(player)) {
            return;
        }
        aimTargetYaw = clampYaw(getBlockState(), targetYaw);
        aimTargetPitch = Mth.clamp(targetPitch, minPitch(getBlockState()), maxPitch(getBlockState()));
        aimTargetSet = true;
    }

    /** 服务端每 tick 朝目标角追赶，最多 {@link #MAX_TURN_PER_TICK}。 */
    private void stepAim() {
        float deltaYaw = Mth.clamp(Mth.wrapDegrees(aimTargetYaw - yaw),
                -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
        float deltaPitch = Mth.clamp(aimTargetPitch - pitch,
                -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
        if (Math.abs(deltaYaw) < 1.0E-4F && Math.abs(deltaPitch) < 1.0E-4F) {
            aimTargetSet = false;
            return;
        }
        applyAim(clampYaw(getBlockState(), yaw + deltaYaw),
                Mth.clamp(pitch + deltaPitch, minPitch(getBlockState()), maxPitch(getBlockState())));
        setChanged();
        // 状态包由 serverTick 每 tick 统一发（镜头锚点也要跟着船走）。
    }

    public void fire(ServerPlayer player) {
        if (!isOperator(player) || !withinReach(player) || cooldown > 0) {
            return;
        }
        cooldown = autoFire ? AUTO_FIRE_COOLDOWN_TICKS : FIRE_COOLDOWN_TICKS;

        Vec3 origin = muzzlePosition();
        Vec3 direction = lookVector();
        Vec3 end = origin.add(direction.scale(RANGE));

        BlockHitResult blockHit = level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        // 直连炮塔时操作者就站在炮座旁，炮口可能插进自己身体：排除自己。
        // 站台接管时身体在别处，属于合法目标（也方便单机验收“炮塔打玩家”）。
        LivingEntity target = findTarget(operatorAnchor == null ? player : null, origin, end);
        if (target != null) {
            boolean damaged = target.hurt(level.damageSources().playerAttack(player), 12.0F);
            if (!damaged && target instanceof Player targetPlayer
                    && !targetPlayer.isDeadOrDying()
                    && !targetPlayer.isSpectator()
                    && targetPlayer.hurtTime <= 0
                    && targetPlayer.isCreative()) {
                // 原版 hurt 在创造模式会直接拒绝玩家伤害；炮塔是测试武器，这里绕过拦截，
                // 直接按医疗系统的近战通用规则注入病症。
                damaged = MedicalEvents.applyDirectDamage(targetPlayer, 12.0F);
            }
            if (damaged) {
                // 命中/击杀反馈只在服务端确认造成伤害后发给操作者，客户端不再按弹道预测。
                boolean killed = target.isDeadOrDying() || !target.isAlive()
                        || target.getHealth() <= 0.0F;
                ModNetwork.sendToPlayer(player, new TurretHitPacket(getBlockPos(), killed));
            }
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.35F, 1.6F);
        }

        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.6F, 0.7F);
        broadcastState();
    }

    /**
     * 切换单发/全自动。只有当前操作者能切；模式跟炮塔走，重新进入或重启后保留。
     * 切到全自动时把剩余冷却压到全自动间隔；切回单发时至少补足单发间隔，
     * 避免靠反复切模式绕过 10 tick 冷却。
     */
    public void toggleFireMode(ServerPlayer player) {
        if (!isOperator(player) || !withinReach(player)) {
            return;
        }
        autoFire = !autoFire;
        if (autoFire) {
            cooldown = Math.min(cooldown, AUTO_FIRE_COOLDOWN_TICKS);
        } else {
            cooldown = Math.max(cooldown, FIRE_COOLDOWN_TICKS);
        }
        setChanged();
        broadcastState();
        player.displayClientMessage(Component.translatable(autoFire
                ? "msg.barotrauma.turret.mode.auto"
                : "msg.barotrauma.turret.mode.single"), true);
    }

    @Nullable
    private LivingEntity findTarget(@Nullable LivingEntity excluded, Vec3 origin, Vec3 end) {
        AABB search = new AABB(origin, end).inflate(1.0D);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> (excluded == null || entity != excluded)
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

    private void applyAim(float newYaw, float newPitch) {
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
        this.yaw = newYaw;
        this.pitch = newPitch;
    }

    private boolean isOperator(ServerPlayer player) {
        return operator != null && operator.equals(player.getUUID());
    }

    private boolean withinReach(ServerPlayer player) {
        if (operatorAnchor != null) {
            // 站台接管不限操作距离；但连接本身必须仍然有效。
            return level instanceof ServerLevel server && stationAnchorValid(server);
        }
        return player.distanceToSqr(interactionAnchor(controllerPos))
                <= MAX_OPERATOR_DISTANCE * MAX_OPERATOR_DISTANCE;
    }

    /**
     * 操作者看到的镜头位置（世界坐标）。
     *
     * <p>物理船上的方块都在船坞里，直接拿方块坐标当镜头会在 x≈-2800 万、z≈1200 万
     * 的船坞虚空里，画面只剩深海背景。这里用
     * {@code VsPilot.assemblyToWorld} 把船坞坐标换回船当前的世界位置；世界方块
     * （未上船的炮塔）保持原坐标。</p>
     */
    public Vec3 cameraAnchor() {
        BlockPos pos = getBlockPos();
        return toWorld(pos, cameraPosition(getBlockState(), pos));
    }

    private Vec3 interactionAnchor(@Nullable BlockPos controller) {
        return controller == null ? cameraAnchor()
                : toWorld(controller, Vec3.atCenterOf(controller).add(0.0D, 0.58D, 0.0D));
    }

    private Vec3 toWorld(BlockPos block, Vec3 localPoint) {
        return com.opamb.barotrauma.ship.vs.VsState.active()
                ? com.opamb.barotrauma.ship.vs.VsPilot.shipyardPointToWorld(level, block, localPoint)
                : localPoint;
    }

    /** 占用锚点是否仍是一个“绑定到本炮塔”的瞄准站。 */
    private boolean stationAnchorValid(ServerLevel server) {
        if (operatorAnchor == null) {
            return true;
        }
        // 区块未加载时不要 getBlockEntity（会同步加载区块）：直接视为失效。
        if (!server.hasChunkAt(operatorAnchor)) {
            return false;
        }
        return server.getBlockEntity(operatorAnchor) instanceof TurretStationBlockEntity station
                && getBlockPos().equals(station.turretPos());
    }

    // ---- 同步 ----

    public TurretStatePacket statePacket() {
        return new TurretStatePacket(getBlockPos(), yaw, pitch, cooldown, operatorEntityId,
                operatorAnchor != null, controllerPos, cameraAnchor(), autoFire);
    }

    public void broadcastState() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        TurretStatePacket packet = statePacket();
        double centerX = getBlockPos().getX() + 0.5D;
        double centerY = getBlockPos().getY() + 0.5D;
        double centerZ = getBlockPos().getZ() + 0.5D;
        double radius = 128.0D * 128.0D;
        ServerPlayer operatorPlayer = operator == null ? null
                : server.getServer().getPlayerList().getPlayer(operator);
        for (ServerPlayer player : server.players()) {
            if (player == operatorPlayer
                    || player.distanceToSqr(centerX, centerY, centerZ) <= radius) {
                ModNetwork.sendToPlayer(player, packet);
            }
        }
    }

    /** 客户端收包后写入纯表现字段（不参与服务端占用逻辑）。 */
    public void applyClientState(float newYaw, float newPitch, int newCooldown, int newOperatorId,
                                 boolean newAutoFire) {
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
        this.yaw = newYaw;
        this.pitch = newPitch;
        this.cooldown = newCooldown;
        this.operatorEntityId = newOperatorId;
        this.autoFire = newAutoFire;
    }

    // ---- 保存 ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("TurretYaw", yaw);
        tag.putFloat("TurretPitch", pitch);
        tag.putBoolean("AutoFire", autoFire);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("TurretYaw")) {
            this.yaw = tag.getFloat("TurretYaw");
        }
        if (tag.contains("TurretPitch")) {
            this.pitch = tag.getFloat("TurretPitch");
        }
        this.autoFire = tag.getBoolean("AutoFire");
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
    }

    /**
     * BER 的可见包围盒。炮管本身伸出方块 1.14 格，未来 2×2/3×3 模型更大；
     * 不扩大这里会在方块刚好出画面时把整根炮管一起剔除。
     */
    @Override
    public AABB getRenderBoundingBox() {
        TurretSpec spec = spec(getBlockState());
        return new AABB(getBlockPos())
                .inflate(2.0D)
                .expandTowards(Math.max(0, spec.sizeX() - 1), 0.0D, Math.max(0, spec.sizeZ() - 1));
    }

    @Override
    public void setRemoved() {
        if (operator != null) {
            UUID old = operator;
            ACTIVE.remove(old, getBlockPos());
            operator = null;
            operatorEntityId = -1;
            operatorAnchor = null;
            controllerPos = null;
            if (level instanceof ServerLevel server) {
                ServerPlayer player = server.getServer().getPlayerList().getPlayer(old);
                if (player != null) {
                    player.displayClientMessage(Component.translatable("msg.barotrauma.turret.exited"), true);
                    // 炮塔被拆时客户端必须收到空占用状态，站台可能在广播半径外。
                    ModNetwork.sendToPlayer(player, statePacket());
                }
            }
        }
        super.setRemoved();
    }
}
