package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 爆炸对船壳的伤害。
 *
 * <p>原版爆炸会直接把方块炸没；这里把船体格子从破坏列表里拿掉，改成先扣耐久，
 * 只有耐久到 0 才真的破口。物理船的方块在世界里是空气，原版列表根本碰不到它们，
 * 所以走 {@link VsState#damageExplosion} 按装配快照单独结算。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HullDamageEvents {

    /** TNT（radius=4）中心约 140 结构伤害：两发打穿 200 血外壳。 */
    private static final float BASE_DAMAGE = 60.0F;
    private static final float DAMAGE_PER_RADIUS = 20.0F;
    private static final float MAX_DAMAGE = 240.0F;

    private HullDamageEvents() {
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = event.getExplosion().getPosition();
        // 1.20.1 的 Explosion.radius 是 private，也没有 getter；用爆炸实际影响范围
        // 反推一个有效半径（TNT ≈ 4~4.5 格）。开阔水域没有方块可炸时退回 4 格。
        double radius = 0.0D;
        for (BlockPos pos : event.getAffectedBlocks()) {
            radius = Math.max(radius,
                    Math.sqrt(Vec3.atCenterOf(pos).distanceToSqr(center)) + 0.5D);
        }
        if (radius < 2.0D) {
            radius = 4.0D;
        }
        float base = (float) Math.min(MAX_DAMAGE, BASE_DAMAGE + DAMAGE_PER_RADIUS * radius);

        BlockPos centerPos = BlockPos.containing(center);
        // 物理船按快照独立结算：damageExplosion 自己会用完整 shipToWorld 变换
        // 判断命中，没有船/没命中时返回 0。不要在外面用只含平移的包围盒粗筛——
        // 船偏航 90° 后，船首附近的爆炸会被旧包围盒排除、整次伤害跳过。
        if (VsState.active()) {
            VsState.damageExplosion(level, center, radius + 0.75D, base);
        }
        // 方块船分支照常跑：世界上有物理船时，旁边方块船的熔毁仍要走船壳耐久，
        // 不能因为全局 active() 提前返回、被原版爆炸直接删掉。
        //
        // 一次爆炸可能同时扫到多艘方块船（A 船反应堆熔毁，冲击波打到旁边的 B 船）。
        // 必须按“这一格归哪艘船”逐格匹配，不能只取离爆心最近的核心：更近的 C 船
        // 会把 B 船的船壳从归属判定里挤掉，B 的船壳就被原版爆炸直接删了（0% 应该
        // 变成 BREACHED，不是删除方块）。
        // 船体扫描是每个坐标轴 ±24 格的立方体：爆炸命中船角时，核心离爆心
        // 最远约 41.6 格，+32 的欧氏半径会漏掉这艘船，船壳就被原版爆炸删了。
        List<ShipCoreBlockEntity> cores = ShipCores.coresNear(level, centerPos,
                Math.max(48.0D, radius + 48.0D));
        if (cores.isEmpty()) {
            return;
        }
        java.util.Map<ShipCoreBlockEntity, List<BlockPos>> hitsByCore =
                new java.util.LinkedHashMap<>();
        Iterator<BlockPos> iterator = event.getAffectedBlocks().iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            net.minecraft.world.level.block.state.BlockState blockState = level.getBlockState(pos);
            boolean brain = blockState.is(ModShipBlocks.SONAR.get())
                    || blockState.is(ModShipBlocks.NAVIGATION_TERMINAL.get());
            if (!brain && !ModShipBlocks.isRepairableHull(blockState)) {
                continue;
            }
            ShipCoreBlockEntity owner = null;
            for (ShipCoreBlockEntity candidate : cores) {
                ShipState state = candidate.state();
                // 物理船的世界方块是空气，不会出现在爆炸列表里；跳过它的状态。
                // 注意 state 为 null 时不能连带跳过核心位置匹配：区块刚重载、
                // 还没 rescan 时声纳/导航台正好在核心位置上，也要算本船船脑。
                if (state != null && state == VsState.activeState()) {
                    continue;
                }
                // 核心自己也是“船脑”：几何收集会跳过核心自身、owns() 不包含它，
                // 所以声纳/导航台正好放在核心位置上时要单独认。
                if (candidate.getBlockPos().equals(pos)
                        || (state != null && state.tracksHullBlock(pos))) {
                    owner = candidate;
                    break;
                }
            }
            if (owner == null) {
                continue;
            }
            if (brain) {
                // 声纳是船脑，爆炸也不能炸掉。
                iterator.remove();
                continue;
            }
            double distance = Math.sqrt(Vec3.atCenterOf(pos).distanceToSqr(center));
            if (distance > radius + 0.75D) {
                continue;
            }
            // 从原版破坏列表移除：爆炸不再秒掉这格，改由耐久系统决定。
            iterator.remove();
            hitsByCore.computeIfAbsent(owner, key -> new ArrayList<>()).add(pos);
        }
        for (java.util.Map.Entry<ShipCoreBlockEntity, List<BlockPos>> entry
                : hitsByCore.entrySet()) {
            entry.getKey().applyExplosion(level, entry.getValue(), center,
                    radius + 0.75D, base);
        }
    }
}
