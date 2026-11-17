package com.opamb.barotrauma.fabricator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * 加工台/解构仪配方表。
 *
 * <p>数据来自 {@code tools/gen_fabricator_recipes.py}：它扫描本机 Barotrauma
 * 的 {@code Content/Items}，只保留“输出和所有材料都在本模组里存在”的原作配方，
 * 生成 {@code data/barotrauma/fabricator/recipes.json}。JSON 与具体物品注册无关，
 * 因此 {@link #parse(Reader)} 是纯函数，可以直接单测。</p>
 */
public final class FabricatorCatalog {

    /** 一条配方里的一项材料。 */
    public record Ingredient(String itemId, int count) {
    }

    /** 加工台配方：原作 {@code <Fabricate>}。 */
    public record Recipe(String id, String outputId, int count, double time,
                         String category, List<Ingredient> ingredients) {
    }

    /** 解构配方：原作 {@code <Deconstruct>}。 */
    public record Deconstruct(String inputId, double time, List<Ingredient> outputs) {
    }

    /** 解析结果。 */
    public record Catalog(List<Recipe> recipes, List<Deconstruct> deconstructs) {

        public Recipe recipe(String id) {
            for (Recipe recipe : recipes) {
                if (recipe.id().equals(id)) {
                    return recipe;
                }
            }
            return null;
        }

        public Deconstruct deconstruct(String inputId) {
            for (Deconstruct entry : deconstructs) {
                if (entry.inputId().equals(inputId)) {
                    return entry;
                }
            }
            return null;
        }

        public List<String> categories() {
            List<String> categories = new ArrayList<>();
            for (Recipe recipe : recipes) {
                if (!categories.contains(recipe.category())) {
                    categories.add(recipe.category());
                }
            }
            return categories;
        }
    }

    private FabricatorCatalog() {
    }

    /** 纯解析：不碰 Minecraft 注册表，单测用这个。 */
    public static Catalog parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        List<Recipe> recipes = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("recipes")) {
            JsonObject object = element.getAsJsonObject();
            recipes.add(new Recipe(
                    object.get("id").getAsString(),
                    object.get("output").getAsString(),
                    object.get("count").getAsInt(),
                    object.get("time").getAsDouble(),
                    object.get("category").getAsString(),
                    ingredients(object.getAsJsonArray("ingredients"))));
        }
        List<Deconstruct> deconstructs = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("deconstruct")) {
            JsonObject object = element.getAsJsonObject();
            deconstructs.add(new Deconstruct(
                    object.get("input").getAsString(),
                    object.get("time").getAsDouble(),
                    ingredients(object.getAsJsonArray("outputs"))));
        }
        return new Catalog(List.copyOf(recipes), List.copyOf(deconstructs));
    }

    private static List<Ingredient> ingredients(JsonArray array) {
        List<Ingredient> list = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            list.add(new Ingredient(object.get("item").getAsString(),
                    object.get("count").getAsInt()));
        }
        return List.copyOf(list);
    }
}
