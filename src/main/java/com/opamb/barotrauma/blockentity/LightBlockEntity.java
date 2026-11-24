package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.LightBlock;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipSimulation;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 灯具的每盏颜色/开关状态 + 电网取电。
 *
 * <p>颜色照原版 {@code LightComponent.LightColor}：RGB + A，A 是发光强度
 * （原作 {@code DiffuseColor = RGB * A/255 * CurrentBrightness}）。客户端
 * {@code BlockColors} 按坐标取 RGB；实际光强映射到方块状态 {@link LightBlock#LIGHT}
 * （0–15），因为 Minecraft 的彩色光强度只能由光照等级表达。</p>
 *
 * <p>亮度照原作 {@code Powered.Update}：{@code min(Voltage, 1)} 连续变化，
 * 不是"到最低电压才亮"。吊灯/荧光灯 5 kW，应急灯 0 W 始终满强度；
 * 不在任何船体里的灯（自由摆放的测试灯）按满电压处理。</p>
 */
public class LightBlockEntity extends BlockEntity implements PowerConsumer {

    private static final String TAG_COLOR = "Color";
    private static final String TAG_ALPHA = "Alpha";
    private static final String TAG_LIT = "Lit";
    private static final String TAG_POWER_BOX = "PowerBox";

    private int color;
    /** 原作 LightColor.A：发光强度 0–255。 */
    private int alpha;
    /** 用户请求的开状态。 */
    private boolean lit = true;
    /** 当前电压决定的亮度系数 0..1（原作 lightBrightness）。 */
    private float brightness = 1.0F;
    /** 是否还有光（供电不足时仍是 true，只是更暗）。 */
    private boolean powered = true;
    private boolean onShip;

    @Nullable
    private BlockPos boundBox;
    @Nullable
    private BlockPos cachedCorePos;
    private int coreRetryTicks;

    public LightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHT.get(), pos, state);
        LightBlock.Kind kind = state.getBlock() instanceof LightBlock light
                ? light.kind() : LightBlock.Kind.LAMP;
        this.color = kind.defaultColor();
        this.alpha = kind.defaultAlpha();
    }

    public int color() {
        return color;
    }

    public int alpha() {
        return alpha;
    }

    /** 用户请求的开状态（面板上的开/关）。 */
    public boolean lit() {
        return lit;
    }

    public boolean powered() {
        return powered;
    }

    public boolean onShip() {
        return onShip;
    }

    /** 服务端入口：改颜色/强度/开关，重算光照等级并同步给客户端。 */
    public void applyColor(int rgb, int alphaValue, boolean on) {
        int nextColor = rgb & 0xFFFFFF;
        int nextAlpha = Math.max(0, Math.min(255, alphaValue));
        if (color == nextColor && alpha == nextAlpha && lit == on) {
            return;
        }
        color = nextColor;
        alpha = nextAlpha;
        lit = on;
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState old = getBlockState();
        BlockState next = old.setValue(LightBlock.LIGHT, emittedLevel());
        if (next != old) {
            level.setBlock(worldPosition, next, Block.UPDATE_ALL);
        }
        // setBlock 只发方块状态；BE 数据包必须自己补一次，客户端才能拿到新颜色。
        level.sendBlockUpdated(worldPosition, next, next, Block.UPDATE_ALL);
    }

    /* ------------------------------------------------------------ 电网 */

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  LightBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        ShipState ship = be.resolveShip(server, pos);
        boolean nextOnShip = false;
        double nextBrightness = 1.0D;
        if (ship != null) {
            BlockPos cell = assemblyPos(pos);
            if (ship.tracksHullBlock(cell)) {
                nextOnShip = true;
                ShipSimulation simulation = ship.simulation();
                simulation.reportConsumer(cell, be.powerDemandKw(),
                        LightBlock.Kind.MIN_VOLTAGE, be.boundBox);
                if (be.kind().powerKw() > 0.0D) {
                    // 原作：lightBrightness -> min(Voltage, 1)，欠压只是变暗。
                    nextBrightness = Math.max(0.0D,
                            Math.min(simulation.consumerVoltage(cell), 1.0D));
                }
            }
        }
        boolean nextPowered = be.kind().powerKw() <= 0.0D || !nextOnShip
                || nextBrightness > 0.0D;
        be.onShip = nextOnShip;
        be.powered = nextPowered;
        be.brightness = (float) nextBrightness;
        int emitted = be.emittedLevel();
        if (state.getValue(LightBlock.LIGHT) != emitted) {
            level.setBlock(pos, state.setValue(LightBlock.LIGHT, emitted), Block.UPDATE_ALL);
        }
    }

    /**
     * 最终光照等级：把原作 {@code RGB * A/255 * brightness} 的强度关系映射到
     * Minecraft 的光照等级。原版亮度是线性的，MC 的光照贴图不是
     * （{@code f/(4-3f)}），所以这里用其反函数 {@code 4v/(1+3v)} 换算，
     * A=128 才会看起来接近原作的 50% 亮度而不是暗一大截。
     *
     * <p>原作欠压时还有 5% 概率闪断（{@code TemporaryFlickerTimer}），这里没做：
     * 每次闪断都要重建区块光照，代价太高；简化为按电压连续变暗。</p>
     */
    private int emittedLevel() {
        if (!lit || alpha <= 0 || brightness <= 0.0F) {
            return 0;
        }
        float intensity = (alpha / 255.0F) * brightness;
        if (intensity >= 1.0F) {
            return kind().lightLevel();
        }
        float perceived = 4.0F * intensity / (1.0F + 3.0F * intensity);
        int value = Math.round(15.0F * perceived);
        return Math.max(1, Math.min(kind().lightLevel(), value));
    }

    private LightBlock.Kind kind() {
        return getBlockState().getBlock() instanceof LightBlock light
                ? light.kind() : LightBlock.Kind.LAMP;
    }

    private static BlockPos assemblyPos(BlockPos pos) {
        return VsState.active() && VsPilot.isYardBlock(pos) ? VsPilot.toAssembly(pos) : pos;
    }

    /** 和电池/制氧机同一套：缓存核心位置，船状态不存档时自己补扫；找不到船冷却 1 秒。 */
    private ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active() && VsPilot.isYardBlock(pos)) {
            return VsState.activeState();
        }
        if (cachedCorePos != null && level.hasChunkAt(cachedCorePos)
                && level.getBlockEntity(cachedCorePos) instanceof ShipCoreBlockEntity core) {
            ShipState state = core.state() != null ? core.state() : core.rescan(level);
            if (state != null && state.tracksHullBlock(assemblyPos(pos))) {
                return state;
            }
        }
        if (coreRetryTicks > 0) {
            coreRetryTicks--;
            return null;
        }
        coreRetryTicks = 20;
        ShipCoreBlockEntity core = ShipCores.coreOwning(level, assemblyPos(pos), 48.0D);
        cachedCorePos = core == null ? null : core.getBlockPos().immutable();
        return core == null ? null : core.state();
    }

    @Override
    public double powerDemandKw() {
        return lit ? kind().powerKw() : 0.0D;
    }

    @Override
    public double minVoltage() {
        return LightBlock.Kind.MIN_VOLTAGE;
    }

    @Nullable
    @Override
    public BlockPos powerBox() {
        return boundBox;
    }

    @Override
    public void setPowerBox(@Nullable BlockPos box) {
        boundBox = box == null ? null : box.immutable();
        setChanged();
    }

    /* ------------------------------------------------------------ 存档/同步 */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_COLOR, color);
        tag.putInt(TAG_ALPHA, alpha);
        tag.putBoolean(TAG_LIT, lit);
        if (boundBox != null) {
            tag.putLong(TAG_POWER_BOX, boundBox.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        LightBlock.Kind kind = kind();
        if (tag.contains(TAG_COLOR)) {
            color = tag.getInt(TAG_COLOR) & 0xFFFFFF;
        } else {
            color = kind.defaultColor();
        }
        // Alpha 是后加的原版字段：旧存档没有就用灯型默认强度。
        alpha = tag.contains(TAG_ALPHA)
                ? Math.max(0, Math.min(255, tag.getInt(TAG_ALPHA)))
                : kind.defaultAlpha();
        if (tag.contains(TAG_LIT)) {
            lit = tag.getBoolean(TAG_LIT);
        }
        boundBox = tag.contains(TAG_POWER_BOX) ? BlockPos.of(tag.getLong(TAG_POWER_BOX)) : null;
        syncClientTint();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        syncClientTint();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        syncClientTint();
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            com.opamb.barotrauma.client.LightClientColors.remove(level, worldPosition);
        }
        super.setRemoved();
    }

    /**
     * 客户端位置色缓存：{@code BlockColors} 会在渲染线程和彩色光传播线程里被调用，
     * 不能从那里查区块/方块实体，只能更新这份并发缓存。
     */
    private void syncClientTint() {
        if (level != null && level.isClientSide) {
            com.opamb.barotrauma.client.LightClientColors.update(
                    level, worldPosition, color, kind().defaultColor());
        }
    }
}
