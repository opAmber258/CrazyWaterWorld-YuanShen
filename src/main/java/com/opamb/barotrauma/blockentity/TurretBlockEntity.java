package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.TurretBlock;
import com.opamb.barotrauma.block.CoilgunBlock;
import com.opamb.barotrauma.block.CoilgunHitbox;
import com.opamb.barotrauma.client.FlashlightRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import com.opamb.barotrauma.entity.CoilgunInteractionEntity;
import com.opamb.barotrauma.network.CoilgunShotPacket;
import com.opamb.barotrauma.entity.CoilgunBolt;
import com.opamb.barotrauma.registry.ModEntities;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import com.opamb.barotrauma.block.TurretSpec;
import com.opamb.barotrauma.block.TurretWeaponKind;
import com.opamb.barotrauma.medical.bridge.MedicalEvents;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.TurretHitPacket;
import com.opamb.barotrauma.network.TurretStatePacket;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.CoilgunPower;
import com.opamb.barotrauma.ship.PowerStorage;
import com.opamb.barotrauma.ship.vs.VsState;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 * <p>电磁枪的镜头沿炮膛取景，弹道从 {@link #muzzlePosition()} 发出。原型炮塔用 HUD
 * 落点括号显示偏离镜头的弹道。</p>
 */
public class TurretBlockEntity extends BlockEntity implements GeoBlockEntity {

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

    /** 电磁炮弹箱（空 = 未装填）；原型炮塔不使用。 */
    private ItemStack ammo = ItemStack.EMPTY;
    private BlockPos loaderOffset;
    private static final int MAX_POWER_LINKS = 5;
    private final Set<BlockPos> powerOffsets = new LinkedHashSet<>();
    private float chargeFraction;
    private float minimumChargeFraction = 1;
    private boolean powered;
    private int loaderShots;
    private boolean loaderBroken;
    private boolean boxPresent;
    private CoilgunInteractionEntity interactionEntity;
    private long recoilStart = -100;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation FIRE_ANIMATION = RawAnimation.begin().thenPlay("animation.coilgun.fire_single");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "coilgun", 0, state -> PlayState.STOP)
                .triggerableAnim("shot", FIRE_ANIMATION));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public void animateCoilgunShot() {
        if (level != null) recoilStart = level.getGameTime();
        // Restart even if the previous shot is still in its return stroke.
        stopTriggeredAnimation("coilgun", "shot");
        triggerAnim("coilgun", "shot");
    }

    public double recoilPixels(float partialTick) {
        return level == null ? 0 : CoilgunHitbox.recoilPixels(level.getGameTime() - recoilStart + partialTick);
    }

    private void updateInteractionEntity(ServerLevel server) {
        if (interactionEntity == null || interactionEntity.isRemoved()) {
            Vec3 position = toWorld(worldPosition, CoilgunHitbox.anchor(worldPosition, mount(getBlockState())));
            if (!server.hasChunkAt(BlockPos.containing(position))) return;
            CoilgunInteractionEntity entity = ModEntities.COILGUN_INTERACTION.get().create(server);
            if (entity == null) return;
            entity.follow(this);
            if (server.addFreshEntity(entity)) interactionEntity = entity;
        } else {
            interactionEntity.follow(this);
        }
    }

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

    public static float initialYaw(BlockState state) {
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

    public TurretWeaponKind weaponKind() {
        return TurretBlock.weaponKind(getBlockState());
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
        return muzzlePosition(getBlockState(), getBlockPos(), yaw, pitch);
    }

    /** 轴心偏移由方块 spec 决定；所有挂载共用同一个轴心，操作/弹道一致。 */
    public static Vec3 pivotOffset(BlockState state) {
        return spec(state).pivotOffset();
    }

    public static Vec3 pivotPosition(BlockState state, BlockPos pos) {
        return spec(state).pivot(pos);
    }

    /** 供客户端按本地瞄准角算炮口的同一套公式（服务端/客户端弹道必须同源）。 */
    public static Vec3 muzzlePosition(BlockState state, BlockPos pos, float aimYaw, float aimPitch) {
        if (state.getBlock() instanceof CoilgunBlock) return CoilgunBlock.muzzle(state, pos, aimYaw, aimPitch);
        return spec(state).muzzle(pos, localLookVector(aimYaw, aimPitch));
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
        return state.getBlock() instanceof CoilgunBlock ? CoilgunBlock.masterPos(state, pos) : pos;
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
        if (weaponKind().usesAmmo()) updateSupplies(server);
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
        if (state.getBlock() instanceof CoilgunBlock) {
            turret.updateInteractionEntity(server);
            if (turret.operator != null) turret.updateSupplies(server);
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
                        invalid = !turret.periscopeValid(server);
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
        TurretWeaponKind kind = weaponKind();
        if (!isOperator(player) || !withinReach(player) || cooldown > 0) {
            return;
        }
        Vec3 muzzle = muzzlePosition();
        Vec3 origin = toWorld(getBlockPos(), muzzle);
        Vec3 direction = toWorld(getBlockPos(), muzzle.add(lookVector())).subtract(origin).normalize();
        if (kind.usesAmmo()) {
            if (level instanceof ServerLevel server) fireCoilgun(server, player, origin, direction);
            return;
        }
        cooldown = autoFire ? AUTO_FIRE_COOLDOWN_TICKS : kind.cooldownTicks();

        Vec3 end = origin.add(direction.scale(RANGE));

        BlockHitResult blockHit = level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        // 直连炮塔时操作者就站在炮座旁，炮口可能插进自己身体：排除自己。
        // 站台接管时身体在别处，属于合法目标（也方便单机验收“炮塔打玩家”）。
        LivingEntity target = findTarget(operatorAnchor == null && controllerPos == null ? player : null, origin, end);
        if (target != null) {
            float damage = kind.entityDamage();
            boolean damaged = target.hurt(level.damageSources().playerAttack(player), damage);
            if (!damaged && target instanceof Player targetPlayer
                    && !targetPlayer.isDeadOrDying()
                    && !targetPlayer.isSpectator()
                    && targetPlayer.hurtTime <= 0
                    && targetPlayer.isCreative()) {
                // 原版 hurt 在创造模式会直接拒绝玩家伤害；炮塔是测试武器，这里绕过拦截，
                // 直接按医疗系统的近战通用规则注入病症。
                damaged = MedicalEvents.applyDirectDamage(targetPlayer, damage);
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

    private void fireCoilgun(ServerLevel server, ServerPlayer operator, Vec3 origin, Vec3 direction) {
        CoilgunLoaderBlockEntity loader = supply(loaderPos(), CoilgunLoaderBlockEntity.class);
        if (loader == null || loader.condition() <= 0 || loader.shots() <= 0) return;
        List<PowerStorage> devices = connectedPowerSources();
        List<CoilgunPower.Source> sources = CoilgunPower.read(server, devices);
        double requiredKw = weaponKind().powerKw();
        if (!CoilgunPower.canFire(sources, requiredKw)) return;
        double[] shares = CoilgunPower.pulseShare(sources, requiredKw);
        CoilgunBolt bolt = ModEntities.COILGUN_BOLT.get().create(server);
        if (bolt == null) return;
        bolt.setOwner(operator);
        bolt.setPos(origin);
        bolt.setDeltaMovement(direction.scale(CoilgunBolt.SPEED));
        if (!server.addFreshEntity(bolt)) return;
        for (int i = 0; i < devices.size(); i++) {
            if (shares[i] > 0) devices.get(i).consumeWeaponCharge(server, shares[i]);
        }
        loader.feedRound();
        recoilStart = server.getGameTime();
        cooldown = weaponKind().cooldownTicks();
        updateSupplies(server);
        server.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.4F, 0.8F);
        broadcastShot(server, operator, origin, direction);
        broadcastState();
    }

    /* ------------------------------------------------------------ 弹药 */

    // Kept for saved boxes from the prototype; transfer the box when its loader is first connected.
    public void dropAmmo() {
        if (!ammo.isEmpty() && level != null) Block.popResource(level, getBlockPos(), ammo);
        ammo = ItemStack.EMPTY;
    }

    @Nullable
    public BlockPos loaderPos() {
        return loaderOffset == null ? null : worldPosition.offset(loaderOffset);
    }

    public List<BlockPos> powerSourcePositions() {
        return powerOffsets.stream().map(worldPosition::offset).toList();
    }

    private List<PowerStorage> connectedPowerSources() {
        return powerSourcePositions().stream().map(pos -> supply(pos, PowerStorage.class))
                .filter(device -> device != null && device.weaponOutputEnabled()).toList();
    }

    public void bindLoader(CoilgunLoaderBlockEntity loader) {
        loaderOffset = loader.getBlockPos().subtract(worldPosition);
        if (!ammo.isEmpty() && loader.ammo().isEmpty()) {
            loader.inventory().setItem(0, ammo);
            ammo = ItemStack.EMPTY;
        }
        supplyLinksChanged();
    }

    public boolean hasPowerInputFor(BlockPos source) {
        return powerOffsets.size() < MAX_POWER_LINKS || powerOffsets.contains(source.subtract(worldPosition));
    }

    public boolean bindPowerSource(BlockPos source) {
        if (!hasPowerInputFor(source)) return false;
        if (powerOffsets.add(source.subtract(worldPosition))) supplyLinksChanged();
        return true;
    }

    public boolean unbindPowerSource(BlockPos source) {
        if (!powerOffsets.remove(source.subtract(worldPosition))) return false;
        supplyLinksChanged();
        return true;
    }

    public void clearSupplies() {
        loaderOffset = null;
        powerOffsets.clear();
        supplyLinksChanged();
    }

    private void supplyLinksChanged() {
        setChanged();
        if (level instanceof ServerLevel server) {
            updateSupplies(server);
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Nullable
    private <T> T supply(@Nullable BlockPos pos, Class<T> type) {
        if (pos == null || level == null || !level.hasChunkAt(pos)) return null;
        BlockEntity device = level.getBlockEntity(pos);
        return type.isInstance(device) ? type.cast(device) : null;
    }

    private void updateSupplies(ServerLevel server) {
        CoilgunLoaderBlockEntity loader = supply(loaderPos(), CoilgunLoaderBlockEntity.class);
        loaderShots = loader == null ? 0 : loader.shots();
        boxPresent = loader != null && !loader.ammo().isEmpty();
        loaderBroken = loader != null && loader.condition() <= 0;
        List<CoilgunPower.Source> sources = CoilgunPower.read(server, connectedPowerSources());
        double charge = sources.stream().mapToDouble(CoilgunPower.Source::chargeKwm).sum();
        double capacity = sources.stream().mapToDouble(CoilgunPower.Source::capacityKwm).sum();
        double requiredKw = weaponKind().powerKw();
        chargeFraction = capacity > 0 ? (float) (charge / capacity) : 0;
        minimumChargeFraction = capacity > 0 ? (float) (CoilgunPower.shotCharge(requiredKw) / capacity) : 1;
        powered = CoilgunPower.canFire(sources, requiredKw);
    }

    /**
     * 切换单发/全自动。只有当前操作者能切；模式跟炮塔走，重新进入或重启后保留。
     * 切到全自动时把剩余冷却压到全自动间隔；切回单发时至少补足单发间隔，
     * 避免靠反复切模式绕过 10 tick 冷却。
     */
    public void toggleFireMode(ServerPlayer player) {
        if (weaponKind().usesAmmo()) return;
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
        return (controllerPos == null || level instanceof ServerLevel server && periscopeValid(server))
                && player.distanceToSqr(interactionAnchor(controllerPos)) <= MAX_OPERATOR_DISTANCE * MAX_OPERATOR_DISTANCE;
    }

    private boolean periscopeValid(ServerLevel server) {
        if (controllerPos == null) return true;
        if (!server.hasChunkAt(controllerPos)
                || !(server.getBlockEntity(controllerPos) instanceof PeriscopeBlockEntity periscope)
                || !com.opamb.barotrauma.block.PeriscopeBlock.hasStand(server, controllerPos)) return false;
        BlockPos target = periscope.turretPos();
        return target == null ? !weaponKind().usesAmmo() : worldPosition.equals(target);
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
        BlockState state = getBlockState();
        Vec3 local = state.getBlock() instanceof CoilgunBlock
                ? CoilgunBlock.sight(state, pos, yaw, pitch) : cameraPosition(state, pos);
        return toWorld(pos, local);
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
        Vec3 center = Vec3.atCenterOf(worldPosition);
        Vec3 south = toWorld(worldPosition, center.add(0, 0, 1)).subtract(toWorld(worldPosition, center));
        float shipYaw = (float) Math.toDegrees(Math.atan2(-south.x, south.z));
        return new TurretStatePacket(getBlockPos(), yaw, pitch, cooldown, operatorEntityId,
                operatorAnchor != null || controllerPos != null, controllerPos, cameraAnchor(),
                weaponKind().usesAmmo(), loaderShots, powered, autoFire, chargeFraction, minimumChargeFraction, loaderBroken, boxPresent, shipYaw);
    }

    public void broadcastState() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        TurretStatePacket packet = statePacket();
        Vec3 center = cameraAnchor();
        double radius = 128.0D * 128.0D;
        ServerPlayer operatorPlayer = operator == null ? null
                : server.getServer().getPlayerList().getPlayer(operator);
        for (ServerPlayer player : server.players()) {
            if (player == operatorPlayer
                    || player.distanceToSqr(center) <= radius) {
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

    private void broadcastShot(ServerLevel server, ServerPlayer operator, Vec3 origin, Vec3 direction) {
        var shot = new CoilgunShotPacket(worldPosition, origin, direction);
        for (ServerPlayer player : server.players()) {
            if (player == operator || player.distanceToSqr(origin) <= 128 * 128) {
                ModNetwork.sendToPlayer(player, shot);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- 保存 ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("TurretYaw", yaw);
        tag.putFloat("TurretPitch", pitch);
        if (!ammo.isEmpty()) {
            tag.put("Ammo", ammo.save(new CompoundTag()));
        }
        tag.putBoolean("AutoFire", autoFire);
        if (loaderOffset != null) tag.putLong("LoaderOffset", loaderOffset.asLong());
        if (!powerOffsets.isEmpty()) tag.putLongArray("PowerOffsets", powerOffsets.stream().mapToLong(BlockPos::asLong).toArray());
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
        this.ammo = tag.contains("Ammo") ? ItemStack.of(tag.getCompound("Ammo")) : ItemStack.EMPTY;
        this.autoFire = !weaponKind().usesAmmo() && tag.getBoolean("AutoFire");
        loaderOffset = tag.contains("LoaderOffset") ? BlockPos.of(tag.getLong("LoaderOffset")) : null;
        powerOffsets.clear();
        for (long offset : tag.getLongArray("PowerOffsets")) {
            if (powerOffsets.size() >= MAX_POWER_LINKS) break;
            powerOffsets.add(BlockPos.of(offset));
        }
        if (powerOffsets.isEmpty() && tag.contains("CapacitorOffset")) {
            powerOffsets.add(BlockPos.of(tag.getLong("CapacitorOffset")));
        }
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
    }

    /**
     * BER 的可见包围盒。炮管本身伸出方块 1.14 格，未来 2×2/3×3 模型更大；
     * 不扩大这里会在方块刚好出画面时把整根炮管一起剔除。
     */
    @Override
    public AABB getRenderBoundingBox() {
        if (weaponKind().usesAmmo()) return new AABB(worldPosition).inflate(6, 8, 6);
        TurretSpec spec = spec(getBlockState());
        return new AABB(getBlockPos())
                .inflate(2.0D)
                .expandTowards(Math.max(0, spec.sizeX() - 1), 0.0D, Math.max(0, spec.sizeZ() - 1));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide && weaponKind().usesAmmo()) {
            FlashlightRenderer.addCoilgunLight((ClientLevel) level, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide && weaponKind().usesAmmo()) {
            FlashlightRenderer.removeCoilgunLight(this);
        }
        if (interactionEntity != null) {
            interactionEntity.discard();
            interactionEntity = null;
        }
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
