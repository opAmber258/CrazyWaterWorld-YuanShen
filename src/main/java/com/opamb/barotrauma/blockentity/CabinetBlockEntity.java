package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.CabinetBlock;
import com.opamb.barotrauma.menu.CabinetMenu;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 钢柜 / 货架 / 潜水服柜 / 氧气瓶架的方块实体。
 *
 * <p>容器内容用标准 {@link Container}，菜单同步走 {@link CabinetMenu}；
 * 槽位数量与可放物品由 {@link CabinetBlock.Kind} 决定。</p>
 */
public class CabinetBlockEntity extends BlockEntity implements Container, MenuProvider, GeoBlockEntity {

    private final NonNullList<ItemStack> items;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CabinetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABINET.get(), pos, state);
        this.items = NonNullList.withSize(kind().slots(), ItemStack.EMPTY);
    }

    public CabinetBlock.Kind kind() {
        return getBlockState().getBlock() instanceof CabinetBlock cabinet
                ? cabinet.kind() : CabinetBlock.Kind.MEDIUM_STEEL_CABINET;
    }

    // ------------------------------------------------------------------ 容器

    @Override
    public int getContainerSize() {
        return items.size();
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
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (kind()) {
            case DIVING_SUIT_LOCKER -> stack.is(ModItems.DIVING_SUIT.get());
            case OXYGEN_TANK_SHELF -> stack.is(ModItems.OXYGEN_TANK.get());
            case DIVING_SUIT_STATION -> slot == 0
                    ? stack.is(ModItems.DIVING_SUIT.get())
                    : stack.is(ModItems.OXYGEN_TANK.get());
            default -> true;
        };
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
    }

    // ------------------------------------------------------------------ 菜单 / 渲染

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.barotrauma." + kind().id());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CabinetMenu(id, inventory, worldPosition);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型，无动画控制器。
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + kind().height() / 16.0D + 0.25D, pos.getZ() + 1.0D)
                .inflate(1.0D, 0.5D, 1.0D);
    }

    // ------------------------------------------------------------------ 存档

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
    }
}
