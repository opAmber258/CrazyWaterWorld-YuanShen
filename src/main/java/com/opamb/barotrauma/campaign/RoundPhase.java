package com.opamb.barotrauma.campaign;

/** 当前巡回所处的阶段。 */
public enum RoundPhase {
    /** 停在某个节点（前哨站或航路点），可以接任务/选下一站。 */
    AT_NODE,
    /** 已出发，正在前往目的地。 */
    DIVING,
    /** 本轮失败，等待回档。 */
    FAILED
}
