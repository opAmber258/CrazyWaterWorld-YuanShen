package com.opamb.barotrauma.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端医疗数据缓存：MedicalSyncPacket 的落点，伤病 UI 的数据源。
 *
 * <p>按玩家 UUID 存快照：自己的（每秒变化同步）和打开治疗面板时请求的队友快照。
 * volatile/并发容器保证网络线程写入 / 渲染线程读取的可见性。</p>
 */
public final class ClientMedicalCache {

    /** 一份玩家医疗快照。vitality 为未钳制值（倒地时为负）。 */
    public record Snapshot(List<String> rows, float vitality, boolean downed) {
    }

    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private ClientMedicalCache() {
    }

    public static void accept(UUID target, String[] rows, float vitality, boolean downed) {
        SNAPSHOTS.put(target, new Snapshot(List.of(rows), vitality, downed));
    }

    public static Snapshot snapshot(UUID player) {
        return SNAPSHOTS.get(player);
    }

    public static void clear(UUID player) {
        SNAPSHOTS.remove(player);
    }
}
