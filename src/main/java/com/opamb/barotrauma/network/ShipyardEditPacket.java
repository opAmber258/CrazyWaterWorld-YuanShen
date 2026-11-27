package com.opamb.barotrauma.network;

import com.opamb.barotrauma.world.shipyard.ShipyardServer;
import com.opamb.barotrauma.world.shipyard.ShipyardShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：编辑器单块放置/删除（支持镜像对称）。 */
public record ShipyardEditPacket(byte action, BlockPos pos, CompoundTag state,
                                 byte mirrorAxis, int mirrorCoord) {

    public static final byte PLACE = 1;
    public static final byte REMOVE = 2;
    public static final byte UNDO = 3;
    public static final byte REDO = 4;

    public static ShipyardEditPacket place(BlockPos pos, BlockState state) {
        return place(pos, state, ShipyardShapes.MIRROR_NONE, 0);
    }

    public static ShipyardEditPacket place(BlockPos pos, BlockState state,
                                           byte mirrorAxis, int mirrorCoord) {
        return new ShipyardEditPacket(PLACE, pos.immutable(),
                net.minecraft.nbt.NbtUtils.writeBlockState(state), mirrorAxis, mirrorCoord);
    }

    public static ShipyardEditPacket remove(BlockPos pos) {
        return remove(pos, ShipyardShapes.MIRROR_NONE, 0);
    }

    public static ShipyardEditPacket remove(BlockPos pos, byte mirrorAxis, int mirrorCoord) {
        return new ShipyardEditPacket(REMOVE, pos.immutable(), new CompoundTag(),
                mirrorAxis, mirrorCoord);
    }

    public static ShipyardEditPacket undo() {
        return new ShipyardEditPacket(UNDO, BlockPos.ZERO, new CompoundTag(),
                ShipyardShapes.MIRROR_NONE, 0);
    }

    public static ShipyardEditPacket redo() {
        return new ShipyardEditPacket(REDO, BlockPos.ZERO, new CompoundTag(),
                ShipyardShapes.MIRROR_NONE, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeBlockPos(pos);
        buf.writeNbt(state);
        buf.writeByte(mirrorAxis);
        buf.writeVarInt(mirrorCoord);
    }

    public static ShipyardEditPacket decode(FriendlyByteBuf buf) {
        byte action = buf.readByte();
        BlockPos pos = buf.readBlockPos();
        CompoundTag state = buf.readNbt();
        byte mirrorAxis = buf.readByte();
        int mirrorCoord = buf.readVarInt();
        return new ShipyardEditPacket(action, pos, state == null ? new CompoundTag() : state,
                mirrorAxis, mirrorCoord);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ShipyardServer.edit(player, action, pos, state, mirrorAxis, mirrorCoord);
            }
        });
        context.setPacketHandled(true);
    }
}
