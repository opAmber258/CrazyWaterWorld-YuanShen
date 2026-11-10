package com.opamb.barotrauma.mixin;

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
 * 把原版玩家背包菜单改造成潜渊症装备栏：
 *
 * <ul>
 *   <li>5..8（原四个护甲槽）→ ID 卡、耳机、头盔、身体护甲；</li>
 *   <li>45（原副手槽）→ 腰带/背包；</li>
 *   <li>追加 46/47 → 潜水服、氧气瓶。</li>
 * </ul>
 *
 * <p>槽对象直接换成 {@link EquipmentSlot}，菜单下标不变，原版 0..44 的
 * 合成/背包/快捷栏逻辑不用改。快速移动（Shift 点击）由本 mixin 接管，
 * 先尝试放入匹配的装备槽，再走原版的背包↔快捷栏搬运。</p>
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
        boolean movedAny = false;

        if (index == 0) {
            // 合成结果：和原版一样进背包，并触发 onQuickCraft。
            movedAny = accessor.barotrauma$moveItemStackTo(stack, 9, 45, true);
            if (!movedAny) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index < 5) {
            // 2×2 合成输入。
            movedAny = accessor.barotrauma$moveItemStackTo(stack, 9, 45, false);
            if (!movedAny) {
                return ItemStack.EMPTY;
            }
        } else if (EquipmentSlotType.byMenuIndex(index) != null) {
            // 从装备槽拿出：只进背包/快捷栏。
            movedAny = accessor.barotrauma$moveItemStackTo(stack, 9, 45, false);
            if (!movedAny) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包/快捷栏 → 先试装备槽，再走原版搬运。
            int target = EquipmentSlots.findMatchingEmptySlotMenuIndex(menu.slots, stack);
            if (target >= 0) {
                movedAny = accessor.barotrauma$moveItemStackTo(stack, target, target + 1, false);
            }
            if (!stack.isEmpty()) {
                boolean intoHotbar = index >= 9 && index < 36;
                boolean intoMain = index >= 36 && index < 45;
                boolean moved;
                if (intoHotbar) {
                    moved = accessor.barotrauma$moveItemStackTo(stack, 36, 45, false);
                } else if (intoMain) {
                    moved = accessor.barotrauma$moveItemStackTo(stack, 9, 36, false);
                } else {
                    moved = accessor.barotrauma$moveItemStackTo(stack, 9, 45, false);
                }
                movedAny |= moved;
            }
            if (!movedAny) {
                return ItemStack.EMPTY;
            }
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
