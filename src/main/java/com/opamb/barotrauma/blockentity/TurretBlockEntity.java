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
 * <p>视角：{@link #eyePosition()} 同时是相机位置和射线起点，所以准星中心和
 * 弹道严格重合（炮管轴线在视点下方 0.17 格，只是视觉上略低）。</p>
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

    public static final float MIN_PITCH = -55.0F;
    public static final float MAX_PITCH = 55.0F;

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

    @Nullable
    private UUID operator;
    /** 同步给客户端的占用者实体 id（-1 = 空）。 */
    private int operatorEntityId = -1;

    public TurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURRET.get(), pos, state);
        this.yaw = initialYaw(state);
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
    }

    private static float initialYaw(BlockState state) {
        if (!state.hasProperty(TurretBlock.FACING)) {
            return 0.0F;
        }
        // 用 if 链而不是 enum switch：switch 会生成 TurretBlockEntity$1 合成类，
        // 开发环境热替换/增量编译时曾出现合成类加载不到（NoClassDefFoundError）。
        Direction facing = state.getValue(TurretBlock.FACING);
        if (facing == Direction.SOUTH) {
            return 0.0F;
        }
        if (facing == Direction.WEST) {
            return 90.0F;
        }
        if (facing == Direction.NORTH) {
            return 180.0F;
        }
        if (facing == Direction.EAST) {
            return -90.0F;
        }
        return 0.0F;
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

    /** 视点（相机位置）：方块中心上方 0.42 格。弹道起点是 {@link #muzzlePosition()}。 */
    public Vec3 eyePosition() {
        BlockPos pos = getBlockPos();
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D + EYE_HEIGHT, pos.getZ() + 0.5D);
    }

    /** 当前瞄准单位向量（MC 约定：yaw 0 = 南，pitch 正值看下）。 */
    public Vec3 lookVector() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new Vec3(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad));
    }

    /** 炮口世界坐标：方块中心 + 枢轴高度，再沿瞄准方向伸出炮管长度。 */
    public Vec3 muzzlePosition() {
        return muzzlePosition(getBlockPos(), lookVector());
    }

    /** 供客户端按本地瞄准角算炮口的同一套公式（服务端/客户端弹道必须同源）。 */
    public static Vec3 muzzlePosition(BlockPos pos, Vec3 direction) {
        Vec3 pivot = new Vec3(pos.getX() + 0.5D, pos.getY() + PIVOT_Y, pos.getZ() + 0.5D);
        return pivot.add(direction.scale(MUZZLE_LENGTH));
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
                && server.getBlockEntity(previous) instanceof TurretBlockEntity old) {
            old.clearOperator(false);
        }

        operator = player.getUUID();
        operatorEntityId = player.getId();
        cooldown = 0;
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
        cooldown = 0;
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
            } else if (turret.cooldown > 0 && level.getGameTime() % 2L == 0L) {
                // 开火后把冷却同步给周围客户端，炮管后坐/准星状态才不会冻住。
                turret.broadcastState();
            }
        }
    }

    // ---- 瞄准 / 射击 ----

    /** 客户端每次鼠标移动后发绝对角度；这里按最大转速截断，防止瞬移瞄准。 */
    public void aim(ServerPlayer player, float targetYaw, float targetPitch) {
        if (!isOperator(player) || !withinReach(player)) {
            return;
        }
        float deltaYaw = Mth.clamp(Mth.wrapDegrees(targetYaw - yaw),
                -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
        float deltaPitch = Mth.clamp(Mth.clamp(targetPitch, MIN_PITCH, MAX_PITCH) - pitch,
                -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
        applyAim(Mth.wrapDegrees(yaw + deltaYaw), Mth.clamp(pitch + deltaPitch, MIN_PITCH, MAX_PITCH));
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
