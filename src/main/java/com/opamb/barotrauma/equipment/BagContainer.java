package com.opamb.barotrauma.equipment;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 工具带/背包提供的 12 格容器。
 *
 * <p>服务端把它绑定到装备槽里那件背包 {@link ItemStack}：内容读写直接落到物品 NBT
 * （{@code ContainerHelper} 的 {@code Items} 列表），所以背包被拿下来/丢掉时内容跟着物品走。
 * 客户端只把它当显示容器，内容由原版槽位包同步。</p>
 */
public class BagContainer implements Container {

    public static final int SLOTS = BagLayout.MAX_SLOTS;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    /** 服务端：当前绑定的背包物品；客户端始终为空。 */
    private ItemStack bound = ItemStack.EMPTY;
    /** 当前可用的格子数（工具带 6 / 背包 12 / 没装备 0）。 */
    private int capacity;

    public int capacity() {
        return capacity;
    }

    public ItemStack boundStack() {
        return bound;
    }

    /** 服务端：绑定到装备槽里的背包，把 NBT 内容读出来。 */
    public void bind(ItemStack bag, int capacity) {
        this.items.clear();
        this.capacity = Math.max(0, Math.min(SLOTS, capacity));
        this.bound = bag == null || bag.isEmpty() ? ItemStack.EMPTY : bag;
        if (!this.bound.isEmpty() && this.bound.getTag() != null) {
            ContainerHelper.loadAllItems(this.bound.getTag(), this.items);
        }
    }

    /** 内容变化立刻写回物品 NBT，保证背包被拿走时东西跟着走。 */
    public void commit() {
        save();
    }

    private void save() {
        if (bound.isEmpty()) {
            return;
        }
        CompoundTag tag = bound.getOrCreateTag();
        ContainerHelper.saveAllItems(tag, items);
        if (isEmpty()) {
            tag.remove("Items");
        }
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
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
        return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
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
        if (slot < 0 || slot >= SLOTS) {
            return;
        }
        // 服务端已绑定背包时，超出容量的槽不许写；客户端（未绑定）要接收槽位包。
        if (!bound.isEmpty() && slot >= capacity) {
            return;
        }
        items.set(slot, stack);
        save();
    }

    @Override
    public void setChanged() {
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

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < capacity && !stack.is(EquipmentTags.BAG);
    }
}
