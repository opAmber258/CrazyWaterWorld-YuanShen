package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 医疗 Capability 的提供者：持有容器实例，负责 NBT 读写。
 *
 * <p>NBT 格式：{@code { afflictions: ["blunttrauma|LEFT_LEG|120.5", "bloodloss|75", ...] }}。
 * 行格式由 {@link AfflictionContainer#rows()} 定义（id|LIMB|strength，全身省略 limb）。</p>
 */
public final class MedicalProvider implements ICapabilitySerializable<CompoundTag> {

    /** 玩家实体存活期内只建一次；死亡后由克隆产生新实体=新空容器。 */
    private final AfflictionContainer container = new AfflictionContainer();
    private final LazyOptional<AfflictionContainer> optional = LazyOptional.of(() -> container);

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == MedicalCapabilities.MEDICAL ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (String row : container.rows()) {
            list.add(StringTag.valueOf(row));
        }
        tag.put("afflictions", list);
        tag.putDouble("downedDrain", container.downedDrainValue());
        tag.putBoolean("downed", container.downedState());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        container.clear();
        ListTag list = tag.getList("afflictions", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            container.loadRow(list.getString(i));
        }
        if (tag.contains("downedDrain")) {
            container.restoreDownedDrain(tag.getDouble("downedDrain"));
        }
        if (tag.contains("downed")) {
            container.restoreDownedState(tag.getBoolean("downed"));
        }
    }

    /** 玩家实体被丢弃时释放 LazyOptional（AttachCapabilitiesEvent 的标准姿势）。 */
    public void invalidate() {
        optional.invalidate();
    }
}
