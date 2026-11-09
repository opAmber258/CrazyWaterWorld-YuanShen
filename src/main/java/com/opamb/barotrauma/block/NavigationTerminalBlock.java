package com.opamb.barotrauma.block;

import com.opamb.barotrauma.block.NavigationTerminalLayout.Part;
import com.opamb.barotrauma.blockentity.NavigationTerminalBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.menu.SonarMenu;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.sonar.SonarScan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 导航终端：按 {@code art/models/navigation_terminal_20261108} 的三格宽 Blockbench 资产
 * 实现的多方块驾驶台（3 格宽 × 3 格高 × 1 格深）。
 *
 * <p>它承接原来声纳台的全部功能：右键打开声纳界面、作为船核心（
 * {@link NavigationTerminalBlockEntity} 继承 {@link ShipCoreBlockEntity}）、
 * 台面上方画 3D 点云全息。原来的 {@code barotrauma:sonar} 方块保持不动，
 * 旧存档和儒艮号模板继续用旧声纳台。</p>
 *
 * <p>主方块是<b>下排 · 中列</b>（模型坐标 X 居中、Y 贴地），其余 8 段是同一方块的
 * 隐形附属段。模型总高 2.375 格：方块占 3 层，顶层碰撞只到 0.375 格，实际高度和模型一致。
 * 几何计算在 {@link NavigationTerminalLayout}，方便单测。</p>
 */
public class NavigationTerminalBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final VoxelShape SHAPE = Shapes.block();
    /** 模型顶格只占 6 BB（2.375 格总高 - 2 格）。 */
    private static final VoxelShape TOP_SHAPE = Shapes.box(
            0.0D, 0.0D, 0.0D, 1.0D, 6.0D / 16.0D, 1.0D);

    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public NavigationTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.LOWER_MIDDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** 主方块（下排中列）位置；任何一段都能反推。 */
    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        return NavigationTerminalLayout.masterPos(pos, state.getValue(FACING), state.getValue(PART));
    }

    /** 某一段相对主方块的坐标。 */
    public static BlockPos partPos(BlockPos master, Direction facing, Part part) {
        return NavigationTerminalLayout.partPos(master, facing, part);
    }

    /** 调试/测试用：主方块相对偏移表。 */
    public static Map<Part, BlockPos> footprint(BlockPos master, Direction facing) {
        return NavigationTerminalLayout.footprint(master, facing);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 落地安装：只认方块顶面。
        if (context.getClickedFace() != Direction.UP) {
            return null;
        }
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();

        // 一艘船只能有一个核心：附近已有声纳台/导航终端时先拒绝，避免两套
        // ShipState 同时 tick 同一艘船。拆掉旧核心后再放。
        ShipCoreBlockEntity existing = ShipCores.nearestCore(level, master, 24.0D);
        if (existing != null) {
            Player player = context.getPlayer();
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(
                        Component.translatable("message.barotrauma.navigation_terminal.core_exists"), false);
            }
            return null;
        }

        for (Part part : Part.values()) {
            BlockPos partPos = partPos(master, facing, part);
            if (!level.getBlockState(partPos).canBeReplaced(context)) {
                return null;
            }
            if (part.row() == 0) {
                BlockPos below = partPos.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.LOWER_MIDDLE);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        if (state.getValue(PART) != Part.LOWER_MIDDLE) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (Part part : Part.values()) {
            if (part == Part.LOWER_MIDDLE) {
                continue;
            }
            level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), Block.UPDATE_ALL);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 只有主方块进掉落表；打任意附属段时在主方块位置补一份。
        if (!level.isClientSide && state.getValue(PART) != Part.LOWER_MIDDLE && !player.isCreative()) {
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            if (masterState.getBlock() instanceof NavigationTerminalBlock) {
                Block.dropResources(masterState, level, master);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && newState.getBlock() != this && !REMOVING.get()) {
            REMOVING.set(Boolean.TRUE);
            try {
                BlockPos master = masterPos(pos, state);
                Direction facing = state.getValue(FACING);
                for (Part part : Part.values()) {
                    BlockPos partPos = partPos(master, facing, part);
                    if (!partPos.equals(pos) && level.getBlockState(partPos).getBlock() == this) {
                        level.removeBlock(partPos, false);
                    }
                }
            } finally {
                REMOVING.set(Boolean.FALSE);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos master = masterPos(pos, state);
            BlockState masterState = level.getBlockState(master);
            if (!(masterState.getBlock() instanceof NavigationTerminalBlock)) {
                return InteractionResult.PASS;
            }
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new SonarMenu(id, inventory, master, SonarScan.DEFAULT_RANGE),
                            Component.translatable("block.barotrauma.navigation_terminal")),
                    buf -> {
                        buf.writeBlockPos(master);
                        buf.writeVarInt(SonarScan.DEFAULT_RANGE);
                    });
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return state.getValue(PART).row() < 2 ? SHAPE : TOP_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return state.getValue(PART).row() < 2 ? SHAPE : TOP_SHAPE;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == Part.LOWER_MIDDLE
                ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.LOWER_MIDDLE
                ? new NavigationTerminalBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (state.getValue(PART) != Part.LOWER_MIDDLE) {
            return null;
        }
        if (level.isClientSide) {
            return createTickerHelper(type, ModBlockEntities.NAVIGATION_TERMINAL.get(),
                    NavigationTerminalBlockEntity::clientTick);
        }
        return createTickerHelper(type, ModBlockEntities.NAVIGATION_TERMINAL.get(),
                ShipCoreBlockEntity::serverTick);
    }
}
