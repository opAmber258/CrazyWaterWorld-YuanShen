package com.opamb.barotrauma.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 让模组能直接触发原版的槽位点击（{@code AbstractContainerScreen#slotClicked} 是 protected）。
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Invoker("slotClicked")
    void barotrauma$slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type);
}
