package com.opamb.barotrauma.fabricator;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 配方表的运行时入口：从 jar 内读 JSON，把物品 id 解析成 {@link Item}。
 *
 * <p>客户端和服务端都会用：客户端画配方列表，服务端校验并真正扣材料，
 * 两边读同一份静态资源，不需要同步配方数据包。</p>
 */
public final class FabricatorData {

    private static FabricatorCatalog.Catalog catalog;

    private FabricatorData() {
    }

    public static FabricatorCatalog.Catalog catalog() {
        if (catalog == null) {
            catalog = load();
        }
        return catalog;
    }

    private static FabricatorCatalog.Catalog load() {
        String path = "/data/" + BarotraumaMod.MOD_ID + "/fabricator/recipes.json";
        try (InputStream stream = FabricatorData.class.getResourceAsStream(path)) {
            if (stream == null) {
                BarotraumaMod.LOGGER.error("缺少加工台配方表 {}", path);
                return new FabricatorCatalog.Catalog(java.util.List.of(), java.util.List.of());
            }
            return FabricatorCatalog.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            BarotraumaMod.LOGGER.error("加工台配方表解析失败", exception);
            return new FabricatorCatalog.Catalog(java.util.List.of(), java.util.List.of());
        }
    }

    /** 物品 id → Item；未知 id 返回空气（配方生成器已过滤，正常不会缺）。 */
    public static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return Items.AIR;
        }
        Item item = BuiltInRegistries.ITEM.get(location);
        return item == null ? Items.AIR : item;
    }

    public static ItemStack result(FabricatorCatalog.Recipe recipe) {
        Item item = item(recipe.outputId());
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, recipe.count());
    }

    public static boolean isKnown(FabricatorCatalog.Recipe recipe) {
        return item(recipe.outputId()) != Items.AIR;
    }
}
