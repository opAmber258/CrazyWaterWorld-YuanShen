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

    public int mapX() {
        return switch (id) {
            case 0 -> 105;
            case 1 -> 354;
            case 2 -> 645;
            case 3 -> 622;
            case 4 -> 814;
            case 5 -> 196;
            case 6 -> 417;
            case 7 -> 638;
            case 8 -> 619;
            case 9 -> 831;
            case 10 -> 839;
            case 11 -> 1106;
            default -> 1086;
        };
    }

    public int mapY() {
        return switch (id) {
            case 0 -> 524;
            case 1 -> 478;
            case 2 -> 317;
            case 3 -> 686;
            case 4 -> 494;
            case 5 -> 255;
            case 6 -> 667;
            case 7 -> 69;
            case 8 -> 585;
            case 9 -> 199;
            case 10 -> 674;
            case 11 -> 184;
            default -> 620;
        };
    }

    public boolean connectsTo(int other) {
        return neighbors.contains(other);
    }
}
