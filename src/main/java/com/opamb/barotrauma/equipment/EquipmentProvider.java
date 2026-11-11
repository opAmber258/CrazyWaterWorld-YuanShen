package com.opamb.barotrauma.equipment;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 挂在玩家实体上的装备 Capability 提供者。
 *
 * <p>实现 {@link ICapabilitySerializable} 后，Forge 会把它写进玩家存档的
 * {@code ForgeCaps} 标签；死亡/换维度克隆由 {@link EquipmentEvents} 手动复制。</p>
 */
public class EquipmentProvider implements ICapabilityProvider, ICapabilitySerializable<CompoundTag> {

    private final EquipmentInventory inventory;
    private final LazyOptional<EquipmentInventory> optional;

    public EquipmentProvider() {
        this(new EquipmentInventory());
    }

    /**
     * 采用外部传入的容器。用于 {@code InventoryMenu} 比 capability 先创建时，
     * 把菜单槽已经包着的 pending 容器接管过来，保证菜单/存档/克隆是同一个实例。
     */
    public EquipmentProvider(EquipmentInventory inventory) {
        this.inventory = inventory;
        this.optional = LazyOptional.of(() -> inventory);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        return capability == EquipmentCapabilities.EQUIPMENT ? this.optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return this.inventory.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.inventory.deserializeNBT(nbt);
    }
}
