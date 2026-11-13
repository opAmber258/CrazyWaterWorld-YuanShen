package com.opamb.barotrauma.network;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道。
 */
public final class ModNetwork {

    // 8：站务员改成对话发任务（C2S 提问 + S2C 对话），报价按站点各自刷新。
    private static final String PROTOCOL = "8";
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
        CHANNEL.messageBuilder(NpcTalkPacket.class, nextId++,
                        net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(NpcTalkPacket::encode).decoder(NpcTalkPacket::decode)
                .consumerNetworkThread(NpcTalkPacket::handle).add();
        CHANNEL.messageBuilder(NpcDialoguePacket.class, nextId++,
                        net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NpcDialoguePacket::encode).decoder(NpcDialoguePacket::decode)
                .consumerNetworkThread(NpcDialoguePacket::handle).add();
        CHANNEL.registerMessage(nextId++, OpenCampaignMapPacket.class,
                OpenCampaignMapPacket::encode, OpenCampaignMapPacket::decode,
                OpenCampaignMapPacket::handle);
        CHANNEL.registerMessage(nextId++, PumpActionPacket.class,
                PumpActionPacket::encode, PumpActionPacket::decode, PumpActionPacket::handle);
        CHANNEL.registerMessage(nextId++, ReactorActionPacket.class,
                ReactorActionPacket::encode, ReactorActionPacket::decode, ReactorActionPacket::handle);
        CHANNEL.registerMessage(nextId++, ShipHullDamagePacket.class,
                ShipHullDamagePacket::encode, ShipHullDamagePacket::decode, ShipHullDamagePacket::handle);
        CHANNEL.messageBuilder(MedicalSyncPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MedicalSyncPacket::encode).decoder(MedicalSyncPacket::decode)
                .consumerNetworkThread(MedicalSyncPacket::handle).add();
        CHANNEL.messageBuilder(MedicalRequestPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(MedicalRequestPacket::encode).decoder(MedicalRequestPacket::decode)
                .consumerNetworkThread(MedicalRequestPacket::handle).add();
        CHANNEL.messageBuilder(MedicalTreatPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(MedicalTreatPacket::encode).decoder(MedicalTreatPacket::decode)
                .consumerNetworkThread(MedicalTreatPacket::handle).add();
        CHANNEL.messageBuilder(SwimStatePacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SwimStatePacket::encode).decoder(SwimStatePacket::decode)
                .consumerNetworkThread(SwimStatePacket::handle).add();
        CHANNEL.messageBuilder(MedicalDebugPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(MedicalDebugPacket::encode).decoder(MedicalDebugPacket::decode)
                .consumerNetworkThread(MedicalDebugPacket::handle).add();
        CHANNEL.messageBuilder(CthulhuGazePacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(CthulhuGazePacket::encode).decoder(CthulhuGazePacket::decode)
                .consumerNetworkThread(CthulhuGazePacket::handle).add();
        CHANNEL.messageBuilder(ItemSubSlotPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(ItemSubSlotPacket::encode).decoder(ItemSubSlotPacket::decode)
                .consumerNetworkThread(ItemSubSlotPacket::handle).add();
        CHANNEL.messageBuilder(OpenCratePacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenCratePacket::encode).decoder(OpenCratePacket::decode)
                .consumerNetworkThread(OpenCratePacket::handle).add();
        CHANNEL.messageBuilder(CrateSlotPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(CrateSlotPacket::encode).decoder(CrateSlotPacket::decode)
                .consumerNetworkThread(CrateSlotPacket::handle).add();
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
