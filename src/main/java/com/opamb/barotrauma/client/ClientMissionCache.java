package com.opamb.barotrauma.client;

import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.network.MissionStatePacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端任务状态缓存（{@link MissionStatePacket} 写入，NPC 任务界面、海图和目标 HUD 读取）。
 *
 * <p>一趟最多 3 条进行中任务，所以这里存的是列表。</p>
 */
public final class ClientMissionCache {

    private static volatile int credits;
    private static volatile List<Mission> active = List.of();
    private static volatile List<Mission> offered = List.of();
    /** 当前站挂着的报价：任务 id → 目的地节点。 */
    private static volatile java.util.Map<Integer, Integer> offers = java.util.Map.of();
    private static volatile long nowGameTime;

    private ClientMissionCache() {
    }

    /** 渲染线程调用（包处理器 enqueueWork 后已经在客户端主线程）。 */
    public static void accept(MissionStatePacket packet) {
        credits = packet.credits();
        nowGameTime = packet.nowGameTime();
        var definitions = com.opamb.barotrauma.world.PoiNetwork.definitions();
        List<Mission> mine = new ArrayList<>();
        for (int i = 0; i < packet.activeIds().length; i++) {
            final int index = i;
            definitions.stream()
                    .filter(def -> def.id == packet.activeIds()[index]).findFirst()
                    .ifPresent(def -> {
                        Mission mission = Mission.clientBrief(def.id, def.type, def.target,
                                def.radius, def.reward, def.deadlineTicks,
                                state(packet.activeStates()[index]), true,
                                packet.activeDeadlineAt()[index], -1,
                                packet.activeDest()[index]);
                        mission.setCargo(packet.activeCargoTotal()[index],
                                packet.activeCargoPerCrate()[index]);
                        mine.add(mission);
                    });
        }
        active = List.copyOf(mine);
        List<Mission> pool = new ArrayList<>();
        for (int i = 0; i < packet.poolIds().length; i++) {
            final int index = i;
            definitions.stream()
                    .filter(def -> def.id == packet.poolIds()[index]).findFirst()
                    .ifPresent(def -> pool.add(Mission.clientBrief(def.id, def.type, def.target,
                            def.radius, def.reward, def.deadlineTicks,
                            state(packet.poolStates()[index]), packet.poolTaken()[index], 0L)));
        }
        offered = List.copyOf(pool);
        java.util.Map<Integer, Integer> offersMap = new java.util.HashMap<>();
        for (int i = 0; i < packet.offerIds().length; i++) {
            offersMap.put(packet.offerIds()[i], packet.offerDests()[i]);
        }
        offers = java.util.Map.copyOf(offersMap);
    }

    private static Mission.State state(int ordinal) {
        Mission.State[] values = Mission.State.values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public static int credits() {
        return credits;
    }

    /** 服务端发包时的游戏时刻，用于倒计时换算。 */
    public static long nowGameTime() {
        return nowGameTime;
    }

    /**
     * 当前站挂出来的、能接去 {@code destination} 的报价
     * （原作海图只列当前目的地能接的委托）。
     */
    public static List<Mission> grantedFor(int destination) {
        List<Mission> out = new ArrayList<>();
        for (Mission mission : offered) {
            Integer dest = offers.get(mission.id);
            if (dest != null && dest == destination
                    && mission.state() == Mission.State.OFFERED && !mission.isTaken()) {
                out.add(mission);
            }
        }
        out.sort(java.util.Comparator.comparingInt(mission -> mission.id));
        return out;
    }

    /** 当前站给这条任务挂的目的地；没有报价返回 -1（对话框显示用）。 */
    public static int offerDestination(int missionId) {
        Integer dest = offers.get(missionId);
        return dest == null ? -1 : dest;
    }

    /** 我这一趟进行中的任务（0..3 条）。 */
    public static List<Mission> active() {
        return active;
    }

    /** 按 id 找进行中的任务；没有返回 null。 */
    public static Mission active(int id) {
        for (Mission mission : active) {
            if (mission.id == id) {
                return mission;
            }
        }
        return null;
    }

    /** 任务池（含已被别人接走/已完成的状态）。 */
    public static List<Mission> offered() {
        return offered;
    }
}
