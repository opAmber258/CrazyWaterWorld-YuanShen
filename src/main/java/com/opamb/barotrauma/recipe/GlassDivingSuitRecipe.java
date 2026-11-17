package com.opamb.barotrauma.recipe;

import com.opamb.barotrauma.equipment.ItemSubSlot;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 普通潜水服 + 2 玻璃 → 透明潜水服。
 *
 * <p>不能用 JSON 无序配方：它只按物品类型匹配，生成的新栈不会继承潜水服
 * 子格里的氧气瓶，玩家的瓶子会凭空消失。这里在 {@link #assemble} 里把
 * {@code ContainedItem} 复制到结果上。</p>
 */
public class GlassDivingSuitRecipe extends CustomRecipe {

    public GlassDivingSuitRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int suits = 0;
        int glass = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.DIVING_SUIT.get())) {
                suits++;
            } else if (stack.is(Items.GLASS)) {
                glass++;
            } else {
                return false;
            }
        }
        return suits == 1 && glass == 2;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        ItemStack result = new ItemStack(ModItems.GLASS_DIVING_SUIT.get());
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(ModItems.DIVING_SUIT.get())) {
                ItemStack contained = ItemSubSlot.get(stack);
                if (!contained.isEmpty()) {
                    ItemSubSlot.set(result, contained);
                }
                break;
            }
        }
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        return NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.GLASS_DIVING_SUIT.get();
    }
}
