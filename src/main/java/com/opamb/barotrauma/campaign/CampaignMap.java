package com.opamb.barotrauma.campaign;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * v1 的固定战役地图：5 个节点，1 处分叉、1 处汇合。
 *
 * <ul>
 *   <li>0 起始哨站（现有哨站坐标）；</li>
 *   <li>1 B 中继站（现有航路点，分叉点）；</li>
 *   <li>2/3 两条航路（热泉 / 沉船带），二选一；</li>
 *   <li>4 冰下聚居地（汇合点，v1 的终点）。</li>
 * </ul>
 *
 * <p>坐标现在是逻辑锚点：抵达判定用它，建筑生成留给后续里程碑。
 * 5 个节点符合需求文档「5–7 节点小型分支图」的上限。</p>
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
            new MapNode(1, NodeType.WAYPOINT, "B 中继站", new BlockPos(320, -301, 0), List.of(0, 2, 3)),
            new MapNode(2, NodeType.WAYPOINT, "冰隙航路", new BlockPos(640, -420, 240), List.of(1, 4)),
            new MapNode(3, NodeType.WAYPOINT, "沉船航路", new BlockPos(640, -520, -240), List.of(1, 4)),
            new MapNode(4, NodeType.OUTPOST, "冰下聚居地", new BlockPos(960, -640, 0), List.of(2, 3))
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
