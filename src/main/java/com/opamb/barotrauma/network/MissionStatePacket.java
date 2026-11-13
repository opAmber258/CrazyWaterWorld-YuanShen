package com.opamb.barotrauma.network;

import com.opamb.barotrauma.campaign.CampaignState;
import com.opamb.barotrauma.data.MissionSavedData;
import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：任务面板 / 目标 HUD / 海图需要的全部任务状态。
 *
 * <p>一趟最多 3 条进行中任务，所以这里用并行数组下发：
 * 进行中（id/状态/超时点/货运箱数） + 任务池（id/状态/是否被接走/起点站/目的地）。
 * 目标坐标、类型、名字等静态数据客户端从 {@code PoiNetwork.definitions()} 查
 * （服务端和客户端用的是同一份确定性任务池）。</p>
 */
public record MissionStatePacket(int credits, long nowGameTime,
                                 int[] activeIds, int[] activeStates, long[] activeDeadlineAt,
                                 int[] activeCargoTotal, int[] activeCargoPerCrate,
                                 int[] activeDest,
                                 int[] poolIds, int[] poolStates, boolean[] poolTaken,
                                 int[] offerIds, int[] offerDests) {

    /** 组装任务状态；余额取自共享钱包。 */
    public static MissionStatePacket build(ServerPlayer player, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return empty();
        }
        MissionSavedData data = MissionSavedData.get(serverLevel);
        List<Mission> active = data.activeMissions(player.getUUID());
        int[] ids = new int[active.size()];
        int[] states = new int[active.size()];
        long[] deadlines = new long[active.size()];
        int[] cargoTotal = new int[active.size()];
        int[] cargoPerCrate = new int[active.size()];
        int[] activeDest = new int[active.size()];
        for (int i = 0; i < active.size(); i++) {
            Mission mission = active.get(i);
            ids[i] = mission.id;
            states[i] = mission.state().ordinal();
            deadlines[i] = mission.deadlineAtGameTime();
            cargoTotal[i] = mission.cargoTotal();
            cargoPerCrate[i] = mission.cargoPerCrate();
            activeDest[i] = mission.destinationNode();
        }
        var missions = data.missions();
        int[] poolIds = new int[missions.size()];
        int[] poolStates = new int[missions.size()];
        boolean[] poolTaken = new boolean[missions.size()];
        int index = 0;
        for (Mission mission : missions.values()) {
            poolIds[index] = mission.id;
            poolStates[index] = mission.state().ordinal();
            poolTaken[index] = mission.isTaken();
            index++;
        }
        // 海图只需要“当前站”挂着的报价；每站各自刷新，未接的留在站里。
        int node = serverLevel.dimension().equals(ModDimensions.THE_DEEP)
                ? CampaignState.wallet(serverLevel).currentNode() : -1;
        List<Integer> offerIds = node >= 0 ? data.offerIds(node) : List.of();
        int[] offers = new int[offerIds.size()];
        int[] offerDests = new int[offerIds.size()];
        for (int i = 0; i < offerIds.size(); i++) {
            offers[i] = offerIds.get(i);
            offerDests[i] = data.offerDestination(node, offers[i]);
        }
        return new MissionStatePacket(walletCredits(serverLevel), serverLevel.getGameTime(),
                ids, states, deadlines, cargoTotal, cargoPerCrate, activeDest,
                poolIds, poolStates, poolTaken, offers, offerDests);
    }

    /** 共享钱包固定挂在深海维度；终端在别的维度打开时也从那里取。 */
    private static int walletCredits(ServerLevel level) {
        ServerLevel deep = level.dimension().equals(ModDimensions.THE_DEEP)
                ? level : level.getServer().getLevel(ModDimensions.THE_DEEP);
        return deep == null ? 0 : CampaignState.wallet(deep).credits();
    }

    private static MissionStatePacket empty() {
        return new MissionStatePacket(0, 0L, new int[0], new int[0], new long[0],
                new int[0], new int[0], new int[0], new int[0], new int[0], new boolean[0],
                new int[0], new int[0]);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(credits);
        buf.writeLong(nowGameTime);
        buf.writeVarInt(activeIds.length);
        for (int i = 0; i < activeIds.length; i++) {
            buf.writeVarInt(activeIds[i]);
            buf.writeVarInt(activeStates[i] + 1);
            buf.writeLong(activeDeadlineAt[i]);
            buf.writeVarInt(activeCargoTotal[i]);
            buf.writeVarInt(activeCargoPerCrate[i]);
            buf.writeInt(activeDest[i]);
        }
        buf.writeVarInt(poolIds.length);
        for (int i = 0; i < poolIds.length; i++) {
            buf.writeVarInt(poolIds[i]);
            buf.writeVarInt(poolStates[i] + 1);
            buf.writeBoolean(poolTaken[i]);
        }
        buf.writeVarInt(offerIds.length);
        for (int i = 0; i < offerIds.length; i++) {
            buf.writeVarInt(offerIds[i]);
            buf.writeInt(offerDests[i]);
        }
    }

    public static MissionStatePacket decode(FriendlyByteBuf buf) {
        int credits = buf.readVarInt();
        long nowGameTime = buf.readLong();
        int activeCount = buf.readVarInt();
        if (activeCount < 0 || activeCount > MissionSavedData.MAX_ACTIVE) {
            throw new IllegalArgumentException("mission active count");
        }
        int[] ids = new int[activeCount];
        int[] states = new int[activeCount];
        long[] deadlines = new long[activeCount];
        int[] cargoTotal = new int[activeCount];
        int[] cargoPerCrate = new int[activeCount];
        int[] activeDest = new int[activeCount];
        for (int i = 0; i < activeCount; i++) {
            ids[i] = buf.readVarInt();
            states[i] = buf.readVarInt() - 1;
            deadlines[i] = buf.readLong();
            cargoTotal[i] = buf.readVarInt();
            cargoPerCrate[i] = buf.readVarInt();
            activeDest[i] = buf.readInt();
        }
        int poolCount = buf.readVarInt();
        if (poolCount < 0 || poolCount > 512) {
            throw new IllegalArgumentException("mission pool count");
        }
        int[] poolIds = new int[poolCount];
        int[] poolStates = new int[poolCount];
        boolean[] poolTaken = new boolean[poolCount];
        for (int i = 0; i < poolCount; i++) {
            poolIds[i] = buf.readVarInt();
            poolStates[i] = buf.readVarInt() - 1;
            poolTaken[i] = buf.readBoolean();
        }
        int offerCount = buf.readVarInt();
        if (offerCount < 0 || offerCount > 512) {
            throw new IllegalArgumentException("mission offer count");
        }
        int[] offerIds = new int[offerCount];
        int[] offerDests = new int[offerCount];
        for (int i = 0; i < offerCount; i++) {
            offerIds[i] = buf.readVarInt();
            offerDests[i] = buf.readInt();
        }
        return new MissionStatePacket(credits, nowGameTime, ids, states, deadlines,
                cargoTotal, cargoPerCrate, activeDest, poolIds, poolStates, poolTaken,
                offerIds, offerDests);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.ClientMissionCache.accept(this)));
        context.setPacketHandled(true);
    }
}
