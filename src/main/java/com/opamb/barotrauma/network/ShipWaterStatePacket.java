package com.opamb.barotrauma.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 物理船的舱内水面状态（服务端 → 客户端），用于自绘平滑水面。
 *
 * <p>每个水体块 7 个 float：包围盒 minX/minY/minZ/maxX/maxY/maxZ + 水面高度 surfaceY。
 * 一个舱室可能拆成多个矩形棱柱。客户端用物理船的渲染变换映射装配坐标。</p>
 *
 * <p>{@code breaches} 每 9 个 float 一个破口/门行：格中心 x/y/z（装配坐标）、
 * 破口自己那一列水柱在 {@code boxes} 里的**绝对 float 偏移**（不是盒子序号，别再乘 7；
 * 取该列水面用）、朝舱内的方向 dx/dz、所在空腔的 minY/maxY、开口类型
 * （0 墙 / 1 舱顶 / 2 舱底）。只包含直接连到外海的破口。
 * {@code streams} 每 7 个 float 描述一段实际流过的路径：起点 x/y/z、终点 x/y/z 和流量强度。
 * {@code sideFlows} 每 8 个 float 描述侧门：门槛 x/y/z、方向 dx/dz、开口内水柱顶、
 * 接收水面高度和实际流量强度。</p>
 */
public record ShipWaterStatePacket(long shipId, boolean physical,
                                   float anchorX, float anchorY, float anchorZ,
                                   float[] boxes, float[] breaches, float[] streams, float[] sideFlows) {

    public static final int BOX_STRIDE = 7;
    public static final int BREACH_STRIDE = 9;
    public static final int STREAM_STRIDE = 7;
    public static final int SIDE_FLOW_STRIDE = 8;
    /** 解码上限：正常船远低于此值，防止恶意包直接分配超大数组。 */
    public static final int MAX_BOXES = 8192;
    public static final int MAX_BREACHES = 1024;
    public static final int MAX_STREAMS = 1024;

    public static ShipWaterStatePacket empty() {
        return new ShipWaterStatePacket(-1L, false, 0, 0, 0,
                new float[0], new float[0], new float[0], new float[0]);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(shipId);
        buf.writeBoolean(physical);
        buf.writeFloat(anchorX);
        buf.writeFloat(anchorY);
        buf.writeFloat(anchorZ);
        writeRows(buf, boxes, BOX_STRIDE, MAX_BOXES);
        writeRows(buf, breaches, BREACH_STRIDE, MAX_BREACHES);
        writeRows(buf, streams, STREAM_STRIDE, MAX_STREAMS);
        writeRows(buf, sideFlows, SIDE_FLOW_STRIDE, MAX_STREAMS);
    }

    private static void writeRows(FriendlyByteBuf buf, float[] values, int stride, int maxRows) {
        int rows = Math.min(maxRows, values.length / stride);
        buf.writeVarInt(rows);
        for (int i = 0; i < rows * stride; i++) {
            buf.writeFloat(values[i]);
        }
    }

    public static ShipWaterStatePacket decode(FriendlyByteBuf buf) {
        long id = buf.readVarLong();
        boolean physical = buf.readBoolean();
        float ax = buf.readFloat();
        float ay = buf.readFloat();
        float az = buf.readFloat();
        return new ShipWaterStatePacket(id, physical, ax, ay, az,
                readRows(buf, BOX_STRIDE, MAX_BOXES),
                readRows(buf, BREACH_STRIDE, MAX_BREACHES),
                readRows(buf, STREAM_STRIDE, MAX_STREAMS),
                readRows(buf, SIDE_FLOW_STRIDE, MAX_STREAMS));
    }

    private static float[] readRows(FriendlyByteBuf buf, int stride, int maxRows) {
        int rows = buf.readVarInt();
        if (rows < 0 || rows > maxRows) {
            throw new DecoderException("Invalid water state row count: " + rows);
        }
        float[] values = new float[rows * stride];
        for (int i = 0; i < values.length; i++) {
            values[i] = buf.readFloat();
        }
        return values;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.ShipWaterRenderer.accept(this)));
        context.setPacketHandled(true);
    }
}
