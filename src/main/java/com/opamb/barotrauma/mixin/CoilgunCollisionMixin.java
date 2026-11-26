package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.block.CoilgunHitbox;
import com.opamb.barotrauma.entity.CoilgunInteractionEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

@Mixin(Level.class)
public abstract class CoilgunCollisionMixin implements EntityGetter {
    /** Extend the inherited entity collision query, as used by both player prediction and the server. */
    @Override
    public List<VoxelShape> getEntityCollisions(Entity moving, AABB area) {
        if (area.getSize() < 1e-7) return List.of();
        AABB query = area.inflate(1e-7);
        var predicate = moving == null ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(moving::canCollideWith);
        List<Entity> ordinary = getEntities(moving, query, predicate);
        List<VoxelShape> collisions = new ArrayList<>(ordinary.size());
        for (Entity other : ordinary) collisions.add(Shapes.create(other.getBoundingBox()));
        if (moving instanceof CoilgunInteractionEntity
                || moving != null && (moving.isSpectator() || moving.noPhysics)) return collisions;
        var guns = getEntitiesOfClass(CoilgunInteractionEntity.class, area.inflate(CoilgunHitbox.MAX_OFFSET),
                Entity::isAlive);
        if (guns.isEmpty()) return collisions;
        VoxelShape search = Shapes.create(query);
        for (var gun : guns) {
            for (VoxelShape shape : gun.collisionShapes()) {
                if (Shapes.joinIsNotEmpty(shape, search, BooleanOp.AND)) collisions.add(shape);
            }
        }
        return collisions;
    }
}
