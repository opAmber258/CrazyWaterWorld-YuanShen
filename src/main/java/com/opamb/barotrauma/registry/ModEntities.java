package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.entity.VesselEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 实体注册。
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BarotraumaMod.MOD_ID);

    public static final RegistryObject<EntityType<VesselEntity>> VESSEL = ENTITIES.register("vessel",
            () -> EntityType.Builder.<VesselEntity>of(VesselEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(BarotraumaMod.MOD_ID, "vessel").toString()));

    /** 哨站站务员：玩家人形、站桩、右键发任务。 */
    public static final RegistryObject<EntityType<com.opamb.barotrauma.entity.NpcEntity>> NPC =
            ENTITIES.register("npc", () -> EntityType.Builder
                    .<com.opamb.barotrauma.entity.NpcEntity>of(
                            com.opamb.barotrauma.entity.NpcEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(BarotraumaMod.MOD_ID, "npc").toString()));

    /** 训练人偶：充气台生成、1 血、固定不动。 */
    public static final RegistryObject<EntityType<com.opamb.barotrauma.entity.TrainingDummyEntity>> TRAINING_DUMMY =
            ENTITIES.register("training_dummy", () -> EntityType.Builder
                    .<com.opamb.barotrauma.entity.TrainingDummyEntity>of(
                            com.opamb.barotrauma.entity.TrainingDummyEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.5F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(BarotraumaMod.MOD_ID, "training_dummy").toString()));

    /** 高级训练人偶：充气台生成、200 血、无受击无敌帧、固定不动。 */
    public static final RegistryObject<EntityType<com.opamb.barotrauma.entity.AdvancedTrainingDummyEntity>> ADVANCED_TRAINING_DUMMY =
            ENTITIES.register("advanced_training_dummy", () -> EntityType.Builder
                    .<com.opamb.barotrauma.entity.AdvancedTrainingDummyEntity>of(
                            com.opamb.barotrauma.entity.AdvancedTrainingDummyEntity::new, MobCategory.MISC)
                    .sized(0.9F, 1.5F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(BarotraumaMod.MOD_ID, "advanced_training_dummy").toString()));

    /** 训练气球：六向气球台生成、1 血、沿台座 FACING 吊挂。 */
    public static final RegistryObject<EntityType<com.opamb.barotrauma.entity.BalloonDummyEntity>> BALLOON_DUMMY =
            ENTITIES.register("balloon_dummy", () -> EntityType.Builder
                    .<com.opamb.barotrauma.entity.BalloonDummyEntity>of(
                            com.opamb.barotrauma.entity.BalloonDummyEntity::new, MobCategory.MISC)
                    .sized(0.625F, 0.625F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(BarotraumaMod.MOD_ID, "balloon_dummy").toString()));

    /** 高级训练气球：200 血、无受击无敌帧。 */
    public static final RegistryObject<EntityType<com.opamb.barotrauma.entity.AdvancedBalloonDummyEntity>> ADVANCED_BALLOON_DUMMY =
            ENTITIES.register("advanced_balloon_dummy", () -> EntityType.Builder
                    .<com.opamb.barotrauma.entity.AdvancedBalloonDummyEntity>of(
                            com.opamb.barotrauma.entity.AdvancedBalloonDummyEntity::new, MobCategory.MISC)
                    .sized(0.625F, 0.625F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(BarotraumaMod.MOD_ID, "advanced_balloon_dummy").toString()));

    /** Forge 要求所有 Mob 在这里注册基础属性，否则生成时会报 "does not have any attributes"。 */
    public static void onAttributes(
            net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(NPC.get(),
                com.opamb.barotrauma.entity.NpcEntity.createAttributes().build());
        event.put(TRAINING_DUMMY.get(),
                com.opamb.barotrauma.entity.TrainingDummyEntity.createAttributes().build());
        event.put(ADVANCED_TRAINING_DUMMY.get(),
                com.opamb.barotrauma.entity.AdvancedTrainingDummyEntity.createAttributes().build());
        event.put(BALLOON_DUMMY.get(),
                com.opamb.barotrauma.entity.BalloonDummyEntity.createAttributes().build());
        event.put(ADVANCED_BALLOON_DUMMY.get(),
                com.opamb.barotrauma.entity.AdvancedBalloonDummyEntity.createAttributes().build());
    }

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
