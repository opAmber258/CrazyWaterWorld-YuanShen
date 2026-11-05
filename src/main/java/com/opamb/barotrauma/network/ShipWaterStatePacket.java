package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 物理船的舱内水面状态（服务端 → 客户端），用于自绘平滑水面。
 *
 * <p>每个水体块 7 个 float：包围盒 minX/minY/minZ/maxX/maxY/maxZ + 水面高度 surfaceY。
 * 一个舱室可能拆成多个矩形棱柱（只覆盖真实格子），所以同一个舱室会出现多条、
 * 水面高度相同。客户端用"当前渲染位置 − 装配锚点"做整体平移（姿态锁死，不需要旋转）。</p>
 *
 * <p>{@code breaches} 每 8 个 float 一个破口：格中心 x/y/z（装配坐标）、
 * 破口自己那一列水柱在 {@code boxes} 里的**绝对 float 偏移**（不是盒子序号，别再乘 7；
 * 取该列水面用）、朝舱内的方向 dx/dz、所在空腔的 minY/maxY（判断顶部破口/灌满）。
 * 方向对舱壁破口是轴对齐水平法线（±1/0），对舱顶/舱底破口是 (0,0)——客户端据此决定
 * 洞口补面画竖直面还是水平面（舱顶/舱底再按格子的 y 与空腔 minY/maxY 区分）。
 * 客户端据此在洞口画水花/气泡。开着的门也追加同样的 8 float，第四位是"接水侧"水体的
 * 第一个盒子偏移，方向保持"洞口→来水侧质心"的斜方向。只包含真正连到外海的洞。</p>
 */
public record ShipWaterStatePacket(long shipId, boolean physical,
                                   float anchorX, float anchorY, float anchorZ,
                                   float[] boxes, float[] breaches) {

    public static ShipWaterStatePacket empty() {
        return new ShipWaterStatePacket(-1L, false, 0, 0, 0, new float[0], new float[0]);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(shipId);
        buf.writeBoolean(physical);
        buf.writeFloat(anchorX);
        buf.writeFloat(anchorY);
        buf.writeFloat(anchorZ);
        buf.writeVarInt(boxes.length / 7);
        for (float value : boxes) {
            buf.writeFloat(value);
        }
        buf.writeVarInt(breaches.length / 8);
        for (float value : breaches) {
            buf.writeFloat(value);
        }
    }

    public static ShipWaterStatePacket decode(FriendlyByteBuf buf) {
        long id = buf.readVarLong();
        boolean physical = buf.readBoolean();
        float ax = buf.readFloat();
        float ay = buf.readFloat();
        float az = buf.readFloat();
        int count = buf.readVarInt();
        float[] boxes = new float[count * 7];
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = buf.readFloat();
        }
        int breachCount = buf.readVarInt();
        float[] breaches = new float[breachCount * 8];
        for (int i = 0; i < breaches.length; i++) {
            breaches[i] = buf.readFloat();
        }
        return new ShipWaterStatePacket(id, physical, ax, ay, az, boxes, breaches);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.ShipWaterRenderer.accept(this)));
        context.setPacketHandled(true);
    }
}
