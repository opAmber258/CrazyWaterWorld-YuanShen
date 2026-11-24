package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.LightBlockEntity;
import com.opamb.barotrauma.network.RepairActionPacket;
import com.opamb.barotrauma.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 灯具面板：一盏灯的原版 RGBA（RGB + A 强度）与开/关。
 *
 * <p>没有物品槽；改动走 {@code LightColorPacket}，数据位把当前
 * RGB/强度/开关/是否有电回传给客户端面板。</p>
 */
public class LightMenu extends AbstractContainerMenu {

    /** 数据位 0：0xRRGGBB。 */
    public static final int DATA_COLOR = 0;
    /** 数据位 1：A 发光强度 0–255（原作 LightColor.A）。 */
    public static final int DATA_ALPHA = 1;
    /** 数据位 2：开/关（0/1，用户请求）。 */
    public static final int DATA_LIT = 2;
    /** 数据位 3：是否还有光（0/1）。 */
    public static final int DATA_POWERED = 3;
    public static final int DATA_COUNT = 4;

    private final BlockPos lightPos;
    private final ContainerData data;
    /** 构造时解析到的灯具实例（仅服务端非 null）；用来防止同位置换灯后写到新灯上。 */
    private final LightBlockEntity light;

    public LightMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf == null ? BlockPos.ZERO : buf.readBlockPos());
    }

    public LightMenu(int id, Inventory inventory, BlockPos lightPos) {
        super(ModMenus.LIGHT.get(), id);
        this.lightPos = lightPos;
        LightBlockEntity resolved = null;
        if (!inventory.player.level().isClientSide
                && inventory.player.level() instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(lightPos) instanceof LightBlockEntity entity) {
            resolved = entity;
        }
        this.light = resolved;
        if (resolved != null) {
            LightBlockEntity target = resolved;
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_COLOR -> target.color();
                        case DATA_ALPHA -> target.alpha();
                        case DATA_LIT -> target.lit() ? 1 : 0;
                        case DATA_POWERED -> target.powered() ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // 服务端数据只由面板按钮改。
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            // 客户端在服务端数据到位前给一盏原版默认白灯（白 α0.5）。
            SimpleContainerData fallback = new SimpleContainerData(DATA_COUNT);
            fallback.set(DATA_COLOR, 0xFFFFFF);
            fallback.set(DATA_ALPHA, 128);
            fallback.set(DATA_LIT, 1);
            fallback.set(DATA_POWERED, 1);
            this.data = fallback;
        }
        addDataSlots(data);
    }

    public ContainerData data() {
        return data;
    }

    public BlockPos lightPos() {
        return lightPos;
    }

    @Override
    public boolean stillValid(Player player) {
        if (lightPos.equals(BlockPos.ZERO)) {
            return false;
        }
        Vec3 center = RepairActionPacket.worldCenter(player.level(), lightPos);
        if (center == null || player.distanceToSqr(center) > 64.0D) {
            return false;
        }
        if (player.level().isClientSide) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return light != null && !light.isRemoved()
                && serverLevel.getBlockEntity(lightPos) == light;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
