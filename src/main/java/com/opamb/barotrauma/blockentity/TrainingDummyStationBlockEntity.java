package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.TrainingDummyStationBlock;
import com.opamb.barotrauma.entity.TrainingDummyEntity;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 训练人偶充气台方块实体：负责人偶的生成、绑定、死亡检测与 2 秒重生。
 *
 * <p>只保存人偶 UUID 和重生时间；人偶自己保存台座坐标，台座被拆时人偶会自毁。</p>
 */
public class TrainingDummyStationBlockEntity extends BlockEntity {

    /** 人偶死亡后 40 tick（2 秒）重新召唤。 */
    public static final int RESPAWN_TICKS = 40;

    @Nullable
    private UUID dummyId;
    /** 0 = 没有待重生倒计时；否则是游戏刻。 */
    private long respawnAt;

    /** 高级版面板可调：最大血量 1..200（普通版固定 1）。 */
    protected int configuredHealth = 1;
    /** 死亡后重新召唤的间隔（tick），面板里按秒显示。 */
    protected int respawnTicks = RESPAWN_TICKS;
    /** 面板里的“自发光”：实体满亮度渲染。 */
    protected boolean selfIlluminated;
    /** 实体重载后第一次 tick 需要把设置重新套上去。 */
    private boolean settingsApplied;

