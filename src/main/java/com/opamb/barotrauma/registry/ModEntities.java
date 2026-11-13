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

    /** Forge 要求所有 Mob 在这里注册基础属性，否则生成时会报 "does not have any attributes"。 */
    public static void onAttributes(
            net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(NPC.get(),
                com.opamb.barotrauma.entity.NpcEntity.createAttributes().build());
    }

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
