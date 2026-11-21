package com.opamb.barotrauma.network;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.event.ItemPickupEvents;
import com.opamb.barotrauma.ship.vs.VsPilot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端右键点到地上的掉落物 → 请求服务端拾取。
 *
 * <p>不能只靠服务端的 {@code RightClickBlock}：物理船上 VS2 把方块收进船坞，
 * 客户端命中的方块坐标是船坞坐标（离玩家几百万格），服务端
 * {@code handleUseItemOn} 的距离检查会直接丢掉这个包；空手右键空气（MISS）
 * 时服务端也会丢掉 use-item 包。所以右键拾取改成客户端报实体 id，服务端
 * 再校验距离/存活后按正常规则塞进背包。</p>
 */
public record PickupItemPacket(int entityId) {

    /** 服务端接受的距离（格）：略大于客户端 2.5 格的准星距离，留出延迟余量。 */
    private static final double MAX_DISTANCE = 4.0D;

    public static void encode(PickupItemPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.entityId);
    }

    public static PickupItemPacket decode(FriendlyByteBuf buf) {
        return new PickupItemPacket(buf.readVarInt());
    }

    public static void handle(PickupItemPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.isSpectator()) {
                return;
            }
            Entity entity = player.level().getEntity(message.entityId);
            if (!(entity instanceof ItemEntity item) || item.isRemoved() || !item.isAlive()) {
                BarotraumaMod.LOGGER.info("[pickup] 拒绝 id={}：服务端找不到存活的掉落物实体",
                        message.entityId());
                return;
            }
            double distance = player.distanceToSqr(item);
            if (distance > MAX_DISTANCE * MAX_DISTANCE) {
                BarotraumaMod.LOGGER.info("[pickup] 拒绝 id={}：距离 {}/{} 格",
                        message.entityId(), Math.sqrt(distance), MAX_DISTANCE);
                return;
            }
            // 客户端 findAimedItem 的射线检查约束不了伪造包：服务端补一次遮挡检查。
            // 物理船的船体方块在船坞里，世界射线打不到，坐在物理船舱内时跳过。
            if (!VsPilot.isInsideCompartment(player.level(), player.getEyePosition())
                    && !hasLineOfSight(player, item)) {
                BarotraumaMod.LOGGER.info("[pickup] 拒绝 id={}：被方块遮挡",
                        message.entityId());
                return;
            }
            if (!ItemPickupEvents.pickup(player, item)) {
                BarotraumaMod.LOGGER.info("[pickup] 未进包 id={} item={}：背包/双手格放不下或归属不符",
                        message.entityId(), item.getItem().getDescriptionId());
            }
        });
        context.setPacketHandled(true);
    }

    /** 眼睛到掉落物之间有没有实心方块挡着（掉落物自身的体积留 0.35 格余量）。 */
    private static boolean hasLineOfSight(ServerPlayer player, ItemEntity item) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = item.position();
        double itemDist = eye.distanceTo(target);
        BlockHitResult hit = player.level().clip(new ClipContext(eye, target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        double clear = Math.max(0.0D, itemDist - 0.35D);
        return hit.getLocation().distanceToSqr(eye) >= clear * clear;
    }
}
