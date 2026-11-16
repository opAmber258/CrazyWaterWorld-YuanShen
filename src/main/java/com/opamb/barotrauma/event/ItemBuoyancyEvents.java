package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 深海里的掉落物浮力（对照原作 {@code Item.ApplyWaterForces}）。
 *
 * <p>原作水密度按 10 算：密度 ≤ 10 的物品浮起来，密度 &gt; 10 的缓慢下沉，
 * 同时有与速度平方成正比的阻力；不是 Minecraft 这种自由落体直接掉到海底。
 * 我们按原作 XML 的密度给物品分类：氧气瓶/焊接油箱是 9.7（浮），
 * 切割机/焊枪/潜水刀/箱子/矿物是 15–25（沉）——绝大多数东西应该是沉的。</p>
 *
 * <p>原作的浮力会让轻物品一路浮到海面；本模组的海面在 y=-1，离可玩区域
 * 几十格，照搬会让物品直接飘出视野消失。所以上浮只在头顶 8 格内有实体方块
 * （进水的船舱/洞穴顶）时生效；开阔水域一律缓慢下沉，掉的东西还能捞回来。</p>
 *
 * <p>只在海水区域且不在物理船干燥舱/实体地面上生效；站内、船上、海底的掉落物
 * 保持原版重力，避免把柜子里的掉落物也吹起来。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemBuoyancyEvents {

    /** 玩家周围多远内的掉落物才处理（区块卸载的本来也不 tick）。 */
    private static final double SCAN_RADIUS = 96.0D;
    /** 水阻：每 tick 保留的水平速度比例。 */
    private static final double DRAG = 0.82D;
    /** 浮力/下沉的终端速度（格/tick）。 */
    private static final double FLOAT_SPEED = 0.020D;
    private static final double SINK_SPEED = -0.018D;
    /** 头顶多少格内有实体方块才允许上浮（进水的船舱/洞穴顶）。 */
    private static final int CEILING_SCAN = 8;
    /** 本处理器把 noGravity 设成 true 的物品；离开扫描范围时要恢复重力。 */
    private static final Set<UUID> CONTROLLED = new HashSet<>();

    private ItemBuoyancyEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel deep = server.getLevel(ModDimensions.THE_DEEP);
        if (deep == null) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != deep) {
                continue;
            }
            AABB area = player.getBoundingBox().inflate(SCAN_RADIUS);
            for (ItemEntity item : deep.getEntitiesOfClass(ItemEntity.class, area)) {
                if (!seen.add(item.getUUID())) {
                    continue;
                }
                applyBuoyancy(deep, item);
            }
        }
        restoreUncontrolled(deep, seen);
    }

    /** 离开处理范围的物品恢复原版重力，避免 noGravity 永久挂在掉落物上。 */
    private static void restoreUncontrolled(ServerLevel deep, Set<UUID> seen) {
        if (CONTROLLED.isEmpty()) {
            return;
        }
        java.util.Iterator<UUID> iterator = CONTROLLED.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            if (seen.contains(id)) {
                continue;
            }
            if (deep.getEntity(id) instanceof ItemEntity item
                    && !item.isRemoved() && item.isNoGravity()) {
                item.setNoGravity(false);
            }
            iterator.remove();
        }
    }

    /** 本处理器接管一个物品的重力：记下来，离开扫描范围时好还原。 */
    private static void hold(ItemEntity item) {
        item.setNoGravity(true);
        CONTROLLED.add(item.getUUID());
    }

    /** 还原原版重力（物品出水/落地/进干区），不再需要跟踪。 */
    private static void release(ItemEntity item) {
        item.setNoGravity(false);
        CONTROLLED.remove(item.getUUID());
    }

    private static void applyBuoyancy(ServerLevel level, ItemEntity item) {
        double y = item.getY();
        boolean inWater = y <= ModDimensions.WATER_TOP_Y
                && y >= ModDimensions.WATER_BOTTOM_Y;
        if (!inWater) {
            if (item.isNoGravity()) {
                release(item);
            }
            return;
        }
        // 干燥区（物理船舱 / 哨站内 / 方块船舱）保持原版重力，物品落在甲板上。
        // 不能只查物理船舱：没有物理船时这里会返回 null，哨站里的物品就会浮上天花板。
        if (!com.opamb.barotrauma.world.OceanEnvironment.isOceanWaterAt(level, item.position())) {
            if (item.isNoGravity()) {
                release(item);
            }
            return;
        }
        // 脚下有实体方块（站内地面/海底/船甲板）：按原版重力落下，不浮。
        if (level.getBlockState(item.blockPosition().below()).isSolid()) {
            if (item.isNoGravity()) {
                release(item);
            }
            return;
        }
        boolean floats = floats(item.getItem());
        Vec3 velocity = item.getDeltaMovement();
        // 只有"轻物品 + 头顶有天花板"才上浮（进水船舱/洞穴顶，对应原作浮到水面）。
        // 开阔水域没有天花板，一律缓慢下沉，否则会一路飘到 y=-1 消失。
        if (floats && hasCeilingAbove(level, item.blockPosition())) {
            if (level.getBlockState(item.blockPosition().above()).isSolid()
                    || y >= ModDimensions.WATER_TOP_Y - 3) {
                // 已经贴住天花板/水面：停住，不再往上顶。
                hold(item);
                item.setDeltaMovement(velocity.x * DRAG, 0.0D, velocity.z * DRAG);
                item.hasImpulse = true;
                return;
            }
            double vy = velocity.y * DRAG + FLOAT_SPEED;
            hold(item);
            item.setDeltaMovement(velocity.x * DRAG, vy, velocity.z * DRAG);
            item.hasImpulse = true;
            return;
        }
        // 下沉（绝大多数物品：原作密度 15–30）。
        double vy = velocity.y * DRAG + SINK_SPEED;
        hold(item);
        item.setDeltaMovement(velocity.x * DRAG, vy, velocity.z * DRAG);
        item.hasImpulse = true;
    }

    /** 头顶 {@link #CEILING_SCAN} 格内有没有实体方块（进水的船舱顶/洞穴顶）。 */
    private static boolean hasCeilingAbove(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= CEILING_SCAN; dy++) {
            cursor.set(pos.getX(), pos.getY() + dy, pos.getZ());
            if (level.getBlockState(cursor).isSolid()) {
                return true;
            }
        }
        return false;
    }

    /** 近似原作密度：只有明确"轻"的物品浮，其余（工具/矿物/箱子/方块）都沉。 */
    private static boolean floats(ItemStack stack) {
        // 原作 9.7：氧气瓶、焊接油箱浮。
        if (stack.is(com.opamb.barotrauma.equipment.EquipmentTags.OXYGEN_TANK)) {
            return true;
        }
        if (stack.is(ModItems.WELDING_FUEL_TANK.get())) {
            return true;
        }
        // 原作 15–25：工具、潜水刀、货箱；矿物 25。全部下沉。
        return false;
    }
}
