package com.opamb.barotrauma.world.shipyard;

import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ShipyardLibraryListPacket;
import com.opamb.barotrauma.network.ShipyardStatePacket;
import com.opamb.barotrauma.network.ShipyardStatusPacket;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 船库 UI 的服务端动作。 */
public final class ShipyardLibraryServer {

    private ShipyardLibraryServer() {
    }

    public static void handle(ServerPlayer player, byte action, String id, String name) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.SHIPYARD)
                || (!player.isCreative() && !player.hasPermissions(2))) {
            return;
        }
        ShipyardSavedData data = ShipyardSavedData.get(level);
        ShipyardSavedData.Plot plot = data.plot(player.getUUID());
        if (plot == null) {
            return;
        }
        switch (action) {
            case 1 -> sendList(player);
            case 2 -> {
                String shipName = ShipLibrary.clip(name == null || name.isBlank()
                        ? "ship_" + plot.index : name);
                String shipId = uniqueId(slug(shipName), shipName, plot.shipId);
                boolean ok = false;
                if (canModify(player, shipId)) {
                    ShipyardValidator.Report report = ShipyardValidator.validate(level, plot);
                    boolean verified = report.connected() && report.sealed();
                    ok = ShipyardStructureIO.save(level, plot, shipId, shipName,
                            player.getUUID().toString(), player.getGameProfile().getName(), verified);
                }
                if (ok) {
                    data.setShipId(player.getUUID(), shipId);
                    data.setDirty(player.getUUID(), false);
                    data.setDirty();
                    ModNetwork.sendToPlayer(player, ShipyardStatePacket.of(plot));
                }
                player.displayClientMessage(Component.translatable(ok
                        ? "shipyard.library.saved" : "shipyard.library.save_failed", shipId), false);
                sendList(player);
            }
            case 3 -> {
                boolean ok = ShipLibrary.validId(id) && ShipyardStructureIO.load(level, plot, id);
                if (ok) {
                    ShipyardServer.clearPlayer(player.getUUID());
                    data.setDirty(player.getUUID(), false);
                    data.setDirty();
                    ModNetwork.sendToPlayer(player, ShipyardStatePacket.of(plot));
                    ModNetwork.sendToPlayer(player, ShipyardStatusPacket.of(
                            ShipyardValidator.validate(level, plot)));
                }
                player.displayClientMessage(Component.translatable(ok
                        ? "shipyard.library.loaded" : "shipyard.library.load_failed", id), false);
                sendList(player);
            }
            case 4 -> {
                boolean ok = ShipLibrary.validId(id) && ShipyardStructureIO.export(id);
                player.displayClientMessage(Component.translatable(ok
                        ? "shipyard.library.exported" : "shipyard.library.export_failed", id), false);
            }
            case 5 -> {
                boolean ok = delete(player, id);
                player.displayClientMessage(Component.translatable(ok
                        ? "shipyard.library.deleted" : "shipyard.library.delete_failed", id), false);
                sendList(player);
            }
            default -> {
            }
        }
    }

    private static void sendList(ServerPlayer player) {
        List<ShipLibrary.Entry> entries = ShipLibrary.list();
        ModNetwork.sendToPlayer(player, ShipyardLibraryListPacket.of(entries));
    }

    /** 供 UI 与命令共用：ASCII 前缀 + 原文 CRC，中文名也不会塌缩成同一个 id。 */
    public static String slug(String name) {
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        String ascii = normalized.replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^_+|_+$", "");
        boolean nonAscii = !normalized.matches("[a-z0-9_-]+");
        if (ascii.isEmpty()) {
            ascii = "ship";
        }
        String id = ascii;
        if (nonAscii) {
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String hash = Long.toHexString(crc.getValue());
            int keep = Math.max(1, 64 - hash.length() - 1);
            id = (ascii.length() > keep ? ascii.substring(0, keep) : ascii) + "_" + hash;
        }
        if (id.length() > 64) {
            id = id.substring(0, 64);
        }
        if (!Character.isLetterOrDigit(id.charAt(0))) {
            id = "ship_" + id;
        }
        return id;
    }

    /** 同名船重复保存时覆盖自己的条目；不同船撞名则追加序号，绝不静默覆盖别人的船。 */
    public static String uniqueId(String base, String name, String currentId) {
        Path dir = ShipLibrary.directory(base);
        if (!Files.isDirectory(dir) || base.equals(currentId)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String suffix = "_" + i;
            String candidate = (base.length() + suffix.length() > 64
                    ? base.substring(0, 64 - suffix.length()) : base) + suffix;
            if (!Files.isDirectory(ShipLibrary.directory(candidate))) {
                return candidate;
            }
        }
        String fallback = (base.length() + 8 > 64 ? base.substring(0, 56) : base)
                + "_" + System.currentTimeMillis() % 100_000L;
        return fallback;
    }

    private static boolean canModify(ServerPlayer player, String id) {
        if (!ShipLibrary.validId(id)) {
            return false;
        }
        Path dir = ShipLibrary.directory(id);
        if (!Files.isDirectory(dir)) {
            return true;
        }
        ShipLibrary.Entry existing = ShipLibrary.readMeta(dir);
        if (existing == null || existing.ownerUuid().isEmpty()) {
            return true;
        }
        return existing.ownerUuid().equals(player.getUUID().toString()) || player.hasPermissions(2);
    }

    private static boolean delete(ServerPlayer player, String id) {
        if (!ShipLibrary.validId(id) || "exports".equals(id) || !canModify(player, id)) {
            return false;
        }
        Path dir = ShipLibrary.directory(id);
        // 只删真正的船库条目，避免把外部放进来的任意目录递归清空。
        if (!Files.isDirectory(dir)
                || (!Files.isRegularFile(dir.resolve(ShipLibrary.META_FILE))
                && !Files.isRegularFile(dir.resolve(ShipLibrary.STRUCTURE_FILE)))) {
            return false;
        }
        boolean[] failed = {false};
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    failed[0] = true;
                }
            });
            return !failed[0];
        } catch (IOException ignored) {
            return false;
        }
    }
}
