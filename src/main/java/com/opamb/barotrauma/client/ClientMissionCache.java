package com.opamb.barotrauma.client;

import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.network.MissionStatePacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端任务状态缓存（{@link MissionStatePacket} 写入，终端界面和目标 HUD 读取）。
 */
public final class ClientMissionCache {

    private static volatile int credits;
    private static volatile Mission active;
    private static volatile List<Mission> offered = List.of();
    private static volatile long activeDeadlineAt;
    private static volatile long nowGameTime;

    private ClientMissionCache() {
    }

    /** 渲染线程调用（包处理器 enqueueWork 后已经在客户端主线程）。 */
    public static void accept(MissionStatePacket packet) {
        credits = packet.credits();
        activeDeadlineAt = packet.activeDeadlineAt();
        nowGameTime = packet.nowGameTime();
        if (packet.activeId() >= 0 && packet.activeTypeEnum() != null) {
            var def = com.opamb.barotrauma.world.PoiNetwork.definitions().stream()
                    .filter(d -> d.id == packet.activeId()).findFirst();
            long ticks = def.map(d -> d.deadlineTicks).orElse(0L);
            Mission mission = Mission.clientBrief(packet.activeId(), packet.activeTypeEnum(),
                    new net.minecraft.core.BlockPos(packet.targetX(), packet.targetY(), packet.targetZ()),
                    packet.radius(), packet.reward(), ticks,
                    Mission.State.values()[Math.max(0, packet.activeState())], true,
                    packet.activeDeadlineAt());
            active = mission;
        } else {
            active = null;
        }
        List<Mission> list = new ArrayList<>();
        var definitions = com.opamb.barotrauma.world.PoiNetwork.definitions();
        for (int i = 0; i < packet.offeredIds().length; i++) {
            final int idx = i;
            definitions.stream()
                    .filter(def -> def.id == packet.offeredIds()[idx]).findFirst()
                    .ifPresent(def -> list.add(Mission.clientBrief(def.id, def.type, def.target,
                            def.radius, def.reward, def.deadlineTicks,
                            Mission.State.values()[Math.max(0, packet.offeredStates()[idx])],
                            packet.offeredTaken()[idx], 0L)));
        }
        offered = list;
    }

    public static int credits() {
        return credits;
    }

    /** 当前进行中任务的绝对超时点（0 = 不限时）。 */
    public static long activeDeadlineAt() {
        return activeDeadlineAt;
    }

    /** 服务端发包时的游戏时刻，用于倒计时换算。 */
    public static long nowGameTime() {
        return nowGameTime;
    }

    public static Mission active() {
        return active;
    }

    public static List<Mission> offered() {
        return offered;
    }
}
