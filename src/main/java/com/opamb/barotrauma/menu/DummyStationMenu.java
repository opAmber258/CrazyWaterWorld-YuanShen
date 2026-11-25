package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.TrainingDummyStationBlockEntity;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * 高级训练设施设置菜单：血量 / 自发光 / 重生间隔。
 *
 * <p>没有物品槽。数值通过 {@link ContainerData} 同步；真正修改走
 * {@code DummySettingsPacket}（客户端屏幕点“应用”时发送），避免客户端
 * 直接改服务端方块实体。</p>
 */
public class DummyStationMenu extends AbstractContainerMenu {

    public static final int DATA_HEALTH = 0;
    public static final int DATA_RESPAWN = 1;
    public static final int DATA_ILLUMINATED = 2;
    public static final int DATA_COUNT = 3;

    private final BlockPos pos;
    private final ContainerData data;

    /** 客户端构造：从 openScreen 的额外数据里读初值。 */
    public DummyStationMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(ModMenus.DUMMY_STATION.get(), id);
        int health = 50;
        int respawn = 40;
        boolean illuminated = false;
        if (buf != null) {
            pos = buf.readBlockPos();
            health = buf.readVarInt();
            respawn = buf.readVarInt();
            illuminated = buf.readBoolean();
        } else {
            pos = BlockPos.ZERO;
        }
        SimpleContainerData synced = new SimpleContainerData(DATA_COUNT);
        synced.set(DATA_HEALTH, health);
        synced.set(DATA_RESPAWN, respawn);
        synced.set(DATA_ILLUMINATED, illuminated ? 1 : 0);
        this.data = synced;
        addDataSlots(data);
    }

    /** 服务端构造：直接读写方块实体字段。 */
    public DummyStationMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.DUMMY_STATION.get(), id);
        this.pos = pos;
        if (!inventory.player.level().isClientSide
                && inventory.player.level().getBlockEntity(pos)
                instanceof TrainingDummyStationBlockEntity station) {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_HEALTH -> station.configuredHealth();
                        case DATA_RESPAWN -> station.respawnTicks();
                        case DATA_ILLUMINATED -> station.selfIlluminated() ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // 只读同步；修改必须走 DummySettingsPacket。
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            SimpleContainerData fallback = new SimpleContainerData(DATA_COUNT);
            fallback.set(DATA_HEALTH, 50);
            fallback.set(DATA_RESPAWN, 40);
            this.data = fallback;
        }
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int health() {
        return data.get(DATA_HEALTH);
    }

    public int respawnTicks() {
        return data.get(DATA_RESPAWN);
    }

    public boolean selfIlluminated() {
        return data.get(DATA_ILLUMINATED) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        // 只对高级台有效：方块被换成普通台/拆掉时立即关面板，别让 UI 继续显示“可保存”。
        Block block = player.level().getBlockState(pos).getBlock();
        return (block == ModBlocks.ADVANCED_TRAINING_DUMMY_STATION.get()
                || block == ModBlocks.ADVANCED_BALLOON_DUMMY_STATION.get())
                && player.level().getBlockEntity(pos) instanceof TrainingDummyStationBlockEntity
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** 右键高级训练台时用的打开器。 */
    public record Provider(BlockPos pos) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return Component.translatable("menu.barotrauma.dummy_station");
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new DummyStationMenu(id, inventory, pos);
        }
    }

    /** 服务端右键入口：把当前设置写进 openScreen 的额外数据。 */
    public static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TrainingDummyStationBlockEntity station) {
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, new Provider(pos), buf -> {
                buf.writeBlockPos(pos);
                buf.writeVarInt(station.configuredHealth());
                buf.writeVarInt(station.respawnTicks());
                buf.writeBoolean(station.selfIlluminated());
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
