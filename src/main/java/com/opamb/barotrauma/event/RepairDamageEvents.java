package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.menu.DeviceMenu;
import com.opamb.barotrauma.ship.RepairTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
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
 * {@code CurrentFixer} 持续加耐久，这里对应 {@code RepairSession.tick}。</p>
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
                && player.containerMenu instanceof DeviceMenu menu) {
            menu.repair().tick();
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // 设备只掉耐久、不该被原版爆炸删掉：单格设备把格子移出破坏列表，
        // 多段设备把同一方块类的相邻段一起保留（否则段被删会连带主段）。
        // 多段设备的方块实体只在主段上：命中上半段/左右段时先解析回主段，
        // 按主段去重扣耐久，两段都保留（否则非主段被删、耐久却没人扣）。
        java.util.List<BlockPos> affected = event.getAffectedBlocks();
        java.util.List<BlockPos> keep = new java.util.ArrayList<>();
        java.util.Set<BlockPos> damaged = new java.util.HashSet<>();
        for (BlockPos pos : affected) {
            BlockPos master = masterOf(level, pos);
            if (!(level.getBlockEntity(master) instanceof RepairTarget target)) {
                continue;
            }
            keep.add(pos);
            if (damaged.add(master)) {
                target.damage(EXPLOSION_DAMAGE);
            }
            Block block = level.getBlockState(master).getBlock();
            if (!isMultiPart(block)) {
                continue;
            }
            for (BlockPos other : affected) {
                if (other.equals(master) || other.distManhattan(master) > 4) {
                    continue;
                }
                if (level.getBlockState(other).getBlock() == block) {
                    keep.add(other);
                }
            }
        }
        affected.removeAll(keep);
    }

    /** 多段设备解析到承载方块实体的主段；单格方块返回自身。 */
    public static BlockPos masterOf(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block == com.opamb.barotrauma.registry.ModBlocks.FABRICATOR.get()) {
            return com.opamb.barotrauma.block.FabricatorBlock.masterPos(pos, state);
        }
        if (block == com.opamb.barotrauma.registry.ModBlocks.DECONSTRUCTOR.get()) {
            return com.opamb.barotrauma.block.DeconstructorBlock.masterPos(pos, state);
        }
        if (block == com.opamb.barotrauma.registry.ModBlocks.REACTOR.get()) {
            return com.opamb.barotrauma.block.ReactorBlock.masterPos(pos, state);
        }
        if (block == com.opamb.barotrauma.registry.ModBlocks.ENGINE.get()) {
            return com.opamb.barotrauma.block.EngineBlock.masterPos(pos, state);
        }
        if (block == com.opamb.barotrauma.registry.ModBlocks.BALLAST_PUMP.get()) {
            return com.opamb.barotrauma.block.BallastPumpBlock.masterPos(pos, state);
        }
        if (block instanceof com.opamb.barotrauma.block.HatchBlock hatch) {
            return hatch.masterPos(pos, state);
        }
        if (block instanceof com.opamb.barotrauma.block.BulkheadDoorBlock door) {
            return door.masterPos(pos, state);
        }
        if (block == com.opamb.barotrauma.registry.ModShipBlocks.NAVIGATION_TERMINAL.get()) {
            return com.opamb.barotrauma.block.NavigationTerminalBlock.masterPos(pos, state);
        }
        return pos;
    }

    /** 多段设备：爆炸时保留所有段，避免段被删后主段/数据一起失效。 */
    private static boolean isMultiPart(Block block) {
        return block == com.opamb.barotrauma.registry.ModBlocks.FABRICATOR.get()
                || block == com.opamb.barotrauma.registry.ModBlocks.DECONSTRUCTOR.get()
                || block == com.opamb.barotrauma.registry.ModBlocks.REACTOR.get()
                || block == com.opamb.barotrauma.registry.ModBlocks.ENGINE.get()
                || block == com.opamb.barotrauma.registry.ModBlocks.BALLAST_PUMP.get()
                || block == com.opamb.barotrauma.registry.ModShipBlocks.HATCH.get()
                || block == com.opamb.barotrauma.registry.ModShipBlocks.HATCH_LADDER.get()
                || block == com.opamb.barotrauma.registry.ModShipBlocks.BULKHEAD_DOOR.get()
                || block == com.opamb.barotrauma.registry.ModShipBlocks.NAVIGATION_TERMINAL.get();
    }
}
