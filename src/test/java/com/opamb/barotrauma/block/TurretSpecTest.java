package com.opamb.barotrauma.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 锁定 1×1 炮塔的默认几何：这些数值必须和模块化之前的硬编码完全一致，
 * 否则三种炮塔的炮口、相机、落点预判会一起偏移。
 */
class TurretSpecTest {

    private static final double EPS = 1.0E-9D;
    private static final BlockPos POS = new BlockPos(10, 20, 30);

    @Test
    void 默认规格保持旧1x1炮塔数值() {
        TurretSpec spec = TurretSpec.DEFAULT;
        assertEquals(1, spec.sizeX());
        assertEquals(1, spec.sizeZ());
        assertEquals(0.5D, spec.pivotX(), EPS);
        assertEquals(0.75D, spec.pivotY(), EPS);
        assertEquals(0.5D, spec.pivotZ(), EPS);
        assertEquals(1.14D, spec.muzzleLength(), EPS);
        assertEquals(1.05D, spec.eyeHeight(), EPS);
        assertEquals(0.15D, spec.ceilingEyeHeight(), EPS);
    }

    @Test
    void 三种挂载的俯仰窗口和旧常量一致() {
        TurretSpec spec = TurretSpec.DEFAULT;
        assertEquals(-89.0F, spec.minPitch(Direction.UP));
        assertEquals(20.0F, spec.maxPitch(Direction.UP));
        assertEquals(-10.0F, spec.minPitch(Direction.DOWN));
        assertEquals(89.0F, spec.maxPitch(Direction.DOWN));
        assertEquals(-80.0F, spec.minPitch(Direction.NORTH));
        assertEquals(80.0F, spec.maxPitch(Direction.NORTH));
        assertEquals(-80.0F, spec.minPitch(Direction.EAST));
        assertEquals(80.0F, spec.maxPitch(Direction.EAST));
    }

    @Test
    void 轴心炮口相机都从同一规格推导() {
        TurretSpec spec = TurretSpec.DEFAULT;
        Vec3 pivot = spec.pivot(POS);
        assertEquals(10.5D, pivot.x, EPS);
        assertEquals(20.75D, pivot.y, EPS);
        assertEquals(30.5D, pivot.z, EPS);

        Vec3 muzzle = spec.muzzle(POS, new Vec3(0.0D, 0.0D, 1.0D));
        assertEquals(10.5D, muzzle.x, EPS);
        assertEquals(20.75D, muzzle.y, EPS);
        assertEquals(31.64D, muzzle.z, EPS);

        Vec3 floorCamera = spec.camera(POS, Direction.UP);
        assertEquals(10.5D, floorCamera.x, EPS);
        assertEquals(21.05D, floorCamera.y, EPS);
        assertEquals(30.5D, floorCamera.z, EPS);

        Vec3 ceilingCamera = spec.camera(POS, Direction.DOWN);
        assertEquals(20.15D, ceilingCamera.y, EPS);

        Vec3 wallCamera = spec.camera(POS, Direction.NORTH);
        assertEquals(21.05D, wallCamera.y, EPS);
    }
}
