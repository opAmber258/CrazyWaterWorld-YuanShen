package com.opamb.barotrauma.network;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道。
 */
public final class ModNetwork {

    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BarotraumaMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int nextId;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(CampaignStatePacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CampaignStatePacket::encode).decoder(CampaignStatePacket::decode)
                .consumerNetworkThread(CampaignStatePacket::handle).add();
        CHANNEL.messageBuilder(CampaignActionPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(CampaignActionPacket::encode).decoder(CampaignActionPacket::decode)
                .consumerNetworkThread(CampaignActionPacket::handle).add();
        CHANNEL.registerMessage(nextId++, ShipActionPacket.class,
                ShipActionPacket::encode, ShipActionPacket::decode, ShipActionPacket::handle);
        CHANNEL.registerMessage(nextId++, VesselPalettePacket.class,
                VesselPalettePacket::encode, VesselPalettePacket::decode, VesselPalettePacket::handle);
        CHANNEL.registerMessage(nextId++, SonarPingPacket.class,
                SonarPingPacket::encode, SonarPingPacket::decode, SonarPingPacket::handle);
        CHANNEL.registerMessage(nextId++, SonarDataPacket.class,
                SonarDataPacket::encode, SonarDataPacket::decode, SonarDataPacket::handle);
        CHANNEL.registerMessage(nextId++, ShipWaterStatePacket.class,
                ShipWaterStatePacket::encode, ShipWaterStatePacket::decode, ShipWaterStatePacket::handle);
        CHANNEL.registerMessage(nextId++, MissionStatePacket.class,
                MissionStatePacket::encode, MissionStatePacket::decode, MissionStatePacket::handle);
        CHANNEL.registerMessage(nextId++, MissionActionPacket.class,
                MissionActionPacket::encode, MissionActionPacket::decode, MissionActionPacket::handle);
        CHANNEL.registerMessage(nextId++, PumpActionPacket.class,
                PumpActionPacket::encode, PumpActionPacket::decode, PumpActionPacket::handle);
        CHANNEL.registerMessage(nextId++, ShipHullDamagePacket.class,
                ShipHullDamagePacket::encode, ShipHullDamagePacket::decode, ShipHullDamagePacket::handle);
        CHANNEL.registerMessage(nextId++, MedicalSyncPacket.class,
                MedicalSyncPacket::encode, MedicalSyncPacket::decode, MedicalSyncPacket::handle);
        CHANNEL.registerMessage(nextId++, MedicalRequestPacket.class,
                MedicalRequestPacket::encode, MedicalRequestPacket::decode, MedicalRequestPacket::handle);
        CHANNEL.registerMessage(nextId++, MedicalTreatPacket.class,
                MedicalTreatPacket::encode, MedicalTreatPacket::decode, MedicalTreatPacket::handle);
    }

    public static <MSG> void sendToAll(MSG message) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), message);
    }

    public static <MSG> void sendToPlayer(net.minecraft.server.level.ServerPlayer player, MSG message) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** 客户端 → 服务端的通用发送（治疗面板的请求/用药都走它）。 */
    public static <MSG> void sendToServer(MSG message) {
        CHANNEL.sendToServer(message);
    }

    public static void send(com.opamb.barotrauma.network.ShipActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void send(SonarPingPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}
