package com.opamb.barotrauma.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 允许运行时改槽位显示坐标。
 *
 * <p>创造模式"物品栏"标签页会把 {@code inventoryMenu} 的槽位包成 {@code SlotWrapper}
 * 并按自己的规则排坐标；我们追加的潜水服槽（46）和背包格（9..20）需要换位置，
 * 但 {@code Slot.x/y} 是 final，这里用 Mixin 的 {@code @Mutable} 打开 setter。</p>
 */
@Mixin(Slot.class)
public interface SlotPositionAccessor {

    @Accessor("x")
    @Mutable
    void barotrauma$setX(int x);

    @Accessor("y")
    @Mutable
    void barotrauma$setY(int y);
}
