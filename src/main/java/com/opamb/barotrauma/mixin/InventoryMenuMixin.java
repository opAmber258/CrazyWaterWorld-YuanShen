package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.equipment.BagManager;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.equipment.EquipmentData;
import com.opamb.barotrauma.equipment.EquipmentInventory;
import com.opamb.barotrauma.equipment.EquipmentSlot;
import com.opamb.barotrauma.equipment.EquipmentSlotType;
import com.opamb.barotrauma.equipment.EquipmentSlots;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把原版玩家背包菜单改造成潜渊症装备栏 + 装备背包：
 *
 * <ul>
 *   <li>5..8（原四个护甲槽）→ ID 卡、耳机、头盔、身体护甲；</li>
 *   <li>45（原副手槽）→ 腰带/背包；46 → 潜水服（内部子格放氧气瓶）；</li>
 *   <li>9..20 → 装备背包提供的格子（工具带 6 / 背包 12，没装备时全部隐藏）；</li>
 *   <li>21..35 → 禁用占位（保留下标，避免 36..46 整体错位）。</li>
 * </ul>
 *
 * <p>菜单下标不变，原版 0..4 合成、36..44 热键栏逻辑不用改。快速移动由本 mixin
 * 接管：目标只可能是装备槽、装备背包、热键栏，绝不会进 21..35。</p>
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void barotrauma$installEquipmentSlots(Inventory inventory, boolean active,
                                                  Player owner, CallbackInfo ci) {
        // 正常情况下 capability 在 Entity 构造器里已挂载；万一菜单先建，
        // getOrCreate 会返回一个 pending 容器，AttachCapabilitiesEvent 再接管它。
        EquipmentInventory equipment = EquipmentData.getOrCreate(owner);
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) (Object) this;
        for (EquipmentSlotType type : EquipmentSlotType.values()) {
            EquipmentSlot slot = new EquipmentSlot(equipment, type.slotIndex(), type.x(), type.y(), type);
            int menuIndex = type.menuIndex();
            if (menuIndex < menu.slots.size()) {
                menu.slots.set(menuIndex, slot);
                slot.index = menuIndex;
            } else {
                accessor.barotrauma$addSlot(slot);
            }
        }
        // 原版 27 格主背包 → 装备背包 + 禁用占位。
        BagSlots.install(menu, owner);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void barotrauma$quickMoveStack(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (menu.slots.size() <= 5 || !(menu.slots.get(5) instanceof EquipmentSlot)) {
            // 没有装上装备槽时保持原版行为。
            return;
        }
        cir.setReturnValue(this.barotrauma$quickMove(menu, player, index));
    }

    private ItemStack barotrauma$quickMove(AbstractContainerMenu menu, Player player, int index) {
        if (index < 0 || index >= menu.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = menu.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) (Object) this;

        int bagCapacity = BagManager.capacity(player);
        int bagStart = BagSlots.START;
        int bagEnd = bagStart + bagCapacity;
        int hotbarStart = 36;
        int hotbarEnd = 45;

        boolean movedAny;
        if (index == 0) {
            // 合成结果：背包 → 热键栏，并触发 onQuickCraft。
            movedAny = accessor.barotrauma$moveItemStackTo(stack, bagStart, bagEnd, false)
                    || accessor.barotrauma$moveItemStackTo(stack, hotbarStart, hotbarEnd, false);
            if (!movedAny) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index < 5) {
            // 2×2 合成输入。
            movedAny = accessor.barotrauma$moveItemStackTo(stack, bagStart, bagEnd, false)
                    || accessor.barotrauma$moveItemStackTo(stack, hotbarStart, hotbarEnd, false);
            if (!movedAny) {
                return ItemStack.EMPTY;
            }
        } else if (EquipmentSlotType.byMenuIndex(index) != null) {
            // 从装备槽拿出：只进背包/热键栏。
            movedAny = accessor.barotrauma$moveItemStackTo(stack, bagStart, bagEnd, false)
                    || accessor.barotrauma$moveItemStackTo(stack, hotbarStart, hotbarEnd, false);
            if (!movedAny) {
                return ItemStack.EMPTY;
            }
        } else if (index >= bagStart && index < bagEnd) {
            // 背包 → 装备槽，再进热键栏。
            int target = EquipmentSlots.findMatchingEmptySlotMenuIndex(menu.slots, stack);
            movedAny = target >= 0
                    && accessor.barotrauma$moveItemStackTo(stack, target, target + 1, false);
            if (!stack.isEmpty()) {
                movedAny |= accessor.barotrauma$moveItemStackTo(stack, hotbarStart, hotbarEnd, false);
            }
            if (!movedAny) {
                return ItemStack.EMPTY;
            }
        } else if (index >= hotbarStart && index < hotbarEnd) {
            // 热键栏 → 装备槽，再进装备背包。
            int target = EquipmentSlots.findMatchingEmptySlotMenuIndex(menu.slots, stack);
            movedAny = target >= 0
                    && accessor.barotrauma$moveItemStackTo(stack, target, target + 1, false);
            if (!stack.isEmpty()) {
                movedAny |= accessor.barotrauma$moveItemStackTo(stack, bagStart, bagEnd, false);
            }
            if (!movedAny) {
                return ItemStack.EMPTY;
            }
        } else {
            // 21..35 禁用格：不处理。
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        if (index == 0) {
            player.drop(stack, false);
        }
        return original;
    }
}
