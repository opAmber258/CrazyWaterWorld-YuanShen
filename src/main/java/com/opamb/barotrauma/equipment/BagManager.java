package com.opamb.barotrauma.equipment;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个玩家的“装备背包”容器管理。
 *
 * <p>工具带/背包占用同一个 {@link EquipmentSlotType#BAG} 槽：工具带 6 格、背包 12 格。
 * 容器内容存在背包物品的 NBT 里，所以换成另一件背包、把背包拿下来或丢掉时，
 * 内容都跟着物品走。</p>
 */
public final class BagManager {

    public static final int TOOLBELT_CAPACITY = 6;
    public static final int BACKPACK_CAPACITY = 12;

    /**
     * 单人存档里客户端和集成服务端同 JVM、玩家 UUID 也相同，
     * 必须按 side 分开存，否则两边的背包容器会串成同一个实例。
     */
    private static final Map<String, BagContainer> CONTAINERS = new ConcurrentHashMap<>();

    private BagManager() {
    }

    private static String key(Player player) {
        return (player.level().isClientSide ? "client:" : "server:") + player.getUUID();
    }

    public static BagContainer container(Player player) {
        return CONTAINERS.computeIfAbsent(key(player), id -> new BagContainer());
    }

    /** 玩家退出时清掉缓存，避免长期服务器上泄漏。 */
    public static void clear(Player player) {
        CONTAINERS.remove(key(player));
    }

    /** 这件背包物品提供多少格。 */
    public static int capacityOf(ItemStack bag) {
        if (bag == null || bag.isEmpty()) {
            return 0;
        }
        if (bag.is(EquipmentItems.BACKPACK.get())) {
            return BACKPACK_CAPACITY;
        }
        if (bag.is(EquipmentItems.TOOLBELT.get())) {
            return TOOLBELT_CAPACITY;
        }
        return 0;
    }

    /** 当前玩家装备的背包能提供多少格（客户端也能算，装备槽是同步的）。 */
    public static int capacity(Player player) {
        EquipmentInventory inventory = EquipmentData.get(player);
        if (inventory == null) {
            return 0;
        }
        return capacityOf(inventory.getStackInSlot(EquipmentSlotType.BAG));
    }

    /**
     * 服务端：装备槽里的背包变了就重新绑定。
     * 每 tick 调一次很便宜（只比较引用），换包当 tick 也会由
     * {@link EquipmentInventory} 的 bag 监听立刻触发，避免“换包后马上点击”丢东西。
     */
    public static void bindFromEquipment(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        EquipmentInventory inventory = EquipmentData.get(player);
        BagContainer container = container(player);
        ItemStack bag = inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(EquipmentSlotType.BAG);
        int capacity = capacityOf(bag);
        if (bag.isEmpty() && container.boundStack().isEmpty()) {
            return;
        }
        if (!bag.isEmpty() && container.boundStack() == bag && container.capacity() == capacity) {
            return;
        }
        container.bind(bag, capacity);
    }

    /**
     * 服务端拾取/添加：先合并进背包与热键栏，再找空位。放不下返回 false（物品留在原地）。
     */
    public static boolean insert(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        int before = stack.getCount();
        BagContainer bag = container(player);
        var hotbar = player.getInventory();
        // 背包类物品不能塞进背包（原作 toolbelt/backpack 也互相装不进去），只走热键栏。
        boolean canUseBag = !stack.is(EquipmentTags.BAG);

        // 1) 先合并到已有堆
        if (canUseBag) {
            for (int i = 0; i < bag.capacity() && !stack.isEmpty(); i++) {
                mergeInto(bag, i, stack);
            }
        }
        for (int i = 0; i < 9 && !stack.isEmpty(); i++) {
            mergeInto(hotbar, i, stack);
        }
        // 2) 再找空位：背包 → 热键栏
        if (canUseBag) {
            for (int i = 0; i < bag.capacity() && !stack.isEmpty(); i++) {
                if (bag.getItem(i).isEmpty()) {
                    int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
                    bag.setItem(i, stack.split(moved));
                }
            }
        }
        for (int i = 0; i < 9 && !stack.isEmpty(); i++) {
            if (hotbar.getItem(i).isEmpty()) {
                int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
                hotbar.setItem(i, stack.split(moved));
            }
        }
        return stack.getCount() < before;
    }

    /**
     * 服务端：放进指定的背包格（对应原版 {@code Inventory.add(slot, stack)} 的 9..35）。
     * 返回是否放进了一部分。
     */
    public static boolean insertAt(Player player, int bagIndex, ItemStack stack) {
        BagContainer bag = container(player);
        if (bagIndex < 0 || bagIndex >= bag.capacity() || stack.isEmpty()
                || stack.is(EquipmentTags.BAG)) {
            return false;
        }
        ItemStack existing = bag.getItem(bagIndex);
        if (existing.isEmpty()) {
            int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
            bag.setItem(bagIndex, stack.split(moved));
            return true;
        }
        int before = stack.getCount();
        mergeInto(bag, bagIndex, stack);
        return stack.getCount() < before;
    }

    private static void mergeInto(net.minecraft.world.Container container, int slot, ItemStack stack) {        ItemStack existing = container.getItem(slot);
        if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, stack) || !existing.isStackable()) {
            return;
        }
        int limit = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
        int room = limit - existing.getCount();
        if (room <= 0) {
            return;
        }
        int moved = Math.min(room, stack.getCount());
        existing.grow(moved);
        stack.shrink(moved);
        container.setItem(slot, existing);
    }

    /** 背包里的所有非空物品（任务计数/医疗取药扫包用）。 */
    public static List<ItemStack> contents(Player player) {
        BagContainer container = container(player);
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    /** 外部直接改了背包物品堆（如药品消耗）后，把内容写回背包 NBT。 */
    public static void commit(Player player) {
        container(player).commit();
    }

    /** 从装备背包里拿走 1 个匹配的物品（任务交付用）。 */
    public static boolean removeOne(Player player, java.util.function.Predicate<ItemStack> match) {
        BagContainer bag = container(player);
        for (int i = 0; i < bag.capacity(); i++) {
            ItemStack stack = bag.getItem(i);
            if (stack.isEmpty() || !match.test(stack)) {
                continue;
            }
            stack.shrink(1);
            bag.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
            return true;
        }
        return false;
    }
}
