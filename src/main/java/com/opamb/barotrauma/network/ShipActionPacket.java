package com.opamb.barotrauma.network;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.menu.SonarMenu;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.sonar.SonarScan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端的船控指令。
 *
 * <p>{@code a/b/c} 用 VarInt 传输，按指令解释为舵令、开关或自动驾驶坐标。</p>
 */
public record ShipActionPacket(BlockPos corePos, int action, int a, int b, int c) {

    public static final int PUMP_OUT = 1;
    public static final int PUMP_IN = 2;
    public static final int AIRLOCK_DRAIN = 3;
    public static final int AIRLOCK_FLOOD = 4;
    public static final int THROTTLE_FORWARD = 5;
    public static final int STOP = 6;
    /** 设定推进方向：a/b/c = x/y/z，每轴 −1/0/1。 */
    public static final int SET_HEADING = 9;
    /** 设定推进功率：a = 百分比。 */
    public static final int SET_POWER = 10;
    /** 自动驾驶开关：a = 1 开启 / 0 关闭。 */
    public static final int SET_AUTOPILOT = 11;
    /** 在声纳上标记目标点：a/b/c = 回波或空白目标的世界坐标。 */
    public static final int SET_AUTOPILOT_TARGET = 12;
    /** 清除目标点。 */
    public static final int CLEAR_AUTOPILOT_TARGET = 13;
    /** 把附近的方块船收进 VS2 物理刚体（声纳屏"下潜"按钮；等价于 /barotrauma ship vs launch）。 */
    public static final int LAUNCH = 14;
    /** 把物理船按装配快照铺回世界（声纳屏"靠泊"按钮；等价于 /barotrauma ship vs dock）。 */
    public static final int DOCK = 15;
    /**
     * 微调自动驾驶目标：a/b/c = 相对**当前目标世界坐标**的增量（−1/0/1）。
     */
    public static final int NUDGE_AUTOPILOT_TARGET = 16;
    /** Three signed percentages. On a physical ship, x/y/z are forward/vertical/yaw. */
    public static final int SET_ANALOG_HELM = 17;

    public ShipActionPacket(BlockPos corePos, int action) {
        this(corePos, action, 0, 0, 0);
    }

