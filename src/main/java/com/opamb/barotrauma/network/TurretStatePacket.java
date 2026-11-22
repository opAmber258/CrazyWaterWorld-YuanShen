package com.opamb.barotrauma.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：炮塔的权威状态。
 *
 * <p>{@code operatorEntityId} 等于本地玩家实体 id 时进入炮塔视角；变回 -1 则强制退出。
 * 其他人的客户端只用 yaw/pitch/cooldown 渲染炮管和后坐。</p>
 *
 * <p>{@code fromStation} 表示占用者是通过瞄准站遥控接管：此时操作者本体留在站台，
 * 客户端命中预判要把自己算进合法目标；直连炮塔时则要把自己排除。</p>
 *
 * <p>{@code controllerPos} 非空表示通过潜望镜进入：客户端用它校验控制器还在。
 * {@code cameraAnchor} 是服务端算好的炮塔镜头世界坐标。
 * 物理船上的方块坐标是船坞坐标，不能直接当镜头，所以必须由服务端换算后下发。</p>
 */
public record TurretStatePacket(BlockPos pos, float yaw, float pitch, int cooldown,
                                int operatorEntityId, boolean fromStation,
                                BlockPos controllerPos, Vec3 cameraAnchor) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeVarInt(cooldown);
        buf.writeVarInt(operatorEntityId);
        buf.writeBoolean(fromStation);
        buf.writeBoolean(controllerPos != null);
        if (controllerPos != null) {
            buf.writeBlockPos(controllerPos);
        }
        buf.writeBoolean(cameraAnchor != null);
        if (cameraAnchor != null) {
            buf.writeDouble(cameraAnchor.x);
            buf.writeDouble(cameraAnchor.y);
            buf.writeDouble(cameraAnchor.z);
        }
    }

    public static TurretStatePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        int cooldown = buf.readVarInt();
        int operator = buf.readVarInt();
        boolean fromStation = buf.readBoolean();
        BlockPos controller = buf.readBoolean() ? buf.readBlockPos() : null;
        Vec3 camera = buf.readBoolean()
                ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
        return new TurretStatePacket(pos, yaw, pitch, cooldown, operator, fromStation,
                controller, camera);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.TurretClient.onState(this)));
        context.setPacketHandled(true);
    }
}
