package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.network.RepairActionPacket;
import com.opamb.barotrauma.registry.ModMenus;
import com.opamb.barotrauma.ship.RepairInteractions;
import com.opamb.barotrauma.ship.RepairTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 修理窗口（对应原作 {@code Repairable} 自己的 GUI 帧）。
 *
 * <p>原作里设备的修理界面是和设备自己的面板并列的第二个 GUI 框：标题、需求
 * 工具、耐久条 + 指针、{@code Repair} 按钮。这里做成独立菜单，手持对应工具
 * 右键设备时打开；设备面板本身不再画耐久条。</p>
 *
 * <p>服务端负责真正的修理会话：窗口打开期间 {@link #serverTick()} 每 tick 按
 * 满耐久 / {@link RepairTarget#repairDurationSeconds()} 推进耐久，客户端只发
 * {@code START}/{@code QTE}/{@code STOP}。这样进度条由 20Hz 数据位驱动，客户端
 * 逐帧插值即可，不会像之前那样一按一蹦。</p>
 */
public class RepairMenu extends AbstractContainerMenu {

    /** 耐久 × 100。 */
    public static final int DATA_CONDITION = 0;
    /** 最大耐久 × 100。 */
    public static final int DATA_MAX = 1;
    /** 修理阈值（0..1）× 100。 */
    public static final int DATA_THRESHOLD = 2;
    /** 需求工具：0 螺丝刀，1 扳手。 */
    public static final int DATA_TOOL = 3;
    /** 手上有没有对应工具（0/1）。 */
    public static final int DATA_HAS_TOOL = 4;
    /** 当前耐久是否低于阈值（0/1）；门在满耐久打开时也是 0。 */
    public static final int DATA_CAN_REPAIR = 5;
    /** 服务端状态：是否正在修理（0/1）。 */
    public static final int DATA_REPAIRING = 6;
    /** 耐久为 0（0/1）。 */
    public static final int DATA_BROKEN = 7;
    /** 服务端 QTE 指针位置 × 1000（0..1000）：客户端只画这个值。 */
    public static final int DATA_QTE = 8;

    public static final int DATA_COUNT = 9;

    /** QTE 冷却：原作 0.5 秒 = 10 tick。 */
    private static final int QTE_COOLDOWN_TICKS = 10;
    /** 原作 QteDuration：指针从右端扫回左端 0.5 秒。 */
    private static final float QTE_DURATION = 0.5F;
    private static final double MAX_DISTANCE_SQ = 8.0D * 8.0D;

    private final BlockPos pos;
    private final ContainerData data;
    /** 仅服务端非 null。 */
    private final Player owner;
    private RepairTarget target;
    private boolean repairing;
    private int qteCooldown;
    /** 服务端自己维护的 QTE 指针时间（秒）；不信任客户端提交的 marker。 */
    private float qteTimer = QTE_DURATION;

    public RepairMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public RepairMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.REPAIR.get(), id);
        this.pos = pos;
        if (inventory.player.level() instanceof ServerLevel level) {
            this.owner = inventory.player;
            this.target = RepairActionPacket.find(level, pos);
            Player player = this.owner;
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    // 每次读当前目标，不缓存构造时的实例：设备被拆掉/换新后
                    // 同步数据必须跟着校验后的 target 走。
                    RepairTarget handle = target;
                    if (handle == null) {
                        return switch (index) {
                            case DATA_MAX, DATA_CONDITION -> 10000;
                            case DATA_HAS_TOOL -> 1;
                            default -> 0;
                        };
                    }
                    return switch (index) {
                        case DATA_CONDITION -> (int) Math.round(handle.condition() * 100.0D);
                        case DATA_MAX -> (int) Math.round(handle.maxCondition() * 100.0D);
                        case DATA_THRESHOLD -> (int) Math.round(handle.repairThreshold() * 100.0D);
                        case DATA_TOOL -> handle.requiredTool().ordinal();
                        case DATA_HAS_TOOL ->
                                RepairInteractions.hasTool(player, handle.requiredTool()) ? 1 : 0;
                        case DATA_CAN_REPAIR -> handle.canRepair() ? 1 : 0;
                        case DATA_REPAIRING -> repairing ? 1 : 0;
                        case DATA_BROKEN -> handle.condition() <= 0.0D ? 1 : 0;
                        case DATA_QTE -> Math.round(Mth.clamp(qteTimer / QTE_DURATION, 0.0F, 1.0F)
                                * 1000.0F);
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // 耐久只由服务端修理会话和伤害改；同步包不会写回来。
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            this.owner = null;
            SimpleContainerData fallback = new SimpleContainerData(DATA_COUNT);
            fallback.set(DATA_CONDITION, 10000);
            fallback.set(DATA_MAX, 10000);
            fallback.set(DATA_THRESHOLD, 8000);
            fallback.set(DATA_HAS_TOOL, 1);
            fallback.set(DATA_QTE, 1000);
            this.data = fallback;
        }
        addDataSlots(data);
    }

    public ContainerData data() {
        return data;
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean repairing() {
        return repairing;
    }

    /** 服务端每 tick（{@code RepairTickEvents}）。 */
    public void serverTick() {
        if (owner == null || !(owner.level() instanceof ServerLevel level)) {
            return;
        }
        if (qteCooldown > 0) {
            qteCooldown--;
            if (qteCooldown == 0) {
                qteTimer = QTE_DURATION;
            }
        } else {
            // 指针扫回左端：指数衰减，和客户端动画同一公式。
            qteTimer -= (1.0F / 20.0F) * (qteTimer / QTE_DURATION);
            if (qteTimer < 0.0F) {
                qteTimer = QTE_DURATION;
            }
        }
        RepairTarget current = RepairActionPacket.find(level, pos);
        if (target == null) {
            target = current;
        } else if (current != target) {
            // 设备被拆掉或同位置换了一台：关闭窗口，别继续修旧/新实例。
            repairing = false;
            target = null;
            if (owner instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.closeContainer();
            }
            return;
        }
        RepairTarget handle = target;
        if (handle == null) {
            repairing = false;
            return;
        }
        if (!owner.isAlive() || owner.distanceToSqr(repairCenter()) > MAX_DISTANCE_SQ) {
            repairing = false;
            return;
        }
        if (!RepairInteractions.hasTool(owner, handle.requiredTool()) || handle.isFull()) {
            repairing = false;
            return;
        }
        if (!repairing) {
            return;
        }
        handle.applyRepair(handle.maxCondition()
                / Math.max(1.0E-4D, handle.repairDurationSeconds()) / 20.0D);
        if (handle.isFull()) {
            repairing = false;
        }
    }

    /** 点“修理”按钮：开始持续修理。 */
    public void startRepair() {
        if (owner == null || target == null) {
            return;
        }
        if (target.isFull() || !target.canRepair()) {
            return;
        }
        if (!RepairInteractions.hasTool(owner, target.requiredTool())) {
            return;
        }
        repairing = true;
        qteTimer = QTE_DURATION;
        qteCooldown = 0;
    }

    /**
     * 点“修理”按钮时同时做一次 QTE（原作按钮按下触发）。
     *
     * <p>原作第一次点击只开始修理（OnButtonDown 早于开始修理），之后每次点击
     * 才判定：指针位置 ≤ 已恢复比例时 +3 耐久。</p>
     */
    public void qte(float marker) {
        if (owner == null || target == null || !repairing) {
            return;
        }
        // 指针位置由服务端自己算：客户端提交的 marker 可以被改包伪造（CWE-602），
        // 否则每 10 tick 发一次 0 就能无脑拿到 QTE 加成。
        if (qteCooldown > 0) {
            // 原作：冷却中乱点会把指针打回右端。
            qteTimer = QTE_DURATION;
            return;
        }
        qteCooldown = QTE_COOLDOWN_TICKS;
        float serverMarker = Math.max(0.0F, Math.min(1.0F, qteTimer / QTE_DURATION));
        if (serverMarker <= target.conditionFraction()) {
            target.applyRepair(target.qteBoostPoints());
        }
    }

    public void stopRepair() {
        repairing = false;
    }

    private Vec3 repairCenter() {
        Vec3 center = owner == null ? null
                : RepairActionPacket.worldCenter(owner.level(), pos);
        return center == null ? Vec3.atCenterOf(pos) : center;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        repairing = false;
    }

    @Override
    public boolean stillValid(Player player) {
        Vec3 center = RepairActionPacket.worldCenter(player.level(), pos);
        if (center == null || player.distanceToSqr(center) > MAX_DISTANCE_SQ) {
            return false;
        }
        if (player.level().isClientSide) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        // 服务端：缓存目标必须仍是当前位置解析到的同一台设备。
        return target != null
                && !(target instanceof net.minecraft.world.level.block.entity.BlockEntity be
                        && be.isRemoved())
                && RepairActionPacket.find(level, pos) == target;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
