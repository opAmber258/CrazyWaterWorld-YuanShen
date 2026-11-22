package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 粒子注册：破口/炮塔用水气泡，炮塔还有一条细粒子束。 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, BarotraumaMod.MOD_ID);

    /**
     * 破口气泡。行为照抄原版 BubbleParticle（上浮 + 阻尼），但尺寸放大到能看清；
     * 贴图由 {@code assets/barotrauma/particles/ship_bubble.json} 指定。
     */
    public static final RegistryObject<SimpleParticleType> SHIP_BUBBLE =
            PARTICLES.register("ship_bubble", () -> new SimpleParticleType(false));

    /** 空气舱破口喷出的细水珠（喷淋头效果）。 */
    public static final RegistryObject<SimpleParticleType> SHIP_SPRAY =
            PARTICLES.register("ship_spray", () -> new SimpleParticleType(false));

    public static final RegistryObject<SimpleParticleType> MARINE_SNOW =
            PARTICLES.register("marine_snow", () -> new SimpleParticleType(false));

    /**
     * 炮塔开火粒子束的单点。故意用自定义粒子而不是原版 END_ROD：原版粒子太小改不了、
     * 太大又糊成一条粗线，这里把 quadSize 压到 0.03 格，贴图见
     * {@code assets/barotrauma/particles/turret_tracer.json}。
     */
    public static final RegistryObject<SimpleParticleType> TURRET_TRACER =
            PARTICLES.register("turret_tracer", () -> new SimpleParticleType(false));

    /**
     * 水下弹道气泡：枪口气泡团、水中尾迹和命中冲击环共用。贴图复用
     * {@code ship_bubble}，行为由 {@code TurretBubbleParticle} 自己控制
     * （轻微上浮 + 阻尼），粒子 JSON 见
     * {@code assets/barotrauma/particles/turret_bubble.json}。
     */
    public static final RegistryObject<SimpleParticleType> TURRET_BUBBLE =
            PARTICLES.register("turret_bubble", () -> new SimpleParticleType(false));

    /** 地波扬起的海底泥沙：贴图复用 {@code ship_bubble}，粒子内做土黄色调。 */
    public static final RegistryObject<SimpleParticleType> TURRET_SEDIMENT =
            PARTICLES.register("turret_sediment", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
