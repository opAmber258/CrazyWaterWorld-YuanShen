package com.opamb.barotrauma.entity;

import com.opamb.barotrauma.block.CoilgunBlock;
import com.opamb.barotrauma.block.CoilgunHitbox;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.item.TurretBinderItem;
import com.opamb.barotrauma.item.WrenchItem;
import com.opamb.barotrauma.ship.vs.VsSupport;
import com.opamb.barotrauma.ship.vs.VsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.UnaryOperator;

/** Transient collision and interaction entity owned by the coilgun's master block. */
public final class CoilgunInteractionEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> MASTER = SynchedEntityData.defineId(
            CoilgunInteractionEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final double NETWORK_TOLERANCE = 0.25;
    private int lastOwnerTick;
    private CoilgunHitbox.Pose collisionPose;
    private List<VoxelShape> collisionShapes = List.of();

    public CoilgunInteractionEntity(EntityType<? extends CoilgunInteractionEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected AABB makeBoundingBox() {
        // Entity interaction packets have a broad reach check before the oriented ray test below.
        return super.makeBoundingBox().inflate(2.5);
    }

    public void follow(TurretBlockEntity turret) {
        entityData.set(MASTER, turret.getBlockPos());
        lastOwnerTick = tickCount;
        Vec3 pos = toWorld(CoilgunHitbox.anchor(turret.getBlockPos(), TurretBlockEntity.mount(turret.getBlockState())));
        setPos(pos.x, pos.y, pos.z);
    }

    public BlockPos masterPos() {
        return entityData.get(MASTER);
    }

    @Nullable
    private TurretBlockEntity turret() {
        BlockPos pos = masterPos();
        return level().hasChunkAt(pos) && level().getBlockEntity(pos) instanceof TurretBlockEntity turret
                && turret.getBlockState().getBlock() instanceof CoilgunBlock ? turret : null;
    }

    private Vec3 toWorld(Vec3 local) {
        return VsSupport.available() ? VsBridge.collisionTransform(level(), masterPos()).apply(local) : local;
    }

    @Nullable
    public Vec3 clip(UnaryOperator<Vec3> transform, Vec3 start, Vec3 end, float partial, double padding) {
        TurretBlockEntity turret = turret();
        if (turret == null || start.distanceToSqr(end) < 1e-12) return null;
        return CoilgunHitbox.clip(masterPos(), TurretBlockEntity.mount(turret.getBlockState()),
                Mth.rotLerp(partial, turret.prevYaw(), turret.yaw()),
                Mth.lerp(partial, turret.prevPitch(), turret.pitch()), turret.recoilPixels(partial),
                transform, start, end, padding);
    }

    public List<VoxelShape> collisionShapes() {
        TurretBlockEntity turret = turret();
        if (turret == null) return List.of();
        UnaryOperator<Vec3> transform = VsSupport.available()
                ? VsBridge.collisionTransform(level(), masterPos()) : UnaryOperator.identity();
        var pose = CoilgunHitbox.pose(masterPos(), TurretBlockEntity.mount(turret.getBlockState()),
                turret.yaw(), turret.pitch(), turret.recoilPixels(0), transform);
        if (!pose.equals(collisionPose)) {
            collisionPose = pose;
            collisionShapes = pose.collisionShapes();
        }
        return collisionShapes;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 relativeHit, InteractionHand hand) {
        var item = player.getItemInHand(hand).getItem();
        boolean dismantling = WrenchItem.isDismantling(player, hand);
        if (player.isSpectator() || (!dismantling && !(item instanceof TurretBinderItem))) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) return InteractionResult.SUCCESS;
        Vec3 eye = player.getEyePosition();
        Vec3 delta = position().add(relativeHit).subtract(eye);
        double reach = Math.min(player.getEntityReach(), player.getBlockReach());
        if (!Double.isFinite(delta.lengthSqr()) || delta.lengthSqr() > reach * reach) return InteractionResult.FAIL;
        Vec3 end = eye.add(delta.normalize().scale(reach));
        Vec3 hit = clip(this::toWorld, eye, end, 1, NETWORK_TOLERANCE);
        if (hit == null) return InteractionResult.FAIL;
        Vec3 reportedHit = eye.add(delta);
        Vec3 sightEnd = eye.distanceToSqr(hit) > delta.lengthSqr() ? hit : reportedHit;
        var blockingShape = dismantling ? ClipContext.Block.COLLIDER : ClipContext.Block.OUTLINE;
        if (level().clip(new ClipContext(eye, sightEnd, blockingShape, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.BLOCK) return InteractionResult.FAIL;
        if (dismantling) {
            return player instanceof ServerPlayer serverPlayer
                    ? WrenchItem.dismantleAt(serverPlayer, masterPos()) : InteractionResult.FAIL;
        }
        return ((TurretBinderItem) item).bindAt(player, hand, masterPos());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount - lastOwnerTick > 5) discard();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        TurretBlockEntity turret = turret();
        return turret == null ? ItemStack.EMPTY : new ItemStack(turret.getBlockState().getBlock());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(MASTER, BlockPos.ZERO);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
