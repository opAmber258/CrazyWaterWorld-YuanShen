package com.opamb.barotrauma.world;

import com.opamb.barotrauma.station.StoreCatalog;
import com.opamb.barotrauma.station.StorePricing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 每个哨站商人自己的库存/余额/价格修正。
 *
 * <p>对照原作 {@code Location.StoreInfo}：商人余额初始 5000，买进增加、卖出减少；
 * 每次到访新哨站库存 +1；每个商店独立 ±5% 价格修正。按站点 (x,z) 存，
 * 主站和每轮副本站各有一份。</p>
 */
public final class StationStoreSavedData extends SavedData {

    public static final String NAME = "barotrauma_station_stores";
    private static final int INITIAL_BALANCE = 5000;

    private final Map<String, SiteStore> stores = new LinkedHashMap<>();

    public static StationStoreSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StationStoreSavedData::load, StationStoreSavedData::new, NAME);
    }

    /** 取站点商店；第一次访问时按目录生成库存，新的轮次自动补货。 */
    public SiteStore store(DockingBay.Site site, int round) {
        String key = site.x() + "," + site.z();
        SiteStore store = stores.get(key);
        if (store == null) {
            store = new SiteStore(key,
                    StorePricing.modifierFor(site.x(), site.z()), round);
            stores.put(key, store);
            setDirty();
            return store;
        }
        if (store.ensureStock(round)) {
            setDirty();
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (SiteStore store : stores.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", store.key);
            entry.putInt("balance", store.balance);
            entry.putInt("round", store.lastRestockRound);
            entry.putInt("modifier", store.modifier);
            CompoundTag stock = new CompoundTag();
            for (Map.Entry<String, Integer> item : store.stock.entrySet()) {
                stock.putInt(item.getKey(), item.getValue());
            }
            entry.put("stock", stock);
            list.add(entry);
        }
        tag.put("stores", list);
        return tag;
    }

    private static StationStoreSavedData load(CompoundTag tag) {
        StationStoreSavedData data = new StationStoreSavedData();
        ListTag list = tag.getList("stores", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String key = entry.getString("key");
            if (key.isBlank()) {
                continue;
            }
            SiteStore store = new SiteStore(key,
                    entry.contains("modifier") ? entry.getInt("modifier") : 0,
                    entry.contains("round") ? entry.getInt("round") : 0, false);
            store.balance = entry.contains("balance") ? entry.getInt("balance") : INITIAL_BALANCE;
            CompoundTag stock = entry.getCompound("stock");
            for (String itemId : stock.getAllKeys()) {
                store.stock.put(itemId, Math.max(0, stock.getInt(itemId)));
            }
            data.stores.put(key, store);
        }
        return data;
    }

    /** 一个站点的商店状态。 */
    public static final class SiteStore {
        private final String key;
        private final Map<String, Integer> stock = new LinkedHashMap<>();
        private int balance = INITIAL_BALANCE;
        private int lastRestockRound;
        private final int modifier;

        private SiteStore(String key, int modifier, int round) {
            this(key, modifier, round, true);
        }

        /** {@code initializeStock=false} 用于读档：库存从 NBT 恢复，不碰物品注册表。 */
        private SiteStore(String key, int modifier, int round, boolean initializeStock) {
            this.key = key;
            this.modifier = modifier;
            this.lastRestockRound = round;
            if (!initializeStock) {
                return;
            }
            for (StoreCatalog.Entry entry : StoreCatalog.entries()) {
                if (entry.buyable()) {
                    stock.put(entry.itemId(), entry.baseStock());
                }
            }
        }

        /** 新轮次到访：每件上架商品 +1，封顶初始 +5。 */
        private boolean ensureStock(int round) {
            if (round <= lastRestockRound) {
                return false;
            }
            lastRestockRound = round;
            for (StoreCatalog.Entry entry : StoreCatalog.entries()) {
                if (!entry.buyable()) {
                    continue;
                }
                int current = stock.getOrDefault(entry.itemId(), 0);
                stock.put(entry.itemId(), StorePricing.restock(current, entry.baseStock()));
            }
            return true;
        }

        public int stock(String itemId) {
            return Math.max(0, stock.getOrDefault(itemId, 0));
        }

        public void takeOne(String itemId) {
            int current = stock(itemId);
            if (current > 0) {
                stock.put(itemId, current - 1);
            }
        }

        public int balance() {
            return balance;
        }

        public void addBalance(int amount) {
            balance += amount;
        }

        public boolean canPay(int amount) {
            return balance >= amount;
        }

        public void pay(int amount) {
            balance -= amount;
        }

        public int modifier() {
            return modifier;
        }
    }
}
