package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.CabinetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 潜渊症钢柜 / 货架 / 潜水服柜 / 氧气瓶架（静态 GeckoLib 模型 + 容器）。
 *
 * <p>五种容器共用一套方块和方块实体，靠 {@link Kind} 区分模型、碰撞、
 * 槽位数量与 GUI 布局。模型正面在 geo 里朝 +Z，渲染器把 {@link #FACING}
 * 取反后交给 GeckoLib（和反应堆一致）。</p>
 *
 * <p><b>竖直多分片</b>：模型超过 1 格高，方块按 {@link #PART}（0..{@code cells-1}）
 * 从 {@code PART=0} 的主方块往上摆一整列。只有主方块带方块实体和 GeckoLib 模型，
 * 其余分片 {@link RenderShape#INVISIBLE}、{@code newBlockEntity} 返回 null，
 * 碰撞/选中形状只是模型实际占用的那一小片。拆任意一片整列一起消失，
 * 内容从主方块的方块实体掉落（照 {@code NavigationTerminalBlock} / {@code EngineBlock}
 * 的写法，带 {@link #REMOVING} 递归保护）。</p>
 *
 * <p><b>自愈</b>：{@code ModBlocks.cabinetProps()} 开了 {@code randomTicks()}，
 * {@link #randomTick} 会清掉没有主方块的孤儿分片、补齐/纠正主方块上方的分片。
 * 存档里旧版本留下的幽灵分片最多几十秒就会被清干净。</p>
 */
public class CabinetBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** PART 的最大值：最高的是组合柜（54.6px），4 片 0..3 够用。 */
    private static final int MAX_PART = 3;

    /** 多格高方块的竖直分片：0 = 底层（带方块实体和模型），1.. = 上面的分片。 */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, MAX_PART);

    /**
     * 拆除整列时的递归保护：{@code onRemove} 里用 {@code level.removeBlock} 拆其它分片
     * 会再次进入 {@code onRemove}，没有它就会重复掉内容/无限递归。
     */
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 容器种类：模型尺寸都是原作图按 0.5 缩放后的真实比例（格）。 */
    public enum Kind {
        MEDIUM_STEEL_CABINET("medium_steel_cabinet", 23, 20, 5, 4, 16.0F, 38.66F, -8.0F, 0.5F, true),
        MEDIUM_WINDOWED_STEEL_CABINET("medium_windowed_steel_cabinet", 23, 20, 5, 4, 16.0F, 38.66F, -8.0F, 0.5F, true),
        CRATE_SHELF("crate_shelf", 4, 4, 1, 4, 16.0F, 31.65F, -8.0F, 0.0F, true),
        DIVING_SUIT_LOCKER("diving_suit_locker", 1, 1, 1, 1, 16.0F, 41.36F, -8.0F, -1.2F, false),
        OXYGEN_TANK_SHELF("oxygen_tank_shelf", 3, 3, 3, 1, 16.0F, 13.25F, -8.0F, -2.0F, false),
        /** 原作装配：潜水服柜在上、氧气瓶架在下，合成一个整体。 */
        DIVING_SUIT_STATION("diving_suit_station", 4, 4, 3, 2, 16.0F, 54.6F, -8.0F, -1.2F, true);

        private final String id;
        private final int slots;
        private final int mainSlots;
        private final int columns;
        private final int rows;
        private final float width;
        private final float height;
        private final float depthMin;
        private final float depthMax;
        private final boolean tall;
        private final int cells;

        Kind(String id, int slots, int mainSlots, int columns, int rows,
             float width, float height, float depthMin, float depthMax, boolean tall) {
            this.id = id;
            this.slots = slots;
            this.mainSlots = mainSlots;
            this.columns = columns;
            this.rows = rows;
            this.width = width;
            this.height = height;
            this.depthMin = depthMin;
            this.depthMax = depthMax;
            this.tall = tall;
            this.cells = Math.max(1, (int) Math.ceil(height / 16.0F));
        }

        public String id() {
            return id;
        }

        public int slots() {
            return slots;
        }

        public int mainSlots() {
            return mainSlots;
        }

        public int columns() {
            return columns;
        }

        public int rows() {
            return rows;
        }

        public int subSlots() {
            return slots - mainSlots;
        }

        /** 模型宽/高/深，单位是 MC 像素（1/16 格）；深度带正负（正面 +Z）。 */
        public float width() {
            return width;
        }

        public float height() {
            return height;
        }

        public float depth() {
            return depthMax - depthMin;
        }

        public float depthMin() {
            return depthMin;
        }

        public float depthMax() {
            return depthMax;
        }

        /** 这个容器在竖直方向占几格（模型超过 1 格高时多格摆放）。 */
        public int cells() {
            return cells;
        }

        /** 高柜（中型柜/货架）用 204 高的 GUI，小件用 166。 */
        public boolean tall() {
            return tall;
        }
    }

    private final Kind kind;

    public CabinetBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, 0));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** 任意一片的主方块位置（列永远是竖直的，主方块在 {@code part} 格正下方）。 */
    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(PART));
    }

    /** 主方块上方第 {@code part} 片的位置。 */
    public static BlockPos partPos(BlockPos master, int part) {
        return master.above(part);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        int cells = kind.cells();
        for (int i = 0; i < cells; i++) {
            BlockPos p = partPos(master, i);
            // 超出建造高度时 getBlockState 是虚空空气，canBeReplaced 会放行，
            // 后续 setBlock 静默失败 → 只放下半截。直接拒绝放置。
            if (level.isOutsideBuildHeight(p)) {
                return null;
            }
            if (!level.getBlockState(p).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (state.getValue(PART) != 0) {
            return;
        }
        for (int i = 1; i < kind.cells(); i++) {
            BlockPos p = partPos(pos, i);
            BlockState existing = level.getBlockState(p);
            if (existing.getBlock() == this) {
                // 同一种柜子的旧分片：PART 不对就纠正成这一片。
                if (existing.getValue(PART) != i) {
                    level.setBlock(p, state.setValue(PART, i), Block.UPDATE_ALL);
                }
            } else if (existing.isAir() || existing.canBeReplaced()) {
                level.setBlock(p, state.setValue(PART, i), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 只有主方块该进掉落表；打上面的分片时在主方块位置补一份方块物品，
        // 避免“打了最上面一片、整列消失却什么都没掉”。
        if (!level.isClientSide && state.getValue(PART) != 0
                && state.getValue(PART) < kind.cells() && !player.isCreative()) {
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            if (masterState.getBlock() == this && masterState.getValue(PART) == 0) {
                // 柜子目前没有 block loot table，dropResources 会掉 0 个，直接掉方块物品。
                // 以后补掉落表时记得沿用导航台/引擎的 part=0 条件，这样这里也不会双倍。
                ItemStack drop = new ItemStack(state.getBlock().asItem());
                if (!drop.isEmpty()) {
                    Block.popResource(level, master, drop);
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && !REMOVING.get()) {
            REMOVING.set(Boolean.TRUE);
            try {
                int part = state.getValue(PART);
                BlockPos master = masterPos(pos, state);
                // 拆的是主方块时它的位置已经换成 newState，masterState 读不到；
                // 拆的是分片时，主方块必须仍然是 PART=0，否则这一列本来就没有主方块。
                // 超出 cells 的分片（旧版本残留）不算这一列，拆它不能带走整列。
                boolean anchored = part == 0;
                if (!anchored && part < kind.cells()) {
                    BlockState masterState = level.getBlockState(master);
                    anchored = masterState.getBlock() == this && masterState.getValue(PART) == 0;
                }
                if (anchored) {
                    // 内容和原版箱子一样从主方块的方块实体掉出来（此时 BE 还在）。
                    if (level.getBlockEntity(master) instanceof CabinetBlockEntity cabinet) {
                        Containers.dropContents(level, master, cabinet);
                    }
                    // 拆掉任意一片，整列一起消失。只认 PART 和相对偏移一致的分片，
                    // 叠放时不会误伤下面/上面另一列柜子。
                    for (int i = 0; i < kind.cells(); i++) {
                        BlockPos sibling = partPos(master, i);
                        if (sibling.equals(pos)) {
                            continue;
                        }
                        BlockState siblingState = level.getBlockState(sibling);
                        if (siblingState.getBlock() == this && siblingState.getValue(PART) == i) {
                            level.removeBlock(sibling, false);
                        }
                    }
                }
            } finally {
                REMOVING.set(Boolean.FALSE);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int part = state.getValue(PART);
        if (part > 0) {
            // 孤儿分片：主方块不在（或者已经不是一个主方块）→ 自己消失。
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            if (masterState.getBlock() != this || masterState.getValue(PART) != 0) {
                level.removeBlock(pos, false);
            }
            return;
        }
        // 主方块：补齐/纠正 0..cells-1 的分片（存在的且 PART 正确就跳过）。
        for (int i = 1; i < kind.cells(); i++) {
            BlockPos p = partPos(pos, i);
            BlockState existing = level.getBlockState(p);
            if (existing.getBlock() == this) {
                if (existing.getValue(PART) == 0) {
                    // 另一列柜子的主方块（叠放），不动它。
                    continue;
                }
                if (existing.getValue(PART) != i
                        || existing.getValue(FACING) != state.getValue(FACING)) {
                    level.setBlock(p, state.setValue(PART, i), Block.UPDATE_ALL);
                }
                continue;
            }
            if (existing.isAir() || existing.canBeReplaced()) {
                level.setBlock(p, state.setValue(PART, i), Block.UPDATE_ALL);
            }
        }
        // 超出这一列范围的老分片（例如尺寸改小之前留下的 PART 3）也清掉。
        for (int i = kind.cells(); i <= MAX_PART; i++) {
            BlockPos above = partPos(pos, i);
            BlockState existing = level.getBlockState(above);
            if (existing.getBlock() == this && existing.getValue(PART) == i) {
                level.removeBlock(above, false);
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            if (masterState.getBlock() == this && masterState.getValue(PART) == 0
                    && level.getBlockEntity(master) instanceof CabinetBlockEntity cabinet) {
                NetworkHooks.openScreen(serverPlayer, cabinet, buf -> buf.writeBlockPos(master));
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    /** 贴墙薄片：模型背面在方块后沿，板从后沿往前占模型深度。 */
    private VoxelShape shape(BlockState state) {
        int part = state.getValue(PART);
        // 注意：VoxelShape 用的是“本格局部坐标”，不能拿模型的绝对 Y
        float remain = kind.height() - part * 16.0F;   // 模型在这一格里还剩多高
        if (remain <= 1.0E-4F) {
            return Shapes.empty();
        }
        float localTop = Math.min(remain, 16.0F) / 16.0F;
        double d = kind.depth() / 16.0D;              // 模型深度（格）
        double x0 = 0.0D;
        double x1 = 1.0D;
        double z0 = 0.0D;
        double z1 = 1.0D;
        switch (state.getValue(FACING)) {
            case NORTH -> {          // 背在南侧（+Z）
                z0 = 1.0D - d;
                z1 = 1.0D;
            }
            case SOUTH -> {          // 背在北侧（-Z）
                z0 = 0.0D;
                z1 = d;
            }
            case WEST -> {           // 背在东侧（+X）
                x0 = 1.0D - d;
                x1 = 1.0D;
            }
            case EAST -> {           // 背在西侧（-X）
                x0 = 0.0D;
                x1 = d;
            }
            default -> {
            }
        }
        return Shapes.box(x0, 0.0D, z0, x1, localTop, z1);
    }

    /**
     * 这一片是不是孤儿：没有主方块，或者本来就超出这一列的范围。
     * 孤儿不给选中/碰撞，等 {@link #randomTick} 把它清掉。
     */
    private boolean orphanPart(BlockState state, BlockGetter level, BlockPos pos) {
        int part = state.getValue(PART);
        if (part == 0) {
            return false;
        }
        if (part >= kind.cells()) {
            return true;
        }
        BlockState master = level.getBlockState(pos.below(part));
        return master.getBlock() != this || master.getValue(PART) != 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return orphanPart(state, level, pos) ? Shapes.empty() : shape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return orphanPart(state, level, pos) ? Shapes.empty() : shape(state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == 0
                ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new CabinetBlockEntity(pos, state) : null;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
