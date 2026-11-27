package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.ship.ShipGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** scanBounds 的 NBT 往返：船被搬走 / 跨维度还原后不能拿旧绝对坐标重扫。 */
class ShipCoreScanBoundsNbtTest {

    private static final BlockPos OLD_CORE = new BlockPos(1_000_480, 64, 0);
    private static final BlockPos NEW_CORE = new BlockPos(2_000, 40, 30);

    @Test
    @DisplayName("新格式存相对偏移，方块实体搬到新位置后 bounds 跟着走")
    void relativeRoundTripFollowsMovedCore() {
        ShipGeometry.Bounds bounds = new ShipGeometry.Bounds(
                OLD_CORE.getX() - 40, OLD_CORE.getY() - 4, OLD_CORE.getZ() - 40,
                OLD_CORE.getX() + 40, OLD_CORE.getY() + 16, OLD_CORE.getZ() + 40);
        CompoundTag tag = new CompoundTag();

        ShipCoreBlockEntity.saveScanBounds(tag, OLD_CORE, bounds);

        assertTrue(tag.getBoolean("ScanBoundsRelative"));
        assertEquals(-40, tag.getInt("ScanMinX"), "NBT 里应是相对核心的偏移，不是 1000440");
        assertEquals(16, tag.getInt("ScanMaxY"));

        ShipGeometry.Bounds restored = ShipCoreBlockEntity.loadScanBounds(tag, NEW_CORE);
        assertEquals(bounds.shifted(NEW_CORE.getX() - OLD_CORE.getX(),
                NEW_CORE.getY() - OLD_CORE.getY(), NEW_CORE.getZ() - OLD_CORE.getZ()), restored);
    }

    @Test
    @DisplayName("旧档绝对坐标：核心没动时保留，跨维度搬走后丢弃")
    void legacyAbsoluteBounds() {
        CompoundTag near = new CompoundTag();
        near.putInt("ScanMinX", -10);
        near.putInt("ScanMinY", 60);
        near.putInt("ScanMinZ", -10);
        near.putInt("ScanMaxX", 10);
        near.putInt("ScanMaxY", 80);
        near.putInt("ScanMaxZ", 10);
        ShipGeometry.Bounds expected = new ShipGeometry.Bounds(-10, 60, -10, 10, 80, 10);
        BlockPos nearCore = new BlockPos(0, 64, 0);
        assertEquals(expected, ShipCoreBlockEntity.loadScanBounds(near, nearCore));

        CompoundTag moved = near.copy();
        assertNull(ShipCoreBlockEntity.loadScanBounds(moved, OLD_CORE),
                "旧绝对坐标离新核心百万格，不能被取并集成大扫描区");
    }

    @Test
    @DisplayName("损坏 NBT / 清空 bounds 时不会留下非法扫描范围")
    void corruptOrClearedBounds() {
        CompoundTag inverted = new CompoundTag();
        inverted.putBoolean("ScanBoundsRelative", true);
        inverted.putInt("ScanMinX", 10);
        inverted.putInt("ScanMinY", 0);
        inverted.putInt("ScanMinZ", 0);
        inverted.putInt("ScanMaxX", -10);
        inverted.putInt("ScanMaxY", 0);
        inverted.putInt("ScanMaxZ", 0);
        assertNull(ShipCoreBlockEntity.loadScanBounds(inverted, OLD_CORE));

        CompoundTag huge = new CompoundTag();
        huge.putInt("ScanMinX", Integer.MIN_VALUE);
        huge.putInt("ScanMinY", 0);
        huge.putInt("ScanMinZ", 0);
        huge.putInt("ScanMaxX", Integer.MAX_VALUE);
        huge.putInt("ScanMaxY", 0);
        huge.putInt("ScanMaxZ", 0);
        assertNull(ShipCoreBlockEntity.loadScanBounds(huge, OLD_CORE));

        CompoundTag partial = new CompoundTag();
        partial.putInt("ScanMinX", -10);
        assertNull(ShipCoreBlockEntity.loadScanBounds(partial, OLD_CORE));
        assertNull(ShipCoreBlockEntity.loadScanBounds(new CompoundTag(), OLD_CORE));

        CompoundTag tag = new CompoundTag();
        ShipCoreBlockEntity.saveScanBounds(tag, OLD_CORE,
                new ShipGeometry.Bounds(-1, -1, -1, 1, 1, 1));
        ShipCoreBlockEntity.saveScanBounds(tag, OLD_CORE, null);
        assertFalse(tag.contains("ScanMinX"));
        assertFalse(tag.contains("ScanBoundsRelative"));
        assertNull(ShipCoreBlockEntity.loadScanBounds(tag, OLD_CORE));
    }
}
