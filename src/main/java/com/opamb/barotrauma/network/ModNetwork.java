package com.opamb.barotrauma.network;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 网络通道。
 */
public final class ModNetwork {

    // 13：购买确认两个交付选项 + 商店三标签。
    // 14：电力面板增加电池条目（类型/电量/容量）与全船电池统计；诊所状态包区分“首次打开 / 治疗后刷新”。
    // 15：修理操作（持续修理 TICK / QTE）+ 电池面板。
    // 16：修理改为独立窗口（RepairMenu）：START/QTE/STOP，进度在服务端按 tick 推进。
    // 17：加工台制作/取消改走独立包（原版按钮编号只有 1 字节，装不下配方下标 + 数量）。
    // 18：新增 MedicalHighlightPacket（H 键长按选队友的服务端高光）。
    private static final String PROTOCOL = "18";
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
        CHANNEL.messageBuilder(MedicalHighlightPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(MedicalHighlightPacket::encode).decoder(MedicalHighlightPacket::decode)
                .consumerNetworkThread(MedicalHighlightPacket::handle).add();
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
        CHANNEL.messageBuilder(PowerStatePacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PowerStatePacket::encode).decoder(PowerStatePacket::decode)
                .consumerNetworkThread(PowerStatePacket::handle).add();
        CHANNEL.messageBuilder(PowerBindPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(PowerBindPacket::encode).decoder(PowerBindPacket::decode)
                .consumerNetworkThread(PowerBindPacket::handle).add();
        CHANNEL.messageBuilder(RepairActionPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(RepairActionPacket::encode).decoder(RepairActionPacket::decode)
                .consumerNetworkThread(RepairActionPacket::handle).add();
        CHANNEL.messageBuilder(PowerOpenRequestPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(PowerOpenRequestPacket::encode).decoder(PowerOpenRequestPacket::decode)
                .consumerNetworkThread(PowerOpenRequestPacket::handle).add();
        CHANNEL.messageBuilder(StoreStatePacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StoreStatePacket::encode).decoder(StoreStatePacket::decode)
                .consumerNetworkThread(StoreStatePacket::handle).add();
        CHANNEL.messageBuilder(StoreActionPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(StoreActionPacket::encode).decoder(StoreActionPacket::decode)
                .consumerNetworkThread(StoreActionPacket::handle).add();
        CHANNEL.messageBuilder(ClinicStatePacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClinicStatePacket::encode).decoder(ClinicStatePacket::decode)
                .consumerNetworkThread(ClinicStatePacket::handle).add();
        CHANNEL.messageBuilder(ClinicActionPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClinicActionPacket::encode).decoder(ClinicActionPacket::decode)
                .consumerNetworkThread(ClinicActionPacket::handle).add();
        CHANNEL.messageBuilder(FabricatorActionPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(FabricatorActionPacket::encode).decoder(FabricatorActionPacket::decode)
                .consumerNetworkThread(FabricatorActionPacket::handle).add();
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