    public TrainingDummyStationBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.TRAINING_DUMMY_STATION.get(), pos, state);
    }

    protected TrainingDummyStationBlockEntity(
            net.minecraft.world.level.block.entity.BlockEntityType<?> type,
            BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TrainingDummyStationBlockEntity station) {
        if (!(level instanceof ServerLevel server) || station.isRemoved()) {
            return;
        }

        if (station.dummyId != null) {
            Entity entity = server.getEntity(station.dummyId);
            if (entity instanceof TrainingDummyEntity dummy && dummy.isAlive()) {
                // 区块重载/换版本后把面板设置重新套到现有实体上：只同步上限和自发光，
                // 当前血量按存档保留（旧的实现会顺手回满，等于开界面就送一次治疗）。
                if (!station.settingsApplied) {
                    station.applySettings(dummy, false);
                    station.settingsApplied = true;
                }
                return;
            }
            // 人偶已死亡/被移除：清掉 id，并按面板配置的重生间隔计时。
            station.dummyId = null;
            station.settingsApplied = false;
            if (station.respawnAt == 0L) {
                station.respawnAt = level.getGameTime() + Math.max(0, station.respawnTicks);
            }
            station.setChanged();
            return;
        }

        if (station.respawnAt == 0L) {
            if (!station.spawnDummy(server)) {
                station.respawnAt = level.getGameTime() + 20L;
            }
            return;
        }
        if (level.getGameTime() >= station.respawnAt) {
            if (station.spawnDummy(server)) {
                station.respawnAt = 0L;
            } else {
                station.respawnAt = level.getGameTime() + 20L;
            }
        }
    }

    /** 子类（高级版）覆盖此方法以生成不同的人偶实体。 */
    @Nullable
    protected TrainingDummyEntity createDummy(ServerLevel server) {
        return ModEntities.TRAINING_DUMMY.get().create(server);
    }

    private boolean spawnDummy(ServerLevel server) {
        // 区块加载顺序可能让 BE 先 tick、实体后加入；先认领附近已绑定的旧人偶，避免重载后刷出第二只。
        for (TrainingDummyEntity existing : server.getEntitiesOfClass(TrainingDummyEntity.class,
                new net.minecraft.world.phys.AABB(worldPosition).inflate(2.0D))) {
            if (existing.isAlive() && worldPosition.equals(existing.stationPos())) {
                dummyId = existing.getUUID();
                settingsApplied = false;
                setChanged();
                return true;
            }
        }

        TrainingDummyEntity dummy = createDummy(server);
        if (dummy == null) {
            return false;
        }
        configureDummy(dummy, getBlockState());
        // 新生成的人偶永远按面板血量满血出场。
        applySettings(dummy, true);
        if (!server.addFreshEntity(dummy)) {
            dummy.discard();
            return false;
        }
        dummyId = dummy.getUUID();
        settingsApplied = true;
        setChanged();
        return true;
    }

    /**
     * 把面板设置套到实体：最大血量、自发光，以及当前血量。
     *
     * <p>{@code refill}：把当前血量直接设成新上限（新生成的人偶、或玩家在面板里
     * 改了血量）；只在同步自发光/重生间隔时传 false，当前血量保持不动，只在上限
     * 变小时截断。这样改个重生间隔不会顺手把打了一半的人偶奶满。</p>
     */
    private void applySettings(TrainingDummyEntity dummy, boolean refill) {
        if (dummy.getAttribute(Attributes.MAX_HEALTH) != null) {
            dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(configuredHealth);
        }
        dummy.setSelfIlluminated(selfIlluminated);
        if (refill) {
            dummy.setHealth(configuredHealth);
        } else if (dummy.getHealth() > configuredHealth) {
            dummy.setHealth(configuredHealth);
        }
    }

    public int configuredHealth() {
        return configuredHealth;
    }

    public int respawnTicks() {
        return respawnTicks;
    }

    public boolean selfIlluminated() {
        return selfIlluminated;
    }

    /** 面板“应用”：血量 1..200、重生间隔 0..200 tick、自发光。 */
    public void setSettings(int health, int respawn, boolean illuminated) {
        int previousHealth = configuredHealth;
        // 倒计时已经开始时，按“实际死亡时刻 + 新间隔”重排，而不是继续等旧值。
        long deathAt = respawnAt != 0L ? respawnAt - respawnTicks : 0L;
        configuredHealth = Mth.clamp(health, 1, 200);
        respawnTicks = Mth.clamp(respawn, 0, 200);
        if (respawnAt != 0L) {
            respawnAt = deathAt + respawnTicks;
        }
        selfIlluminated = illuminated;
        setChanged();
        if (level instanceof ServerLevel server && dummyId != null
                && server.getEntity(dummyId) instanceof TrainingDummyEntity dummy
                && dummy.isAlive()) {
            // 只有玩家真的改了血量才把当前人偶重置到新上限；只改重生间隔/自发光时
            // 不动当前血量（否则开着面板调个间隔就能把靶子奶满）。
            // 死亡中的实体不能套：给尸体 setHealth 会把 health 从 0 拉回正数，
            // tickDeath 停止但 dead 标记还在，既不播死亡动画也不安排重生（假复活）。
            applySettings(dummy, configuredHealth != previousHealth);
            settingsApplied = true;
        }
    }

    /** 放置/朝向设置；气球台子类覆盖成沿 FACING 方向摆放。 */
    protected void configureDummy(TrainingDummyEntity dummy, BlockState state) {
        Direction facing = state.hasProperty(TrainingDummyStationBlock.FACING)
                ? state.getValue(TrainingDummyStationBlock.FACING) : Direction.SOUTH;
        float yaw = facing.toYRot();
        dummy.moveTo(worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D,
                worldPosition.getZ() + 0.5D, yaw, 0.0F);
        // MobRenderer 用的是 yBodyRot，不只 yRot；生成时一起对齐，避免人偶永远朝南。
        dummy.setYHeadRot(yaw);
        dummy.yBodyRot = yaw;
        dummy.yBodyRotO = yaw;
        dummy.yHeadRotO = yaw;
        dummy.yRotO = yaw;
        dummy.setStationPos(worldPosition);
    }

    /** 台座被拆时清掉人偶；方块实体卸载时不要调用，否则跨区块重载会丢人偶。 */
    public void discardDummy() {
        if (dummyId != null && level instanceof ServerLevel server) {
            Entity entity = server.getEntity(dummyId);
            if (entity != null) {
                entity.discard();
            }
            dummyId = null;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (dummyId != null) {
            tag.putUUID("Dummy", dummyId);
        }
        tag.putLong("RespawnAt", respawnAt);
        tag.putInt("MaxHealth", configuredHealth);
        tag.putInt("RespawnTicks", respawnTicks);
        tag.putBoolean("SelfIlluminated", selfIlluminated);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        dummyId = tag.contains("Dummy") ? tag.getUUID("Dummy") : null;
        respawnAt = tag.getLong("RespawnAt");
        // 没有这些键的旧存档沿用子类默认值（普通 1 血 / 高级 50 血）。
        if (tag.contains("MaxHealth")) {
            configuredHealth = Mth.clamp(tag.getInt("MaxHealth"), 1, 200);
        }
        if (tag.contains("RespawnTicks")) {
            respawnTicks = Mth.clamp(tag.getInt("RespawnTicks"), 0, 200);
        }
        selfIlluminated = tag.getBoolean("SelfIlluminated");
        settingsApplied = false;
    }
}
