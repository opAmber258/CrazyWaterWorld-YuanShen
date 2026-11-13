package com.opamb.barotrauma.equipment;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;

/**
 * "是否正穿着潜水服"的同步标志。
 *
 * <p>装备槽不在原版 {@code Inventory.armor} 里，其内容默认只存在服务端的
 * Capability 里，客户端拿不到。护甲渲染需要每 tick 都知道所有可见玩家穿没穿，
 * 所以把状态写进原版 {@link SynchedEntityData}：服务端变化后由原版
 * {@code ClientboundSetEntityDataPacket} 同步给同维度玩家（包含第三人称的自己），
 * 不需要额外的追踪/登录/换维度同步逻辑。</p>
 *
 * <p>真正的物品数据仍在 {@link EquipmentInventory}；这里只同步渲染要用的布尔值。
 * 实现由 {@link com.opamb.barotrauma.mixin.PlayerMixin} 挂到 {@link Player} 上，
 * 因此任何 {@code Player} 实例都能直接强转。</p>
 */
public interface DivingSuitState {

    EntityDataAccessor<Boolean> DATA_WEARING =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);

    boolean barotrauma$wearingDivingSuit();

    void barotrauma$setWearingDivingSuit(boolean wearing);

    static DivingSuitState of(Player player) {
        return (DivingSuitState) player;
    }
}
