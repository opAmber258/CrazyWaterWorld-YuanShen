package com.opamb.barotrauma.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * 高级训练人偶：200 血，且**没有受击无敌帧**，用于测连发 DPS。
 *
 * <p>原版 {@code LivingEntity.hurt} 在 {@code invulnerableTime > 10} 时会吞掉同一窗口内的
 * 后续伤害；这里每次结算前把计时清零，让炮塔 5 发/秒的每一发都真正扣血。
 * 其余行为（固定站位、不击退、绑定台座、2 秒重生）全部继承自
 * {@link TrainingDummyEntity}。</p>
 */
public class AdvancedTrainingDummyEntity extends TrainingDummyEntity {

    public AdvancedTrainingDummyEntity(EntityType<? extends AdvancedTrainingDummyEntity> type,
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
        // 清掉原版 10 tick 受伤间隔，连发/多段伤害的每一段都结算。
        this.invulnerableTime = 0;
        return super.hurt(source, amount);
    }
}
