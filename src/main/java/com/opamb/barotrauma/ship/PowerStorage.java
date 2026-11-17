package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;

/**
 * 一个能向船上电网放电/从电网充电的储能设备（目前是电池）。
 *
 * <p>对应原作 {@code PowerContainer}：电池不是用电设备，它在供电不足时补充
 * 差额、供电富余时吸收剩余功率；和 {@link PowerConsumer} 一样用 {@code boundBox}
 * 记录绑定的接线盒，{@code null} 表示挂在全船默认总线上。</p>
 */
public interface PowerStorage {

    /** 存电量（kW·min）；原作 Capacity/Charge 的单位。 */
    double storedKwm();

    /** 额定容量（kW·min）。 */
    double capacityKwm();

    /** 当前电量比例 0..1。 */
    default double chargeFraction() {
        double capacity = capacityKwm();
        return capacity <= 1.0E-9D ? 0.0D : Math.max(0.0D, Math.min(1.0D, storedKwm() / capacity));
    }

    /** 绑定的接线盒位置（装配/世界坐标）；null = 默认总线。 */
    BlockPos storageBox();

    /** 设置绑定的接线盒；null = 解绑回默认总线。 */
    void setStorageBox(BlockPos box);
}
