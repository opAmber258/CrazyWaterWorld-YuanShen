package com.opamb.barotrauma.network;

import com.opamb.barotrauma.client.TurretClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Visual event sent only after the server accepts a shot. */
public record CoilgunShotPacket(BlockPos turret, Vec3 muzzle, Vec3 direction) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(turret);
        writePoint(buf, muzzle);
        writePoint(buf, direction);
    }

    public static CoilgunShotPacket decode(FriendlyByteBuf buf) {
        return new CoilgunShotPacket(buf.readBlockPos(), readPoint(buf), readPoint(buf));
    }

    private static void writePoint(FriendlyByteBuf buf, Vec3 point) {
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
    }

    private static Vec3 readPoint(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> TurretClient.onCoilgunShot(this)));
        context.setPacketHandled(true);
    }
}
