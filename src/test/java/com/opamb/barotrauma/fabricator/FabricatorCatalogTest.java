package com.opamb.barotrauma.fabricator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 加工台配方表的纯解析测试（不加载 Minecraft 注册表）。 */
class FabricatorCatalogTest {

    private static final String SAMPLE = """
            {
              "recipes": [
                {
                  "id": "steel",
                  "output": "barotrauma:steel",
                  "count": 1,
                  "time": 5.0,
                  "money": 0,
                  "category": "Material",
                  "ingredients": [
                    {"item": "barotrauma:carbon", "count": 1},
                    {"item": "barotrauma:iron", "count": 2}
                  ]
                },
                {
                  "id": "wrench",
                  "output": "barotrauma:wrench",
                  "count": 1,
                  "time": 10.0,
                  "money": 0,
                  "category": "Equipment",
                  "ingredients": [{"item": "barotrauma:iron", "count": 2}]
                }
              ],
              "deconstruct": [
                {
                  "id": "steel",
                  "input": "barotrauma:steel",
                  "time": 5.0,
                  "outputs": [
                    {"item": "barotrauma:iron", "count": 2},
                    {"item": "barotrauma:carbon", "count": 1}
                  ]
                }
              ]
            }
            """;

    @Test
    void 解析配方与材料() {
        FabricatorCatalog.Catalog catalog = FabricatorCatalog.parse(new StringReader(SAMPLE));
        assertEquals(2, catalog.recipes().size());
        FabricatorCatalog.Recipe steel = catalog.recipe("steel");
        assertNotNull(steel);
        assertEquals("barotrauma:steel", steel.outputId());
        assertEquals(1, steel.count());
        assertEquals(5.0D, steel.time(), 1.0E-9D);
        assertEquals(2, steel.ingredients().size());
        assertEquals("barotrauma:iron", steel.ingredients().get(1).itemId());
        assertEquals(2, steel.ingredients().get(1).count());
        assertNull(catalog.recipe("不存在"));
    }

    @Test
    void 解析解构并按输入查找() {
        FabricatorCatalog.Catalog catalog = FabricatorCatalog.parse(new StringReader(SAMPLE));
        FabricatorCatalog.Deconstruct deconstruct = catalog.deconstruct("barotrauma:steel");
        assertNotNull(deconstruct);
        assertEquals(2, deconstruct.outputs().size());
        assertNull(catalog.deconstruct("barotrauma:iron"));
    }

    @Test
    void 分类按配方顺序去重() {
        FabricatorCatalog.Catalog catalog = FabricatorCatalog.parse(new StringReader(SAMPLE));
        assertEquals(java.util.List.of("Material", "Equipment"), catalog.categories());
    }

    @Test
    void 仓库里的配方表自洽() throws IOException {
        Path path = Path.of("src/main/resources/data/barotrauma/fabricator/recipes.json");
        assertTrue(Files.exists(path), "配方表不存在，先跑 tools/gen_fabricator_recipes.py");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            FabricatorCatalog.Catalog catalog = FabricatorCatalog.parse(reader);
            assertFalse(catalog.recipes().isEmpty(), "配方表为空");
            assertFalse(catalog.deconstructs().isEmpty(), "解构表为空");

            Set<String> ids = new HashSet<>();
            for (FabricatorCatalog.Recipe recipe : catalog.recipes()) {
                assertTrue(ids.add(recipe.id()), "配方 id 重复: " + recipe.id());
                assertTrue(recipe.outputId().startsWith("barotrauma:"), recipe.outputId());
                assertTrue(recipe.count() > 0, recipe.id());
                assertTrue(recipe.time() > 0.0D, recipe.id());
                assertFalse(recipe.category().isBlank(), recipe.id());
                assertFalse(recipe.ingredients().isEmpty(), recipe.id());
                for (FabricatorCatalog.Ingredient ingredient : recipe.ingredients()) {
                    assertTrue(ingredient.itemId().startsWith("barotrauma:"), ingredient.itemId());
                    assertTrue(ingredient.count() > 0, recipe.id());
                }
            }
            for (FabricatorCatalog.Deconstruct entry : catalog.deconstructs()) {
                assertTrue(entry.inputId().startsWith("barotrauma:"), entry.inputId());
                assertTrue(entry.time() > 0.0D, entry.inputId());
                assertFalse(entry.outputs().isEmpty(), entry.inputId());
            }
        }
    }

    @Test
    void 组合分类按第一个标签归类() throws IOException {
        String json = """
                {"recipes":[
                  {"id":"cell","output":"barotrauma:cell","count":1,"time":1.0,"money":0,
                   "category":"Equipment,Electrical","ingredients":[]},
                  {"id":"wrench","output":"barotrauma:wrench","count":1,"time":1.0,"money":0,
                   "category":"Equipment","ingredients":[]}
                ],"deconstruct":[]}
                """;
        FabricatorCatalog.Catalog catalog = FabricatorCatalog.parse(new StringReader(json));
        assertEquals("Equipment", catalog.recipe("cell").category());
        assertEquals(List.of("Equipment"), catalog.categories());
    }
}
