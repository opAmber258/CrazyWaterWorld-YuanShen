package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.item.BatteryCellItem;
import com.opamb.barotrauma.item.DivingSuitItem;
import com.opamb.barotrauma.item.FlashlightItem;
import com.opamb.barotrauma.item.WeldingToolItem;
import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 物品内部的单个"悬停子格"（原作潜水服的氧气瓶格、焊接工具的燃料格）。
 *
 * <p>内容存在宿主物品的 NBT（{@code ContainedItem}）里，物品本身跟着走；
 * 子格只允许一件，类型由宿主决定。</p>
 */
public final class ItemSubSlot {

    private static final String KEY = "ContainedItem";

    private ItemSubSlot() {
    }

    /** 宿主物品是不是有子格（潜水服 / 焊接工具 / 等离子切割机 / 手电筒）。 */
    public static boolean hasSubSlot(ItemStack host) {
        return !host.isEmpty()
                && (host.getItem() instanceof DivingSuitItem
                || host.getItem() instanceof WeldingToolItem
                || host.getItem() instanceof com.opamb.barotrauma.item.PlasmaCutterItem
                || host.getItem() instanceof FlashlightItem);
    }

    /** 子格里现在装的东西。 */
    public static ItemStack get(ItemStack host) {
        if (host == null || host.isEmpty() || !host.hasTag()) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = host.getTag();
        if (tag == null || !tag.contains(KEY, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(tag.getCompound(KEY));
    }

    /** 写入子格；传空栈等于清空。 */
    public static void set(ItemStack host, ItemStack contained) {
        if (host == null || host.isEmpty()) {
            return;
        }
        if (contained == null || contained.isEmpty()) {
            if (host.hasTag()) {
                CompoundTag tag = host.getTag();
                tag.remove(KEY);
                if (tag.isEmpty()) {
                    host.setTag(null);
                }
            }
            return;
        }
        ItemStack stored = contained.copy();
        stored.setCount(1);
        host.getOrCreateTag().put(KEY, stored.save(new CompoundTag()));
    }

    /** 这件东西能不能放进宿主的子格。 */
    public static boolean accepts(ItemStack host, ItemStack candidate) {
        if (host.isEmpty() || candidate.isEmpty()) {
            return false;
        }
        if (host.getItem() instanceof DivingSuitItem) {
            return candidate.is(EquipmentTags.OXYGEN_TANK);
        }
        if (host.getItem() instanceof WeldingToolItem) {
            return candidate.is(ModItems.WELDING_FUEL_TANK.get());
        }
        if (host.getItem() instanceof com.opamb.barotrauma.item.PlasmaCutterItem) {
            // 原作 plasmacutter 的容器只该放氧气瓶；焊接油箱放进去会炸（1.13.5
            // tools.xml 里对 weldingfueltank 有 OnUse 爆炸效果），所以这里直接拒绝。
            return candidate.is(EquipmentTags.OXYGEN_TANK);
        }
        if (host.getItem() instanceof FlashlightItem) {
            // 原作 flashlight 的 ItemContainer 只收 mobilebattery（电池 cell）。
            return candidate.getItem() instanceof BatteryCellItem;
        }
        return false;
    }

    /** 空子格上画的两字符提示。 */
    public static String hint(ItemStack host) {
        if (host.getItem() instanceof DivingSuitItem) {
            return "O2";
        }
        if (host.getItem() instanceof DivingSuitItem
                || host.getItem() instanceof com.opamb.barotrauma.item.PlasmaCutterItem) {
            return "O2";
        }
        if (host.getItem() instanceof WeldingToolItem) {
            return "FUEL";
        }
        if (host.getItem() instanceof FlashlightItem) {
            return "BAT";
        }
        return "";
    }
}
