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
        UUID online = UUID.randomUUID();
        UUID offline = UUID.randomUUID();
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
}
