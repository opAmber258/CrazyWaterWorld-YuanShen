package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;

/**
 * 一个会从船上电网取电的设备。
 *
 * <p>对应原作 {@code Powered}：设备有自己的功率和最低电压，接线盒只是把它接到
 * 电网上。模组里没有实体电线，所以每个设备用一个 {@code boundBox} 记录自己绑在
 * 哪个接线盒上；{@code boundBox == null} 表示直接挂在全船默认总线上（兼容旧船）。</p>
 */
public interface PowerConsumer {

    /** 当前实际耗电（kW）；停机/不活动时返回 0。 */
    double powerDemandKw();

    /** 设备开始工作所需的最低电压（原作 MinVoltage）：泵 0.3、引擎/声纳 0.5。 */
    double minVoltage();

    /** 绑定的接线盒位置（装配/世界坐标）；null = 默认总线。 */
    BlockPos powerBox();

    /** 设置绑定的接线盒；null = 解绑回默认总线。 */
    void setPowerBox(BlockPos box);
}
