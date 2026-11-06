package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 空气海域里的玩家游泳。
 *
 * <p>不走原版 {@code isInWater} 假水路径——那条路会触发 VS2 的实体区块碰撞迭代异常。
 * 这里只在客户端给玩家施加水阻、上浮/下潜和（可选的）缓慢下沉；服务端仍按客户端位置同步。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UnderwaterMovement {

    /** 上浮/下潜使用同一速度，避免手感不对称。 */
    private static final double SWIM_VERTICAL_SPEED = 0.12D;

    /** 自然下沉速度（格/tick）：约 0.4 格/秒，接近原版水里松手后的缓慢下沉。 */
    private static final double NATURAL_SINK_SPEED = 0.02D;

    /** 一键切换"松手后是否自然下沉"；默认关闭 = 保持当前深度。 */
    public static final KeyMapping TOGGLE_NATURAL_SINK = new KeyMapping(
            "key.barotrauma.toggle_natural_sink", GLFW.GLFW_KEY_N,
            "key.categories.barotrauma");

    private static boolean naturalSink;

    private UnderwaterMovement() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null
                || player.isSpectator() || player.isPassenger()) {
            return;
        }
        // 一键切换自然下沉（默认 N，可在"控制 → 按键绑定 → 潜渊症"里改）。
        // 放在浸没判定之前，岸上/船里也能先设好，下水直接生效。
        while (TOGGLE_NATURAL_SINK.consumeClick()) {
            naturalSink = !naturalSink;
            player.displayClientMessage(Component.translatable(naturalSink
                    ? "message.barotrauma.natural_sink.on"
                    : "message.barotrauma.natural_sink.off"), true);
        }
        // 判定点取"脚上方 0.4 格"而不是眼睛，也不是脚底：
        //  - 用眼睛：水淹到胸口时还算干燥，沉不下去；
        //  - 用脚底：干燥舱室的水面就是舱底 y，脚正好等于它，会被判成"在水里"，
        //    于是干舱里也有粘滞的游泳感（用户实测"船内还没开就有漏水游泳的感觉"）。
        Vec3 sample = player.position().add(0.0D, 0.4D, 0.0D);
        // 保留局部变量：下面自由泳分支要复用同一个浸没判定（上游曾把它内联掉）。
        boolean submergedFeet = OceanEnvironment.isSubmerged(mc.level, sample);
        // 疾跑 + 浸没 = 原版自由泳：swimming 标志 + SWIMMING 姿态（本地即时生效，
        // 服务端 handler 负责同步给其他客户端）。
        boolean sprintSwim = submergedFeet && player.isSprinting()
                && !player.getAbilities().flying;
        if (sprintSwim) {
            player.setSwimming(true);
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        } else if (player.isSwimming()) {
            player.setSwimming(false);
        }
        if (!submergedFeet) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        // 开 GUI（声纳台）时原版会 releaseAll 键位，KeyMapping.isDown() 恒为 false；
        // 这里直接读物理键盘，保证开着声纳按 Shift 也能下潜。
        boolean shiftDown = physicalKeyDown(mc, mc.options.keyShift);
        boolean jumpDown = physicalKeyDown(mc, mc.options.keyJump);

        // 自由泳（疾跑触发）：按视线方向游，像原版水里疾泳一样头朝哪游哪。
        //
        // 关键：不能用"旧速度 × 阻力 + 加速度"，因为原版每 tick 都会先给 delta.y
        // 叠一次重力，乘阻力会把它累积成持续下沉（用户实测"只会往底下游"）。
        // 这里每 tick 直接把竖直速度设成 look.y × 泳速（重力清零），水平保留一点惯性。
        if (sprintSwim) {
            final double swimSpeed = 0.11D;
            Vec3 look = player.getLookAngle();
            double yaw = Math.toRadians(player.getYRot());
            // 注意：player.xxa 左正右负（A=+1、D=-1），所以这里取的是"左"方向向量；
            // 之前用右向量乘 xxa，导致按 A 往右前方游（用户实测的左右反了）。
            Vec3 left = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
            Vec3 wish = look.scale(player.zza).add(left.scale(player.xxa * 0.7D));
            if (wish.lengthSqr() > 1.0E-4D) {
                wish = wish.normalize().scale(swimSpeed);
            } else {
                wish = Vec3.ZERO;
            }
            double vy = wish.y;
            if (jumpDown) {
                vy = Math.max(vy, 0.10D);
            } else if (shiftDown) {
                vy = Math.min(vy, -0.10D);
            }
            double blend = 0.55D;
            player.setDeltaMovement(
                    motion.x * (1.0D - blend) + wish.x * blend,
                    vy,
                    motion.z * (1.0D - blend) + wish.z * blend);
            player.resetFallDistance();
            return;
        }
        double y;
        if (jumpDown) {
            // 直接给一个明确的上浮速度；不要在原版重力后的负速度上做加法。
            y = SWIM_VERTICAL_SPEED;
        } else if (shiftDown) {
            y = -SWIM_VERTICAL_SPEED;
        } else {
            // 松开按键：默认保持当前深度；按 N 打开"自然下沉"后改为缓慢下沉
            // （0.02 格/tick，接近原版水里松手）。开 GUI 时同理，Shift 仍然有效。
            y = naturalSink ? -NATURAL_SINK_SPEED : 0.0D;
        }
        player.setDeltaMovement(motion.x * 0.78D, y, motion.z * 0.78D);
        player.resetFallDistance();
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
    private static boolean physicalKeyDown(Minecraft mc, net.minecraft.client.KeyMapping mapping) {
        try {
            com.mojang.blaze3d.platform.InputConstants.Key key = mapping.getKey();
            if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
                long window = mc.getWindow().getWindow();
                int code = key.getValue();
                if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, code)) {
                    return true;
                }
                if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) {
                    return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                            window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
                }
                if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) {
                    return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                            window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT);
                }
            }
        } catch (Throwable ignored) {
            // 读不到就退回原版状态
        }
        return mapping.isDown();
    }
}
