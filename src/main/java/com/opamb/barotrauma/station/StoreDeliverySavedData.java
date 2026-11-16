package com.opamb.barotrauma.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 商店"送至潜艇"的待交付货物。
 *
 * <p>照原作 {@code CargoManager.PurchasedItems}：购买时只记账，起航
 * （{@code CampaignManager.startTravel}）时才送进潜艇货柜。按玩家 UUID 分开存，
 * 存档持久化；起航时货柜放不下就留在待交付里，下次起航再试。</p>
 */
public final class StoreDeliverySavedData extends SavedData {

    public static final String NAME = "barotrauma_store_delivery";

    private final Map<UUID, Map<String, Integer>> pending = new LinkedHashMap<>();

    public static StoreDeliverySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StoreDeliverySavedData::load, StoreDeliverySavedData::new, NAME);
    }

    public void add(UUID player, String itemId, int count) {
        if (itemId == null || itemId.isEmpty() || count <= 0) {
            return;
        }
        pending.computeIfAbsent(player, key -> new LinkedHashMap<>())
                .merge(itemId, count, Integer::sum);
        setDirty();
    }

    /** 取出并清空这个玩家的待交付货物；调用方送不成时用 {@link #restore} 放回。 */
    public Map<String, Integer> take(UUID player) {
        Map<String, Integer> taken = pending.remove(player);
        if (taken == null) {
            return Map.of();
        }
        setDirty();
        return new LinkedHashMap<>(taken);
    }

    /**
     * 取出并清空所有玩家的待交付货物（起航交付用）。
     *
     * <p>下单后退出游戏的玩家没有 {@code ServerPlayer}，但"送进船柜"不需要玩家
     * 对象，所以起航时要按 UUID 遍历全部订单，不能只处理在线玩家。</p>
     *
     * @return UUID → 物品 ID → 数量；送不成的用 {@link #restore} 放回
     */
    public Map<UUID, Map<String, Integer>> takeAll() {
        if (pending.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, Integer>> taken = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : pending.entrySet()) {
            taken.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        pending.clear();
        setDirty();
        return taken;
    }

    public void restore(UUID player, Map<String, Integer> items) {
        if (items.isEmpty()) {
            return;
        }
        Map<String, Integer> target = pending.computeIfAbsent(player,
                key -> new LinkedHashMap<>());
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (entry.getValue() > 0) {
                target.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Map<String, Integer>> entry : pending.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", entry.getKey());
            ListTag items = new ListTag();
            for (Map.Entry<String, Integer> item : entry.getValue().entrySet()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putString("Item", item.getKey());
                itemTag.putInt("Count", item.getValue());
                items.add(itemTag);
            }
            playerTag.put("Items", items);
            players.add(playerTag);
        }
        tag.put("Pending", players);
        return tag;
    }

    private static StoreDeliverySavedData load(CompoundTag tag) {
        StoreDeliverySavedData data = new StoreDeliverySavedData();
        if (!tag.contains("Pending", Tag.TAG_LIST)) {
            return data;
        }
        ListTag players = tag.getList("Pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            if (!playerTag.contains("Player")) {
                continue;
            }
            UUID uuid = playerTag.getUUID("Player");
            if (uuid == null) {
                continue;
            }
            Map<String, Integer> map = data.pending.computeIfAbsent(uuid,
                    key -> new LinkedHashMap<>());
            ListTag items = playerTag.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag itemTag = items.getCompound(j);
                String id = itemTag.getString("Item");
                int count = itemTag.getInt("Count");
                if (!id.isEmpty() && count > 0) {
                    map.merge(id, count, Integer::sum);
                }
            }
        }
        return data;
    }
}
