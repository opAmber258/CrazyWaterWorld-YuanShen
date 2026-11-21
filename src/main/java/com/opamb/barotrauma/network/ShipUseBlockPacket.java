package com.opamb.barotrauma.network;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 物理船上右键方块（舱门/舱口/货架/柜子…）。
 *
 * <p>VS2 把船体方块收在船坞里，客户端命中的对方块坐标是船坞坐标。服务端
 * {@code handleUseItemOn} 用玩家到方块的距离做检查，船坞坐标离玩家几百万格，
 * 包会被直接丢掉——所以物理船上右键舱门/舱口/货架原本完全没有反应。</p>
 *
 * <p>客户端把<b>船坞坐标 + 面 + 相对命中点</b>发过来；服务端确认这一格属于
 * 当前物理船、玩家离命中点足够近，然后在船坞方块上调用原版的 {@code use}。
 * 方块实体、菜单、开门重扫都还是走原来的逻辑。</p>
 */
public record ShipUseBlockPacket(BlockPos yardPos, Direction face, InteractionHand hand,
                                 double hitX, double hitY, double hitZ, Vec3 worldHit) {

    /** 玩家眼睛到命中点的最大距离（格）。 */
    private static final double MAX_DISTANCE = 6.0D;

    public static void encode(ShipUseBlockPacket message, FriendlyByteBuf buf) {
        buf.writeBlockPos(message.yardPos);
        buf.writeEnum(message.face);
        buf.writeEnum(message.hand);
        buf.writeFloat((float) message.hitX);
        buf.writeFloat((float) message.hitY);
        buf.writeFloat((float) message.hitZ);
        buf.writeDouble(message.worldHit.x);
        buf.writeDouble(message.worldHit.y);
        buf.writeDouble(message.worldHit.z);
    }

    public static ShipUseBlockPacket decode(FriendlyByteBuf buf) {
        return new ShipUseBlockPacket(buf.readBlockPos(), buf.readEnum(Direction.class),
                buf.readEnum(InteractionHand.class),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public static void handle(ShipUseBlockPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!VsState.active() || !VsPilot.isYardBlock(message.yardPos)) {
                BarotraumaMod.LOGGER.info("[ship-use] 拒绝 {}：active={} yard={}",
                        message.yardPos, VsState.active(),
                        VsPilot.isYardBlock(message.yardPos));
                return;
            }
            // 客户端给的 worldHit 是**船坞坐标**里的命中点（VS2 把射线打在船坞方块上），
            // 直接拿它和玩家眼睛比会得到 3.1e7 格，所有右键都会被拒。服务端自己把
            // 船坞方块换算回世界坐标再验距离。
            // 命中偏移必须是 [0,1] 的有限值：NaN 会让下面的距离比较失效，
            // 越界值又可以把“命中点”伪造到玩家身边，从而远程 use 任意船坞方块。
            double hitX = message.hitX;
            double hitY = message.hitY;
            double hitZ = message.hitZ;
            if (!Double.isFinite(hitX) || !Double.isFinite(hitY) || !Double.isFinite(hitZ)
                    || hitX < 0.0D || hitX > 1.0D
                    || hitY < 0.0D || hitY > 1.0D
                    || hitZ < 0.0D || hitZ > 1.0D) {
                BarotraumaMod.LOGGER.info("[ship-use] 拒绝 {}：非法命中偏移 ({},{},{})",
                        message.yardPos, hitX, hitY, hitZ);
                return;
            }
            Vec3 check = null;
            BlockPos assembly = VsPilot.toAssembly(message.yardPos);
            Vec3 cellWorld = assembly == null ? null : VsPilot.assemblyToWorld(assembly);
            if (cellWorld != null) {
                // cellWorld 是方块中心；相对命中点 0..1，减 0.5 变到中心坐标系。
                check = cellWorld.add(hitX - 0.5D, hitY - 0.5D, hitZ - 0.5D);
            }
            double distance = check == null
                    ? Double.MAX_VALUE
                    : player.getEyePosition().distanceToSqr(check);
            double maxDistance = MAX_DISTANCE + 1.0D;
            if (!Double.isFinite(distance) || distance > maxDistance * maxDistance) {
                BarotraumaMod.LOGGER.info("[ship-use] 拒绝 {}：距离 {}/{} 格（世界命中点 {}）",
                        message.yardPos, check == null ? "未知" : Math.sqrt(distance),
                        maxDistance, check);
                return;
            }
            BlockState state = level.getBlockState(message.yardPos);
            if (state.isAir()) {
                BarotraumaMod.LOGGER.info("[ship-use] 拒绝 {}：船坞方块是空气", message.yardPos);
                return;
            }
            Vec3 localHit = Vec3.atLowerCornerOf(message.yardPos)
                    .add(hitX, hitY, hitZ);
            BlockHitResult hit = new BlockHitResult(localHit, message.face, message.yardPos, false);
            BarotraumaMod.LOGGER.info("[ship-use] 执行 {} @ {}", state.getBlock(), message.yardPos);
            state.use(level, player, message.hand, hit);
        });
        context.setPacketHandled(true);
    }
}
