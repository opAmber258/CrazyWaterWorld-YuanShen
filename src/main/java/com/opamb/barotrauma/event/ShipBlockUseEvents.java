package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.BallastPumpBlock;
import com.opamb.barotrauma.block.BulkheadDoorBlock;
import com.opamb.barotrauma.block.CabinetBlock;
import com.opamb.barotrauma.block.DeconstructorBlock;
import com.opamb.barotrauma.block.DrainPumpBlock;
import com.opamb.barotrauma.block.EngineBlock;
import com.opamb.barotrauma.block.FabricatorBlock;
import com.opamb.barotrauma.block.HatchBlock;
import com.opamb.barotrauma.block.JunctionBoxBlock;
import com.opamb.barotrauma.block.LightBlock;
import com.opamb.barotrauma.block.MissionTerminalBlock;
import com.opamb.barotrauma.block.NavigationTerminalBlock;
import com.opamb.barotrauma.block.OxygenGeneratorBlock;
import com.opamb.barotrauma.block.PeriscopeBlock;
import com.opamb.barotrauma.block.ReactorBlock;
import com.opamb.barotrauma.block.SonarBlock;
import com.opamb.barotrauma.block.SupercapacitorBlock;
import com.opamb.barotrauma.block.TurretBlock;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ShipUseBlockPacket;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 物理船上的方块右键：舱门、舱口、货架、柜子、终端、机器面板。
 *
 * <p>VS2 把船体方块收在船坞里，客户端命中结果是船坞坐标；原版的 use-item 包
 * 会被服务端的距离检查丢掉，所以物理船上手动门/舱口/货架原来点不动（用户
 * 实测“连门都开不了”“箱子捡不下来”）。这里在客户端发现“命中的方块远在
 * 船坞”时发 {@link ShipUseBlockPacket}，服务端在船坞方块上调用原版
 * {@code use}。</p>
 *
 * <p>优先级放到最低：让 {@link ItemPickupEvents} 先判断准星里有没有掉落物；
 * 它一旦取消事件（要捡东西），这里就不再抢这次右键。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipBlockUseEvents {

    /** 客户端射线命中的方块坐标离玩家超过这个距离，就一定是船坞坐标（正常交互 4.5 格）。 */
    private static final double SHIPYARD_BLOCK_DISTANCE_SQR = 64.0D;

    /**
     * 按住右键时 {@code Minecraft.startUseItem} 每 4 tick 再触发一次事件；对门/舱口
     * 来说就是每 0.2 秒开关一次，看起来像“打不开”。同一次按住只发一次包，
     * 松开右键（client tick 检测）后才允许下一次；炮塔/舱口的命中格可能在
     * 上下半块之间跳，所以不能按方块坐标去重。
     */
    private static boolean useHeldSent;

    private ShipBlockUseEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && !Minecraft.getInstance().options.keyUse.isDown()) {
            useHeldSent = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !event.getEntity().level().isClientSide) {
            return;
        }
        Player player = event.getEntity();
        if (player.isSpectator() || !VsState.active()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (pos.distSqr(player.blockPosition()) <= SHIPYARD_BLOCK_DISTANCE_SQR) {
            // 普通世界/停靠状态的方块：交给原版。
            return;
        }
        BlockState state = player.level().getBlockState(pos);
        if (!interactive(state)) {
            // 船壳等非交互方块不拦：放置方块等操作仍走原版/VS2 路径。
            return;
        }
        boolean useDown = Minecraft.getInstance().options.keyUse.isDown();
        if (useDown && useHeldSent) {
            // 同一次按住里的重复触发：只取消，不再发包（否则门每 4 tick 翻一次）。
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        useHeldSent = useDown;
        Vec3 hit = event.getHitVec().getLocation();
        // 相对命中点用世界坐标的小数部分：船的偏航只影响水平面，Y 分层的
        // 货架/机器面板读到的层高仍然正确。
        double relX = hit.x - Math.floor(hit.x);
        double relY = hit.y - Math.floor(hit.y);
        double relZ = hit.z - Math.floor(hit.z);
        ModNetwork.sendToServer(new ShipUseBlockPacket(pos, event.getFace(), event.getHand(),
                relX, relY, relZ, hit));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** 这些方块的 {@code use} 才是“打开/搬东西”；船壳、梯子等不要拦。 */
    private static boolean interactive(BlockState state) {
        var block = state.getBlock();
        return block instanceof BulkheadDoorBlock
                || block instanceof HatchBlock
                || block instanceof CabinetBlock
                || block instanceof NavigationTerminalBlock
                || block instanceof SonarBlock
                || block instanceof MissionTerminalBlock
                || block instanceof FabricatorBlock
                || block instanceof DeconstructorBlock
                || block instanceof OxygenGeneratorBlock
                || block instanceof JunctionBoxBlock
                || block instanceof SupercapacitorBlock
                || block instanceof com.opamb.barotrauma.block.BatteryBlock
                // 灯具面板：物理船上也要能右键开 RGB 面板。
                || block instanceof LightBlock
                // 炮塔/潜望镜放在物理船上时方块也在船坞里，右键坐标同样是船坞坐标。
                || block instanceof com.opamb.barotrauma.block.CoilgunLoaderBlock
                || block instanceof com.opamb.barotrauma.block.TurretStationBlock
                || block instanceof TurretBlock
                || block instanceof PeriscopeBlock
                || block instanceof ReactorBlock
                || block instanceof EngineBlock
                || block instanceof DrainPumpBlock
                || block instanceof BallastPumpBlock;
    }
}
