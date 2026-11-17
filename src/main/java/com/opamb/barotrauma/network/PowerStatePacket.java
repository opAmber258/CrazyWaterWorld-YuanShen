package com.opamb.barotrauma.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端 → 客户端：接线盒电力面板状态。 */
public record PowerStatePacket(BlockPos boxPos, double supplyKw, double demandKw,
                               double voltage, double condition, double boxLoadKw,
                               boolean open, double batteryOutputKw, double storedKwm,
                               double storageCapacityKwm, List<Device> devices) {

    /** 设备类型：0 = 用电设备，1 = 电池。 */
    public static final int KIND_CONSUMER = 0;
    public static final int KIND_BATTERY = 1;

    public record Device(BlockPos pos, String nameKey, double demandKw,
                         double minVoltage, BlockPos box, int kind,
                         double chargeFraction, double storedKwm, double capacityKwm) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(boxPos);
        buf.writeDouble(supplyKw);
        buf.writeDouble(demandKw);
        buf.writeDouble(voltage);
        buf.writeDouble(condition);
        buf.writeDouble(boxLoadKw);
        buf.writeBoolean(open);
        buf.writeDouble(batteryOutputKw);
        buf.writeDouble(storedKwm);
        buf.writeDouble(storageCapacityKwm);
        buf.writeVarInt(devices.size());
        for (Device device : devices) {
            buf.writeBlockPos(device.pos());
            buf.writeUtf(device.nameKey());
            buf.writeDouble(device.demandKw());
            buf.writeDouble(device.minVoltage());
            buf.writeBoolean(device.box() != null);
            if (device.box() != null) {
                buf.writeBlockPos(device.box());
            }
            buf.writeByte(device.kind());
            buf.writeDouble(device.chargeFraction());
            buf.writeDouble(device.storedKwm());
            buf.writeDouble(device.capacityKwm());
        }
    }

    public static PowerStatePacket decode(FriendlyByteBuf buf) {
        BlockPos box = buf.readBlockPos();
        double supply = buf.readDouble();
        double demand = buf.readDouble();
        double voltage = buf.readDouble();
        double condition = buf.readDouble();
        double load = buf.readDouble();
        boolean open = buf.readBoolean();
        double batteryOutput = buf.readDouble();
        double stored = buf.readDouble();
        double storageCapacity = buf.readDouble();
        int count = buf.readVarInt();
        List<Device> devices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos = buf.readBlockPos();
            String key = buf.readUtf();
            double deviceDemand = buf.readDouble();
            double minVoltage = buf.readDouble();
            BlockPos bound = buf.readBoolean() ? buf.readBlockPos() : null;
            int kind = buf.readByte();
            double chargeFraction = buf.readDouble();
            double storedKwm = buf.readDouble();
            double capacityKwm = buf.readDouble();
            devices.add(new Device(pos, key, deviceDemand, minVoltage, bound, kind,
                    chargeFraction, storedKwm, capacityKwm));
        }
        return new PowerStatePacket(box, supply, demand, voltage, condition, load,
                open, batteryOutput, stored, storageCapacity, devices);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> com.opamb.barotrauma.client.JunctionBoxScreen.updateOrOpen(this));
        context.setPacketHandled(true);
    }
}
