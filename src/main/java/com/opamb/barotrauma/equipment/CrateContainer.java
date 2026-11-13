package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.item.CrateItem;
import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 货运箱的内部容器：内容存在箱子物品自己的 NBT（{@code Crate.Items}）里。
 *
 * <p>和 {@link BagContainer} 一个思路——容器只是物品 NBT 的视图，
 * 所以箱子被搬上货架、放进背包、丢到地上时，里面的东西都跟着走。
 * 所有改写在 {@link #setItem}/{@link #setChanged} 里立刻落盘。</p>
 */
public class CrateContainer implements Container {

    /** 箱子物品 NBT 里存内容的子标签（里面再用 ContainerHelper 的 {@code Items} 列表）。 */
    public static final String TAG = "Crate";

    private final ItemStack host;
    private final int size;
    private final NonNullList<ItemStack> items;

    public CrateContainer(ItemStack host, int size) {
        this.host = host;
        this.size = Math.max(0, size);
        this.items = NonNullList.withSize(this.size, ItemStack.EMPTY);
        if (!host.isEmpty() && host.hasTag()) {
            CompoundTag tag = host.getTag();
            if (tag != null && tag.contains(TAG, Tag.TAG_COMPOUND)) {
                ContainerHelper.loadAllItems(tag.getCompound(TAG), items);
            }
        }
    }

    /** 宿主物品还在不在（物品被移走/替换后容器就不该再写）。 */
    public boolean valid() {
        return !host.isEmpty() && host.getItem() instanceof CrateItem crate && crate.capacity() == size;
    }

    private void save() {
        if (host.isEmpty()) {
            return;
        }
        CompoundTag tag = host.getOrCreateTagElement(TAG);
        ContainerHelper.saveAllItems(tag, items);
        if (isEmpty()) {
            host.getTag().remove(TAG);
        }
    }

    @Override
    public int getContainerSize() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < size ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            save();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        save();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= size) {
            return;
        }
        items.set(slot, stack);
        save();
    }

    @Override
    public void setChanged() {
        // 原版合并（ItemStack.grow/shrink）只调 setChanged，必须落盘。
        save();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
        save();
    }

    /**
     * 原作 {@code Containable items="smallitem,mediumitem"}：箱子/工具带/背包这类
     * 大件或移动容器装不进去（箱子之间也不能互相套）。
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < size
                && !TwoHanded.is(stack)
                && !stack.is(EquipmentTags.BAG);
    }
}
