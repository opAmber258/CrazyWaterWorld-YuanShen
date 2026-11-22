package com.opamb.barotrauma.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.world.AirOceanSwimmer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 空气海域里的玩家游泳。
 *
 * <p>直立划水和疾游都在 travel 中按输入推进，使用原版的实体移动和 VS2 碰撞。
 * 舱内或舱外的浸没状态仍由服务端同步。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UnderwaterMovement {

    /** 上浮/下潜使用同一速度，避免手感不对称。 */
    private static final double SWIM_VERTICAL_SPEED = 0.12D;

    /** 原作人类普通游和疾游约为 1.5 和 2.5 米/秒，按一格一米换算。 */
    private static final double SWIM_SPEED = 1.5D / 20.0D;
    private static final double SPRINT_SWIM_SPEED = 2.5D / 20.0D;

    /** 自然下沉速度（格/tick）：约 0.4 格/秒，接近原版水里松手后的缓慢下沉。 */
    private static final double NATURAL_SINK_SPEED = 0.02D;

    /** 一键切换"松手后是否自然下沉"；默认关闭 = 保持当前深度。 */
    public static final KeyMapping TOGGLE_NATURAL_SINK = new KeyMapping(
            "key.barotrauma.toggle_natural_sink", GLFW.GLFW_KEY_N,
            "key.categories.barotrauma");

    private static boolean naturalSink;

    private UnderwaterMovement() {
    }

    /** 每 tick 同步随船拖拽和自然下沉开关。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (player.isSpectator() || player.isPassenger()) {
            com.opamb.barotrauma.ship.vs.VsClientDrag.apply(player, -1L);
            // 丢弃积压的 N 键点击。
            while (TOGGLE_NATURAL_SINK.consumeClick()) {
                // discard
            }
            return;
        }
        // 随船拖拽完全跟随服务端下发的 dragShipId：两边用同一个船 id，
        // 不再各算一次"在不在舱里"，避免贴墙时一边拖一边不拖导致位置回拉。
        if (ClientSwimState.known()) {
            com.opamb.barotrauma.ship.vs.VsClientDrag.apply(
                    player, ClientSwimState.dragShipId());
        }
        // 一键切换自然下沉（默认 N，可在"控制 → 按键绑定 → 潜渊症"里改）。
        // 放在浸没判定之前，岸上/船里也能先设好，下水直接生效。
        while (TOGGLE_NATURAL_SINK.consumeClick()) {
            naturalSink = !naturalSink;
            player.displayClientMessage(Component.translatable(naturalSink
                    ? "message.barotrauma.natural_sink.on"
                    : "message.barotrauma.natural_sink.off"), true);
        }
    }

    /** 在开始移动之前应用原版疾跑绑定的状态，包括切换疾跑。 */
    public static void updateSwimInput(LocalPlayer player) {
        AirOceanSwimmer swimmer = (AirOceanSwimmer) player;
        boolean immersed = hasAirOceanWater(player);
        boolean fast = immersed && !player.isUsingItem() && Minecraft.getInstance().options.keySprint.isDown();
        if ((immersed || swimmer.barotrauma$isAirOceanSwimming()) && player.isSprinting() != fast) {
            // LocalPlayer.sendPosition 继续用原版 START/STOP_SPRINTING 同步服务端。
            player.setSprinting(fast);
        }
        swimmer.barotrauma$setAirOceanSwimState(immersed, fast);
    }

    /** 达到可游水深时，两种姿态都用水中推进。 */
    public static boolean swimTravel(LocalPlayer player, Vec3 input) {
        AirOceanSwimmer swimmer = (AirOceanSwimmer) player;
        if (!swimmer.barotrauma$isInAirOceanWater()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean sprint = swimmer.barotrauma$isAirOceanSwimming();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = sprint ? player.getLookAngle() : new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 left = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 wish = forward.scale(input.z).add(left.scale(input.x));
        double speed = sprint ? SPRINT_SWIM_SPEED : SWIM_SPEED;
        wish = wish.scale(speed / Math.max(1.0D, wish.length()));

        Vec3 motion = player.getDeltaMovement().lerp(wish, 0.62D);
        double y = verticalSpeed(swimDirection(mc), wish.y);
        Vec3 start = player.position();
        player.setDeltaMovement(motion.x, y, motion.z);
        player.move(MoverType.SELF, player.getDeltaMovement());
        player.resetFallDistance();
        Vec3 moved = player.position().subtract(start);
        player.checkMovementStatistics(moved.x, moved.y, moved.z);
        return true;
    }

    private static int swimDirection(Minecraft mc) {
        // 开 GUI（声纳台）时原版会 releaseAll 键位，KeyMapping.isDown() 恒为 false；
        // 这里直接读物理键盘，保证开着声纳按 Shift 也能下潜。
        if (physicalKeyDown(mc, mc.options.keyJump)) {
            return 1;
        }
        return physicalKeyDown(mc, mc.options.keyShift) ? -1 : 0;
    }

    private static double verticalSpeed(int direction, double aim) {
        if (direction != 0) {
            return direction * SWIM_VERTICAL_SPEED;
        }
        return aim != 0.0D ? aim : naturalSink ? -NATURAL_SINK_SPEED : 0.0D;
    }

    /** 游泳条件由服务端采样，首包前用相同规则在本地判断。 */
    public static boolean hasAirOceanWater(LocalPlayer player) {
        return AirOceanSwimmer.canUseAirOceanMovement(player)
                && (ClientSwimState.known() ? ClientSwimState.swimMovement()
                // 迟滞带内要传“当前泳姿”，不能传浸没状态，否则带里会被永远判成游泳。
                : AirOceanSwimmer.canSwimAtDepth(player,
                        ((AirOceanSwimmer) player).barotrauma$isAirOceanSwimming()));
    }

    /**
     * 直接问窗口"这个物理键现在按着没有"。
     *
     * <p>开 GUI 后 {@code KeyMapping.isDown()} 全是 false（{@code setScreen} 会
     * releaseAll），所以声纳台开着时按 Shift 原来完全没反应。</p>
     *
     * <p>左右 Shift 在 GLFW 里是两个不同的键：绑定读的是 {@code left.shift}，
     * 只查它会把"按右 Shift"当成没按。这里两种都认。</p>
     */
    private static boolean physicalKeyDown(Minecraft mc, KeyMapping mapping) {
        try {
            InputConstants.Key key = mapping.getKey();
            if (key.getType() == InputConstants.Type.KEYSYM) {
                long window = mc.getWindow().getWindow();
                int code = key.getValue();
                if (InputConstants.isKeyDown(window, code)) {
                    return true;
                }
                if (code == GLFW.GLFW_KEY_LEFT_SHIFT) {
                    return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
                }
                if (code == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                    return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT);
                }
            }
        } catch (Throwable ignored) {
            // 读不到就退回原版状态
        }
        return mapping.isDown();
    }
}
