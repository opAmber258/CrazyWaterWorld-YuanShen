package com.opamb.barotrauma.medical;

/**
 * 医疗系统的交互参数（客户端与服务端**共用同一份**，避免校验不一致）。
 *
 * <p>距离判定出现在三处：H 键长按选队友（客户端 `MedicalClientHooks`）、
 * 面板请求快照（`MedicalRequestPacket`）、实际用药（`MedicalTreatPacket`）。
 * 任何一处单独硬编码都会出现"客户端能选中、服务端拒绝"或反之的割裂，
 * 所以统一收口到这里。</p>
 */
public final class MedicalTuning {

    /**
     * 能相互治疗的最大距离（格）。**用户 2026-11-10 指定 2 格**——
     * 原作治疗是贴身的，远一点就该走过去。
     */
    public static final double TREAT_RANGE_BLOCKS = 2.0D;

    /** 距离平方，避免每次调用都开方。 */
    public static final double TREAT_RANGE_SQR =
            TREAT_RANGE_BLOCKS * TREAT_RANGE_BLOCKS;

    private MedicalTuning() {
    }
}
