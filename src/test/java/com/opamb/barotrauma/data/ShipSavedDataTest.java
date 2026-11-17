package com.opamb.barotrauma.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装配认领记录的 NBT 往返（纯 NBT，不需要开世界）。
 *
 * <p>重点是历史舱内格：切掉外壁舱口后几何的 {@code interiorCells} 会被裁空，
 * 靠泊吸附靠这份记录认出房里的人/任务货，所以它必须随认领记录落盘。</p>
 */
class ShipSavedDataTest {

    @Test
    @DisplayName("历史舱内格随认领记录落盘并读回")
    void knownInteriorRoundTrip() {
        ShipSavedData.AssembledShip ship = new ShipSavedData.AssembledShip();
        ship.vsShipId = 42L;
        ship.corePos = new BlockPos(1, -70, 3);
        ship.proxyPos = ship.corePos;
        ship.knownInterior.add(new BlockPos(1, -70, 3));
        ship.knownInterior.add(new BlockPos(2, -70, 3));

        CompoundTag tag = ship.save();
        // 快照为空，load 不会查方块注册表，可以传 null。
        ShipSavedData.AssembledShip loaded = ShipSavedData.AssembledShip.load(tag, null);

        assertEquals(ship.knownInterior, loaded.knownInterior,
                "历史舱内格必须原样读回");
        assertTrue(loaded.knownInterior.contains(new BlockPos(2, -70, 3)));
    }

    @Test
    @DisplayName("旧记录没有 knownInterior 字段：读成空集，不报错")
    void legacyRecordWithoutKnownInterior() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("shipId", 7L);
        tag.putLong("core", BlockPos.ZERO.asLong());
        tag.putLong("proxy", BlockPos.ZERO.asLong());

        ShipSavedData.AssembledShip loaded = ShipSavedData.AssembledShip.load(tag, null);

        assertTrue(loaded.knownInterior.isEmpty(), "旧存档按空集处理，认领时再按几何重建");
    }
}
