package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.menu.RepairMenu;
import com.opamb.barotrauma.network.RepairActionPacket;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
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
        BlockPos menuPos = stablePos(pos, target);
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider(
                        (id, inventory, p) -> new RepairMenu(id, inventory, menuPos),
                        Component.translatable("gui.barotrauma.repair")),
                buf -> buf.writeBlockPos(menuPos));
        return true;
    }

    /**
     * 菜单里保存的坐标必须在船移动后仍能解析回同一台设备。
     *
     * <p>物理船上玩家右键的是跟着船平移的代理方块，世界坐标会随船移动；
     * 直接存世界坐标的话，船一开动 pos 就悬空，{@code worldCenter} 会把它
     * 当装配坐标换算，距离检查失败、维修窗口被关掉。这里改存装配坐标
     * （设备在快照里的原始位置）：{@code RepairActionPacket.find} 能经
     * {@code toYard} 找回船坞里的方块实体，{@code worldCenter} 也能用
     * {@code assemblyToWorld} 算出当前位置。</p>
     */
    private static BlockPos stablePos(BlockPos pos, RepairTarget target) {
        if (!VsState.active()) {
            return pos;
        }
        // 代理方块：真实方块实体在船坞里，用它的坐标换算成装配坐标。
        if (target instanceof BlockEntity be && VsPilot.isYardBlock(be.getBlockPos())) {
            return VsPilot.toAssembly(be.getBlockPos());
        }
        // 船坞里的方块（电池等直接右键的实体）：换算成装配坐标。
        if (VsPilot.isYardBlock(pos)) {
            return VsPilot.toAssembly(pos);
        }
        return pos;
    }

    /** 按落点自行解析目标（代理/方块船都支持）。 */
    public static boolean tryOpen(ServerPlayer player, BlockPos pos, boolean force) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return tryOpen(player, pos, RepairActionPacket.find(level, pos), force);
    }
}
