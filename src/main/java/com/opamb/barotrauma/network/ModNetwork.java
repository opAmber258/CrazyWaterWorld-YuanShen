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
    // 18：炮塔原型新增瞄准/开火/退出/状态同步包（在 17 基础上追加）。
    // 19：右键拾取改走实体 id 包；物理船方块右键改走船坞坐标包（制氧 WIP 分支）。
    // 20：合并后两批包同时存在，统一升到 20，避免与只含其中一批的开发构建混连。
    // 21：水面破口行 8→9 float（新增开口类型），客户端/服务端必须同版本。
    // 22：SwimStatePacket 增加服务端权威泳姿位（客户端不再自己算舱顶净高）。
    // 23：TurretStatePacket 增加潜望镜控制器位置（客户端镜头锚点）。
    // 24：TurretStatePacket 增加服务端换算好的世界镜头位置（物理船船坞坐标 → 世界坐标）。
    // 25：TurretStatePacket 增加 fromStation（瞄准站遥控标记），客户端命中预判据此处理操作者本体。
    // 26：ShipWaterStatePacket 同步门洞实际下落水流。
    // 27：ShipWaterStatePacket 增加侧门溢流行（8 float：门槛/方向/水柱顶/接收水面/流量）。
    // 28：SwimStatePacket 首字段语义改为“是否启用水中移动”（疾跑泳姿改由客户端预测）。
    // 29：ShipWaterStatePacket 增加源维度（方块船水包是广播的，客户端拒绝异维度同坐标）。
    // 30：TurretStatePacket 增加 autoFire；新增 TurretModePacket（客户端请求切换单发/全自动）。
    // 31：灯具 RGBA（A=强度）面板改走独立 LightColorPacket。
    // 32：TurretStatePacket 增加弹药/供电，客户端据此拦截空射并显示弹药。
    // 33：追踪玩家手电筒状态的实体数据字段。
    // 34：新增 TurretHitPacket（服务端确认命中/击杀，客户端据此播放准星反馈）。
    // 35：新增 DummySettingsPacket（高级训练台血量/自发光/重生间隔）。
    // 36：DummySettingsPacket 带设置版本（拒绝过期草稿）。
    // 37：SonarDataPacket/SonarPingPacket 改为按脉冲与回波同步（世界坐标 + 到达 tick）。
    // 38：ShipActionPacket 增加驾驶状态/定点目标与舵令确认，功率拒绝回显。
    // 39：RepairActionPacket 与设备修理/接线盒菜单改版。
    // 40：新增 NightVisionPacket（亮度调节器面板的无限夜视开关）。
    private static final String PROTOCOL = "40";
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
        // 炮塔原型：进入后客户端每 tick 发绝对角度，服务端限速并广播状态。
        CHANNEL.messageBuilder(TurretAimPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(TurretAimPacket::encode).decoder(TurretAimPacket::decode)
                .consumerNetworkThread(TurretAimPacket::handle).add();
        CHANNEL.messageBuilder(TurretFirePacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(TurretFirePacket::encode).decoder(TurretFirePacket::decode)
                .consumerNetworkThread(TurretFirePacket::handle).add();
        CHANNEL.messageBuilder(TurretModePacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(TurretModePacket::encode).decoder(TurretModePacket::decode)
                .consumerNetworkThread(TurretModePacket::handle).add();
        CHANNEL.messageBuilder(TurretExitPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(TurretExitPacket::encode).decoder(TurretExitPacket::decode)
                .consumerNetworkThread(TurretExitPacket::handle).add();
        CHANNEL.messageBuilder(TurretStatePacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TurretStatePacket::encode).decoder(TurretStatePacket::decode)
                .consumerNetworkThread(TurretStatePacket::handle).add();
        CHANNEL.messageBuilder(TurretHitPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TurretHitPacket::encode).decoder(TurretHitPacket::decode)
                .consumerNetworkThread(TurretHitPacket::handle).add();
        CHANNEL.messageBuilder(PickupItemPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(PickupItemPacket::encode).decoder(PickupItemPacket::decode)
                .consumerNetworkThread(PickupItemPacket::handle).add();
        CHANNEL.messageBuilder(ShipUseBlockPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShipUseBlockPacket::encode).decoder(ShipUseBlockPacket::decode)
                .consumerNetworkThread(ShipUseBlockPacket::handle).add();
CHANNEL.messageBuilder(LightColorPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(LightColorPacket::encode).decoder(LightColorPacket::decode)
                .consumerNetworkThread(LightColorPacket::handle).add();
        CHANNEL.messageBuilder(DummySettingsPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(DummySettingsPacket::encode).decoder(DummySettingsPacket::decode)
                .consumerNetworkThread(DummySettingsPacket::handle).add();
        CHANNEL.messageBuilder(NightVisionPacket.class, nextId++, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(NightVisionPacket::encode).decoder(NightVisionPacket::decode)
                .consumerNetworkThread(NightVisionPacket::handle).add();
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
