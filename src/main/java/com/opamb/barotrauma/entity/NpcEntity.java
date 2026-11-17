package com.opamb.barotrauma.entity;

import com.opamb.barotrauma.mission.MissionNpc;
import com.opamb.barotrauma.station.MedicalClinicService;
import com.opamb.barotrauma.station.StoreService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 哨站里的站务员 NPC：站在站内不动、打不死，右键打开任务列表。
 *
 * <p>外观直接用玩家人形模型 + 玩家皮肤（皮肤文件在
 * {@code assets/barotrauma/textures/entity/npc/}），皮肤名存在同步数据里。</p>
 */
public class NpcEntity extends PathfinderMob {

    /** 站务员可选皮肤（对应贴图文件名，不含扩展名），按站点坐标散列固定。 */
    public static final String[] SKINS = {"opamber", "hanxing", "tenbaku"};

    /**
     * 自带细手臂（slim）布局的皮肤。
     *
     * <p>{@code opamber.png} 的手臂区按 3px 宽绘制（顶层/底层 6 列），必须用 slim
     * 模型；{@code hanxing}/{@code tenbaku} 是标准 4px 皮肤（顶层/底层 8 列），
     * 用 classic 模型，否则手臂 UV 会错位。</p>
     */
    public static boolean isSlimSkin(String skin) {
        return "opamber".equals(skin);
    }

    /** 站务员（发任务）。 */
    public static final String ROLE_MISSION = "mission";
    /** 商人（商店）。 */
    public static final String ROLE_MERCHANT = "merchant";
    /** 医生（医疗诊所）。 */
    public static final String ROLE_DOCTOR = "doctor";

    /** 默认皮肤：用户要求"不指定皮肤就用史蒂夫"。 */
    public static final String DEFAULT_SKIN = "steve";

    private static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(
            NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ROLE = SynchedEntityData.defineId(
            NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_SLIM = SynchedEntityData.defineId(
            NpcEntity.class, EntityDataSerializers.BOOLEAN);

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setNoAi(true);
        applyRoleName(ROLE_MISSION);
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SKIN, DEFAULT_SKIN);
        entityData.define(DATA_ROLE, ROLE_MISSION);
        entityData.define(DATA_SLIM, false);
    }

    public String skin() {
        return entityData.get(DATA_SKIN);
    }

    public void setSkin(String skin) {
        entityData.set(DATA_SKIN, skin == null || skin.isBlank() ? DEFAULT_SKIN : skin);
    }

    /** 细手臂模型（Alex/slim）；站务员使用。 */
    public boolean slim() {
        return entityData.get(DATA_SLIM);
    }

    public void setSlim(boolean slim) {
        entityData.set(DATA_SLIM, slim);
    }

    public String role() {
        return entityData.get(DATA_ROLE);
    }

    public void setRole(String role) {
        String normalized = normalizeRole(role);
        entityData.set(DATA_ROLE, normalized);
        applyRoleName(normalized);
    }

    private static String normalizeRole(String role) {
        if (ROLE_MERCHANT.equals(role)) {
            return ROLE_MERCHANT;
        }
        if (ROLE_DOCTOR.equals(role)) {
            return ROLE_DOCTOR;
        }
        return ROLE_MISSION;
    }

    private void applyRoleName(String role) {
        Component name = switch (normalizeRole(role)) {
            case ROLE_MERCHANT -> Component.translatable("entity.barotrauma.npc.merchant")
                    .withStyle(ChatFormatting.GOLD);
            case ROLE_DOCTOR -> Component.translatable("entity.barotrauma.npc.doctor")
                    .withStyle(ChatFormatting.RED);
            default -> Component.translatable("entity.barotrauma.npc")
                    .withStyle(ChatFormatting.WHITE);
        };
        setCustomName(name);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Skin", skin());
        tag.putString("Role", role());
        tag.putBoolean("Slim", slim());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkin(tag.contains("Skin") ? tag.getString("Skin") : DEFAULT_SKIN);
        setSlim(tag.contains("Slim") && tag.getBoolean("Slim"));
        setRole(tag.contains("Role") ? tag.getString("Role") : ROLE_MISSION);
    }

    // ------------------------------------------------------------------ 站桩

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // 永不自然消失。
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            switch (role()) {
                case ROLE_MERCHANT -> StoreService.open(serverPlayer, this);
                case ROLE_DOCTOR -> MedicalClinicService.open(serverPlayer, this);
                default -> MissionNpc.talk(serverPlayer, this);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
}
