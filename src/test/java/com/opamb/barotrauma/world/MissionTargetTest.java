package com.opamb.barotrauma.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** 航路目标点：不同目的地给不同方向，且都在实例净空半径内。 */
class MissionTargetTest {

    @Test
    @DisplayName("不同任务节点的目标点不在同一个方向")
    void everyDestinationHasItsOwnBearing() {
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(0);
        BlockPos one = MissionTarget.targetFor(anchor, 1);
        BlockPos two = MissionTarget.targetFor(anchor, 2);
        BlockPos three = MissionTarget.targetFor(anchor, 3);
        BlockPos four = MissionTarget.targetFor(anchor, 4);
        assertNotEquals(one, two);
        assertNotEquals(two, three);
        assertNotEquals(three, four);
        // 四个点都在锚点 40 格内（SeaTerrain 的 64 格机动净空里）
        for (BlockPos target : new BlockPos[]{one, two, three, four}) {
            double distance = Math.hypot(target.getX() - anchor.x(), target.getZ() - anchor.z());
            assertEquals(40.0D, distance, 0.01D);
        }
    }

    @Test
    @DisplayName("目标点在船下方 20 格，副本站锚点上移后仍一致")
    void targetSitsTwentyBelowAnchor() {
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(2);
        BlockPos target = MissionTarget.targetFor(anchor, 1);
        assertEquals(anchor.y() + MissionTarget.offsetFor(1)[1], target.getY());
        assertEquals(MissionInstance.ANCHOR_Y - 20, target.getY());
    }
}
