package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.LightBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 客户端每盏灯的位置色缓存 + 彩色光重算触发。
 *
 * <p>Colorful Lighting 在采样发光体时会对每个位置调用
 * {@code BlockColors.getColor(state, level, pos, 0)}，这个调用发生在它的
 * 传播线程（CL-LightPropagator）里，不能从那里查区块或方块实体，所以这里
 * 只维护一份并发缓存：方块实体的数据包/BE 加载时写入，渲染和传播只读。</p>
 *
 * <p>颜色改变后要通知彩色光引擎重新传播该方块。2.7.0 的
 * {@code ColoredLightEngine.onBlockLightPropertiesChanged(BlockPos)} 是公开方法，
 * 但模组不在编译依赖里，用反射调用；没有装这个模组时静默跳过。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class LightClientColors {

    private static final int UPDATES_PER_TICK = 32;
    private static final Map<Level, TintCache> LEVELS = new ConcurrentHashMap<>();

    private static Method forEachMethod;
    private static Method changedMethod;
    private static Method engineLevelMethod;
    private static Method accessorLevelMethod;
    private static boolean reflectionUnavailable;

    private static final class TintCache {
        private final Map<Long, Integer> colors = new ConcurrentHashMap<>();
        // 修改由客户端世界线程处理，传播线程只读 colors。
        private final Set<BlockPos> dirty = new LinkedHashSet<>();
    }

    private LightClientColors() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            flush();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide) {
            LEVELS.remove(level);
        }
    }

    /** {@code BlockColors} 处理器：按坐标取 UI 设定的颜色，未同步时落到灯型默认色。 */
    public static int tint(BlockState state, BlockAndTintGetter level, BlockPos pos,
                           int tintIndex) {
        if (!(state.getBlock() instanceof LightBlock light)) {
            return -1;
        }
        int fallback = light.kind().defaultColor();
        if (level == null || pos == null) {
            return fallback;
        }
        Level world = level instanceof Level real ? real : Minecraft.getInstance().level;
        TintCache cache = world == null ? null : LEVELS.get(world);
        Integer rgb = cache == null ? null : cache.colors.get(pos.asLong());
        return rgb == null ? fallback : rgb;
    }

    public static void update(Level level, BlockPos pos, int rgb, int fallback) {
        TintCache cache = LEVELS.computeIfAbsent(level, key -> new TintCache());
        Integer old = rgb == fallback ? cache.colors.remove(pos.asLong()) : cache.colors.put(pos.asLong(), rgb);
        if ((old == null ? fallback : old) == rgb) {
            return;
        }
        cache.dirty.add(pos.immutable());
        // 方块 tint 是区块网格烘焙时写进顶点的，光改缓存不会重画灯面，必须标脏重建。
        markSectionDirty(level, pos);
    }

    public static void remove(Level level, BlockPos pos) {
        TintCache cache = LEVELS.get(level);
        if (cache != null) {
            cache.colors.remove(pos.asLong());
            cache.dirty.remove(pos);
        }
    }

    private static void markSectionDirty(Level level, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level) {
            return;
        }
        minecraft.levelRenderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    private static void flush() {
        Level level = Minecraft.getInstance().level;
        TintCache cache = level == null ? null : LEVELS.get(level);
        if (cache == null || cache.dirty.isEmpty()) {
            return;
        }
        initReflection();
        if (reflectionUnavailable) {
            cache.dirty.clear();
            return;
        }
        List<BlockPos> positions = new ArrayList<>(UPDATES_PER_TICK);
        var iterator = cache.dirty.iterator();
        while (iterator.hasNext() && positions.size() < UPDATES_PER_TICK) {
            positions.add(iterator.next());
            iterator.remove();
        }
        try {
            Consumer<Object> consumer = engine -> {
                try {
                    if (accessorLevelMethod.invoke(engineLevelMethod.invoke(engine)) != level) {
                        return;
                    }
                    for (BlockPos changed : positions) {
                        changedMethod.invoke(engine, changed);
                    }
                } catch (ReflectiveOperationException failure) {
                    disableReflection(failure);
                }
            };
            forEachMethod.invoke(null, consumer);
        } catch (ReflectiveOperationException failure) {
            disableReflection(failure);
        }
    }

    private static synchronized void initReflection() {
        if (forEachMethod != null || reflectionUnavailable) {
            return;
        }
        try {
            Class<?> engine = Class.forName("me.erykczy.colorfullighting.common.ColoredLightEngine");
            forEachMethod = engine.getMethod("forEach", Consumer.class);
            changedMethod = engine.getMethod("onBlockLightPropertiesChanged", BlockPos.class);
            engineLevelMethod = engine.getMethod("getLevel");
            accessorLevelMethod = engineLevelMethod.getReturnType().getMethod("getLevel");
        } catch (ClassNotFoundException absent) {
            reflectionUnavailable = true;
        } catch (ReflectiveOperationException | LinkageError failure) {
            disableReflection(failure);
        }
    }

    private static void disableReflection(Throwable failure) {
        reflectionUnavailable = true;
        BarotraumaMod.LOGGER.warn("[light] 无法通知 Colorful Lighting 更新灯具颜色", failure);
    }
}
