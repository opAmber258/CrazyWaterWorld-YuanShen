package com.opamb.barotrauma.ship.vs;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

/**
 * 让物理船带着舱内玩家走（服务端）。
 *
 * <p>VS2 只拖拽“站在船上”的实体（{@code lastShipStoodOn} + 25 tick 宽限）。
 * 玩家在进水舱里游泳时不算站在船上，船下沉/移动时会被留在原地——相对船体
 * 看起来就是“贴着天花板游，从天花板穿到上面”（用户实测；当时船正在以
 * 约 8 格/秒下沉）。这里在玩家位于物理船舱内时每 tick 把他标记为随船，
 * VS2 的 {@code EntityDragger} 就会按船的变换增量带着他走。</p>
 *
 * <p>只在 {@link VsSupport#available()} 且确实在舱内时调用；VS2 类型只出现在
 * 这个 {@code ship/vs/} 包里。</p>
 */
public final class VsServerDrag {

    private VsServerDrag() {
    }

    /**
     * 玩家是否在物理船舱里（干舱/进水舱都算）。
     *
     * <p>直接用 VS2 桥的舱室查询，不经过 {@code OceanEnvironment.isInOcean}：
     * 船在接近水面时脚部可能高过 {@code WATER_TOP_Y}，但人在船里仍然必须被拖。</p>
     */
    public static boolean isInsideCompartment(ServerLevel level, Player player) {
        if (level == null || player == null || !VsSupport.available()) {
            return false;
        }
        try {
            return VsState.queryPlayerInDryCompartment(level, player.position()) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 玩家脚所在舱室的头顶净高是否放得下 {@code height} 格站立碰撞箱。
     *
     * @return null = 不在物理船里/无法判定；true = 能站；false = 天花板太低
     */
    public static Boolean standingRoom(ServerLevel level, Player player, double height) {
        if (level == null || player == null || !VsSupport.available()) {
            return null;
        }
        try {
            return VsState.queryStandingRoom(level, player.position(), height);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 脚是否贴着舱底/平台顶（水里只有落到地面才允许站起来）。 */
    public static Boolean nearFloor(ServerLevel level, Player player, double maxAbove) {
        if (level == null || player == null || !VsSupport.available()) {
            return null;
        }
        try {
            return VsState.queryNearFloor(level, player.position(), maxAbove);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void keepDragged(ServerLevel level, Player player, long shipId) {
        if (level == null || player == null || shipId < 0L || !VsSupport.available()) {
            return;
        }
        try {
            if (player instanceof IEntityDraggingInformationProvider provider) {
                EntityDraggingInformation info = provider.getDraggingInformation();
                info.setLastShipStoodOn(shipId);
                info.setTicksSinceStoodOnShip(0);
                if (LOGGED.add(player.getUUID())) {
                    BarotraumaMod.LOGGER.info("[vs] 舱内玩家开始随船拖拽：{} ship={}",
                            player.getName().getString(), shipId);
                }
            }
        } catch (Throwable ignored) {
            // VS2 没加载 / 船世界还没准备好：这一 tick 不拖，下一 tick 再试。
        }
    }

    /** 绝对传送前丢掉 VS2 的随船坐标和惯性，防止下一帧应用旧船的位移。 */
    public static void detach(Entity entity) {
        if (!(entity instanceof IEntityDraggingInformationProvider provider)) {
            return;
        }
        EntityDraggingInformation info = provider.getDraggingInformation();
        info.setLastShipStoodOn(null);
        info.setLastShipStoodOnServerWriteOnly(null);
        info.setTicksSinceStoodOnShip(EntityDraggingInformation.TICKS_TO_DRAG_ENTITIES);
        info.setAddedMovementLastTick(new org.joml.Vector3d());
        info.setAddedYawRotLastTick(0);
        info.setShouldImpulseMovement(false);
        info.setRestoreCachedLastPosition(false);
        info.setServerRelativePlayerPosition(null);
        info.setServerRelativePlayerYaw(null);
    }

    /** 每个玩家只打一次，确认拖拽真的挂上了。 */
    private static final java.util.Set<java.util.UUID> LOGGED = new java.util.HashSet<>();
}
