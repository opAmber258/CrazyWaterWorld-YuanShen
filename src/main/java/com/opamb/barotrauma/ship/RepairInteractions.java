package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.menu.RepairMenu;
import com.opamb.barotrauma.network.RepairActionPacket;
import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkHooks;

/**
 * 修理交互的公共入口。
 *
 * <p>对应原作 {@code Repairable} 的显示条件：<b>必须手持对应工具</b>
 * （{@code RequiredItem items="wrench"/"screwdriver"}）并且耐久低于
 * {@link RepairTarget#repairThreshold()}；满足时弹出独立的修理窗口，
 * 不满足时设备保持原来的面板/开关行为。</p>
 *
 * <p>门和舱口按用户要求放宽：只要手里有扳手就开修理窗口（满耐久显示
 * “无需修理”），空手右键才是开关门。</p>
 */
public final class RepairInteractions {

    private RepairInteractions() {
    }

    /** 主手或副手是否拿着要求的工具。 */
    public static boolean hasTool(Player player, RepairTarget.Tool tool) {
        if (player == null) {
            return false;
        }
        Item required = tool == RepairTarget.Tool.WRENCH
                ? ModItems.WRENCH.get() : ModItems.SCREWDRIVER.get();
        return player.getMainHandItem().is(required) || player.getOffhandItem().is(required);
    }

    /**
     * 手持工具且（可修 或 {@code force}）时打开修理窗口。
     *
     * @param pos    右键落点（物理船上是船坞代理坐标，菜单里会自行换算）
     * @param target 解析好的可修理目标，null 时直接返回 false
     * @param force  true = 满耐久也开（门/舱口）
     * @return 是否打开了修理窗口
     */
    public static boolean tryOpen(ServerPlayer player, BlockPos pos,
                                  RepairTarget target, boolean force) {
        if (player == null || target == null || pos == null) {
            return false;
        }
        if (!hasTool(player, target.requiredTool())) {
            return false;
        }
        if (!force && !target.canRepair()) {
            return false;
        }
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider(
                        (id, inventory, p) -> new RepairMenu(id, inventory, pos),
                        Component.translatable("gui.barotrauma.repair")),
                buf -> buf.writeBlockPos(pos));
        return true;
    }

    /** 按落点自行解析目标（代理/方块船都支持）。 */
    public static boolean tryOpen(ServerPlayer player, BlockPos pos, boolean force) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return tryOpen(player, pos, RepairActionPacket.find(level, pos), force);
    }
}
