package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.mission.MissionCargo;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 让掉在船上的**任务货物散箱**跟着船走。
 *
 * <p>货架一格都放不下时（原版退化规则），货运任务会生成 1 个散箱掉在货架/玩家旁边。
 * 出航后船是 VS2 物理船，普通掉落物不保证跟船，所以这里把任务货物用“相对船核心的
 * 偏移”钉住：每 tick 按船当前世界坐标重新定位，船转向时不做旋转补偿（散箱只在
 * 货架满了这种兜底情况下出现，偏移很小）。</p>
 *
 * <p>船没装配（停泊/解体）时不钉，箱子就停在原地。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CargoFollowEvents {

    /** 追踪半径：船核心周围这么多格内的任务散箱都算“在船上”。 */
    private static final double RADIUS = 48.0D;

    private CargoFollowEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !VsState.active()) {
            return;
        }
        ServerLevel deep = server.getLevel(ModDimensions.THE_DEEP);
        if (deep == null) {
            return;
        }
        Vec3 ship = VsPilot.shipPosition();
        Vec3 coreOffset = VsPilot.shipCoreOffset();
        if (ship == null || coreOffset == null) {
            return;
        }
        Vec3 core = ship.add(coreOffset);
        AABB area = new AABB(core.x - RADIUS, core.y - RADIUS, core.z - RADIUS,
                core.x + RADIUS, core.y + RADIUS, core.z + RADIUS);
        for (ItemEntity item : deep.getEntitiesOfClass(ItemEntity.class, area)) {
            // 只钉"接任务时生成的那只散箱"。玩家自己把货架上的箱子丢下海/丢半路，
            // 不能每 tick 被拉回船边（用户实测会来回闪）。
            if (!item.getPersistentData().getBoolean(MissionCargo.TAG_PINNED)) {
                continue;
            }
            CompoundTag tag = item.getPersistentData();
            String keyX = MissionCargo.TAG_OFFSET + "X";
            String keyY = MissionCargo.TAG_OFFSET + "Y";
            String keyZ = MissionCargo.TAG_OFFSET + "Z";
            if (!tag.contains(keyX)) {
                // 第一次看到它：按当前位置记下相对核心的偏移（船没转的时候足够准）。
                Vec3 delta = item.position().subtract(core);
                tag.putDouble(keyX, delta.x);
                tag.putDouble(keyY, delta.y);
                tag.putDouble(keyZ, delta.z);
            }
            item.setPos(core.x + tag.getDouble(keyX),
                    core.y + tag.getDouble(keyY),
                    core.z + tag.getDouble(keyZ));
            item.setDeltaMovement(Vec3.ZERO);
        }
    }
}
