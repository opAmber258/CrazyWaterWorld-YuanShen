package com.opamb.barotrauma.network;

import com.opamb.barotrauma.world.shipyard.ShipyardServer;
import com.opamb.barotrauma.world.shipyard.ShipyardShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：编辑器多模式批量填充/椭球艇壳/材质替换/直线/框选拆除。 */
public record ShipyardFillPacket(BlockPos a, BlockPos b, byte mode,
                                 byte mirrorAxis, int mirrorCoord,
                                 CompoundTag state, CompoundTag filter) {

    public static ShipyardFillPacket of(BlockPos a, BlockPos b, BlockState state) {
        return of(a, b, ShipyardShapes.MODE_FILL_SOLID, ShipyardShapes.MIRROR_NONE, 0, state);
    }

    public static ShipyardFillPacket of(BlockPos a, BlockPos b, byte mode,
                                        byte mirrorAxis, int mirrorCoord, BlockState state) {
        return new ShipyardFillPacket(a.immutable(), b.immutable(), mode,
                mirrorAxis, mirrorCoord, NbtUtils.writeBlockState(state), new CompoundTag());
    }

    public static ShipyardFillPacket replace(BlockPos a, BlockPos b,
                                             byte mirrorAxis, int mirrorCoord,
                                             BlockState newState, BlockState filterState) {
        return new ShipyardFillPacket(a.immutable(), b.immutable(), ShipyardShapes.MODE_REPLACE,
                mirrorAxis, mirrorCoord, NbtUtils.writeBlockState(newState),
                filterState == null ? new CompoundTag() : NbtUtils.writeBlockState(filterState));
    }

    public static ShipyardFillPacket clear(BlockPos a, BlockPos b,
                                           byte mirrorAxis, int mirrorCoord) {
        return new ShipyardFillPacket(a.immutable(), b.immutable(),
                ShipyardShapes.MODE_CLEAR, mirrorAxis, mirrorCoord,
                new CompoundTag(), new CompoundTag());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(a);
        buf.writeBlockPos(b);
        buf.writeByte(mode);
        buf.writeByte(mirrorAxis);
        buf.writeVarInt(mirrorCoord);
        buf.writeNbt(state);
        buf.writeNbt(filter);
    }

    public static ShipyardFillPacket decode(FriendlyByteBuf buf) {
        BlockPos a = buf.readBlockPos();
        BlockPos b = buf.readBlockPos();
        byte mode = buf.readByte();
        byte mirrorAxis = buf.readByte();
        int mirrorCoord = buf.readVarInt();
        CompoundTag state = buf.readNbt();
        CompoundTag filter = buf.readNbt();
        return new ShipyardFillPacket(a, b, mode, mirrorAxis, mirrorCoord,
                state == null ? new CompoundTag() : state,
                filter == null ? new CompoundTag() : filter);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ShipyardServer.fill(player, a, b, mode, mirrorAxis, mirrorCoord, state, filter);
            }
        });
        context.setPacketHandled(true);
    }
}
