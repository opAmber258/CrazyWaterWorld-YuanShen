package com.opamb.barotrauma.station;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商店待交付订单的账本逻辑。
 *
 * <p>起航交付按 UUID 遍历所有订单（包括下单后退出游戏的玩家），
 * 所以数据层必须能一次性取出全部订单，而不是只按在线玩家取。</p>
 */
class StoreDeliverySavedDataTest {

    @Test
    @DisplayName("takeAll 取出所有玩家的订单并清空")
    void takeAllReturnsEveryOwner() {
        StoreDeliverySavedData data = new StoreDeliverySavedData();
        // 固定 UUID：测试身份可重复，且两个玩家必须不同。
        UUID online = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID offline = UUID.fromString("22222222-2222-2222-2222-222222222222");
        data.add(online, "barotrauma:oxygen_tank", 2);
        data.add(offline, "barotrauma:welding_tool", 1);
        data.add(offline, "barotrauma:oxygen_tank", 1);

        Map<UUID, Map<String, Integer>> taken = data.takeAll();

        assertEquals(2, taken.size(), "离线玩家的订单也必须被取出");
        assertEquals(Map.of("barotrauma:oxygen_tank", 2), taken.get(online));
        assertEquals(Map.of("barotrauma:welding_tool", 1,
                "barotrauma:oxygen_tank", 1), taken.get(offline));
        assertTrue(data.takeAll().isEmpty(), "取出后待交付列表必须清空");
    }

    @Test
    @DisplayName("没有订单时 takeAll 返回空表")
    void takeAllEmpty() {
        assertTrue(new StoreDeliverySavedData().takeAll().isEmpty());
    }

    @Test
    @DisplayName("空 itemId / 非正数量不会进待交付列表")
    void invalidInputIgnored() {
        StoreDeliverySavedData data = new StoreDeliverySavedData();
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        data.add(id, "", 1);
        data.add(id, "barotrauma:oxygen_tank", 0);
        data.add(id, "barotrauma:oxygen_tank", -1);
        assertTrue(data.takeAll().isEmpty());
    }
}
