package com.opamb.barotrauma.item;

import com.opamb.barotrauma.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 吉他：长按右键持续演奏，松手停止。
 *
 * <p>按下时先扫一个和弦（{@code guitar}），之后每 {@link #INTERVAL} tick 弹一个单音
 * （{@code guitar_note}，靠 pitch 变调），按 {@link #TUNE}（相对 C4 的半音）循环。
 * 曲目只是占位符，之后换真正的曲子只改这个数组/音源即可。</p>
 *
 * <p>不用物品冷却：冷却会在手持渲染里画白色进度框，切人称时会和实际冷却脱节。</p>
 */
public class GuitarItem extends Item {

    /** 每个音的间隔（tick），8 tick = 0.4 秒。 */
    private static final int INTERVAL = 8;

    /** 占位曲目：C - Am - F - G 的琶音，单位是相对 C4 的半音。 */
    private static final int[] TUNE = {
            0, 4, 7, 12,
            -3, 0, 4, 9,
            -7, -3, 0, 5,
            -5, -1, 2, 7
    };

    public GuitarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.GUITAR.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(user instanceof Player player)) {
            return;
        }
        int used = getUseDuration(stack) - remainingUseDuration;
        if (used <= 0 || used % INTERVAL != 0) {
            return;
        }
        int index = (used / INTERVAL - 1) % TUNE.length;
        float pitch = (float) Math.pow(2.0D, TUNE[index] / 12.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.GUITAR_NOTE.get(), SoundSource.PLAYERS, 0.8F, pitch);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // 不要原版拉弓/吃东西的手部动作，长按只负责持续发声
        return UseAnim.NONE;
    }
}
