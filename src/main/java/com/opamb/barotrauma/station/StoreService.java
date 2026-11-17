package com.opamb.barotrauma.station;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.CabinetBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.campaign.CampaignState;
import com.opamb.barotrauma.entity.NpcEntity;
import com.opamb.barotrauma.equipment.BagManager;
import com.opamb.barotrauma.equipment.CrateContainer;
import com.opamb.barotrauma.equipment.ItemSubSlot;
import com.opamb.barotrauma.item.CrateItem;
import com.opamb.barotrauma.item.TwoHanded;
import com.opamb.barotrauma.mission.MissionCargo;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.StoreActionPacket;
import com.opamb.barotrauma.network.StoreStatePacket;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipGeometry;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.world.DockingBay;
import com.opamb.barotrauma.world.StationStoreSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商人商店服务端逻辑：打开、整单购买、整单出售、从潜艇出售。
 *
 * <p>对照原作 Store：库存有限、商人余额 5000、卖出价 30%、每次到访补货。
 * 购买先校验整单库存/余额，再逐件入包；背包放不下的件数退款。出售只收
 * 主背包里耐久 ≥90%、没有装东西的物品；"潜艇"页（原作 {@code StoreTab.SellSub}）
 * 按物品类型聚合船上钢柜/货架里的货。</p>
 */
public final class StoreService {

    private static final double MAX_DISTANCE_SQ = 8.0D * 8.0D;
    private static final int MAX_LINE_QUANTITY = 64;
    /** 找船核心的范围（靠泊后潜艇可能离商人二三十格）。 */
    private static final double SHIP_SEARCH_RANGE = 64.0D;

    private StoreService() {
    }

    public static void open(ServerPlayer player, NpcEntity npc) {
        if (!NpcEntity.ROLE_MERCHANT.equals(npc.role())) {
            return;
        }
        if (player.distanceToSqr(npc) > MAX_DISTANCE_SQ) {
            return;
        }
        ServerLevel level = player.serverLevel();
        DockingBay.Site site = DockingBay.siteAt(npc.getBlockX(), npc.getBlockZ());
        if (site == null) {
            return;
        }
        sendState(player, site, true);
    }

    public static void handle(ServerPlayer player, StoreActionPacket packet) {
        ServerLevel level = player.serverLevel();
        NpcEntity merchant = merchantNear(player);
        if (merchant == null) {
            return;
        }
        DockingBay.Site site = DockingBay.siteAt(merchant.getBlockX(), merchant.getBlockZ());
        if (site == null) {
            return;
        }
        CampaignState wallet = CampaignState.wallet(level);
        StationStoreSavedData data = StationStoreSavedData.get(level);
        StationStoreSavedData.SiteStore store =
                data.store(site, wallet.roundsCompleted());
        if (packet.action() == StoreActionPacket.BUY) {
            buyCart(player, wallet, data, store, packet.lines(), packet.deliverImmediately());
        } else if (packet.action() == StoreActionPacket.SELL) {
            sellCart(player, wallet, data, store, packet.lines());
        } else if (packet.action() == StoreActionPacket.SELL_SUB) {
            sellSubCart(player, wallet, data, store, packet.lines());
        }
        sendState(player, site, false);
    }

    // ------------------------------------------------------------------
    // 购买：整单校验 → 逐件入包 → 放不下的退款
    // ------------------------------------------------------------------

    private static void buyCart(ServerPlayer player, CampaignState wallet,
                                StationStoreSavedData data,
                                StationStoreSavedData.SiteStore store,
                                List<StoreActionPacket.Line> lines,
                                boolean deliverImmediately) {
        Map<String, Integer> cart = new LinkedHashMap<>();
        for (StoreActionPacket.Line line : lines) {
            int quantity = line.quantity();
            if (quantity <= 0 || quantity > MAX_LINE_QUANTITY) {
                continue;
            }
            cart.merge(line.itemId(), quantity, Integer::sum);
        }
        if (cart.isEmpty()) {
            return;
        }

        int total = 0;
        List<StoreActionPacket.Line> valid = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            StoreCatalog.Entry catalog = StoreCatalog.byItemId(entry.getKey());
            if (catalog == null || !catalog.buyable()) {
                message(player, "store.no_such_item", ChatFormatting.RED);
                return;
            }
            int quantity = Math.min(entry.getValue(), store.stock(entry.getKey()));
            if (quantity <= 0) {
                message(player, "store.out_of_stock", ChatFormatting.GRAY, catalog.itemId());
                return;
            }
            total += StorePricing.buyPrice(catalog.basePrice(), store.modifier()) * quantity;
            valid.add(new StoreActionPacket.Line(entry.getKey(), -1, quantity));
        }
        if (!wallet.spendCredits(total)) {
            message(player, "store.not_enough_money", ChatFormatting.RED, total, wallet.credits());
            return;
        }

