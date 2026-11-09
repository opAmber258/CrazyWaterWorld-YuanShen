package com.opamb.barotrauma.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 单发乐器：右键播放一段声音。
 *
 * <p>限流用服务端自己的 tick 记录，<b>不用</b> {@code ItemCooldowns}：物品冷却会在
 * 手持渲染里画一层白色进度框，切人称时进度框和实际冷却会脱节（2026-11-09 实机确认）。
 * 这里只是防止连点叠音，玩家看不到任何冷却 UI。</p>
 */
public class InstrumentItem extends Item {

    private static final Map<UUID, Long> LAST_PLAY_GAME_TIME = new ConcurrentHashMap<>();

    private final Supplier<SoundEvent> sound;
    private final int minIntervalTicks;
    private final float basePitch;
    private final float pitchJitter;

    public InstrumentItem(Properties properties, Supplier<SoundEvent> sound,
                          int minIntervalTicks, float basePitch, float pitchJitter) {
        super(properties);
        this.sound = sound;
        this.minIntervalTicks = minIntervalTicks;
        this.basePitch = basePitch;
        this.pitchJitter = pitchJitter;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        // 用关卡时间而不是 player.tickCount：重生后 tickCount 会重置，旧记录会一直挡住播放
        long now = level.getGameTime();
        Long last = LAST_PLAY_GAME_TIME.get(player.getUUID());
        if (last != null && now - last < minIntervalTicks) {
            return InteractionResultHolder.fail(stack);
        }
        LAST_PLAY_GAME_TIME.put(player.getUUID(), now);
        float pitch = basePitch + (player.getRandom().nextFloat() * 2.0F - 1.0F) * pitchJitter;
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                sound.get(), SoundSource.PLAYERS, 1.0F, Math.max(0.5F, pitch));
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
