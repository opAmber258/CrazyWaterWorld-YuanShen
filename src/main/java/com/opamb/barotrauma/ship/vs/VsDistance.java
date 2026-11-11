package com.opamb.barotrauma.ship.vs;

import net.minecraft.world.level.Level;

/**
 * 船上坐标的"真实距离"。
 *
 * <p>VS2 物理船的方块交互坐标在**船坞**里，和玩家所在的世界坐标差了上万格；
 * 菜单的 {@code stillValid} 如果直接算欧氏距离，物理船上打开的任何容器都会被
 * 服务端下一 tick 关掉（泵/声纳之所以做不可见代理就是为了绕开这一点）。
 * VS2 提供了船感知的距离函数（先把船坞坐标变换回世界坐标再比距离），这里做一层
 * 无 VS2 依赖的薄封装：没装 VS2 / 调用失败时退回普通距离。</p>
 */
public final class VsDistance {

    private VsDistance() {
    }

    /** 两点（任意一侧可以是船坞坐标）之间的平方距离。 */
    public static double squared(Level level, double x1, double y1, double z1,
                                 double x2, double y2, double z2) {
        if (level == null) {
            return plain(x1, y1, z1, x2, y2, z2);
        }
        if (VsSupport.available()) {
            try {
                return org.valkyrienskies.mod.common.VSGameUtilsKt.squaredDistanceBetweenInclShips(
                        level, x1, y1, z1, x2, y2, z2);
            } catch (Throwable ignored) {
                // 映射失败就退回普通距离，别把界面卡死
            }
        }
        return plain(x1, y1, z1, x2, y2, z2);
    }

    private static double plain(double x1, double y1, double z1,
                                double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}
