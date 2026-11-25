package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.LightBlockEntity;
import com.opamb.barotrauma.menu.LightMenu;
import com.opamb.barotrauma.network.RepairActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 占位灯具：吊灯 / 应急灯 / 荧光灯。
 *
 * <p>颜色照原版 {@code LightComponent.LightColor}：R,G,B,A 四通道 0–255，
 * A 是发光强度（原作 {@code DiffuseColor = RGB * A/255 * brightness}）。
 * 默认值也照原作：吊灯/荧光灯 {@code 1.0,1.0,1.0,0.5}（白色 α128）、
 * 应急灯 {@code 255,0,0,25}（红色 α25）。右键面板四通道可调，不接信号线。</p>
 *
 * <p>方块状态 {@link #LIGHT}(0..15) 是最终光照等级：由 A、灯型基础射程和
 * 电网电压共同算出（原作亮度 {@code min(Voltage,1)}，不是"到 0.5V 才亮"）。
 * 0 用熄灯模型，1..15 用发光面板模型。</p>
 *
 * <p>方块本身无碰撞、不遮挡、不窒息：它只是挂在舱顶的薄板，不能把舱室
 * 隔开，也不能挡水；{@code ModShipBlocks.isSolidForShip} 同样按可穿格处理。</p>
 */
public class LightBlock extends BaseEntityBlock {

    /** 最终光照等级；0 = 不发光（关灯/无电/A=0）。 */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);

    /** 占位灯具种类；射程/耗电按原版，正式模型替换时保留这三个入口。 */
    public enum Kind {
        /** 吊灯：主照明（原作 range 800、5 kW、白色 α0.5）。 */
        LAMP(15, 5.0D, MapColor.COLOR_LIGHT_BLUE, 1, 1, 15, 15,
                0xFFFFFF, 128),
        /** 应急灯：断电也亮（原作 range 250、0 W、红色 α25）。 */
        EMERGENCY(12, 0.0D, MapColor.COLOR_RED, 3, 3, 13, 13,
                0xFF0000, 25),
        /** 荧光灯：大舱室主照明（原作 range 800、5 kW、白色 α0.5）。 */
        FLUORESCENT(15, 5.0D, MapColor.COLOR_LIGHT_BLUE, 1, 2, 15, 14,
                0xFFFFFF, 128);

        /** 灯泡开始工作所需的最低电压（原作 Powered.MinVoltage）。 */
        public static final double MIN_VOLTAGE = 0.5D;

        private final int lightLevel;
        private final double powerKw;
        private final MapColor mapColor;
        private final VoxelShape shape;
        private final int defaultColor;
        private final int defaultAlpha;

        Kind(int lightLevel, double powerKw, MapColor mapColor,
             int minX, int minZ, int maxX, int maxZ, int defaultColor, int defaultAlpha) {
            this.lightLevel = lightLevel;
            this.powerKw = powerKw;
            this.mapColor = mapColor;
            this.shape = Shapes.box(minX / 16.0D, 14.0D / 16.0D, minZ / 16.0D,
                    maxX / 16.0D, 1.0D, maxZ / 16.0D);
            this.defaultColor = defaultColor;
            this.defaultAlpha = defaultAlpha;
        }

        /** 满强度（A=255、满电压）时的基础光照等级。 */
        public int lightLevel() {
            return lightLevel;
        }

        public MapColor mapColor() {
            return mapColor;
        }

        public VoxelShape shape() {
            return shape;
        }

        /** 原作 lightcolor 的 RGB 部分。 */
        public int defaultColor() {
            return defaultColor;
        }

        /** 原作 lightcolor 的 A 部分（发光强度 0–255）。 */
        public int defaultAlpha() {
            return defaultAlpha;
        }

        /** 原作 PowerConsumption：吊灯/荧光灯 5 kW，应急灯 0 W。 */
        public double powerKw() {
            return powerKw;
        }
    }

    private final Kind kind;

    public LightBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(LIGHT, 0));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return kind.shape();
    }

    /** 薄板本身有方块的 cube 模型（金属框 + 发光面板），不是 GeckoLib 方块。 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LightBlockEntity(pos, state);
    }

    /** 服务端需要 tick 来按电网电压算实际发光强度；客户端不跑。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof LightBlockEntity light) {
                LightBlockEntity.serverTick(lvl, pos, st, light);
            }
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            Vec3 center = RepairActionPacket.worldCenter(level, pos);
            if (center == null || serverPlayer.distanceToSqr(center) > 64.0D) {
                return InteractionResult.CONSUME;
            }
            if (!(level.getBlockEntity(pos) instanceof LightBlockEntity light)) {
                return InteractionResult.CONSUME;
            }
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new LightMenu(id, inventory, pos),
                            Component.translatable(state.getBlock().getDescriptionId())),
                    buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }
}
