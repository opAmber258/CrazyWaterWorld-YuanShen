package com.opamb.barotrauma.medical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 医疗交互参数（用户 2026-11-10 指定队友治疗距离为 2 格）。
 *
 * <p>这组断言是**回归护栏**：距离曾在三个地方各自硬编码（H 键选人、面板请求快照、
 * 实际用药），改一处漏两处就会出现"客户端能选中、服务端回距离太远"的割裂。
 * 现在统一到 {@link MedicalTuning}，这里锁住数值本身。</p>
 */
class MedicalTuningTest {

    @Test
    void treatRangeIsTwoBlocks() {
        assertEquals(2.0D, MedicalTuning.TREAT_RANGE_BLOCKS, 1.0E-9);
        // 平方值必须与距离一致，否则服务端校验会比客户端松/紧
        assertEquals(4.0D, MedicalTuning.TREAT_RANGE_SQR, 1.0E-9);
    }

    @Test
    void treatRangeSqrMatchesBlocks() {
        assertEquals(MedicalTuning.TREAT_RANGE_BLOCKS * MedicalTuning.TREAT_RANGE_BLOCKS,
                MedicalTuning.TREAT_RANGE_SQR, 1.0E-9);
    }

    @Test
    void treatRangeIsShorterThanVanillaInteractionReach() {
        // 原版手臂交互距离 4.5 格；治疗必须更近（用户要求"贴过去"），
        // 防止后续有人把数值往上调回 6 格而不自知。
        assertTrue(MedicalTuning.TREAT_RANGE_BLOCKS < 4.5D,
                "治疗距离应当短于原版交互距离");
    }
}
