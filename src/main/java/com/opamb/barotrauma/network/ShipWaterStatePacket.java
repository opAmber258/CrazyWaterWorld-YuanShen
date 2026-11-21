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
 * <p>{@code breaches} 每 9 个 float 一个破口/门行：格中心 x/y/z（装配坐标）、
 * 破口自己那一列水柱在 {@code boxes} 里的**绝对 float 偏移**（不是盒子序号，别再乘 7；
 * 取该列水面用）、朝舱内的方向 dx/dz、所在空腔的 minY/maxY、开口类型
 * （0 墙 / 1 舱顶 / 2 舱底）。开着的门也追加同样的 9 float，第四位是"接水侧"水体的
 * 第一个盒子偏移，方向保持"洞口→来水侧质心"的斜方向。只包含真正连到外海的洞。</p>
 */
public record ShipWaterStatePacket(long shipId, boolean physical,
                                   float anchorX, float anchorY, float anchorZ,
                                   float[] boxes, float[] breaches) {

    public static final int BOX_STRIDE = 7;
    public static final int BREACH_STRIDE = 9;
    /** 解码上限：正常船远低于此值，防止恶意包直接分配超大数组。 */
    public static final int MAX_BOXES = 8192;
    public static final int MAX_BREACHES = 1024;

    public static ShipWaterStatePacket empty() {
        return new ShipWaterStatePacket(-1L, false, 0, 0, 0, new float[0], new float[0]);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(shipId);
        buf.writeBoolean(physical);
        buf.writeFloat(anchorX);
        buf.writeFloat(anchorY);
        buf.writeFloat(anchorZ);
        buf.writeVarInt(boxes.length / BOX_STRIDE);
        for (float value : boxes) {
            buf.writeFloat(value);
        }
        buf.writeVarInt(breaches.length / BREACH_STRIDE);
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
        int count = Math.max(0, Math.min(MAX_BOXES, buf.readVarInt()));
        float[] boxes = new float[count * BOX_STRIDE];
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = buf.readFloat();
        }
        int breachCount = Math.max(0, Math.min(MAX_BREACHES, buf.readVarInt()));
        float[] breaches = new float[breachCount * BREACH_STRIDE];
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