    public ShipActionPacket(BlockPos corePos, int action, int a) {
        this(corePos, action, a, 0, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(corePos);
        buf.writeVarInt(action);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        buf.writeVarInt(c);
    }

    public static ShipActionPacket decode(FriendlyByteBuf buf) {
        return new ShipActionPacket(buf.readBlockPos(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof SonarMenu menu)
                    || !menu.sonarPos().equals(corePos) || !menu.stillValid(player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            ShipCoreBlockEntity terminal = menu.terminal();
            ShipState ship = menu.ship(level);
            boolean physical = menu.physical();
            if (ship == null) {
                return;
            }
            if (!ship.canTakeHelm(player.getUUID(), level.getGameTime())) {
                return;
            }
            if (action == LAUNCH || action == DOCK) {
                if (action == LAUNCH && !physical) {
                    handleLaunch(player, level, terminal, ship);
                } else if (action == DOCK && physical) {
                    handleDock(player, level);
                }
                return;
            }
            if (terminal.condition() <= 0.0D) {
                return;
            }
            BlockPos terminalCell = physical
                    ? com.opamb.barotrauma.ship.vs.VsPilot.toAssembly(terminal.getBlockPos())
                    : terminal.getBlockPos();
            double terminalVoltage = ship.simulation().consumerVoltage(terminalCell);
            if (terminalVoltage < ShipCoreBlockEntity.TERMINAL_MIN_VOLTAGE) {
                if (action == SET_HEADING) {
                    BarotraumaMod.LOGGER.info("[helm] 拒绝 SET_HEADING：终端电压 {} < {}",
                            terminalVoltage, ShipCoreBlockEntity.TERMINAL_MIN_VOLTAGE);
                }
                return;
            }
            if (action == SET_HEADING) {
                BarotraumaMod.LOGGER.info("[helm] SET_HEADING a={} b={} c={} physical={} volt={}",
                        a, b, c, physical, terminalVoltage);
            }
            switch (action) {
                case PUMP_OUT -> ship.simulation().setPumpOut(true);
                case PUMP_IN -> ship.simulation().setPumpOut(false);
                case AIRLOCK_DRAIN -> ship.simulation().requestDrain();
                case AIRLOCK_FLOOD -> ship.simulation().requestFlood();
                case THROTTLE_FORWARD -> {
                    if (physical) {
                        ship.setHelm(1, 0, 0);
                    } else {
                        ship.setThrottle(0, 0, 1);
                    }
                }
                case STOP -> {
                    if (physical) {
                        ship.setHelm(0, 0, 0);
                    } else {
                        ship.setThrottle(0, 0, 0);
                    }
                }
                case SET_HEADING -> {
                    if (physical) {
                        ship.setHelm(clampAxis(a), clampAxis(c), clampAxis(b));
                    } else {
                        ship.setThrottle(clampAxis(a), clampAxis(b), clampAxis(c));
                    }
                }
                case SET_ANALOG_HELM -> ship.acceptManualInput(player.getUUID(), level.getGameTime(),
                        Mth.clamp(a, -100, 100) / 100.0D, Mth.clamp(b, -100, 100) / 100.0D,
                        Mth.clamp(c, -100, 100) / 100.0D, physical);
                case SET_POWER -> ship.setPower(a);
                case SET_AUTOPILOT -> {
                    if (a != 0) {
                        holdPosition(ship, terminal, physical);
                    } else {
                        ship.setAutopilotHold(false);
                    }
                }
                case SET_AUTOPILOT_TARGET -> {
                    if (!ship.autopilotHold()) {
                        return;
                    }
                    BlockPos target = new BlockPos(a, b, c);
                    // A faded return can still be outside the current sphere after the boat moves.
                    if (level.isOutsideBuildHeight(target)
                            || target.distToCenterSqr(player.position()) > 4.0D * SonarScan.MAX_RANGE * SonarScan.MAX_RANGE) {
                        return;
                    }
                    ship.setAutopilotTarget(target);
                }
                case NUDGE_AUTOPILOT_TARGET -> {
                    // 增量直接加在目标的世界坐标上：船怎么移动，目标都不会漂。
                    BlockPos target = ship.autopilotTarget();
                    if (target != null) {
                        ship.setAutopilotTarget(target.offset(
                                clampAxis(a), clampAxis(b), clampAxis(c)));
                    }
                }
                case CLEAR_AUTOPILOT_TARGET -> {
                    if (ship.autopilotHold()) {
                        holdPosition(ship, terminal, physical);
                    }
                }
                default -> BarotraumaMod.LOGGER.warn("[Barotrauma] 未知船控指令 {}", action);
            }
            // 船已经被收成物理刚体时，把油门/功率/压载立刻同步给 VS2 推进器。
            // 这是"一套操作面板、一套移动系统"的关键：面板照旧只改 ShipState，
            // 真正推动船的是哪个系统由 VsState 决定。
            com.opamb.barotrauma.ship.vs.VsState.syncControl(ship);
            // 掌舵指令每一下都刷屏，交给面板显示
            if (action != SET_HEADING && action != SET_ANALOG_HELM && action != SET_POWER
                    && action != SET_AUTOPILOT_TARGET
                    && action != NUDGE_AUTOPILOT_TARGET) {
                player.sendSystemMessage(ship.liveReport());
            }
        });
        context.setPacketHandled(true);
    }

    private static int clampAxis(int value) {
        return Math.max(-1, Math.min(1, value));
    }

    private static void holdPosition(ShipState ship, ShipCoreBlockEntity terminal, boolean physical) {
        Vec3 position = physical
                ? com.opamb.barotrauma.ship.vs.VsPilot.assemblyToWorld(com.opamb.barotrauma.ship.vs.VsState.corePos())
                : terminal == null ? null : ship.physics().positionAt(terminal.getBlockPos());
        if (position == null) {
            ship.setAutopilotHold(true);
        } else {
            ship.holdAt(position);
        }
    }

    /**
     * 声纳屏“下潜”只装配当前菜单绑定的船。
     */
    private static void handleLaunch(ServerPlayer player, ServerLevel level,
                                     ShipCoreBlockEntity core, ShipState state) {
        if (!com.opamb.barotrauma.ship.vs.VsSupport.available()) {
            player.sendSystemMessage(Component.literal(
                    "没装瓦尔基里天空 2（valkyrienskies），物理船不可用")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (com.opamb.barotrauma.ship.vs.VsPilot.hasShip()) {
            player.sendSystemMessage(Component.literal("已经有一艘物理船了，先靠泊再启动下一艘")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        java.util.Set<BlockPos> blocks = state.collectBlocks(level);
        player.sendSystemMessage(Component.literal(
                        com.opamb.barotrauma.ship.vs.VsPilot.launch(level, blocks, state, core.getBlockPos()))
                .withStyle(ChatFormatting.AQUA));
    }

    /** 声纳屏"靠泊"：物理船按装配快照铺回世界；没船/不同维度由 {@code VsPilot.dock} 回报原因。 */
    private static void handleDock(ServerPlayer player, ServerLevel level) {
        String message = com.opamb.barotrauma.ship.vs.VsPilot.dock(level);
        player.sendSystemMessage(Component.literal(message)
                .withStyle(ChatFormatting.AQUA));
    }
}
