package com.opamb.barotrauma.entity;

import com.opamb.barotrauma.mission.MissionNpc;
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

    /** 可选皮肤（对应贴图文件名，不含扩展名）。 */
    public static final String[] SKINS = {"opamber", "hanxing", "tenbaku"};

    private static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(
            NpcEntity.class, EntityDataSerializers.STRING);

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setNoAi(true);
        setCustomName(Component.translatable("entity.barotrauma.npc"));
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
        entityData.define(DATA_SKIN, SKINS[0]);
    }

    public String skin() {
        return entityData.get(DATA_SKIN);
    }

    public void setSkin(String skin) {
        entityData.set(DATA_SKIN, skin == null || skin.isBlank() ? SKINS[0] : skin);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Skin", skin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkin(tag.contains("Skin") ? tag.getString("Skin") : SKINS[0]);
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
            MissionNpc.talk(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
}
