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

    /**
     * 从已同步的病症行里读某类型的强度（找不到返回 0）。
     *
     * <p>行格式：{@code id|strength}（全身）或 {@code id|LIMB|strength}（部位），
     * 与 {@code AfflictionContainer.rows()} 一致；治疗行以 {@code t|...} 开头，
     * 不会和类型 id 撞上。</p>
     */
    public static double strength(UUID player, String typeId) {
        Snapshot snapshot = SNAPSHOTS.get(player);
        if (snapshot == null) {
            return 0.0;
        }
        for (String row : snapshot.rows()) {
            int bar = row.indexOf('|');
            if (bar < 0 || !row.substring(0, bar).equals(typeId)) {
                continue;
            }
            int last = row.lastIndexOf('|');
            try {
                return Double.parseDouble(row.substring(last + 1));
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /** 退出登录时清空全部快照，避免下次进入世界读到上一局的旧数据。 */
    public static void clearAll() {
        SNAPSHOTS.clear();
    }
}
