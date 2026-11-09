package com.opamb.barotrauma.campaign;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * v1 的固定战役地图：13 个节点、17 条连接。
 *
 * <p>比例参照原作第一个群系（寒冷洞穴）：<b>站点多、无人航路点少</b>。
 * 原作存档里该群系有 36 个位置，其中 25 个是站点、11 个是无人航路点；
 * 这里按 M1 体量做成 11 个站点 + 2 个无人航路点。</p>
 *
 * <ul>
 *   <li>0 起始哨站（起点，只通中继站）；</li>
 *   <li>1 B 中继站（枢纽，五条边）；</li>
 *   <li>2/3 冰隙航路 / 沉船航路（仅有的两个无人航路点，分叉）；</li>
 *   <li>5–12 沿两条航路分布的站点（锈锚、寒晶矿、热泉、深井、断链、潮汐、冰臼、暗流）；</li>
 *   <li>4 冰下聚居地（汇合点，v1 的终点）。</li>
 * </ul>
 *
 * <p>节点 0–4 的 id 和旧版一致，老存档的 currentNode 仍然有效。</p>
 */
public final class CampaignMap {

    public static final int START_NODE = 0;
    /** 本机原作 1.13.5.0 MapGenerationParameters.xml，完整地图不是 GUI 面板尺寸。 */
    public static final int MAP_WIDTH = 8000;
    public static final int MAP_HEIGHT = 800;
    /** M1 手工首群系裁剪范围，非宣称原作首群系固定宽度。 */
    public static final int VIEW_WIDTH = 1200;
    public static final int DEEP_OUTPOST_NODE = 4;

    private static final List<MapNode> NODES = List.of(
            new MapNode(0, NodeType.OUTPOST, "起始哨站", new BlockPos(0, -62, 0), List.of(1)),
            new MapNode(1, NodeType.OUTPOST, "B 中继站", new BlockPos(320, -301, 0), List.of(0, 2, 3, 5, 6)),
            new MapNode(2, NodeType.WAYPOINT, "冰隙航路", new BlockPos(640, -420, 240), List.of(1, 4, 7, 9)),
            new MapNode(3, NodeType.WAYPOINT, "沉船航路", new BlockPos(640, -520, -240), List.of(1, 4, 8, 10)),
            new MapNode(4, NodeType.OUTPOST, "冰下聚居地", new BlockPos(960, -640, 0), List.of(2, 3, 11, 12)),
            new MapNode(5, NodeType.OUTPOST, "锈锚站", new BlockPos(470, -360, 180), List.of(1, 7)),
            new MapNode(6, NodeType.OUTPOST, "寒晶矿站", new BlockPos(470, -460, -180), List.of(1, 8)),
            new MapNode(7, NodeType.OUTPOST, "热泉前哨", new BlockPos(760, -500, 120), List.of(2, 5)),
            new MapNode(8, NodeType.OUTPOST, "深井维修站", new BlockPos(760, -560, -120), List.of(3, 6)),
            new MapNode(9, NodeType.OUTPOST, "断链站", new BlockPos(900, -520, 220), List.of(2, 11)),
            new MapNode(10, NodeType.OUTPOST, "潮汐站", new BlockPos(900, -600, -220), List.of(3, 12)),
            new MapNode(11, NodeType.OUTPOST, "冰臼前哨", new BlockPos(1000, -600, 180), List.of(4, 9)),
            new MapNode(12, NodeType.OUTPOST, "暗流站", new BlockPos(1000, -640, -180), List.of(4, 10))
    );

    private CampaignMap() {
    }

    public static List<MapNode> nodes() {
        return NODES;
    }

    public static boolean isValid(int id) {
        return id >= 0 && id < NODES.size();
    }

    public static MapNode node(int id) {
        return isValid(id) ? NODES.get(id) : null;
    }

    public static MapNode start() {
        return NODES.get(START_NODE);
    }

    public static boolean connects(int from, int to) {
        MapNode node = node(from);
        return node != null && isValid(to) && node.connectsTo(to);
    }

    public static List<Integer> neighbors(int id) {
        MapNode node = node(id);
        return node == null ? List.of() : node.neighbors();
    }

    /** 广度优先可达性（单元测试与存档校验用）。 */
    public static boolean reachable(int from, int to) {
        if (!isValid(from) || !isValid(to)) {
            return false;
        }
        boolean[] seen = new boolean[NODES.size()];
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(from);
        seen[from] = true;
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            if (current == to) {
                return true;
            }
            for (int next : NODES.get(current).neighbors()) {
                if (isValid(next) && !seen[next]) {
                    seen[next] = true;
                    queue.addLast(next);
                }
            }
        }
        return false;
    }
}
