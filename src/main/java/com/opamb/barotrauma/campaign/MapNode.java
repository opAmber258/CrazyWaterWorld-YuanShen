package com.opamb.barotrauma.campaign;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 战役地图上的一个节点。
 *
 * <p>{@code anchor} 是它在深海维度里的世界坐标，v1 先用它做抵达判定；
 * 后续里程碑再按这个坐标生成前哨站/航路点建筑。</p>
 */
public record MapNode(int id, NodeType type, String name, BlockPos anchor, List<Integer> neighbors) {

    public MapNode {
        anchor = anchor.immutable();
        neighbors = List.copyOf(neighbors);
    }

    public String biome() { return "coldcaverns"; }

    public String locationType() {
        return type == NodeType.OUTPOST ? (id == 4 ? "city" : "outpost") : "none";
    }

    public List<String> services() {
        // 原作服务信息，不宣称 M1 已实现医疗/雇员/潜艇商店。
        return type == NodeType.OUTPOST ? List.of("trade", "repair", "missions") : List.of();
    }

    public String faction() { return type == NodeType.OUTPOST ? "coalition" : "none"; }

    /** 官方 factions.xml 初始值；M1 暂无声望变更机制。 */
    public int reputation() { return type == NodeType.OUTPOST ? 15 : 0; }

    public int mapX() { return switch (id) { case 0 -> 100; case 1 -> 330; case 2, 3 -> 710; default -> 1040; }; }
    public int mapY() { return switch (id) { case 2 -> 200; case 3 -> 610; default -> 410; }; }

    public boolean connectsTo(int other) {
        return neighbors.contains(other);
    }
}
