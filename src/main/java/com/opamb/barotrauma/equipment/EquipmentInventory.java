package com.opamb.barotrauma.equipment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家身上的七个装备槽（持久化在玩家 Capability 里）。
 *
 * <p>每个槽只放一件：{@link #setItem(int, ItemStack)} 会复制并压到 1 个，
 * 避免快捷移动/命令/NBT 塞进堆叠物品。槽位接受规则见 {@link EquipmentSlots}。</p>
 *
 * <p>同步方式：这些槽被 {@code InventoryMenu} 的自定义 {@code Slot} 包着，
 * 服务端每 tick 的 {@code AbstractContainerMenu#broadcastChanges()} 会把变化
 * 通过原版槽位包发给客户端，不需要另写网络包。NBT 由
 * {@link EquipmentProvider} 随玩家存档保存。</p>
 */
public class EquipmentInventory extends SimpleContainer {

    public static final int SIZE = EquipmentSlotType.values().length;
    private static final String ITEMS_TAG = "Items";
    private static final String SLOT_TAG = "Slot";

    /** 腰带槽（背包/工具带）变化时通知外层重新绑定 BagContainer。 */
    private java.util.function.Consumer<ItemStack> bagListener = stack -> {
    };

    public EquipmentInventory() {
        super(SIZE);
    }

    /** 设置腰带槽变化回调（通常是 BagManager 的绑定逻辑）。 */
    public void setBagListener(java.util.function.Consumer<ItemStack> listener) {
        this.bagListener = listener == null ? stack -> {
        } : listener;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return EquipmentSlots.accepts(slot, stack);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        ItemStack copy = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!copy.isEmpty()) {
            copy.setCount(1);
            stripLegacyDamage(copy);
        }
        super.setItem(slot, copy);
        if (slot == EquipmentSlotType.BAG.slotIndex()) {
            bagListener.accept(copy);
        }
    }

    /**
     * 潜水服从 2026-11-11 起不再是可损耗的 {@code ArmorItem}（原作没有护甲值/耐久），
     * 旧存档里的 {@code Damage} 标记没有意义，留着会让物品栏按 maxDamage=0 画出错误耐久条。
     */
    static void stripLegacyDamage(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof com.opamb.barotrauma.item.DivingSuitItem) {
            stack.removeTagKey("Damage");
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /** 读某个装备槽；越界返回空。 */
    public ItemStack getStackInSlot(EquipmentSlotType type) {
        return this.getItem(type.slotIndex());
    }

    /** 写某个装备槽；越界忽略。 */
    public void setStackInSlot(EquipmentSlotType type, ItemStack stack) {
        this.setItem(type.slotIndex(), stack);
    }

    /** 装备容器 → NBT。 */
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = this.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag itemTag = new CompoundTag();
            itemTag.putByte(SLOT_TAG, (byte) i);
            stack.save(itemTag);
            list.add(itemTag);
        }
        tag.put(ITEMS_TAG, list);
        return tag;
    }

    /** NBT → 装备容器。非法槽位下标直接丢弃，避免坏存档崩游戏。 */
    public void deserializeNBT(CompoundTag tag) {
        this.clearContent();
        ListTag list = tag.getList(ITEMS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getByte(SLOT_TAG) & 0xFF;
            if (slot >= SIZE) {
                continue;
            }
            ItemStack stack = ItemStack.of(itemTag);
            if (!stack.isEmpty()) {
                stack.setCount(1);
                stripLegacyDamage(stack);
                super.setItem(slot, stack);
            }
        }
        // 读档完成后腰带槽可能已经有背包，通知外层绑定内容。
        bagListener.accept(getStackInSlot(EquipmentSlotType.BAG));
    }

    /** 死亡/换维度克隆时复制（装备不随死亡掉落）。 */
    public void copyFrom(EquipmentInventory other) {
        for (int i = 0; i < SIZE; i++) {
            this.setItem(i, other.getItem(i).copy());
        }
    }
}
