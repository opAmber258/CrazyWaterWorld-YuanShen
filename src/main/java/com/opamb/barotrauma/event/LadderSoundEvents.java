package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.HatchBlock;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 爬铁梯音效。
 *
 * <p>原版《潜渊症》没有单独的"爬梯"音效文件：角色手脚碰到梯级时走的是
 * {@code footstep_metal} 那一组（见 {@code Content/Sounds/sounds.xml}）。
 * 这里把原版本机的 {@code FootstepMetal1..6.ogg} 放进模组，攀爬时按固定间隔
 * 随机播放一段，音量压低、音高轻微随机，避免连续爬升时像机关枪。</p>
 *
 * <p>只在客户端播放：玩家自己的位置变化只有本地最可靠，单人存档也不会
 * 出现服务端回声式重复。铁梯格和打开的水平舱口格都算梯子；只有两次播放间隔内
 * 脚部高度真的变了才响，因此挂在梯子上不动不会一直响。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LadderSoundEvents {

    /** 两次攀爬音之间的 tick 间隔（约 0.35 秒）。 */
    private static final int INTERVAL_TICKS = 7;

    /** 每个玩家上次播放时的脚部 Y；用来判断"真的在动"，挂在梯子上不动不出声。 */
    private static final Map<UUID, Double> LAST_Y = new HashMap<>();

    private LadderSoundEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (!player.level().isClientSide
                || player.tickCount % INTERVAL_TICKS != 0
                || !player.onClimbable()) {
            return;
        }
        if (!isClimbableDockBlock(player.getFeetBlockState())) {
            return;
        }
        // 用两次间隔之间的实际脚部高度差判断是否在移动。
        // 不能只看 deltaMovement.y：挂在梯子上不动时它也可能被重力/攀爬逻辑留着非零值。
        double lastY = LAST_Y.getOrDefault(player.getUUID(), player.getY());
        LAST_Y.put(player.getUUID(), player.getY());
        if (Math.abs(player.getY() - lastY) < 0.05D) {
            return;
        }
        float pitch = 0.88F + player.getRandom().nextFloat() * 0.2F;
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                ModSounds.LADDER_CLIMB.get(), SoundSource.PLAYERS, 0.5F, pitch, false);
    }

    /** 铁梯或打开的水平舱口（对接井里的舱口格）。 */
    private static boolean isClimbableDockBlock(BlockState state) {
        if (state.is(ModBlocks.IRON_LADDER.get())) {
            return true;
        }
        return state.getBlock() instanceof HatchBlock
                && state.hasProperty(HatchBlock.OPEN)
                && state.getValue(HatchBlock.OPEN);
    }
}
