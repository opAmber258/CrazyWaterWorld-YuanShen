package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.recipe.GlassDivingSuitRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 自定义配方序列化器（需要复制子格 NBT 的合成走这里）。 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, BarotraumaMod.MOD_ID);

    /** 潜水服 + 2 玻璃 → 透明潜水服，并保留氧气瓶。 */
    public static final RegistryObject<RecipeSerializer<?>> GLASS_DIVING_SUIT =
            SERIALIZERS.register("glass_diving_suit",
                    () -> new SimpleCraftingRecipeSerializer<>(GlassDivingSuitRecipe::new));

    private ModRecipes() {
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
