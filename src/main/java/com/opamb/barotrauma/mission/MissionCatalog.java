package com.opamb.barotrauma.mission;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opamb.barotrauma.BarotraumaMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 寒冷深穴任务目录：把原版 Missions.xml 里该群系能出的任务全部登记下来，
 * 但只有 {@link MissionFeature#implemented()} 的功能对应条目会被终端发布。
 *
 * <p>数据文件：{@code /data/barotrauma/missions/cold_caverns.json}。</p>
 */
public final class MissionCatalog {

    private static final String RESOURCE = "/data/barotrauma/missions/cold_caverns.json";

    /**
     * 当前终端实际发布的货运条目：4 条有代表性的（化学品/医疗/建材/挥发性炸药），
     * 箱型、每箱报酬、箱内清单见 {@link CargoManifest}。其余仍在目录里登记。
     */
    private static final Set<String> CARGO_OFFERS = Set.of(
            "cargochemicals", "cargomeditems", "cargoconstructionminerals",
            "cargovolatilecompoundn");

    private static final List<MissionSpec> SPECS = load();
    private static final List<MissionSpec> OFFERS = buildOffers();

    private MissionCatalog() {
    }

    /** 全部已登记的寒冷深穴任务。 */
    public static List<MissionSpec> specs() {
        return SPECS;
    }

    /** 终端实际发布的任务：依赖已实装且地形要求为 none/cave。 */
    public static List<MissionSpec> offers() {
        return OFFERS;
    }

    /** 按任务 id（1 起）取终端发布的条目；越界返回 null。 */
    public static MissionSpec offer(int oneBasedId) {
        int index = oneBasedId - 1;
        return index >= 0 && index < OFFERS.size() ? OFFERS.get(index) : null;
    }

    private static List<MissionSpec> buildOffers() {
        List<MissionSpec> out = new ArrayList<>();
        for (MissionSpec spec : SPECS) {
            if (!spec.implemented()) {
                continue;
            }
            String spawn = spec.spawnType() == null ? "none" : spec.spawnType().toLowerCase();
            if (!spawn.equals("none") && !spawn.equals("cave")) {
                continue;
            }
            if (spec.kind() == MissionKind.CARGO && !CARGO_OFFERS.contains(spec.id())) {
                continue;
            }
            out.add(spec);
        }
        return List.copyOf(out);
    }

    private static List<MissionSpec> load() {
        try (InputStream stream = MissionCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                BarotraumaMod.LOGGER.warn("[missions] 找不到任务目录 {}", RESOURCE);
                return List.of();
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("specs");
            List<MissionSpec> list = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                MissionKind kind = MissionKind.fromJson(string(obj, "kind"));
                if (kind == null) {
                    BarotraumaMod.LOGGER.warn("[missions] 未知任务类型 {}",
                            string(obj, "kind") + " (" + string(obj, "id") + ")");
                    continue;
                }
                list.add(new MissionSpec(
                        string(obj, "id"), kind, string(obj, "text"),
                        string(obj, "nameZh"), string(obj, "nameEn"),
                        integer(obj, "reward"), integer(obj, "difficulty"),
                        integer(obj, "minLevelDifficulty"), integer(obj, "maxLevelDifficulty"),
                        string(obj, "spawnType"), string(obj, "fromType"), string(obj, "toType")));
            }
            return List.copyOf(list);
        } catch (Exception e) {
            BarotraumaMod.LOGGER.error("[missions] 读取任务目录失败", e);
            return List.of();
        }
    }

    private static String string(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el == null || el.isJsonNull() ? "" : el.getAsString();
    }

    private static int integer(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el == null || el.isJsonNull() ? 0 : el.getAsInt();
    }
}
