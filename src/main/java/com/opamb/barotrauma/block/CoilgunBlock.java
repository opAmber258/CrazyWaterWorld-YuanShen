package com.opamb.barotrauma.block;

import com.opamb.barotrauma.ship.vs.VsSupport;
import com.opamb.barotrauma.ship.vs.VsBridge;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CoilgunBlock extends TurretBlock {
    // The center is 0 for old saves. The other values cover offsets in the mounting plane.
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 24);
    private static final double BASE_CENTER_Z = 25.2 / 16;
    private static final double PITCH_HEIGHT = 38.24 / 16;
    private static final double PITCH_FORWARD = 1.68 / 16;
    private static final double MUZZLE_HEIGHT = 5.04 / 16;
    private static final double MUZZLE_FORWARD = 71.68 / 16;
    private static final TurretSpec SPEC = new TurretSpec(5, 5, 0.5, PITCH_HEIGHT, 0.5,
            MUZZLE_FORWARD, 3.55, -2.55, -89, 0, 0, 89, -80, 80);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    public CoilgunBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, 0));
    }

    @Override
    public TurretSpec spec() {
        return SPEC;
    }

    @Override
    public TurretWeaponKind weaponKind() {
        return TurretWeaponKind.COILGUN;
    }

    public static double baseCenterZ() {
        return BASE_CENTER_Z;
    }

    public static Vec3 barrelPivot(BlockState state, BlockPos pos, float yaw) {
        int side = state.getValue(MOUNT) == Direction.DOWN ? -1 : 1;
        double deck = side < 0 ? 1 : 0;
        return Vec3.atBottomCenterOf(pos).add(0, deck + side * PITCH_HEIGHT, 0)
                .add(TurretBlockEntity.localLookVector(yaw, 0).scale(PITCH_FORWARD));
    }

    public static Vec3 muzzle(BlockState state, BlockPos pos, float yaw, float pitch) {
        Vec3 flat = TurretBlockEntity.localLookVector(yaw, 0);
        Vec3 forward = TurretBlockEntity.localLookVector(yaw, pitch);
        double angle = Math.toRadians(pitch);
        Vec3 barrelUp = flat.scale(Math.sin(angle)).add(0, Math.cos(angle), 0);
        int side = state.getValue(MOUNT) == Direction.DOWN ? -1 : 1;
        return barrelPivot(state, pos, yaw).add(forward.scale(MUZZLE_FORWARD))
                .add(barrelUp.scale(side * MUZZLE_HEIGHT));
    }

    public static Vec3 sight(BlockState state, BlockPos pos, float yaw, float pitch) {
        // The remote view sits on the bore axis, between the pivot and muzzle.
        return muzzle(state, pos, yaw, pitch).subtract(
                TurretBlockEntity.localLookVector(yaw, pitch).scale(MUZZLE_FORWARD - 1.0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    private static BlockPos offset(BlockState state, int part) {
        if (part == 0) return BlockPos.ZERO;
        int index = part <= 12 ? part - 1 : part;
        int x = index % 5 - 2;
        int z = index / 5 - 2;
        Direction forward = state.getValue(FACING);
        Direction right = forward.getCounterClockWise();
        return new BlockPos(right.getStepX() * x + forward.getStepX() * z, 0,
                right.getStepZ() * x + forward.getStepZ() * z);
    }

    public static BlockPos masterPos(BlockState state, BlockPos pos) {
        return pos.subtract(offset(state, state.getValue(PART)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace().getAxis().isHorizontal()) return null;
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Level level = context.getLevel();
        for (int part = 1; part <= 24; part++) {
            BlockPos cell = context.getClickedPos().offset(offset(state, part));
            if (!level.hasChunkAt(cell) || !level.getWorldBorder().isWithinBounds(cell)
                    || !level.getBlockState(cell).isAir()) return null;
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || state.getValue(PART) != 0) return;
        for (int part = 1; part <= 24; part++) {
            level.setBlock(pos.offset(offset(state, part)), state.setValue(PART, part), Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(PART) == 0) return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        return state;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        boolean assembly = VsSupport.available() && VsBridge.isAssembling();
        if (!level.isClientSide && !assembly && !moving && !next.is(this) && !REMOVING.get()) {
            REMOVING.set(true);
            try {
                BlockPos master = masterPos(state, pos);
                for (int part = 0; part <= 24; part++) {
                    BlockPos cell = master.offset(offset(state, part));
                    if (cell.equals(pos) || !level.hasChunkAt(cell)) continue;
                    BlockState other = level.getBlockState(cell);
                    if (other.is(this) && other.getValue(PART) == part
                            && other.getValue(FACING) == state.getValue(FACING)
                            && other.getValue(MOUNT) == state.getValue(MOUNT)) {
                        level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            } finally {
                REMOVING.remove();
            }
        }
        if (!level.isClientSide && !assembly && !moving && !next.is(this)
                && level.getBlockEntity(pos) instanceof TurretBlockEntity turret) {
            turret.dropAmmo();
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && state.getValue(PART) != 0) {
            BlockPos master = masterPos(state, pos);
            if (level.hasChunkAt(master) && level.getBlockState(master).is(this)
                    && level.getBlockState(master).getValue(PART) == 0) {
                Block.dropResources(level.getBlockState(master), level, master);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == 0 ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? super.newBlockEntity(pos, state) : null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return baseShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return baseShape(state);
    }

    private static VoxelShape baseShape(BlockState state) {
        // Approximate the fixed mounting plate, not the swept volume of the rotating barrel.
        BlockPos offset = offset(state, state.getValue(PART));
        Direction facing = state.getValue(FACING);
        double alongX = facing.getAxis() == Direction.Axis.X ? -facing.getStepX() * 0.03 : 0;
        double alongZ = facing.getAxis() == Direction.Axis.Z ? -facing.getStepZ() * 0.03 : 0;
        boolean ceiling = state.getValue(MOUNT) == Direction.DOWN;
        return Block.box(
                Math.max(0, (0.5 - 1.85 + alongX - offset.getX()) * 16), ceiling ? 12 : 0,
                Math.max(0, (0.5 - 1.85 + alongZ - offset.getZ()) * 16),
                Math.min(16, (0.5 + 1.85 + alongX - offset.getX()) * 16), ceiling ? 16 : 4,
                Math.min(16, (0.5 + 1.85 + alongZ - offset.getZ()) * 16));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // The barrel is operated by a linked periscope, leaving right-click free for the binder.
        return InteractionResult.PASS;
    }
}
