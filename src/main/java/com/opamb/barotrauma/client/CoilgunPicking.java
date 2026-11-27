package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.CoilgunHitbox;
import com.opamb.barotrauma.entity.CoilgunInteractionEntity;
import com.opamb.barotrauma.item.TurretBinderItem;
import com.opamb.barotrauma.item.WrenchItem;
import com.opamb.barotrauma.ship.vs.VsClientTurret;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class CoilgunPicking {
    private static boolean binderUsed;
    private static boolean wrenchUsed;

    private CoilgunPicking() {}

    /** Runs after the ordinary block/entity ray, and keeps its hit if it is closer. */
    public static void pick(float partial) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null || player.isSpectator() || mc.getCameraEntity() != player
                || TurretClient.isActive()) return;
        double range = Math.min(player.getEntityReach(), player.getBlockReach());
        Vec3 eye = player.getEyePosition(partial);
        Vec3 direction = player.getViewVector(1);
        HitResult ordinary = mc.hitResult;
        if (ordinary != null && ordinary.getType() != HitResult.Type.MISS) {
            Vec3 point = ordinary.getLocation();
            if (ordinary instanceof BlockHitResult block) {
                Vec3 world = VsClientTurret.toWorld(level, block.getBlockPos(), point);
                if (world.distanceToSqr(eye) < point.distanceToSqr(eye)) point = world;
            }
            range = Math.min(range, eye.distanceTo(point));
        }
        Vec3 end = eye.add(direction.scale(range));
        double nearest = range * range;
        var search = new AABB(eye, end).inflate(CoilgunHitbox.MAX_OFFSET);
        for (var proxy : level.getEntitiesOfClass(CoilgunInteractionEntity.class, search)) {
            Vec3 hit = proxy.clip(point -> VsClientTurret.toWorld(level, proxy.masterPos(), point),
                    eye, end, partial, 0);
            if (hit != null && eye.distanceToSqr(hit) < nearest) {
                nearest = eye.distanceToSqr(hit);
                mc.hitResult = new EntityHitResult(proxy, hit);
                mc.crosshairPickEntity = null;
            }
        }
    }

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isUseItem() && mc.player != null && mc.level != null
                && WrenchItem.isDismantling(mc.player, event.getHand())) {
            if (wrenchUsed) {
                event.setSwingHand(false);
                event.setCanceled(true);
            } else if (mc.options.keyUse.isDown()) {
                wrenchUsed = (mc.hitResult instanceof EntityHitResult hit
                        && hit.getEntity() instanceof CoilgunInteractionEntity)
                        || (mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                        && WrenchItem.canTarget(mc.level.getBlockState(block.getBlockPos())));
            }
            return;
        }
        if (!event.isUseItem() || mc.player == null || !(mc.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof CoilgunInteractionEntity)
                || !(mc.player.getItemInHand(event.getHand()).getItem() instanceof TurretBinderItem)) return;
        if (binderUsed) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
        binderUsed = mc.options.keyUse.isDown();
    }

    @SubscribeEvent
    public static void release(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().options.keyUse.isDown()) {
            binderUsed = false;
            wrenchUsed = false;
        }
    }
}
