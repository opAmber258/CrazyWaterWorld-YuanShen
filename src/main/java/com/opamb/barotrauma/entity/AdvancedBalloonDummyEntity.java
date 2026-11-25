package com.opamb.barotrauma.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** 高级训练气球：200 血，且没有原版 10 tick 受击无敌帧。 */
public class AdvancedBalloonDummyEntity extends BalloonDummyEntity {

    public AdvancedBalloonDummyEntity(EntityType<? extends AdvancedBalloonDummyEntity> type,
                                      Level level) {
        super(type, level);
        setHealth(getMaxHealth());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 0.0D);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 连发/多段伤害每一段都结算。
        this.invulnerableTime = 0;
        return super.hurt(source, amount);
    }
}
