package com.opamb.barotrauma.mission;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.CabinetBlock;
import com.opamb.barotrauma.blockentity.CabinetBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.equipment.BagContainer;
import com.opamb.barotrauma.equipment.BagManager;
import com.opamb.barotrauma.equipment.CrateContainer;
import com.opamb.barotrauma.item.CrateItem;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 货运任务的货物（照原版 {@code CargoMission}）：
 *
 * <ul>
 *   <li>接取时把真实箱子生成到船上 {@code crateshelf} 的空格里（一箱一格），
 *       箱子里按 {@link CargoManifest} 塞内容物，箱子 NBT 记下任务 id；</li>
 *   <li>船上没有空货架时退化成 1 个散箱（掉在货架/玩家旁边，出航后跟着船走）；</li>
 *   <li>靠泊目的站时统计“还在船上/玩家身上”的任务箱，达到 98% 才成功，
 *       结算按 箱数 × 每箱报酬，任务结束（成功/失败/放弃/超时）清掉所有任务箱。</li>
 * </ul>
 *
 * <p>原版完成判定见 {@code CargoMission.DetermineCompleted}/{@code IsItemDelivered}：
 * 靠站时统计未移除、Condition&gt;0、还在本船（或连挂船）上的货物 / 总数 ≥
 * {@code requireddeliveryamount}（默认 0.98）。</p>
 */
public final class MissionCargo {

    /** 箱子物品 NBT：这条货物属于哪个任务（{@link Mission#id}）。 */
    public static final String TAG_MISSION = "BarotraumaCargoMission";
    /** 散箱 ItemEntity 持久数据：相对船核心的偏移（出航后跟船用）。 */
    public static final String TAG_OFFSET = "BarotraumaCargoOffset";
    /** 散箱 ItemEntity 持久数据：只有接任务时生成的那一个散箱会跟船（玩家事后丢下海的不会）。 */
    public static final String TAG_PINNED = "BarotraumaCargoPinned";

    /** 统计“还在船上”的散箱半径（相对船核心）。 */
    private static final double LOOSE_RADIUS = 48.0D;

    /** 原版默认 {@code requireddeliveryamount}。 */
    public static final float REQUIRED_DELIVERY_AMOUNT = CargoManifest.REQUIRED_DELIVERY_AMOUNT;

    private MissionCargo() {
    }

    /** 这条任务的货物清单；不是货运任务返回 null。 */
    @Nullable
    public static CargoManifest manifestFor(Mission mission) {
        MissionSpec spec = MissionCatalog.offer(mission.id);
        return spec == null ? null : CargoManifest.forMission(spec.id());
    }

    public static boolean isCargoMission(Mission mission) {
        return manifestFor(mission) != null;
    }

    /** 生成一箱任务货物（箱型 + 内容物 + 任务 id 标签）。 */
    public static ItemStack createCrate(Mission mission, CargoManifest manifest) {
        Item crateItem = BuiltInRegistries.ITEM.get(
                new ResourceLocation(BarotraumaMod.MOD_ID, manifest.crateItemId()));
        ItemStack stack = new ItemStack(crateItem);
        if (!(crateItem instanceof CrateItem crate)) {
            // 箱型物品没注册上（不该发生）：至少别把任务卡死。
            stack.getOrCreateTag().putInt(TAG_MISSION, mission.id);
            return stack;
        }
        int capacity = crate.capacity();
        CrateContainer container = new CrateContainer(stack, capacity);
        int slot = 0;
        for (CargoManifest.Entry entry : manifest.contents()) {
            Item item = BuiltInRegistries.ITEM.get(
                    new ResourceLocation(BarotraumaMod.MOD_ID, entry.itemId()));
            int left = entry.count();
            int maxStack = Math.max(1, item.getMaxStackSize());
            while (left > 0 && slot < capacity) {
                int amount = Math.min(left, maxStack);
                ItemStack inner = new ItemStack(item, amount);
                if (container.canPlaceItem(slot, inner)) {
                    // 原作：任务货物（含箱内每一件）都是 AllowStealing=false + 在站内生成
                    // → 算赃物，格子上显示红手。
                    container.setItem(slot, com.opamb.barotrauma.item.Illegitimate.mark(inner));
                    left -= amount;
                }
                slot++;
            }
        }
        stack.getOrCreateTag().putInt(TAG_MISSION, mission.id);
        return com.opamb.barotrauma.item.Illegitimate.mark(stack);
    }

