package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.item.TurretBinderItem;
import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
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
import org.jetbrains.annotations.Nullable;

/**
 * 舰载炮塔（原型，开发者工具）。
 *
 * <p>右键进入炮台视角（服务端记录占用者），视角由 {@link TurretBlockEntity} 的
 * 方位/俯仰角驱动；按住右键瞄准（FOV 缩放），左键沿准星射击，左 Shift 退出。
 * 炮管是方块实体渲染的，方块模型只负责底座和炮座。</p>
 *
 * <p>朝向前方 = 玩家放置时面向的方向（炮口朝外）；水平四向贴墙时朝安装面外侧。
 * 安装面只改变底座外观和俯仰窗口，yaw/pitch 仍是世界角，操作与落地炮塔一致。</p>
 */
public class TurretBlock extends BaseEntityBlock {

    /**
     * 炮塔几何/操作规格。换 2×2 / 3×3 炮塔时由方块类覆盖 {@link #spec()}，
     * 瞄准、开火、相机和网络逻辑不需要跟着改。
     */
    private static final TurretSpec DEFAULT_SPEC = TurretSpec.DEFAULT;

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * 安装面朝向：UP=落地，DOWN=吊顶，水平四向=贴墙。它指向“离开支撑方块”的方向，
     * 和炮塔局部 +Y 一致，渲染/相机/俯仰限位都按它取。
     */
    public static final EnumProperty<Direction> MOUNT =
            EnumProperty.create("mount", Direction.class);

    /** 落地炮座：14×12×14 像素。 */
    private static final VoxelShape FLOOR_SHAPE =
            boxPx(1.0D, 0.0D, 1.0D, 15.0D, 12.0D, 15.0D);

    /** 吊顶：贴顶法兰 + 细主轴。 */
    private static final VoxelShape CEILING_SHAPE = Shapes.or(
            boxPx(2.0D, 13.0D, 2.0D, 14.0D, 16.0D, 14.0D),
            boxPx(6.5D, 7.0D, 6.5D, 9.5D, 13.0D, 9.5D));

    public TurretBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(MOUNT, Direction.UP));
    }

    /** 当前方块类型的规格；2×2 / 3×3 子类覆盖这里即可。 */
    public TurretSpec spec() {
        return DEFAULT_SPEC;
    }

    public static TurretSpec spec(BlockState state) {
        return state.getBlock() instanceof TurretBlock turret
                ? turret.spec()
                : DEFAULT_SPEC;
    }

    /** 当前方块类型的武器规格；电磁炮子类覆盖成 {@link TurretWeaponKind#COILGUN}。 */
    public TurretWeaponKind weaponKind() {
        return TurretWeaponKind.PROTOTYPE;
    }

    public static TurretWeaponKind weaponKind(BlockState state) {
        return state.getBlock() instanceof TurretBlock turret
                ? turret.weaponKind()
                : TurretWeaponKind.PROTOTYPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction mount = context.getClickedFace();
        BlockPos supportPos = context.getClickedPos().relative(mount.getOpposite());
        if (!context.getLevel().getBlockState(supportPos)
                .isFaceSturdy(context.getLevel(), supportPos, mount)) {
            return null;
        }
        // 落地/吊顶：炮口朝玩家水平方向；贴墙：炮口朝墙外。
        Direction facing = mount.getAxis().isHorizontal()
                ? mount
                : context.getHorizontalDirection();
        return defaultBlockState().setValue(MOUNT, mount).setValue(FACING, facing);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction mount = state.getValue(MOUNT);
        BlockPos supportPos = pos.relative(mount.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, mount);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(MOUNT).getOpposite()
                && !state.getBlock().canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction mount = state.getValue(MOUNT);
        Direction rotatedMount = mount.getAxis().isHorizontal()
                ? rotation.rotate(mount)
                : mount;
        return state.setValue(MOUNT, rotatedMount)
                .setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction mount = state.getValue(MOUNT);
        Direction mirroredMount = mount.getAxis().isHorizontal()
                ? mirror.mirror(mount)
                : mount;
        return state.setValue(MOUNT, mirroredMount)
                .setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // BaseEntityBlock 默认 INVISIBLE；这里要方块模型（底座）+ BER（炮管）同时出现。
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return shapeFor(state);
    }

    private static VoxelShape shapeFor(BlockState state) {
        Direction mount = state.getValue(MOUNT);
        if (mount == Direction.DOWN) {
            return CEILING_SHAPE;
        }
        if (mount.getAxis().isHorizontal()) {
            return wallShape(mount);
        }
        return FLOOR_SHAPE;
    }

    /** 墙面托架占位：贴支撑面的竖板 + 伸向方块中心的短臂。 */
    private static VoxelShape wallShape(Direction mount) {
        if (mount == Direction.SOUTH) {
            return boxPx(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 4.5D);
        }
        if (mount == Direction.NORTH) {
            return boxPx(2.0D, 2.0D, 11.5D, 14.0D, 14.0D, 16.0D);
        }
        if (mount == Direction.WEST) {
            return boxPx(11.5D, 2.0D, 2.0D, 16.0D, 14.0D, 14.0D);
        }
        return boxPx(0.0D, 2.0D, 2.0D, 4.5D, 14.0D, 14.0D);
    }

    private static VoxelShape boxPx(double x1, double y1, double z1,
                                    double x2, double y2, double z2) {
        return Shapes.box(x1 / 16.0D, y1 / 16.0D, z1 / 16.0D,
                x2 / 16.0D, y2 / 16.0D, z2 / 16.0D);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurretBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            // 客户端也要 tick：把 prev 角收敛到当前角，否则观察者看到的炮管会一直重复插值。
            return createTickerHelper(type, ModBlockEntities.TURRET.get(), TurretBlockEntity::clientTick);
        }
        return createTickerHelper(type, ModBlockEntities.TURRET.get(), TurretBlockEntity::serverTick);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 炮塔里装着的弹箱随方块掉落，不能被方块本身的掉落表吞掉。
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurretBlockEntity turret) {
            turret.dropAmmo();
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        // Forge 的服务端交互顺序是「方块 use → 物品 useOn」：手持绑定器时必须让位，
        // 否则右键炮塔会先被当成进入炮塔，绑定器的 useOn 永远不会执行。
        if (player.getItemInHand(hand).getItem() instanceof TurretBinderItem) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TurretBlockEntity turret) {
            return turret.enter(player);
        }
        return InteractionResult.PASS;
    }
}