        // 照原作购买确认的两个选项：立即获得（角色背包）或送至潜艇。
        // "送至潜艇"只记账，起航时由 deliverPending 送进潜艇货柜。
        StoreDeliverySavedData delivery = deliverImmediately
                ? null : StoreDeliverySavedData.get(player.serverLevel());

        int bought = 0;
        int charged = 0;
        int failed = 0;
        Component firstName = null;
        for (StoreActionPacket.Line line : valid) {
            StoreCatalog.Entry catalog = StoreCatalog.byItemId(line.itemId());
            if (catalog == null) {
                failed += line.quantity();
                continue;
            }
            Item item = resolveItem(line.itemId());
            if (item == null) {
                failed += line.quantity();
                continue;
            }
            int unitPrice = StorePricing.buyPrice(catalog.basePrice(), store.modifier());
            for (int i = 0; i < line.quantity(); i++) {
                ItemStack unit = new ItemStack(item);
                if (item == ModItems.WELDING_TOOL.get()) {
                    ItemSubSlot.set(unit, new ItemStack(ModItems.WELDING_FUEL_TANK.get()));
                }
                // 先取名字再 add：Inventory.add 会把传入 stack 吃掉。
                Component name = unit.getHoverName();
                boolean placed;
                if (deliverImmediately) {
                    placed = giveToCharacter(player, unit);
                } else {
                    delivery.add(player.getUUID(), line.itemId(), 1);
                    placed = true;
                }
                if (!placed) {
                    failed++;
                    continue;
                }
                if (firstName == null) {
                    firstName = name;
                }
                store.takeOne(line.itemId());
                store.addBalance(unitPrice);
                bought++;
                charged += unitPrice;
            }
        }
        if (deliverImmediately) {
            player.getInventory().setChanged();
        }
        int refund = total - charged;
        if (refund > 0) {
            wallet.addCredits(refund);
        }
        data.setDirty();
        if (bought <= 0) {
            message(player, "store.purchase_failed_refund", ChatFormatting.RED);
            return;
        }
        Component where = deliverImmediately ? Component.empty()
                : Component.translatable("store.purchased.deliver");
        Component suffix = failed > 0
                ? Component.translatable("store.purchased.failed_suffix", failed) : Component.empty();
        message(player, "store.purchased", ChatFormatting.GREEN,
                firstName, bought, where, suffix, charged);
    }

    /**
     * 起航时把"送至潜艇"的订购货送进潜艇货舱。
     *
     * <p>照原作 {@code CargoManager.DeliverItemsToSub}：只认货舱里的<b>箱子</b>
     * （货架上的箱子 + 船上地上躺着的箱子），钢柜/衣柜/潜水服柜都不算货舱；
     * 一件箱子都没有时就地生成一个金属箱来装，实在装不下（箱子类大件、箱满）
     * 的散落在货舱地上。物品都已生成成功，所以不再留到下次起航。</p>
     */
    public static void deliverPending(ServerPlayer player) {
        deliverPending(player, player.serverLevel(), player.blockPosition());
    }

    /**
     * 起航交付：显式指定存档维度与潜艇锚点（核心坐标），不依赖乘员传送前的位置。
     */
    public static void deliverPending(ServerPlayer player, ServerLevel level, BlockPos shipAnchor) {
        StoreDeliverySavedData data = StoreDeliverySavedData.get(level);
        Map<String, Integer> pending = data.take(player.getUUID());
        if (pending.isEmpty()) {
            return;
        }
        List<ContainerSlot> containers = cargoContainers(level, shipAnchor);
        Map<String, Integer> remaining = deliverPendingFor(level, shipAnchor, player.getUUID(),
                pending, player, containers);
        if (!remaining.isEmpty()) {
            data.restore(player.getUUID(), remaining);
        }
    }

    /**
     * 起航交付（全体）：按待交付订单的 UUID 遍历，包括已经离线的玩家。
     *
     * <p>离线玩家没有 {@code ServerPlayer}，但“送进货舱的箱子”不需要玩家对象；
     * 只有“没有箱子时生成金属箱 / 放不下时丢到货舱地上”需要在线玩家。离线玩家
     * 送不出去的货物继续留在待交付列表，下次起航再试。</p>
     *
     * @param online 当前在线玩家：用来匹配订单主人（生成箱子/落地 + 聊天提示）
     */
    public static void deliverPendingOnDeparture(ServerLevel level, BlockPos shipAnchor,
                                                 List<ServerPlayer> online) {
        StoreDeliverySavedData data = StoreDeliverySavedData.get(level);
        Map<UUID, Map<String, Integer>> pending = data.takeAll();
        if (pending.isEmpty()) {
            return;
        }
        List<ContainerSlot> containers = cargoContainers(level, shipAnchor);
        Map<UUID, ServerPlayer> onlineById = new LinkedHashMap<>();
        for (ServerPlayer player : online) {
            onlineById.put(player.getUUID(), player);
        }
        for (Map.Entry<UUID, Map<String, Integer>> entry : pending.entrySet()) {
            UUID owner = entry.getKey();
            ServerPlayer player = onlineById.get(owner);
            Map<String, Integer> remaining = deliverPendingFor(level, shipAnchor, owner,
                    entry.getValue(), player, containers);
            if (!remaining.isEmpty()) {
                data.restore(owner, remaining);
            }
        }
    }

    /**
     * 把一名玩家的待交付货物送进货舱：货架上的箱子 → 生成金属箱 → 货舱地上。
     *
     * @param player 订单主人在线时的玩家对象；离线传 {@code null}（没有落点，
     *               装不下的留在待交付列表，下次起航再试）
     * @return 送不出去的剩余（调用方负责 {@link StoreDeliverySavedData#restore}）
     */
    private static Map<String, Integer> deliverPendingFor(ServerLevel level, BlockPos shipAnchor,
                                                          UUID owner,
                                                          Map<String, Integer> items,
                                                          @Nullable ServerPlayer player,
                                                          List<ContainerSlot> containers) {
        Map<String, Integer> remaining = new LinkedHashMap<>();
        int delivered = 0;
        Component firstName = null;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Item item = resolveItem(entry.getKey());
            if (item == null) {
                remaining.merge(entry.getKey(), entry.getValue(), Integer::sum);
                continue;
            }
            for (int i = 0; i < entry.getValue(); i++) {
                ItemStack unit = new ItemStack(item);
                if (item == ModItems.WELDING_TOOL.get()) {
                    ItemSubSlot.set(unit, new ItemStack(ModItems.WELDING_FUEL_TANK.get()));
                }
                Component name = unit.getHoverName();
                // 潜水服这类大件不进箱子：必须先跳过所有装箱路径，直接落地。
                boolean bulky = isBulkyCargo(unit);
                boolean placed = !bulky && deliverToShip(containers, unit);
                if (!placed && player != null && !bulky) {
                    // 原作有 cargocontaineridentifier 的小件：没有/放不下箱子时就生成一个金属箱。
                    placed = spawnCargoCrate(level, shipAnchor, player, unit, containers);
                }
                if (!placed && player != null) {
                    // 原作：箱子放不下就留在潜艇货舱地上。离线交付没有落点，留待下次起航。
                    // 实体生成可能被 EntityJoinLevelEvent 取消，只有真的进世界才算交付。
                    placed = dropInShip(level, shipAnchor, player, unit);
                }
                if (!placed) {
                    remaining.merge(entry.getKey(), 1, Integer::sum);
                    continue;
                }
                delivered++;
                if (firstName == null) {
                    firstName = name;
                }
            }
        }
        // 货架上的箱子内容改了：刷新方块实体，客户端列表才会更新。
        for (CabinetBlockEntity shelf : MissionCargo.shelvesNear(level, shipAnchor)) {
            shelf.setChanged();
        }
        if (player != null) {
            if (delivered > 0) {
                player.getInventory().setChanged();
                message(player, "store.delivered", ChatFormatting.GREEN, firstName, delivered);
            }
            if (!remaining.isEmpty()) {
                int count = remaining.values().stream().mapToInt(Integer::intValue).sum();
                message(player, "store.delivery_remaining", ChatFormatting.GRAY, count);
            }
        } else {
            BarotraumaMod.LOGGER.info("[store] 离线玩家 {} 的待交付货物：送进货舱 {} 件，剩余 {} 件",
                    owner, delivered,
                    remaining.values().stream().mapToInt(Integer::intValue).sum());
        }
        return remaining;
    }

    /**
     * 「立即获得」入包顺序，照原作 {@code Character.Inventory.TryPutItem}：
     * 先手持位（我们的热键栏 0..8），再装备背包（工具带/背包）。
     */
    private static boolean giveToCharacter(ServerPlayer player, ItemStack stack) {
        // 双手物品（箱子）不能进快捷栏/背包：和 InventoryMixin 一致，副手空就放副手。
        if (TwoHanded.is(stack)) {
            if (!player.getOffhandItem().isEmpty()) {
                return false;
            }
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack.copy());
            stack.setCount(0);
            return true;
        }
        if (tryPutInRange(player.getInventory(), 0, 9, stack)) {
            return true;
        }
        int capacity = BagManager.capacity(player);
        return capacity > 0 && tryPutInRange(BagManager.container(player), 0, capacity, stack);
    }

    /** 在容器的 [start, end) 里放东西：先合并同类堆，再找空槽。放完返回 true。 */
    private static boolean tryPutInRange(Container container, int start, int end, ItemStack stack) {
        for (int i = start; i < end; i++) {
            ItemStack existing = container.getItem(i);
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, stack)
                    || !container.canPlaceItem(i, stack)) {
                continue;
            }
            int max = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            int move = Math.min(stack.getCount(), max - existing.getCount());
            if (move <= 0) {
                continue;
            }
            existing.grow(move);
            stack.shrink(move);
            container.setChanged();
            if (stack.isEmpty()) {
                return true;
            }
        }
        for (int i = start; i < end; i++) {
            if (!container.getItem(i).isEmpty() || !container.canPlaceItem(i, stack)) {
                continue;
            }
            container.setItem(i, stack.copy());
            container.setChanged();
            stack.setCount(0);
            return true;
        }
        return false;
    }

    /**
     * 货舱里的箱子：货架上的箱子 + 船上地上躺着的箱子。
     *
     * <p>原作 {@code FindReusableCargoContainers} 只收 {@code Tags.Crate} 的容器，
     * 所以钢柜/衣柜这些不算——之前的实现把全船钢柜都当货舱，买的东西直接塞柜子里，
     * 和原作行为不一致。</p>
     */
    private static List<ContainerSlot> cargoContainers(ServerLevel level, BlockPos shipAnchor) {
        List<ContainerSlot> out = new ArrayList<>();
        // 1) 货架上摆着的箱子。未装配时没有物理船位置，按起航锚点找核心。
        for (CabinetBlockEntity shelf : MissionCargo.shelvesNear(level, shipAnchor)) {
            for (int i = 0; i < shelf.getContainerSize(); i++) {
                addCrateSlots(out, shelf.getItem(i), shelf);
            }
        }
        // 2) 货舱地上躺着的箱子（物理船的箱子跟着船在世界坐标里）。
        Vec3 center = MissionCargo.coreWorldNear(level, shipAnchor);
        if (center != null) {
            double radius = 48.0D;
            AABB area = new AABB(center.x - radius, center.y - radius, center.z - radius,
                    center.x + radius, center.y + radius, center.z + radius);
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
                // 只复用已经钉住跟船的箱子：没标记的箱子不会跟着潜艇走，
                // 货装进去后船一开就留在原地。
                if (!entity.getPersistentData().getBoolean(MissionCargo.TAG_PINNED)) {
                    continue;
                }
                addCrateSlots(out, entity.getItem());
            }
        }
        return out;
    }

    /** 这件物品是不是能装货的箱子（货运任务的箱子不算）。 */
    private static boolean isCargoCrate(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof CrateItem crate
                && crate.capacity() > 0 && !MissionCargo.isMissionCrate(stack);
    }

    private static void addCrateSlots(List<ContainerSlot> out, ItemStack crate) {
        addCrateSlots(out, crate, null);
    }

    /** @param host 箱子所在的宿主容器（货架/柜子）；地面箱子传 null */
    private static void addCrateSlots(List<ContainerSlot> out, ItemStack crate,
                                      @Nullable Container host) {
        if (!isCargoCrate(crate)) {
            return;
        }
        int capacity = ((CrateItem) crate.getItem()).capacity();
        CrateContainer container = new CrateContainer(crate, capacity);
        for (int i = 0; i < capacity; i++) {
            out.add(new ContainerSlot(container, i, host));
        }
    }

    /** 潜水服这类大件原作不进箱子，直接放货舱地上。 */
    private static boolean isBulkyCargo(ItemStack stack) {
        return stack.is(ModItems.DIVING_SUIT.get()) || stack.is(ModItems.GLASS_DIVING_SUIT.get());
    }

    /**
     * 货舱里没有能装的箱子时，照原作 {@code GetOrCreateCargoContainerFor} 生成一个金属箱，
     * 把货装进去后再丢到货舱地上；一个都没装下（箱子类大件）就不生成空箱。
     */
    private static boolean spawnCargoCrate(ServerLevel level, BlockPos shipAnchor,
                                           ServerPlayer player, ItemStack stack,
                                           List<ContainerSlot> slots) {
        ItemStack original = stack.copy();
        ItemStack crateStack = new ItemStack(ModItems.METAL_CRATE.get());
        CrateContainer container = new CrateContainer(crateStack,
                ((CrateItem) crateStack.getItem()).capacity());
        if (!tryPutInRange(container, 0, container.getContainerSize(), stack)) {
            return false;
        }
        Vec3 drop = cargoDropPoint(level, shipAnchor, player);
        ItemEntity entity = new ItemEntity(level, drop.x, drop.y, drop.z, crateStack);
        entity.setDefaultPickUpDelay();
        pinToShip(entity);
        // 生成可能被 EntityJoinLevelEvent 取消：只有真的进世界才登记槽位。
        if (!level.addFreshEntity(entity)) {
            // 货物已经被搬进这个没生成的箱子：还回原堆，让调用方走“落地”分支。
            stack.setCount(original.getCount());
            return false;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            slots.add(new ContainerSlot(container, i));
        }
        return true;
    }

    /** 货舱落点：物理船掉玩家脚边，普通船掉货架顶/船核心上。 */
    private static Vec3 cargoDropPoint(ServerLevel level, BlockPos shipAnchor,
                                       ServerPlayer player) {
        if (VsState.active()) {
            // 物理船：用船核心世界坐标（舱内）。不能用订单主人的位置——
            // 起航交付时主人可能还在另一个维度，货会生成在远处。
            Vec3 core = MissionCargo.coreWorld(level);
            if (core != null) {
                return core;
            }
            return player.position().add(0.0D, 0.2D, 0.0D);
        }
        for (CabinetBlockEntity shelf : MissionCargo.shelvesNear(level, shipAnchor)) {
            BlockPos pos = shelf.getBlockPos();
            return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D);
        }
        Vec3 core = MissionCargo.coreWorldNear(level, shipAnchor);
        return core != null ? core : player.position().add(0.0D, 0.2D, 0.0D);
    }

    /** 货舱容器都满了：把货丢在货舱地上（原作也是留在货舱地上）。 */
    private static boolean dropInShip(ServerLevel level, BlockPos shipAnchor,
                                      ServerPlayer player, ItemStack stack) {
        Vec3 drop = cargoDropPoint(level, shipAnchor, player);
        ItemEntity entity = new ItemEntity(level, drop.x, drop.y, drop.z, stack.copy());
        entity.setDefaultPickUpDelay();
        pinToShip(entity);
        return level.addFreshEntity(entity);
    }

    /**
     * 让散在货舱地上的订购货跟着潜艇走。
     *
     * <p>交付发生在装配之前，普通掉落物在船变成 VS2 物理船并开走后会留在原地；
     * 复用货运散箱那套"按核心偏移钉住"的标记（{@code CargoFollowEvents}），
     * 和原作"货物是潜艇的子物体"等价。</p>
     */
    private static void pinToShip(ItemEntity entity) {
        entity.getPersistentData().putBoolean(MissionCargo.TAG_PINNED, true);
    }

    /**
     * 把一件商品放进潜艇货柜：先合并到同类堆，再找空槽。
     * 货架/潜水服柜等有 {@code canPlaceItem} 限制的槽位不会硬塞。
     */
    private static boolean deliverToShip(List<ContainerSlot> slots, ItemStack stack) {
        for (ContainerSlot slot : slots) {
            if (!slot.container().canPlaceItem(slot.slot(), stack)) {
                continue;
            }
            ItemStack existing = slot.container().getItem(slot.slot());
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, stack)) {
                continue;
            }
            int max = Math.min(existing.getMaxStackSize(), slot.container().getMaxStackSize());
            int move = Math.min(stack.getCount(), max - existing.getCount());
            if (move <= 0) {
                continue;
            }
            existing.grow(move);
            stack.shrink(move);
            slot.setChanged();
            if (stack.isEmpty()) {
                return true;
            }
        }
        for (ContainerSlot slot : slots) {
            if (!slot.container().getItem(slot.slot()).isEmpty()
                    || !slot.container().canPlaceItem(slot.slot(), stack)) {
                continue;
            }
            slot.container().setItem(slot.slot(), stack.copy());
            slot.setChanged();
            stack.setCount(0);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 出售：整单校验 → 逐件移除
    // ------------------------------------------------------------------

    private static void sellCart(ServerPlayer player, CampaignState wallet,
                                 StationStoreSavedData data,
                                 StationStoreSavedData.SiteStore store,
                                 List<StoreActionPacket.Line> lines) {
        Map<Integer, Integer> cart = new LinkedHashMap<>();
        Map<Integer, String> expectedIds = new LinkedHashMap<>();
        for (StoreActionPacket.Line line : lines) {
            int quantity = line.quantity();
            if (quantity <= 0 || quantity > MAX_LINE_QUANTITY) {
                continue;
            }
            String expected = line.itemId();
            if (expected == null || expected.isEmpty()) {
                message(player, "store.cart_changed", ChatFormatting.RED);
                return;
            }
            String previous = expectedIds.putIfAbsent(line.slot(), expected);
            if (previous != null && !previous.equals(expected)) {
                message(player, "store.cart_changed", ChatFormatting.RED);
                return;
            }
            cart.merge(line.slot(), quantity, Integer::sum);
        }
        if (cart.isEmpty()) {
            return;
        }

        int total = 0;
        List<SellUnit> valid = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0
                    || slot >= net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE) {
                // 商店只收主背包 0..35；护甲槽/副手槽不能通过出售行写入。
                continue;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String expected = expectedIds.get(slot);
            String actual = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (expected == null || !expected.equals(actual)) {
                message(player, "store.cart_changed", ChatFormatting.RED);
                return;
            }
            StoreCatalog.Entry catalog = catalogFor(stack);
            if (catalog == null) {
                message(player, "store.not_buying", ChatFormatting.RED);
                return;
            }
            if (MissionCargo.isMissionCrate(stack)) {
                message(player, "store.mission_crate", ChatFormatting.RED);
                return;
            }
            if (!canSell(stack)) {
                message(player, "store.cant_sell", ChatFormatting.RED);
                return;
            }
            int quantity = Math.min(entry.getValue(), stack.getCount());
            if (quantity <= 0) {
                continue;
            }
            int unitPrice = StorePricing.sellPrice(catalog.basePrice(), store.modifier());
            total += unitPrice * quantity;
            valid.add(new SellUnit(slot, quantity, unitPrice));
        }
        if (valid.isEmpty()) {
            return;
        }
        if (!store.canPay(total)) {
            message(player, "store.merchant_broke", ChatFormatting.RED);
            return;
        }

        int sold = 0;
        int earned = 0;
        Component firstName = null;
        for (SellUnit unit : valid) {
            ItemStack stack = player.getInventory().getItem(unit.slot());
            if (stack.isEmpty()) {
                continue;
            }
            int quantity = Math.min(unit.quantity(), stack.getCount());
            if (quantity <= 0) {
                continue;
            }
            if (firstName == null) {
                firstName = stack.getHoverName();
            }
            stack.shrink(quantity);
            if (stack.isEmpty()) {
                player.getInventory().setItem(unit.slot(), ItemStack.EMPTY);
            }
            sold += quantity;
            earned += unit.unitPrice() * quantity;
        }
        player.getInventory().setChanged();
        if (sold <= 0) {
            return;
        }
        wallet.addCredits(earned);
        store.pay(earned);
        data.setDirty();
        message(player, "store.sold", ChatFormatting.GREEN, firstName, sold, earned);
    }

    // ------------------------------------------------------------------
    // 从潜艇出售（原作 StoreTab.SellSub）：按物品类型聚合船上货柜
    // ------------------------------------------------------------------

    private static void sellSubCart(ServerPlayer player, CampaignState wallet,
                                    StationStoreSavedData data,
                                    StationStoreSavedData.SiteStore store,
                                    List<StoreActionPacket.Line> lines) {
        Map<String, Integer> cart = new LinkedHashMap<>();
        for (StoreActionPacket.Line line : lines) {
            int quantity = line.quantity();
            if (quantity <= 0 || quantity > MAX_LINE_QUANTITY) {
                continue;
            }
            if (line.itemId() == null || line.itemId().isEmpty()) {
                continue;
            }
            cart.merge(line.itemId(), quantity, Integer::sum);
        }
        if (cart.isEmpty()) {
            return;
        }

        List<ContainerSlot> containers = shipContainers(player);
        Map<String, Integer> available = shipSellable(containers);
        int total = 0;
        List<SubSale> valid = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            StoreCatalog.Entry catalog = StoreCatalog.byItemId(entry.getKey());
            if (catalog == null) {
                message(player, "store.not_buying", ChatFormatting.RED);
                return;
            }
            int quantity = Math.min(entry.getValue(), available.getOrDefault(entry.getKey(), 0));
            if (quantity <= 0) {
                message(player, "store.sub_missing", ChatFormatting.GRAY, entry.getKey());
                return;
            }
            int unitPrice = StorePricing.sellPrice(catalog.basePrice(), store.modifier());
            total += unitPrice * quantity;
            valid.add(new SubSale(entry.getKey(), quantity, unitPrice));
        }
        if (valid.isEmpty()) {
            return;
        }
        if (!store.canPay(total)) {
            message(player, "store.merchant_broke_sub", ChatFormatting.RED);
            return;
        }

        int sold = 0;
        int earned = 0;
        Component firstName = null;
        for (SubSale sale : valid) {
            int remaining = sale.quantity();
            for (ContainerSlot slot : containers) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack stack = slot.container().getItem(slot.slot());
                if (stack.isEmpty()) {
                    continue;
                }
                StoreCatalog.Entry catalog = catalogFor(stack);
                if (catalog == null || !catalog.itemId().equals(sale.itemId())) {
                    continue;
                }
                if (MissionCargo.isMissionCrate(stack) || !canSell(stack)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                if (firstName == null) {
                    firstName = stack.getHoverName();
                }
                slot.container().removeItem(slot.slot(), take);
                slot.setChanged();
                remaining -= take;
                sold += take;
                earned += sale.unitPrice() * take;
            }
        }
        if (sold <= 0) {
            return;
        }
        wallet.addCredits(earned);
        store.pay(earned);
        data.setDirty();
        message(player, "store.sub_sold", ChatFormatting.GREEN, firstName, sold, earned);
    }

    private record SubSale(String itemId, int quantity, int unitPrice) {
    }

    /**
     * 一个可写入的容器格：钢柜/货架方块实体，或箱子物品内部的 {@link CrateContainer}。
     *
     * <p>{@code host} 是箱子所在的宿主容器：卖箱内商品后只标箱子物品的 NBT 不够，
     * 宿主货架区块也要 setChanged，否则存档会跳过、售出的货重载后回来。</p>
     */
    private record ContainerSlot(Container container, int slot, @Nullable Container host) {
        ContainerSlot(Container container, int slot) {
            this(container, slot, container);
        }

        void setChanged() {
            container.setChanged();
            if (host != null && host != container) {
                host.setChanged();
            }
        }
    }

    /** 玩家所在潜艇上的所有钢柜/货架槽位；找不到船核心时返回空表。 */
    private static List<ContainerSlot> shipContainers(ServerPlayer player) {
        return shipContainers(player.serverLevel(), player.blockPosition());
    }

    /**
     * 以 {@code center} 为锚点找潜艇货柜：{@code center} 可以是船核心本身
     * （起航交付时用），也可以是玩家位置。
     */
    private static List<ContainerSlot> shipContainers(ServerLevel level, BlockPos center) {
        ShipState state;
        ShipGeometry.Bounds bounds;
        boolean physicalShip;
        // 只有活动物理船就在这个维度、且锚点确实属于它时才用船坞范围。
        if (VsState.active() && VsPilot.activeLevel() == level
                && VsState.isPhysical(level, center)) {
            physicalShip = true;
            // 物理船的方块实体在船坞里：几何是装配坐标，扫描范围要加上船坞偏移。
            state = VsState.activeState();
            if (state == null) {
                return List.of();
            }
            ShipGeometry.Bounds assembly = state.geometry().bounds();
            BlockPos minYard = VsPilot.toYard(new BlockPos(
                    assembly.minX(), assembly.minY(), assembly.minZ()));
            BlockPos maxYard = VsPilot.toYard(new BlockPos(
                    assembly.maxX(), assembly.maxY(), assembly.maxZ()));
            if (minYard == null || maxYard == null) {
                return List.of();
            }
            bounds = new ShipGeometry.Bounds(minYard.getX(), minYard.getY(), minYard.getZ(),
                    maxYard.getX(), maxYard.getY(), maxYard.getZ());
        } else {
            physicalShip = false;
            ShipCoreBlockEntity core = null;
            if (level.hasChunkAt(center)
                    && level.getBlockEntity(center) instanceof ShipCoreBlockEntity atCenter) {
                core = atCenter;
            }
            if (core == null) {
                core = ShipCores.nearestCore(level, center, SHIP_SEARCH_RANGE);
            }
            if (core == null) {
                return List.of();
            }
            state = core.state();
            if (state == null) {
                state = core.rescan(level);
            }
            if (state == null) {
                return List.of();
            }
            bounds = state.geometry().bounds();
        }
        List<ContainerSlot> out = new ArrayList<>();
        ChunkPos min = new ChunkPos(bounds.minX() >> 4, bounds.minZ() >> 4);
        ChunkPos max = new ChunkPos(bounds.maxX() >> 4, bounds.maxZ() >> 4);
        for (int cx = min.x; cx <= max.x; cx++) {
            for (int cz = min.z; cz <= max.z; cz++) {
                ChunkAccess chunk = level.getChunkSource()
                        .getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    if (!bounds.contains(pos)) {
                        continue;
                    }
                    // 包围盒内不等于船体：柜子必须已经收进这艘船的船体成员
                    // （实心家具也算船体方块），码头旁边同包围盒的柜子不算。
                    BlockPos cell = physicalShip ? VsPilot.toAssembly(pos) : pos;
                    if (cell == null || !state.tracksHullBlock(cell)) {
                        continue;
                    }
                    if (chunk.getBlockEntity(pos) instanceof CabinetBlockEntity cabinet) {
                        for (int slot = 0; slot < cabinet.getContainerSize(); slot++) {
                            out.add(new ContainerSlot(cabinet, slot));
                            // 货舱箱子里的商品也要能卖：把箱内容器展开成槽位。
                            addCrateSlots(out, cabinet.getItem(slot), cabinet);
                        }
                    }
                }
            }
        }
        // 地面上的跟船货箱（交付时生成的金属箱）也要能出售。
        // 已拆回方块形态的潜艇没有 shipPosition()，用锚点找核心。
        Vec3 coreCenter = MissionCargo.coreWorldNear(level, center);
        if (coreCenter != null) {
            double radius = 48.0D;
            AABB area = new AABB(coreCenter.x - radius, coreCenter.y - radius, coreCenter.z - radius,
                    coreCenter.x + radius, coreCenter.y + radius, coreCenter.z + radius);
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
                if (entity.getPersistentData().getBoolean(MissionCargo.TAG_PINNED)) {
                    addCrateSlots(out, entity.getItem());
                }
            }
        }
        return out;
    }

    /** 按物品 id 聚合船上可出售的件数（任务货箱和装东西的箱子不算）。 */
    private static Map<String, Integer> shipSellable(List<ContainerSlot> containers) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ContainerSlot slot : containers) {
            ItemStack stack = slot.container().getItem(slot.slot());
            if (stack.isEmpty() || MissionCargo.isMissionCrate(stack) || !canSell(stack)) {
                continue;
            }
            StoreCatalog.Entry catalog = catalogFor(stack);
            if (catalog == null) {
                continue;
            }
            out.merge(catalog.itemId(), stack.getCount(), Integer::sum);
        }
        return out;
    }

    private record SellUnit(int slot, int quantity, int unitPrice) {
    }

    /** 原作出售规则：耐久 ≥90%，且没有装填/装内容。 */
    private static boolean canSell(ItemStack stack) {
        if (ItemSubSlot.hasSubSlot(stack) && !ItemSubSlot.get(stack).isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof CrateItem crate
                && !new CrateContainer(stack, crate.capacity()).isEmpty()) {
            return false;
        }
        if (stack.isDamageableItem()) {
            double ratio = (stack.getMaxDamage() - stack.getDamageValue())
                    / (double) Math.max(1, stack.getMaxDamage());
            return ratio >= 0.9D;
        }
        return true;
    }

    private static StoreCatalog.Entry catalogFor(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? null : StoreCatalog.byItemId(key.toString());
    }

    private static Item resolveItem(String itemId) {
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            // 找不到的 ID 在部分注册表实现里会返回 AIR，不能当成合法商品。
            return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static NpcEntity merchantNear(ServerPlayer player) {
        List<NpcEntity> found = player.serverLevel().getEntitiesOfClass(
                NpcEntity.class, player.getBoundingBox().inflate(8.0D),
                npc -> NpcEntity.ROLE_MERCHANT.equals(npc.role()));
        return found.isEmpty() ? null : found.get(0);
    }

    private static void sendState(ServerPlayer player, DockingBay.Site site, boolean open) {
        ServerLevel level = player.serverLevel();
        CampaignState wallet = CampaignState.wallet(level);
        StationStoreSavedData.SiteStore store = StationStoreSavedData.get(level)
                .store(site, wallet.roundsCompleted());
        List<StoreStatePacket.Entry> entries = new ArrayList<>();
        for (StoreCatalog.Entry entry : StoreCatalog.entries()) {
            entries.add(new StoreStatePacket.Entry(
                    entry.itemId(), entry.buyable(), store.stock(entry.itemId()),
                    StorePricing.buyPrice(entry.basePrice(), store.modifier()),
                    StorePricing.sellPrice(entry.basePrice(), store.modifier()),
                    entry.category()));
        }
        List<StoreStatePacket.SubEntry> subEntries = new ArrayList<>();
        for (Map.Entry<String, Integer> line : shipSellable(shipContainers(player)).entrySet()) {
            StoreCatalog.Entry catalog = StoreCatalog.byItemId(line.getKey());
            if (catalog == null) {
                continue;
            }
            subEntries.add(new StoreStatePacket.SubEntry(line.getKey(), line.getValue(),
                    StorePricing.sellPrice(catalog.basePrice(), store.modifier())));
        }
        ModNetwork.sendToPlayer(player,
                new StoreStatePacket(wallet.credits(), store.balance(), open, entries, subEntries));
    }

    private static void message(ServerPlayer player, String key, ChatFormatting color,
                                Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(color));
    }
}
