package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.menu.RepairMenu;
import com.opamb.barotrauma.ship.RepairTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 爆炸损伤可修理设备（对应原作 {@code damagedbyexplosions}）。
 *
 * <p>反应堆熔毁、手雷等爆炸会把范围内的电池/接线盒/泵/引擎/门/核心耐久打掉，
 * 之后用对应工具修。每格 20 点，避免一发爆炸直接把整船设备全打没。</p>
 *
 * <p>同时驱动修理窗口的服务端会话：原作 {@code Repairable.Update} 在服务端按
 * {@code CurrentFixer} 持续加耐久，这里对应 {@link RepairMenu#serverTick()}。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RepairDamageEvents {

    /** 每格爆炸伤害（物理船设备也复用这个值）。 */
    public static final double EXPLOSION_DAMAGE = 20.0D;

    private RepairDamageEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (event.player instanceof ServerPlayer player
                && player.containerMenu instanceof RepairMenu menu) {
            menu.serverTick();
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (level.getBlockEntity(pos) instanceof RepairTarget target) {
                target.damage(EXPLOSION_DAMAGE);
            }
        }
    }
}
