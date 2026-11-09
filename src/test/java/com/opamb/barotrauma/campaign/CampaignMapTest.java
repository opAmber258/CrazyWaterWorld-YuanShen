package com.opamb.barotrauma.campaign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v1 固定战役地图的结构约束：节点数、连通性、对称边、分叉与终点。 */
class CampaignMapTest {

    @Test
    @DisplayName("节点数在 12–15 之间（精简首群系：站点多、航路点少）")
    void nodeBudget() {
        assertTrue(CampaignMap.nodes().size() >= 12, "至少 12 个节点");
        assertTrue(CampaignMap.nodes().size() <= 15, "不超过 15 个节点");
    }

    @Test
    @DisplayName("任意两个节点互相可达")
    void graphIsConnected() {
        int size = CampaignMap.nodes().size();
        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                assertTrue(CampaignMap.reachable(from, to), from + " -> " + to + " 不可达");
            }
        }
    }

    @Test
    @DisplayName("边是双向的，且不指向不存在的节点")
    void edgesAreSymmetric() {
        for (MapNode node : CampaignMap.nodes()) {
            for (int neighbor : node.neighbors()) {
                assertTrue(CampaignMap.isValid(neighbor), "非法邻居 " + neighbor);
                assertTrue(CampaignMap.node(neighbor).neighbors().contains(node.id()),
                        node.id() + " -> " + neighbor + " 不是双向边");
            }
            assertFalse(node.neighbors().contains(node.id()), "节点不能连自己");
        }
    }

    @Test
    @DisplayName("有分叉点，且至少两个前哨站")
    void hasBranchAndOutposts() {
        assertTrue(CampaignMap.nodes().stream().anyMatch(n -> n.neighbors().size() >= 3),
                "至少一个分叉点");
        long outposts = CampaignMap.nodes().stream()
                .filter(n -> n.type() == NodeType.OUTPOST)
                .count();
        assertTrue(outposts >= 2, "至少两个前哨站");
    }

    @Test
    @DisplayName("起点是前哨站，且只能从起点到 B 中继站")
    void startNodeAndFirstEdge() {
        assertEquals(NodeType.OUTPOST, CampaignMap.start().type());
        assertTrue(CampaignMap.connects(CampaignMap.START_NODE, 1));
        assertFalse(CampaignMap.connects(CampaignMap.START_NODE, 2));
    }
}
