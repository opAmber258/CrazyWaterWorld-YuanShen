package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.TurretBlock;
import com.opamb.barotrauma.network.ModNetwork;
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

    /** 开火间隔（tick）：0.5 秒，和原版炮塔的连射节奏接近。 */
    public static final int FIRE_COOLDOWN_TICKS = 10;

    /** 射程（格）。 */
    public static final double RANGE = 48.0D;

    /** 服务端每 tick 允许的最大转动（度）；客户端用 120°/s 的同一上限。 */
    public static final float MAX_TURN_PER_TICK = 6.0F;

    /** 客户端每秒最大转动（度），供 HUD/输入层换算每帧上限。 */
    public static final float MAX_TURN_PER_SECOND = MAX_TURN_PER_TICK * 20.0F;

    /** 兜底值；实际每座炮塔按 MOUNT 走 {@link #minPitch(BlockState)} / {@link #maxPitch(BlockState)}。 */
    public static final float MIN_PITCH = -89.0F;
    public static final float MAX_PITCH = 89.0F;

    /** 世界 pitch：正=向下，和落地炮塔完全一致，不做安装面局部轴转换。 */
    public static final float FLOOR_MIN_PITCH = -89.0F;
    public static final float FLOOR_MAX_PITCH = 20.0F;
    public static final float CEILING_MIN_PITCH = -10.0F;
    public static final float CEILING_MAX_PITCH = 89.0F;
    public static final float WALL_MIN_PITCH = -80.0F;
    public static final float WALL_MAX_PITCH = 80.0F;

    /** 操作者离炮塔的最远距离（格）。 */
    public static final double MAX_OPERATOR_DISTANCE = 6.0D;

    /** 视点相对方块中心的高度（格）：0.5 + 0.55 = 1.05，位于炮座和炮尾上方，避免相机嵌进模型。 */
    public static final float EYE_HEIGHT = 0.55F;

    /** 炮管枢轴在方块内的 y 偏移（格）：0.75，渲染器与弹道起点共用。 */
    public static final double PIVOT_Y = 0.75D;

    /** 炮口（制退器前端）到枢轴的距离（格）：开火与落点预判都以炮口为起点。 */
    public static final double MUZZLE_LENGTH = 1.14D;

    /** 每个玩家当前占用的炮塔，防止同时占用两台后旧的那台一直锁着。 */
    private static final Map<UUID, BlockPos> ACTIVE = new HashMap<>();

    private float yaw;
    private float pitch;
    private float prevYaw;
    private float prevPitch;
    private int cooldown;
    /** 客户端最近一次请求的目标角：服务端每 tick 朝它转，丢包不会丢掉剩余角度。 */
    private float aimTargetYaw;
    private float aimTargetPitch;
    private boolean aimTargetSet;

    @Nullable
    private UUID operator;
    /** 同步给客户端的占用者实体 id（-1 = 空）。 */
    private int operatorEntityId = -1;

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

    public static float minPitch(BlockState state) {
        Direction mount = mount(state);
        if (mount == Direction.DOWN) {
            return CEILING_MIN_PITCH;
        }
        return mount.getAxis().isHorizontal() ? WALL_MIN_PITCH : FLOOR_MIN_PITCH;
    }

    public static float maxPitch(BlockState state) {
        Direction mount = mount(state);
        if (mount == Direction.DOWN) {
            return CEILING_MAX_PITCH;
        }
        return mount.getAxis().isHorizontal() ? WALL_MAX_PITCH : FLOOR_MAX_PITCH;
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

    /** 所有挂载共用同一个枢轴：方块中心 y=0.75，操作/弹道和落地炮塔完全一致。 */
    public static Vec3 pivotOffset(BlockState state) {
        return new Vec3(0.5D, PIVOT_Y, 0.5D);
    }

    public static Vec3 pivotPosition(BlockState state, BlockPos pos) {
        Vec3 offset = pivotOffset(state);
        return new Vec3(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z);
    }

    /** 供客户端按本地瞄准角算炮口的同一套公式（服务端/客户端弹道必须同源）。 */
    public static Vec3 muzzlePosition(BlockState state, BlockPos pos, Vec3 direction) {
        return pivotPosition(state, pos).add(direction.scale(MUZZLE_LENGTH));
    }

    // ---- 进入 / 退出 ----

    public InteractionResult enter(Player player) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (operator != null && !operator.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("msg.barotrauma.turret.occupied"), true);
            return InteractionResult.FAIL;
        }
        if (player.distanceToSqr(getBlockPos().getX() + 0.5D, getBlockPos().getY() + 0.5D,
                getBlockPos().getZ() + 0.5D) > MAX_OPERATOR_DISTANCE * MAX_OPERATOR_DISTANCE) {
            return InteractionResult.FAIL;
        }

        BlockPos previous = ACTIVE.put(player.getUUID(), getBlockPos());
        if (previous != null && !previous.equals(getBlockPos())
                && server.getBlockEntity(previous) instanceof TurretBlockEntity old
                // ACTIVE 只存坐标不存维度：只有旧炮塔确实归本人时才清，避免跨维度误踢别人。
                && player.getUUID().equals(old.operator)) {
            old.clearOperator(false);
        }

        operator = player.getUUID();
        operatorEntityId = player.getId();
        // 冷却由射击与 serverTick 管理，换人不重置：接替者不能靠重新进入跳过后坐。
        player.displayClientMessage(Component.translatable("msg.barotrauma.turret.entered"), true);
        broadcastState();
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
        aimTargetSet = false;
        // 冷却跨占用者保留：退出再进入不能立刻连发。
        if (old != null) {
            ACTIVE.remove(old, getBlockPos());
            if (notify && level instanceof ServerLevel server) {
                ServerPlayer player = server.getServer().getPlayerList().getPlayer(old);
                if (player != null) {
                    player.displayClientMessage(Component.translatable("msg.barotrauma.turret.exited"), true);
                }
            }
        }
        broadcastState();
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
                    || player.isSpectator()
                    || player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                    > MAX_OPERATOR_DISTANCE * MAX_OPERATOR_DISTANCE;
            if (invalid) {
                turret.clearOperator(true);
            } else {
                if (turret.aimTargetSet) {
                    turret.stepAim();
                }
                if (turret.cooldown > 0 && level.getGameTime() % 2L == 0L) {
                    // 开火后把冷却同步给周围客户端，炮管后坐/准星状态才不会冻住。
                    turret.broadcastState();
                }
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
        broadcastState();
    }

    public void fire(ServerPlayer player) {
        if (!isOperator(player) || !withinReach(player) || cooldown > 0) {
            return;
        }
        cooldown = FIRE_COOLDOWN_TICKS;

        Vec3 origin = muzzlePosition();
        Vec3 direction = lookVector();
        Vec3 end = origin.add(direction.scale(RANGE));

        BlockHitResult blockHit = level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        LivingEntity target = findTarget(player, origin, end);
        if (target != null) {
            target.hurt(level.damageSources().playerAttack(player), 12.0F);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.35F, 1.6F);
        }

        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.6F, 0.7F);
        broadcastState();
    }

    @Nullable
    private LivingEntity findTarget(ServerPlayer shooter, Vec3 origin, Vec3 end) {
        AABB search = new AABB(origin, end).inflate(1.0D);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> entity != shooter && entity.isAlive() && !entity.isSpectator())) {
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
        return player.distanceToSqr(getBlockPos().getX() + 0.5D, getBlockPos().getY() + 0.5D,
                getBlockPos().getZ() + 0.5D) <= MAX_OPERATOR_DISTANCE * MAX_OPERATOR_DISTANCE;
    }

    // ---- 同步 ----

    public TurretStatePacket statePacket() {
        return new TurretStatePacket(getBlockPos(), yaw, pitch, cooldown, operatorEntityId);
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
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(centerX, centerY, centerZ) <= radius) {
                ModNetwork.sendToPlayer(player, packet);
            }
        }
    }

    /** 客户端收包后写入纯表现字段（不参与服务端占用逻辑）。 */
    public void applyClientState(float newYaw, float newPitch, int newCooldown, int newOperatorId) {
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
        this.yaw = newYaw;
        this.pitch = newPitch;
        this.cooldown = newCooldown;
        this.operatorEntityId = newOperatorId;
    }

    // ---- 保存 ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("TurretYaw", yaw);
        tag.putFloat("TurretPitch", pitch);
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
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
    }

    @Override
    public void setRemoved() {
        if (operator != null) {
            ACTIVE.remove(operator, getBlockPos());
            if (level instanceof ServerLevel server) {
                ServerPlayer player = server.getServer().getPlayerList().getPlayer(operator);
                if (player != null) {
                    player.displayClientMessage(Component.translatable("msg.barotrauma.turret.exited"), true);
                }
            }
            operator = null;
            operatorEntityId = -1;
        }
        super.setRemoved();
    }
}
