package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 状态效果注册表。
 *
 * <p>「克苏鲁的注视」系列的几个预设都是纯客户端视觉状态（镜头 FOV + 后处理），
 * 服务端只负责挂 effect；具体渲染在 {@code client/CthulhuGazeClient} 里。</p>
 */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, BarotraumaMod.MOD_ID);

    /** 鱼眼凝视：镜头拉近 + 桶形畸变 + 色散。 */
    public static final RegistryObject<MobEffect> CTHULHU_FISHEYE = register("cthulhu_fisheye", 0x6A4C93);
    /** 深渊低语：画面波动 + 色相漂移。 */
    public static final RegistryObject<MobEffect> CTHULHU_WHISPER = register("cthulhu_whisper", 0x3AAE7A);
    /** 星之彩：异常配色 + 像素化。 */
    public static final RegistryObject<MobEffect> CTHULHU_COLOUR = register("cthulhu_colour", 0xB03AA0);
    /** 时间涟漪：同心波纹。 */
    public static final RegistryObject<MobEffect> CTHULHU_RIPPLE = register("cthulhu_ripple", 0x4A7BA8);
    /** 静电噪点：扫描线 + 雪花。 */
    public static final RegistryObject<MobEffect> CTHULHU_STATIC = register("cthulhu_static", 0x8A8A8A);
    /** 梦境漂移：柔化 + 色相循环。 */
    public static final RegistryObject<MobEffect> CTHULHU_DREAM = register("cthulhu_dream", 0xA78BC4);
    /** 平面凝视：透视被压向正交并缓慢晃动（投影矩阵驱动）。 */
    public static final RegistryObject<MobEffect> CTHULHU_FLAT = register("cthulhu_flat", 0x6E8FA8);
    /** 万花筒：世界几何被折成六瓣并缩放（顶点着色器驱动）。 */
    public static final RegistryObject<MobEffect> CTHULHU_KALEIDO = register("cthulhu_kaleido", 0x7B5CD6);

    private ModEffects() {
    }

    private static RegistryObject<MobEffect> register(String name, int color) {
        return EFFECTS.register(name, () -> new MobEffect(MobEffectCategory.HARMFUL, color) {
            // 纯视觉状态：没有 gameplay 效果，也不需要 tick
        });
    }

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
