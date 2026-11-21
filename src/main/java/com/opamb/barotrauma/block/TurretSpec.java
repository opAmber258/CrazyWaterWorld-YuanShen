package com.opamb.barotrauma.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * 炮塔的几何/操作规格：只描述“炮塔长多大、轴心在哪、炮口多长、相机多高、俯仰窗口”，
 * 不包含任何模型、贴图、方块放置或瞄准输入逻辑。
 *
 * <p>当前 1×1 原型用 {@link #DEFAULT}；未来 2×2 / 3×3 炮塔只需让方块类返回
 * 不同的 {@code TurretSpec}（以及对应的方块形状/多块主坐标），
 * {@link com.opamb.barotrauma.blockentity.TurretBlockEntity}、
 * 客户端瞄准/相机、网络包和 HUD 都不需要改。</p>
 *
 * <p>坐标约定：{@code pos} 是多块炮塔的**主方块**（当前 1×1 就是自身），
 * pivot 使用相对主方块最小角的格坐标。例如 1×1 的轴心是 (0.5, 0.75, 0.5)，
 * 2×2 可以是 (1.0, 0.75, 1.0)。</p>
 */
public record TurretSpec(
        int sizeX,
        int sizeZ,
        double pivotX,
        double pivotY,
        double pivotZ,
        double muzzleLength,
        /** 落地/墙面时相机相对主方块底面的 y。 */
        double eyeHeight,
        /** 吊顶时相机相对主方块底面的 y。 */
        double ceilingEyeHeight,
        float floorMinPitch,
        float floorMaxPitch,
        float ceilingMinPitch,
        float ceilingMaxPitch,
        float wallMinPitch,
        float wallMaxPitch) {

    /** 当前三段式 1×1 原型：数值与旧硬编码逐项一致。 */
    public static final TurretSpec DEFAULT = new TurretSpec(
            1, 1,
            0.5D, 0.75D, 0.5D,
            1.14D,
            1.05D, 0.15D,
            -89.0F, 20.0F,
            -10.0F, 89.0F,
            -80.0F, 80.0F);

    public Vec3 pivotOffset() {
        return new Vec3(pivotX, pivotY, pivotZ);
    }

    public Vec3 pivot(BlockPos pos) {
        return new Vec3(pos.getX() + pivotX, pos.getY() + pivotY, pos.getZ() + pivotZ);
    }

    /** 炮口 = 轴心 + 瞄准方向 × 炮管长度。 */
    public Vec3 muzzle(BlockPos pos, Vec3 direction) {
        return pivot(pos).add(direction.scale(muzzleLength));
    }

    /** 相机位置：落地/墙面与吊顶只差相机高度，和旧实现一致。 */
    public Vec3 camera(BlockPos pos, Direction mount) {
        double y = mount == Direction.DOWN ? ceilingEyeHeight : eyeHeight;
        return new Vec3(pos.getX() + pivotX, pos.getY() + y, pos.getZ() + pivotZ);
    }

    public float minPitch(Direction mount) {
        if (mount == Direction.DOWN) {
            return ceilingMinPitch;
        }
        return mount.getAxis().isHorizontal() ? wallMinPitch : floorMinPitch;
    }

    public float maxPitch(Direction mount) {
        if (mount == Direction.DOWN) {
            return ceilingMaxPitch;
        }
        return mount.getAxis().isHorizontal() ? wallMaxPitch : floorMaxPitch;
    }
}
