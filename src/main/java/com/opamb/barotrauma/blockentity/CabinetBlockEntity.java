package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.CabinetBlock;
import com.opamb.barotrauma.item.TwoHanded;
import com.opamb.barotrauma.menu.CabinetMenu;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
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

    /**
     * 箱子货架四层隔板的可用区间（模型像素，y=0 是主方块底面）：
     * 下面是隔板顶面，上面是再上一层隔板的底面，数值取自
     * {@code crate_shelf.geo.json}。服务端用它把点击高度换算成层号，
     * 客户端渲染器用同一份数据摆箱子。
     */
    public static final int CRATE_TIERS = 4;
    public static final float[] CRATE_TIER_BOTTOM = {1.6F, 8.9375F, 16.275F, 23.612F};
    public static final float[] CRATE_TIER_TOP = {8.037F, 15.375F, 22.712F, 30.05F};

    private final NonNullList<ItemStack> items;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CabinetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABINET.get(), pos, state);
        this.items = NonNullList.withSize(kind().slots(), ItemStack.EMPTY);
    }

    /**
     * 点击高度（相对主方块底面的像素）落在哪一层隔板。
     * 点在隔板本身、立柱或最上/最下的边框上时取最近的一层。
     */
    public static int crateTierAt(double localY) {
        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < CRATE_TIERS; i++) {
            if (localY >= CRATE_TIER_BOTTOM[i] && localY <= CRATE_TIER_TOP[i]) {
                return i;
            }
            double distance = Math.abs(localY - (CRATE_TIER_BOTTOM[i] + CRATE_TIER_TOP[i]) * 0.5D);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        return nearest;
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
            case CRATE_SHELF -> TwoHanded.is(stack);
            case DIVING_SUIT_LOCKER -> stack.is(ModItems.DIVING_SUIT.get());
            case OXYGEN_TANK_SHELF -> stack.is(ModItems.OXYGEN_TANK.get());
            case DIVING_SUIT_STATION -> slot == 0
                    ? stack.is(ModItems.DIVING_SUIT.get())
                    : stack.is(ModItems.OXYGEN_TANK.get());
            default -> true;
        };
    }

    // ------------------------------------------------------------------ 货架

    /** 货架上第一个空层，没有空层返回 -1。 */
    public int firstEmptyCrateSlot() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 货架内容变化要主动推给客户端：它不再打开菜单，物品列表没法靠
     * {@code Container#broadcastChanges} 同步，只能走方块实体更新包。
     */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        ContainerHelper.saveAllItems(tag, items);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
