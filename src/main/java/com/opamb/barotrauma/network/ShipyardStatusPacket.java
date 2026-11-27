package com.opamb.barotrauma.network;

import com.opamb.barotrauma.client.ShipyardClient;
import com.opamb.barotrauma.world.shipyard.ShipyardValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端 → 客户端：船厂校验结果、三维诊断高亮、潜艇性能预算、舱室着色与连线拓扑。 */
public record ShipyardStatusPacket(int blocks, int sizeX, int sizeY, int sizeZ,
                                   int compartments, int openPockets,
                                   boolean connected, boolean sealed,
                                   List<ShipyardValidator.Requirement> requirements,
                                   List<BlockPos> disconnectedPos,
                                   List<BlockPos> leakPos,
                                   BlockPos minPos, BlockPos maxPos,
                                   int totalInteriorVolume, int ballastVolume,
                                   int powerGenKw, int powerLoadKw, int engineCount,
                                   List<BlockPos> ballastFloorCells,
                                   List<BlockPos> dryFloorCells,
                                   List<ShipyardValidator.WireLink> wireLinks) {

    public static ShipyardStatusPacket of(ShipyardValidator.Report report) {
        return new ShipyardStatusPacket(report.blocks(), report.sizeX(), report.sizeY(), report.sizeZ(),
                report.compartments(), report.openPockets(), report.connected(), report.sealed(),
                report.requirements(), report.disconnectedPos(), report.leakPos(),
                report.minPos(), report.maxPos(),
                report.totalInteriorVolume(), report.ballastVolume(),
                report.powerGenKw(), report.powerLoadKw(), report.engineCount(),
                report.ballastFloorCells(), report.dryFloorCells(), report.wireLinks());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(blocks);
        buf.writeVarInt(sizeX);
        buf.writeVarInt(sizeY);
        buf.writeVarInt(sizeZ);
        buf.writeVarInt(compartments);
        buf.writeVarInt(openPockets);
        buf.writeBoolean(connected);
        buf.writeBoolean(sealed);
        buf.writeVarInt(requirements.size());
        for (ShipyardValidator.Requirement requirement : requirements) {
            buf.writeUtf(requirement.id(), 32);
            buf.writeByte(requirement.state().ordinal());
            buf.writeUtf(requirement.detail(), 256);
            if (requirement.target() == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeBlockPos(requirement.target());
            }
        }
        writePosList(buf, disconnectedPos);
        writePosList(buf, leakPos);
        if (minPos != null && maxPos != null) {
            buf.writeBoolean(true);
            buf.writeBlockPos(minPos);
            buf.writeBlockPos(maxPos);
        } else {
            buf.writeBoolean(false);
        }
        buf.writeVarInt(totalInteriorVolume);
        buf.writeVarInt(ballastVolume);
        buf.writeVarInt(powerGenKw);
        buf.writeVarInt(powerLoadKw);
        buf.writeVarInt(engineCount);
        writePosList(buf, ballastFloorCells);
        writePosList(buf, dryFloorCells);
        buf.writeVarInt(wireLinks.size());
        for (ShipyardValidator.WireLink link : wireLinks) {
            buf.writeBlockPos(link.from());
            buf.writeBlockPos(link.to());
            buf.writeByte(link.kind());
        }
    }

    public static ShipyardStatusPacket decode(FriendlyByteBuf buf) {
        int blocks = buf.readVarInt();
        int sizeX = buf.readVarInt();
        int sizeY = buf.readVarInt();
        int sizeZ = buf.readVarInt();
        int compartments = buf.readVarInt();
        int openPockets = buf.readVarInt();
        boolean connected = buf.readBoolean();
        boolean sealed = buf.readBoolean();
        int count = buf.readVarInt();
        List<ShipyardValidator.Requirement> requirements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(32);
            ShipyardValidator.State state = ShipyardValidator.State.values()[buf.readByte()];
            String detail = buf.readUtf(256);
            BlockPos target = buf.readBoolean() ? buf.readBlockPos() : null;
            requirements.add(new ShipyardValidator.Requirement(id, state, detail, target));
        }
        List<BlockPos> disconnectedPos = readPosList(buf);
        List<BlockPos> leakPos = readPosList(buf);
        BlockPos minPos = null;
        BlockPos maxPos = null;
        if (buf.readBoolean()) {
            minPos = buf.readBlockPos();
            maxPos = buf.readBlockPos();
        }
        int totalInteriorVolume = buf.readVarInt();
        int ballastVolume = buf.readVarInt();
        int powerGenKw = buf.readVarInt();
        int powerLoadKw = buf.readVarInt();
        int engineCount = buf.readVarInt();
        List<BlockPos> ballastFloorCells = readPosList(buf);
        List<BlockPos> dryFloorCells = readPosList(buf);
        int wireCount = Math.min(256, Math.max(0, buf.readVarInt()));
        List<ShipyardValidator.WireLink> wireLinks = new ArrayList<>(wireCount);
        for (int i = 0; i < wireCount; i++) {
            wireLinks.add(new ShipyardValidator.WireLink(
                    buf.readBlockPos(), buf.readBlockPos(), buf.readByte()));
        }
        return new ShipyardStatusPacket(blocks, sizeX, sizeY, sizeZ, compartments, openPockets,
                connected, sealed, requirements, disconnectedPos, leakPos, minPos, maxPos,
                totalInteriorVolume, ballastVolume, powerGenKw, powerLoadKw, engineCount,
                ballastFloorCells, dryFloorCells, List.copyOf(wireLinks));
    }

    private static void writePosList(FriendlyByteBuf buf, List<BlockPos> list) {
        buf.writeVarInt(list.size());
        for (BlockPos pos : list) {
            buf.writeBlockPos(pos);
        }
    }

    private static List<BlockPos> readPosList(FriendlyByteBuf buf) {
        int size = Math.min(512, Math.max(0, buf.readVarInt()));
        List<BlockPos> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(buf.readBlockPos());
        }
        return List.copyOf(out);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShipyardClient.acceptStatus(this)));
        context.setPacketHandled(true);
    }
}
