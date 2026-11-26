package com.opamb.barotrauma.item;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.TurretBlock;
import com.opamb.barotrauma.blockentity.CoilgunLoaderBlockEntity;
import com.opamb.barotrauma.blockentity.PeriscopeBlockEntity;
import com.opamb.barotrauma.blockentity.SupercapacitorBlockEntity;
import com.opamb.barotrauma.blockentity.BatteryBlockEntity;
import com.opamb.barotrauma.ship.PowerStorage;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.block.TurretStationBlock;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.blockentity.TurretStationBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 炮台绑定器（开发者工具）。
 *
 * <p>两端式绑定：右键炮塔或瞄准站记录第一端，再右键另一端完成绑定；顺序不限。
 * 绑定只校验“同维度 + 一端炮塔一端瞄准站”，距离不限（用户 2026-11-19 定稿）。
 * 潜行右键瞄准站解除该站绑定；右键空气或潜行右键炮塔取消待绑定。</p>
 *
 * <p>待绑定记录只放在服务端内存里、按玩家 UUID 索引，2 分钟无操作自动过期，
 * 所以不需要额外的网络包，也不会写进物品 NBT。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TurretBinderItem extends Item {

    /** 待绑定状态的保留时长：2 分钟（用挂钟时间，切换世界/暂停也不受影响）。 */
    private static final long PENDING_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(2L);

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private enum Endpoint {
        TURRET,
        STATION, PERISCOPE, LOADER, CAPACITOR, BATTERY
    }

    private record Pending(BlockPos pos, ResourceKey<Level> dimension, long createdAtNanos) {
    }

    public TurretBinderItem(Properties properties) {
        super(properties);
    }

    /** 断线时清掉待绑定，避免静态 Map 随长期服务器运行无界增长。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        return player == null ? InteractionResult.PASS : bindAt(player, context.getHand(), context.getClickedPos());
    }

    public InteractionResult bindAt(Player player, InteractionHand hand, BlockPos clicked) {
        Level level = player.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown()) {
            handleSneak(level, player, clicked);
            return InteractionResult.CONSUME;
        }

        Endpoint clickedEndpoint = endpointOf(level, clicked);
        if (clickedEndpoint == Endpoint.TURRET) {
            clicked = TurretBlockEntity.masterPos(level.getBlockState(clicked), clicked);
        }
        if (clickedEndpoint == null) {
            message(player, "msg.barotrauma.turret.binder.only");
            return InteractionResult.CONSUME;
        }

        Pending pending = currentPending(level, player);
        if (pending == null) {
            PENDING.put(player.getUUID(),
                    new Pending(clicked.immutable(), level.dimension(), System.nanoTime()));
            message(player, "msg.barotrauma.turret.binder.recorded",
                    Component.translatable(endpointKey(clickedEndpoint)),
                    Component.literal(clicked.toShortString()));
            return InteractionResult.CONSUME;
        }

        if (pending.pos().equals(clicked)) {
            PENDING.remove(player.getUUID());
            message(player, "msg.barotrauma.turret.binder.cancelled");
            return InteractionResult.CONSUME;
        }

        Endpoint pendingEndpoint = endpointOf(level, pending.pos());
        if (pendingEndpoint == null) {
            PENDING.remove(player.getUUID());
            message(player, "msg.barotrauma.turret.binder.invalid_target");
            return InteractionResult.CONSUME;
        }
        if ((pendingEndpoint == Endpoint.TURRET) == (clickedEndpoint == Endpoint.TURRET)) {
            // 两端同类型：保留第一端，让玩家去点另一端，避免误点覆盖。
            message(player, "msg.barotrauma.turret.binder.need_pair");
            return InteractionResult.CONSUME;
        }

        BlockPos stationPos = pendingEndpoint == Endpoint.TURRET ? clicked : pending.pos();
        BlockPos rawTurretPos = pendingEndpoint == Endpoint.TURRET ? pending.pos() : clicked;
        // 未来 2×2/3×3 炮塔：绑定到主方块，站台/状态包始终按主坐标工作。
        BlockPos turretPos = TurretBlockEntity.masterPos(
                level.getBlockState(rawTurretPos), rawTurretPos);
        TurretBlockEntity turret = TurretBlockEntity.at(level, turretPos);
        if (turret != null && level.getBlockEntity(stationPos) instanceof PowerStorage
                && !turret.hasPowerInputFor(stationPos)) {
            PENDING.remove(player.getUUID());
            message(player, "msg.barotrauma.turret.binder.power_full");
            return InteractionResult.CONSUME;
        }
        if (turret == null || !bind(level, stationPos, turret)) {
            PENDING.remove(player.getUUID());
            message(player, "msg.barotrauma.turret.binder.invalid_target");
            return InteractionResult.CONSUME;
        }

        PENDING.remove(player.getUUID());
        message(player, "msg.barotrauma.turret.binder.bound",
                Component.literal(stationPos.toShortString()),
                Component.literal(turretPos.toShortString()));
        player.swing(hand, true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        if (PENDING.remove(player.getUUID()) != null) {
            message(player, "msg.barotrauma.turret.binder.cancelled");
        } else {
            message(player, "msg.barotrauma.turret.binder.nothing");
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private static boolean bind(Level level, BlockPos pos, TurretBlockEntity turret) {
        BlockPos target = turret.getBlockPos();
        var device = level.getBlockEntity(pos);
        if (device instanceof TurretStationBlockEntity station) {
            station.bindTo(target);
            return true;
        }
        // Relative links survive VS assembly and docking when the two blocks move together.
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)
                || VsState.isPhysical(server, pos) != VsState.isPhysical(server, target)) return false;
        if (device instanceof PeriscopeBlockEntity periscope) {
            periscope.bindTo(target);
            return true;
        }
        if (!turret.weaponKind().usesAmmo()) return false;
        if (device instanceof CoilgunLoaderBlockEntity loader) {
            turret.bindLoader(loader);
            return true;
        }
        return device instanceof PowerStorage && turret.bindPowerSource(pos);
    }

    private static void handleSneak(Level level, Player player, BlockPos clicked) {
        Pending pending = currentPending(level, player);
        TurretBlockEntity selected = pending == null || !level.hasChunkAt(pending.pos())
                ? null : TurretBlockEntity.at(level, pending.pos());
        if (selected != null && level.getBlockEntity(clicked) instanceof PowerStorage
                && selected.unbindPowerSource(clicked)) {
            PENDING.remove(player.getUUID());
            message(player, "msg.barotrauma.turret.binder.power_unbound");
            return;
        }
        if (level.getBlockEntity(clicked) instanceof PeriscopeBlockEntity periscope) {
            periscope.bindTo(null);
            message(player, "msg.barotrauma.turret.binder.cleared");
            return;
        }
        TurretBlockEntity turret = TurretBlockEntity.at(level, clicked);
        if (turret != null && turret.weaponKind().usesAmmo() && !PENDING.containsKey(player.getUUID())) {
            turret.clearSupplies();
            message(player, "msg.barotrauma.turret.binder.supplies_cleared");
            return;
        }
        if (level.getBlockEntity(clicked) instanceof TurretStationBlockEntity station
                && level.getBlockState(clicked).getBlock() instanceof TurretStationBlock) {
            boolean hadBinding = station.isBound();
            station.clearBinding();
            message(player, hadBinding
                    ? "msg.barotrauma.turret.binder.cleared"
                    : "msg.barotrauma.turret.binder.station_unbound");
            return;
        }
        if (PENDING.remove(player.getUUID()) != null) {
            message(player, "msg.barotrauma.turret.binder.cancelled");
        } else {
            message(player, "msg.barotrauma.turret.binder.nothing");
        }
    }

    @Nullable
    private static Pending currentPending(Level level, Player player) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return null;
        }
        if (!level.dimension().equals(pending.dimension())) {
            // 换维度后旧记录没有意义：静默丢弃，把这次点击当成新的第一端。
            PENDING.remove(player.getUUID());
            return null;
        }
        if (System.nanoTime() - pending.createdAtNanos() > PENDING_TIMEOUT_NANOS) {
            PENDING.remove(player.getUUID());
            message(player, "msg.barotrauma.turret.binder.expired");
            return null;
        }
        return pending;
    }

    @Nullable
    private static Endpoint endpointOf(Level level, BlockPos pos) {
        // 未加载区块不要读方块状态：hasChunkAt 为假时直接当作无效目标。
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof TurretBlock) {
            return Endpoint.TURRET;
        }
        if (block instanceof TurretStationBlock) {
            return Endpoint.STATION;
        }
        var device = level.getBlockEntity(pos);
        if (device instanceof PeriscopeBlockEntity) return Endpoint.PERISCOPE;
        if (device instanceof CoilgunLoaderBlockEntity) return Endpoint.LOADER;
        if (device instanceof SupercapacitorBlockEntity) return Endpoint.CAPACITOR;
        if (device instanceof BatteryBlockEntity) return Endpoint.BATTERY;
        return null;
    }

    private static String endpointKey(Endpoint endpoint) {
        return "msg.barotrauma.turret.binder.type." + endpoint.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static void message(Player player, String key, Component... args) {
        player.displayClientMessage(Component.translatable(key, (Object[]) args), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.barotrauma.turret_binder.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.barotrauma.turret_binder.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.barotrauma.turret_binder.3")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.barotrauma.turret_binder.4")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.barotrauma.turret_binder.5")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.barotrauma.turret_binder.6")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
