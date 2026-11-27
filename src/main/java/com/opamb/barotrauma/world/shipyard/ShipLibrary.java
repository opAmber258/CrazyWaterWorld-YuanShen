package com.opamb.barotrauma.world.shipyard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨存档船库：`<gameDir>/barotrauma/ships/<id>/`。
 *
 * <p>首版只负责目录、meta.json 与文件路径；structure.nbt 的读写由
 * {@link ShipyardStructureIO} 在 M4 接入。外部工具可以直接改这两个文件。</p>
 */
public final class ShipLibrary {

    public static final int SCHEMA = 2;
    public static final String META_FILE = "meta.json";
    public static final String STRUCTURE_FILE = "structure.nbt";

    private ShipLibrary() {
    }

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("barotrauma").resolve("ships");
    }

    public static void ensureRoot() {
        try {
            Files.createDirectories(root());
        } catch (IOException ignored) {
            // 目录不可写时由上层报错，不在静态路径里抛。
        }
    }

    public static List<Entry> list() {
        ensureRoot();
        List<Entry> entries = new ArrayList<>();
        try (var stream = Files.list(root())) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                Entry entry = readMeta(dir);
                if (entry != null) {
                    entries.add(entry);
                }
            });
        } catch (IOException ignored) {
            return List.of();
        }
        entries.sort(Comparator.comparingLong(Entry::modified).reversed());
        return entries;
    }

    public static boolean validId(String id) {
        return id != null && !id.isBlank() && id.length() <= 64
                && id.matches("[a-z0-9][a-z0-9_-]*");
    }

    public static Path directory(String id) {
        if (!validId(id)) {
            throw new IllegalArgumentException("invalid ship id: " + id);
        }
        Path dir = root().resolve(id).normalize();
        if (!dir.startsWith(root())) {
            throw new IllegalArgumentException("ship id escapes library root");
        }
        return dir;
    }

    public static Path structurePath(String id) {
        return directory(id).resolve(STRUCTURE_FILE);
    }

    public static Entry readMeta(Path dir) {
        Path meta = dir.resolve(META_FILE);
        if (!Files.isRegularFile(meta)) {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            String id = string(json, "id", dir.getFileName().toString());
            if (!validId(id)) {
                return null;
            }
            int[] size = size(json);
            Map<String, Integer> devices = devices(json);
            long modified = json.has("modified") ? json.get("modified").getAsLong()
                    : Files.getLastModifiedTime(dir).toMillis();
            String ownerUuid = clip(string(json, "ownerUuid", ""));
            String ownerName = clip(string(json, "ownerName", ""));
            boolean verified = json.has("verified") && json.get("verified").getAsBoolean();
            return new Entry(id, clip(string(json, "name", id)), size[0], size[1], size[2],
                    json.has("blocks") ? json.get("blocks").getAsInt() : 0,
                    devices, modified, dir, ownerUuid, ownerName, verified);
        } catch (RuntimeException | IOException ignored) {
            return null;
        }
    }

    public static boolean writeMeta(Path dir, Entry entry) {
        try {
            Files.createDirectories(dir);
            JsonObject json = new JsonObject();
            json.addProperty("schema", SCHEMA);
            json.addProperty("id", entry.id());
            json.addProperty("name", clip(entry.name()));
            JsonArrayInt size = new JsonArrayInt(entry.sizeX(), entry.sizeY(), entry.sizeZ());
            json.add("size", size.json());
            json.addProperty("blocks", entry.blocks());
            JsonObject devices = new JsonObject();
            entry.devices().forEach(devices::addProperty);
            json.add("devices", devices);
            json.addProperty("modified", entry.modified());
            if (!entry.ownerUuid().isEmpty()) {
                json.addProperty("ownerUuid", entry.ownerUuid());
            }
            if (!entry.ownerName().isEmpty()) {
                json.addProperty("ownerName", clip(entry.ownerName()));
            }
            json.addProperty("verified", entry.verified());
            Path meta = dir.resolve(META_FILE);
            Path temp = dir.resolve(META_FILE + ".tmp");
            Files.writeString(temp, json.toString(), StandardCharsets.UTF_8);
            Files.move(temp, meta, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException ignored) {
            // 上层通过返回值判断保存是否完整。
            return false;
        }
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    /** 名称会走 writeUtf 与 UI 渲染，外部 meta 再长也必须截断。 */
    public static String clip(String name) {
        if (name == null) {
            return "";
        }
        return name.length() <= 48 ? name : name.substring(0, 48);
    }

    private static int[] size(JsonObject json) {
        int[] out = {0, 0, 0};
        if (json.has("size") && json.get("size").isJsonArray()) {
            var array = json.getAsJsonArray("size");
            for (int i = 0; i < Math.min(3, array.size()); i++) {
                out[i] = array.get(i).getAsInt();
            }
        }
        return out;
    }

    private static Map<String, Integer> devices(JsonObject json) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (json.has("devices") && json.get("devices").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("devices").entrySet()) {
                try {
                    out.put(entry.getKey(), entry.getValue().getAsInt());
                } catch (RuntimeException ignored) {
                    // 外部手改坏了就跳过这一项。
                }
            }
        }
        return out;
    }

    /** 只用于把三个整数写成 JSON 数组的小工具（避免 Gson 对 record 的兼容性差异）。 */
    private record JsonArrayInt(int x, int y, int z) {
        com.google.gson.JsonArray json() {
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            array.add(x);
            array.add(y);
            array.add(z);
            return array;
        }
    }

    public record Entry(String id, String name, int sizeX, int sizeY, int sizeZ, int blocks,
                        Map<String, Integer> devices, long modified, Path directory,
                        String ownerUuid, String ownerName, boolean verified) {

        public Entry(String id, String name, int sizeX, int sizeY, int sizeZ, int blocks,
                     Map<String, Integer> devices, long modified, Path directory) {
            this(id, name, sizeX, sizeY, sizeZ, blocks, devices, modified, directory, "", "", false);
        }

        public String sizeText() {
            return sizeX + "×" + sizeY + "×" + sizeZ;
        }
    }
}
