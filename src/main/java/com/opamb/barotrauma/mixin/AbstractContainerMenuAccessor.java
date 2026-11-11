package com.opamb.barotrauma.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link AbstractContainerMenu} 的 {@code addSlot}/{@code moveItemStackTo}
 * 给装备槽 mixin 使用（两个方法都是 protected）。
 */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {

    @Invoker("addSlot")
    Slot barotrauma$addSlot(Slot slot);

    @Invoker("moveItemStackTo")
    boolean barotrauma$moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection);
}
