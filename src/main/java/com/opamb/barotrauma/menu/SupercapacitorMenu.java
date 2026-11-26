package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.SupercapacitorBlockEntity;
import com.opamb.barotrauma.registry.ModMenus;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 超级电容器面板：电量、充电速率、耐久。
 *
 * <p>和电池面板同一套布局，但没有电池 cell 充电槽。滑条 0..100 对应
 * 0..{@link SupercapacitorBlockEntity#MAX_RECHARGE_KW}（1000 kW）。</p>
 *
 * <p>物理船上右键由 {@code ShipUseBlockPacket} 在船坞方块上调用 {@code use}，
 * 所以菜单拿到的是船坞坐标；同时服务端把世界锚点写进 buf，客户端
 * {@link #stillValid} 用锚点判距离，不依赖客户端没有的 VsPilot 变换。</p>
 */
public class SupercapacitorMenu extends DeviceMenu {

    /** 数据位 0：电量（kW·min × 10）。 */
    public static final int DATA_CHARGE = 0;
    /** 数据位 1：容量（kW·min × 10）。 */
    public static final int DATA_CAPACITY = 1;
    /** 数据位 2：充电速率（kW）。 */
    public static final int DATA_RECHARGE = 2;
    /** 数据位 3：耐久 × 10。 */
    public static final int DATA_CONDITION = 3;
    /** 数据位 4：是否绑定接线盒（0/1）。 */
    public static final int DATA_BOUND = 4;

    public static final int DATA_COUNT = 5;

    /** 这面板没有设备槽，玩家背包从 0 开始。 */
    public static final int INV_START = 0;
    public static final int HOTBAR_START = INV_START + 27;
    public static final int HOTBAR_END = HOTBAR_START + 9;
    public static final int INV_AREA_X = 12;
    public static final int INV_AREA_Y = 206;
    public static final int INV_AREA_W = 160;
    public static final int INV_AREA_H = 54;

    private final BlockPos capacitorPos;
    private final Vec3 anchor;
    private final ContainerData data;
    /** 构造时解析到的电容实例（仅服务端非 null），用于校验菜单还绑着同一台设备。 */
    private final SupercapacitorBlockEntity capacitor;

    public SupercapacitorMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory,
                buf == null ? BlockPos.ZERO : buf.readBlockPos(),
                buf == null ? null : new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public SupercapacitorMenu(int id, Inventory inventory, BlockPos capacitorPos, Vec3 anchor) {
        super(ModMenus.SUPERCAPACITOR.get(), id, inventory, capacitorPos);
        this.capacitorPos = capacitorPos;
        this.anchor = anchor;
        SupercapacitorBlockEntity resolved = null;
        if (!inventory.player.level().isClientSide
                && inventory.player.level() instanceof ServerLevel serverLevel) {
            resolved = find(serverLevel, capacitorPos);
        }
        this.capacitor = resolved;
        if (resolved != null) {
            SupercapacitorBlockEntity cap = resolved;
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_CHARGE -> (int) Math.round(cap.storedKwm() * 10.0D);
                        case DATA_CAPACITY -> (int) Math.round(cap.capacityKwm() * 10.0D);
                        case DATA_RECHARGE -> (int) Math.round(cap.rechargeKw());
                        case DATA_CONDITION -> (int) Math.round(cap.condition() * 10.0D);
                        case DATA_BOUND -> cap.storageBox() == null ? 0 : 1;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // 服务端数据只由滑条按钮改，原版同步包不写这里。
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            SimpleContainerData fallback = new SimpleContainerData(DATA_COUNT);
            fallback.set(DATA_CAPACITY,
                    (int) Math.round(SupercapacitorBlockEntity.CAPACITY_KWM * 10.0D));
            fallback.set(DATA_RECHARGE,
                    (int) Math.round(SupercapacitorBlockEntity.MAX_RECHARGE_KW));
            fallback.set(DATA_CONDITION, 1000);
            this.data = fallback;
        }
        addDataSlots(data);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new net.minecraft.world.inventory.Slot(inventory,
                        col + row * 9 + 9,
                        INV_AREA_X + col * 18, INV_AREA_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new net.minecraft.world.inventory.Slot(inventory, col,
                    INV_AREA_X + col * 18, INV_AREA_Y + 58));
        }
        com.opamb.barotrauma.equipment.BagSlots.installAt(this, inventory.player, INV_START,
                INV_AREA_X, INV_AREA_Y, INV_AREA_W, INV_AREA_H);
        addSlot(new com.opamb.barotrauma.equipment.HandsSlot(inventory,
                com.opamb.barotrauma.equipment.HandsSlot.CONTAINER_X,
                com.opamb.barotrauma.equipment.HandsSlot.containerY(INV_AREA_Y)));
    }

    /** 世界坐标/船坞坐标都试一下，找到真正的电容实体。 */
    private static SupercapacitorBlockEntity find(ServerLevel level, BlockPos pos) {
        BlockEntity direct = level.getBlockEntity(pos);
        if (direct instanceof SupercapacitorBlockEntity cap) {
            return cap;
        }
        if (VsState.active()) {
            BlockPos yard = VsPilot.toYard(pos);
            if (yard != null && level.getBlockEntity(yard) instanceof SupercapacitorBlockEntity cap) {
                return cap;
            }
            BlockPos assembly = VsPilot.toAssembly(pos);
            if (assembly != null
                    && level.getBlockEntity(assembly) instanceof SupercapacitorBlockEntity cap) {
                return cap;
            }
        }
        return null;
    }

    public ContainerData data() {
        return data;
    }

    public BlockPos capacitorPos() {
        return capacitorPos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id > 100 || player.level().isClientSide) {
            return false;
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            SupercapacitorBlockEntity target = capacitor != null ? capacitor
                    : find(serverLevel, capacitorPos);
            if (target != null) {
                target.setRechargeKw(SupercapacitorBlockEntity.MAX_RECHARGE_KW * id / 100.0D);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        if (capacitorPos.equals(BlockPos.ZERO)) {
            return false;
        }
        Vec3 center = anchor != null ? anchor : Vec3.atCenterOf(capacitorPos);
        if (player.distanceToSqr(center) > 64.0D) {
            return false;
        }
        if (player.level().isClientSide) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return capacitor != null && !capacitor.isRemoved()
                && find(level, capacitorPos) == capacitor;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        net.minecraft.world.inventory.Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int bagStart = INV_START;
        int bagEnd = bagStart + com.opamb.barotrauma.equipment.BagManager.capacity(player);
        boolean moved;
        if (index >= HOTBAR_START) {
            moved = moveItemStackTo(stack, bagStart, bagEnd, false);
        } else {
            moved = moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
