package com.opamb.barotrauma.ship;

/**
 * 水盒（7 float：minX/minY/minZ/maxX/maxY/maxZ/surface）的纯几何查询。
 *
 * <p>不引用 Minecraft/VS2 类型，客户端与服务端共用同一套"舱内头顶净高"判定：
 * 玩家脚在某个 water body 的盒子里，盒子的 maxY+1 就是这一列空气段的天花板；
 * 站立碰撞箱（1.8 格）放不下时，玩家必须保持游泳碰撞箱，否则原版把碰撞箱
 * 撑进天花板后会把人挤到上方（用户实测"从游泳变成站立就穿上去"）。</p>
 */
public final class ShipWaterBoxes {

    private ShipWaterBoxes() {
    }

    /**
     * 玩家脚位置所在的水盒里，是否有 {@code height} 格的站立空间。
     *
     * @return {@code null}=脚不在任何水盒里（不在船里）；{@code true}=能站；
     *         {@code false}=天花板太低，只能保持游泳碰撞箱
     */
    /**
     * 脚离所在水盒底面的高度（水盒 minY 就是这一列空气段的地板/平台顶）。
     *
     * @return 不在任何水盒里返回 {@link Double#NaN}
     */
    public static double floorDistance(float[] boxes, double x, double y, double z) {
        if (boxes == null || boxes.length < 7) {
            return Double.NaN;
        }
        double best = Double.NaN;
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            float minX = boxes[i];
            float minY = boxes[i + 1];
            float minZ = boxes[i + 2];
            float maxX = boxes[i + 3] + 1.0F;
            float maxY = boxes[i + 4] + 1.0F;
            float maxZ = boxes[i + 5] + 1.0F;
            if (x < minX - 0.01D || x > maxX + 0.01D
                    || z < minZ - 0.01D || z > maxZ + 0.01D) {
                continue;
            }
            if (y < minY - 0.01D || y > maxY + 0.01D) {
                continue;
            }
            double distance = y - minY;
            if (Double.isNaN(best) || distance < best) {
                best = distance;
            }
        }
        return best;
    }

    public static Boolean hasStandingRoom(float[] boxes, double x, double y, double z,
                                          double height) {
        if (boxes == null || boxes.length < 7) {
            return null;
        }
        boolean found = false;
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            float minX = boxes[i];
            float minY = boxes[i + 1];
            float minZ = boxes[i + 2];
            float maxX = boxes[i + 3] + 1.0F;
            float maxY = boxes[i + 4] + 1.0F;
            float maxZ = boxes[i + 5] + 1.0F;
            if (x < minX - 0.01D || x > maxX + 0.01D
                    || z < minZ - 0.01D || z > maxZ + 0.01D) {
                continue;
            }
            if (y < minY - 0.01D || y > maxY + 0.01D) {
                continue;
            }
            found = true;
            if (y + height <= maxY + 0.02D) {
                return true;
            }
        }
        return found ? Boolean.FALSE : null;
    }
}
