package com.opamb.barotrauma.equipment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 读取/迁移玩家装备数据的入口。
 *
 * <p>{@link #getOrCreate(Player)} 是给菜单/效果用的稳定入口：正常情况下直接返回
 * Capability 里的容器；如果 {@code InventoryMenu} 比 Capability 先创建（例如某些
 * 加载顺序或兼容层），则先建一个 pending 容器给菜单槽用，
 * {@code AttachCapabilitiesEvent} 再把这个同一实例接管进 {@link EquipmentProvider}。
 * 这样菜单槽、Capability、Clone、登录迁移永远指向同一个 {@link EquipmentInventory}。</p>
 */
public final class EquipmentData {

    /** 菜单先于 Capability 创建时的临时容器；key 是弱引用，玩家回收后自动消失。 */
    private static final Map<Player, EquipmentInventory> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());

    private EquipmentData() {
    }

    /** 取玩家装备容器；Capability 尚未挂载时返回 null（只读场景可用）。 */
    public static EquipmentInventory get(Player player) {
        return player.getCapability(EquipmentCapabilities.EQUIPMENT).orElse(null);
    }

    /**
     * 取玩家装备容器，没有就创建一个 pending 容器。
     * 之后 {@code AttachCapabilitiesEvent} 会通过 {@link #takePending(Player)} 接管它。
     */
    public static EquipmentInventory getOrCreate(Player player) {
        EquipmentInventory existing = get(player);
        if (existing != null) {
            return existing;
        }
        synchronized (PENDING) {
            // 拿到锁后再查一次：capability 可能刚好在第一次 get 之后挂上。
            EquipmentInventory attached = get(player);
            if (attached != null) {
                return attached;
            }
            // 与 takePending 用同一把锁：要么我们先放 pending 再被接管，
            // 要么 attach 已经完成、上面这次 get 直接返回它。
            return PENDING.computeIfAbsent(player, p -> new EquipmentInventory());
        }
    }

    /** 把 pending 容器交给新挂载的 Capability；没有则返回 null。 */
    public static EquipmentInventory takePending(Player player) {
        synchronized (PENDING) {
            return PENDING.remove(player);
        }
    }

    public static ItemStack getStack(Player player, EquipmentSlotType type) {
        return getOrCreate(player).getStackInSlot(type);
    }

    /** 潜水服是否穿在新潜水服槽里（旧胸甲槽的兼容在 ShipPressure 里双读）。 */
    public static boolean hasDivingSuit(Player player) {
        ItemStack stack = getStack(player, EquipmentSlotType.DIVING_SUIT);
        return !stack.isEmpty() && stack.is(EquipmentTags.DIVING_SUIT);
    }

    /** 头盔 + 身体护甲提供的护甲点数（只统计本模组的 EquipmentItem）。 */
    public static int armorPoints(Player player) {
        EquipmentInventory inventory = getOrCreate(player);
        int points = 0;
        for (EquipmentSlotType type : new EquipmentSlotType[]{
                EquipmentSlotType.HELMET, EquipmentSlotType.BODY_ARMOR}) {
            ItemStack stack = inventory.getStackInSlot(type);
            if (!stack.isEmpty() && stack.getItem() instanceof EquipmentItem item
                    && item.slotType() == type) {
                points += item.armorPoints();
            }
        }
        return points;
    }

    /**
     * 旧存档迁移：原版护甲槽/副手槽在新界面里已经不存在，登录时把里面的东西
     * 搬走——能进装备槽的进装备槽，剩下的进背包，背包满了就掉在脚下。
     *
     * <p>只在服务端 {@code PlayerLoggedInEvent} 调一次/每次登录调；清空后的槽位
     * 不会再被原版逻辑填充，所以重复调用是安全的。</p>
     */
    public static void migrateLegacy(ServerPlayer player) {
        EquipmentInventory equipment = getOrCreate(player);
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.armor.size(); i++) {
            ItemStack stack = inventory.armor.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remaining = moveIntoEquipment(equipment, stack);
            inventory.armor.set(i, ItemStack.EMPTY);
            storeOrDrop(player, inventory, remaining);
        }

        ItemStack offhand = inventory.offhand.get(0);
        if (!offhand.isEmpty()) {
            ItemStack remaining = moveIntoEquipment(equipment, offhand);
            inventory.offhand.set(0, ItemStack.EMPTY);
            storeOrDrop(player, inventory, remaining);
        }
    }

    private static ItemStack moveIntoEquipment(EquipmentInventory equipment, ItemStack stack) {
        ItemStack remaining = stack.copy();
        int slot = EquipmentSlots.findMatchingEmptySlot(equipment, remaining);
        if (slot >= 0) {
            EquipmentSlotType type = EquipmentSlotType.byIndex(slot);
            if (type != null) {
                equipment.setStackInSlot(type, remaining.copyWithCount(1));
                remaining.shrink(1);
            }
        }
        return remaining;
    }

    private static void storeOrDrop(ServerPlayer player, Inventory inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!inventory.add(stack)) {
            player.drop(stack, false);
        }
    }
}