    public static boolean isCargo(ItemStack stack, int missionId) {
        return !stack.isEmpty() && stack.getTag() != null
                && stack.getTag().contains(TAG_MISSION)
                && stack.getTag().getInt(TAG_MISSION) == missionId;
    }

    /** 是不是任意一条任务的货物箱。 */
    public static boolean isMissionCrate(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().contains(TAG_MISSION);
    }

    // ------------------------------------------------------------------ 生成

    /**
     * 接取货运任务：把货装到船上货架（有几个空位装几箱），一个空位都没有时
     * 生成 1 个散箱。返回实际生成的箱数（&gt;0），失败返回 0。
     */
    public static int spawn(ServerLevel level, ServerPlayer player, Mission mission) {
        CargoManifest manifest = manifestFor(mission);
        if (manifest == null) {
            return 0;
        }
        int placed = 0;
        for (CabinetBlockEntity shelf : shelves(level, player)) {
            for (int slot = 0; slot < shelf.getContainerSize() && placed < manifest.maxCrates();
                 slot++) {
                if (!shelf.getItem(slot).isEmpty()) {
                    continue;
                }
                ItemStack crate = createCrate(mission, manifest);
                if (!shelf.canPlaceItem(slot, crate)) {
                    continue;
                }
                shelf.setItem(slot, crate);
                placed++;
            }
        }
        if (placed == 0) {
            // 原版退化成 1 个散箱；我们把它掉在货架/玩家旁边，出航后由
            // CargoFollowEvents 钉在船上。
            Vec3 at = looseSpawnPoint(level, player);
            ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z,
                    createCrate(mission, manifest));
            entity.setPickUpDelay(10);
            // 只有这一只"接任务时生成的散箱"打跟船标记；玩家事后自己丢下海的箱子不钉。
            entity.getPersistentData().putBoolean(TAG_PINNED, true);
            Vec3 core = coreWorld(level, player);
            if (core != null) {
                Vec3 offset = at.subtract(core);
                entity.getPersistentData().putDouble(TAG_OFFSET + "X", offset.x);
                entity.getPersistentData().putDouble(TAG_OFFSET + "Y", offset.y);
                entity.getPersistentData().putDouble(TAG_OFFSET + "Z", offset.z);
            }
            level.addFreshEntity(entity);
            placed = 1;
        }
        mission.setCargo(placed, manifest.rewardPerCrate());
        BarotraumaMod.LOGGER.info("[missions] 货运 {} 装了 {} 箱（每箱 {} credits）",
                mission.id, placed, manifest.rewardPerCrate());
        return placed;
    }

    // ------------------------------------------------------------------ 判定 / 清理

    /** 靠泊目的站时统计还在“船上”的任务箱（货架 + 玩家背包/双手 + 船附近散箱）。 */
    public static int countDelivered(ServerLevel level, ServerPlayer player, Mission mission) {
        int total = 0;
        for (CabinetBlockEntity shelf : shelves(level, player)) {
            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                if (isCargo(shelf.getItem(slot), mission.id)) {
                    total++;
                }
            }
        }
        // 散箱必须还在船核心附近才算交到；丢进海里/丢在半路的箱子自然不计。
        Vec3 center = coreWorld(level, player);
        if (center == null) {
            center = player.position();
        }
        double radiusSqr = LOOSE_RADIUS * LOOSE_RADIUS;
        for (ServerPlayer online : level.getServer().getPlayerList().getPlayers()) {
            // 只统计已经随船抵达的玩家：留在出发站/别的维度的箱子不算交付。
            if (online.level() != level
                    || online.position().distanceToSqr(center) > radiusSqr) {
                continue;
            }
            total += countInventory(online, mission.id);
        }
        for (ItemEntity entity : looseCrates(level, center)) {
            if (isCargo(entity.getItem(), mission.id)) {
                total++;
            }
        }
        return Math.min(total, Math.max(1, mission.cargoTotal()));
    }

    /** 离线持有者的交付统计：只数船上的货架和船附近的散箱，不碰任何玩家背包。 */
    public static int countDeliveredOffline(ServerLevel level, Mission mission) {
        int total = 0;
        for (CabinetBlockEntity shelf : shelves(level)) {
            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                if (isCargo(shelf.getItem(slot), mission.id)) {
                    total++;
                }
            }
        }
        Vec3 center = coreWorld(level);
        if (center != null) {
            for (ItemEntity entity : looseCrates(level, center)) {
                if (isCargo(entity.getItem(), mission.id)) {
                    total++;
                }
            }
        }
        return Math.min(total, Math.max(1, mission.cargoTotal()));
    }

    /** 这条任务是否达到交付阈值（原版默认 98%）。 */
    public static boolean deliveredEnough(Mission mission, int delivered) {
        return CargoManifest.deliveredEnough(mission.cargoTotal(), delivered);
    }

    /** 清掉这条任务的所有货物：货架、玩家背包/双手、装备背包、地上散箱。 */
    public static void clear(ServerLevel level, ServerPlayer player, Mission mission) {
        for (CabinetBlockEntity shelf : shelves(level, player)) {
            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                if (isCargo(shelf.getItem(slot), mission.id)) {
                    shelf.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
        for (ServerPlayer online : level.getServer().getPlayerList().getPlayers()) {
            clearInventory(online, mission.id);
        }
        clearShipCrates(level, mission);
    }

    /** 只清船上的任务货（货架 + 地上的散箱），不碰玩家背包；离线结算用。 */
    public static void clearShip(ServerLevel level, Mission mission) {
        for (CabinetBlockEntity shelf : shelves(level)) {
            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                if (isCargo(shelf.getItem(slot), mission.id)) {
                    shelf.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
        clearShipCrates(level, mission);
    }

    private static void clearShipCrates(ServerLevel level, Mission mission) {
        for (ItemEntity entity : allLooseCrates(level)) {
            if (isCargo(entity.getItem(), mission.id)) {
                entity.discard();
            }
        }
    }

    // ------------------------------------------------------------------ 内部

    /** 船上所有箱子货架的方块实体（按坐标排序，保证装载顺序稳定）。 */
    public static List<CabinetBlockEntity> shelves(ServerLevel level, ServerPlayer player) {
        List<CabinetBlockEntity> assembled = assembledShelves(level);
        if (assembled != null) {
            return assembled;
        }
        ShipCoreBlockEntity core = ShipCores.nearestCore(
                level, player.blockPosition(), 64.0D);
        return core == null ? List.of() : shelvesOf(level, core, BlockPos.ZERO);
    }

    /** 不依赖玩家实体的货架列表（离线结算/清理用）。 */
    public static List<CabinetBlockEntity> shelves(ServerLevel level) {
        List<CabinetBlockEntity> assembled = assembledShelves(level);
        if (assembled != null) {
            return assembled;
        }
        Vec3 center = coreWorld(level);
        ShipCoreBlockEntity core = center == null ? null
                : ShipCores.nearestCore(level, BlockPos.containing(center), 64.0D);
        return core == null ? List.of() : shelvesOf(level, core, BlockPos.ZERO);
    }

    /**
     * 装配后的货架：方块被 VS2 搬到船坞，船核心的几何仍是装配时的世界坐标，
     * 所以按“船坞核心 − 装配核心”的平移去读船坞里的方块实体。
     *
     * @return null = 没装配/船坞核心还没就绪，调用方退回世界查找
     */
    @Nullable
    private static List<CabinetBlockEntity> assembledShelves(ServerLevel level) {
        if (!com.opamb.barotrauma.ship.vs.VsState.active()) {
            return null;
        }
        BlockPos yard = com.opamb.barotrauma.ship.vs.VsPilot.coreYardPos();
        BlockPos original = com.opamb.barotrauma.ship.vs.VsPilot.corePos();
        if (yard == null || original == null) {
            return null;
        }
        if (level.getBlockEntity(yard) instanceof ShipCoreBlockEntity core) {
            return shelvesOf(level, core, yard.subtract(original));
        }
        return null;
    }

    private static List<CabinetBlockEntity> shelvesOf(ServerLevel level, ShipCoreBlockEntity core,
                                                      BlockPos translate) {
        ShipState state = core.state();
        if (state == null) {
            return List.of();
        }
        Set<BlockPos> blocks = state.collectBlocks(level, translate);
        List<CabinetBlockEntity> out = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (level.getBlockEntity(pos) instanceof CabinetBlockEntity cabinet
                    && cabinet.kind() == CabinetBlock.Kind.CRATE_SHELF) {
                out.add(cabinet);
            }
        }
        out.sort(Comparator.comparingLong(cabinet -> cabinet.getBlockPos().asLong()));
        return out;
    }

    /** 船核心的世界坐标（船坞里用船体位置 + 核心偏移）。 */
    @Nullable
    public static Vec3 coreWorld(ServerLevel level, ServerPlayer player) {
        Vec3 world = coreWorld(level);
        if (world != null) {
            return world;
        }
        ShipCoreBlockEntity core = ShipCores.nearestCore(level, player.blockPosition(), 64.0D);
        return core == null ? null : Vec3.atCenterOf(core.getBlockPos());
    }

    /** 不依赖玩家实体的船核心世界坐标（离线结算用）。 */
    @Nullable
    public static Vec3 coreWorld(ServerLevel level) {
        Vec3 ship = com.opamb.barotrauma.ship.vs.VsPilot.shipPosition();
        Vec3 offset = com.opamb.barotrauma.ship.vs.VsPilot.shipCoreOffset();
        return ship != null && offset != null ? ship.add(offset) : null;
    }

    /** 散箱落点：停泊时掉在货架顶上，出航（物理船装配）时掉在玩家脚边。 */
    private static Vec3 looseSpawnPoint(ServerLevel level, ServerPlayer player) {
        if (com.opamb.barotrauma.ship.vs.VsState.active()) {
            // 物理船装配后货架方块在船坞坐标系里，世界坐标不能用；直接掉在玩家脚边。
            return player.position().add(0.0D, 0.2D, 0.0D);
        }
        for (CabinetBlockEntity shelf : shelves(level, player)) {
            BlockPos pos = shelf.getBlockPos();
            return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D);
        }
        return player.position().add(0.0D, 0.2D, 0.0D);
    }

    private static int countInventory(ServerPlayer player, int missionId) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isCargo(inventory.getItem(i), missionId)) {
                count++;
            }
        }
        BagContainer bag = BagManager.container(player);
        for (int i = 0; i < bag.capacity(); i++) {
            if (isCargo(bag.getItem(i), missionId)) {
                count++;
            }
        }
        return count;
    }

    private static void clearInventory(ServerPlayer player, int missionId) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isCargo(inventory.getItem(i), missionId)) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        BagContainer bag = BagManager.container(player);
        for (int i = 0; i < bag.capacity(); i++) {
            if (isCargo(bag.getItem(i), missionId)) {
                bag.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /** 当前维度里 center 附近 {@link #LOOSE_RADIUS} 格内躺着的 ItemEntity（算“在船上”）。 */
    private static List<ItemEntity> looseCrates(ServerLevel level, Vec3 center) {
        List<ItemEntity> out = new ArrayList<>();
        double radiusSqr = LOOSE_RADIUS * LOOSE_RADIUS;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item && !item.getItem().isEmpty()
                    && item.position().distanceToSqr(center) <= radiusSqr) {
                out.add(item);
            }
        }
        return out;
    }

    /** 当前维度里所有躺在地上的 ItemEntity（结算清理用，不限制距离）。 */
    private static List<ItemEntity> allLooseCrates(ServerLevel level) {
        List<ItemEntity> out = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item && !item.getItem().isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }
}
