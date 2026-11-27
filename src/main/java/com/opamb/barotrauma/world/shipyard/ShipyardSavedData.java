package com.opamb.barotrauma.world.shipyard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 造船厂地块与会话记录（挂在 shipyard 维度）。
 *
 * <p>只保存“玩家 → 地块/返回点/当前船库 id”这类世界级状态；船体本身是普通
 * 方块船，随区块与核心 NBT 存档。船库是跨存档目录，不写进这里。</p>
 */
public final class ShipyardSavedData extends SavedData {

    public static final String NAME = "barotrauma_shipyard";

    private int nextPlot;
    private final Map<UUID, Plot> plots = new HashMap<>();
    /** 同服只允许一艘测试船：记录当前试航玩家与测试原点。 */
    private UUID testOwner;
    private int testOriginX;
    private int testOriginY;
    private int testOriginZ;
    private int testMaxX;
    private int testMaxY;
    private int testMaxZ;

    public static ShipyardSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ShipyardSavedData::load, ShipyardSavedData::new, NAME);
    }

    public Plot plot(UUID player) {
        return plots.get(player);
    }

    /** 分配新地块；已有则原样返回。 */
    public Plot allocate(UUID player) {
        Plot existing = plots.get(player);
        if (existing != null) {
            return existing;
        }
        Plot plot = new Plot(player, nextPlot++);
        plots.put(player, plot);
        setDirty();
        return plot;
    }

    public void recordReturn(UUID player, ResourceLocation dimension, double x, double y, double z,
                             float yaw, float pitch) {
        Plot plot = plots.get(player);
        if (plot == null) {
            return;
        }
        plot.returnDimension = dimension;
        plot.returnX = x;
        plot.returnY = y;
        plot.returnZ = z;
        plot.returnYaw = yaw;
        plot.returnPitch = pitch;
        setDirty();
    }

    public void setShipId(UUID player, String shipId) {
        Plot plot = plots.get(player);
        if (plot != null) {
            plot.shipId = shipId == null ? "" : shipId;
            setDirty();
        }
    }

    public void setDirty(UUID player, boolean dirty) {
        Plot plot = plots.get(player);
        if (plot != null) {
            plot.dirty = dirty;
            setDirty();
        }
    }

    public void markVisited(UUID player, long gameTime) {
        Plot plot = plots.get(player);
        if (plot != null) {
            plot.lastVisit = gameTime;
            setDirty();
        }
    }

    public UUID testOwner() {
        return testOwner;
    }

    public void beginTest(UUID owner, int x, int y, int z, int maxX, int maxY, int maxZ) {
        testOwner = owner;
        testOriginX = x;
        testOriginY = y;
        testOriginZ = z;
        testMaxX = maxX;
        testMaxY = maxY;
        testMaxZ = maxZ;
        setDirty();
    }

    public void endTest() {
        testOwner = null;
        testOriginX = 0;
        testOriginY = 0;
        testOriginZ = 0;
        testMaxX = 0;
        testMaxY = 0;
        testMaxZ = 0;
        setDirty();
    }

    public int testOriginX() {
        return testOriginX;
    }

    public int testOriginY() {
        return testOriginY;
    }

    public int testOriginZ() {
        return testOriginZ;
    }

    public int testMaxX() {
        return testMaxX;
    }

    public int testMaxY() {
        return testMaxY;
    }

    public int testMaxZ() {
        return testMaxZ;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextPlot", nextPlot);
        if (testOwner != null) {
            tag.putUUID("TestOwner", testOwner);
            tag.putInt("TestX", testOriginX);
            tag.putInt("TestY", testOriginY);
            tag.putInt("TestZ", testOriginZ);
            tag.putInt("TestMaxX", testMaxX);
            tag.putInt("TestMaxY", testMaxY);
            tag.putInt("TestMaxZ", testMaxZ);
        }
        ListTag list = new ListTag();
        for (Plot plot : plots.values()) {
            list.add(plot.save());
        }
        tag.put("Plots", list);
        return tag;
    }

    private static ShipyardSavedData load(CompoundTag tag) {
        ShipyardSavedData data = new ShipyardSavedData();
        data.nextPlot = tag.getInt("NextPlot");
        if (tag.hasUUID("TestOwner")) {
            data.testOwner = tag.getUUID("TestOwner");
            data.testOriginX = tag.getInt("TestX");
            data.testOriginY = tag.getInt("TestY");
            data.testOriginZ = tag.getInt("TestZ");
            data.testMaxX = tag.getInt("TestMaxX");
            data.testMaxY = tag.getInt("TestMaxY");
            data.testMaxZ = tag.getInt("TestMaxZ");
        }
        ListTag list = tag.getList("Plots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Plot plot = Plot.load(list.getCompound(i));
            if (plot != null) {
                data.plots.put(plot.player, plot);
                data.nextPlot = Math.max(data.nextPlot, plot.index + 1);
            }
        }
        return data;
    }

    /** 一个玩家在船厂里的固定地块与会话状态。 */
    public static final class Plot {
        public final UUID player;
        public final int index;
        public ResourceLocation returnDimension;
        public double returnX;
        public double returnY;
        public double returnZ;
        public float returnYaw;
        public float returnPitch;
        public String shipId = "";
        public boolean dirty;
        public long lastVisit;
        public int minX;
        public int minY;
        public int minZ;
        public int maxX;
        public int maxY;
        public int maxZ;

        private int originX;
        private int originY;
        private int originZ;

        Plot(UUID player, int index) {
            this.player = player;
            this.index = index;
            this.originX = com.opamb.barotrauma.world.ModDimensions.SHIPYARD_ORIGIN_X
                    + index * com.opamb.barotrauma.world.ModDimensions.SHIPYARD_PLOT_SPACING;
            this.originY = com.opamb.barotrauma.world.ModDimensions.SHIPYARD_PLOT_Y;
            this.originZ = 0;
            resetBounds();
        }

        private void resetBounds() {
            int x = originX();
            int y = originY();
            int z = originZ();
            minX = x - 4;
            minY = y - 1;
            minZ = z - 4;
            maxX = x + 4;
            maxY = y + 4;
            maxZ = z + 4;
        }

        public void expand(net.minecraft.core.BlockPos pos) {
            int r = com.opamb.barotrauma.world.ModDimensions.SHIPYARD_PLOT_RADIUS;
            minX = Math.max(originX - r, Math.min(minX, pos.getX()));
            minY = Math.max(originY - 8, Math.min(minY, pos.getY()));
            minZ = Math.max(originZ - r, Math.min(minZ, pos.getZ()));
            maxX = Math.min(originX + r, Math.max(maxX, pos.getX()));
            maxY = Math.min(originY + r, Math.max(maxY, pos.getY()));
            maxZ = Math.min(originZ + r, Math.max(maxZ, pos.getZ()));
        }

        public com.opamb.barotrauma.ship.ShipGeometry.Bounds bounds(int margin) {
            return new com.opamb.barotrauma.ship.ShipGeometry.Bounds(
                    minX - margin, minY - margin, minZ - margin,
                    maxX + margin, maxY + margin, maxZ + margin);
        }

        public int originX() {
            return originX;
        }

        public int originZ() {
            return originZ;
        }

        public int originY() {
            return originY;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Player", player);
            tag.putInt("Index", index);
            if (returnDimension != null) {
                tag.putString("ReturnDimension", returnDimension.toString());
            }
            tag.putDouble("ReturnX", returnX);
            tag.putDouble("ReturnY", returnY);
            tag.putDouble("ReturnZ", returnZ);
            tag.putFloat("ReturnYaw", returnYaw);
            tag.putFloat("ReturnPitch", returnPitch);
            tag.putString("ShipId", shipId);
            tag.putInt("OriginX", originX);
            tag.putInt("OriginY", originY);
            tag.putInt("OriginZ", originZ);
            tag.putBoolean("Dirty", dirty);
            tag.putLong("LastVisit", lastVisit);
            tag.putInt("MinX", minX);
            tag.putInt("MinY", minY);
            tag.putInt("MinZ", minZ);
            tag.putInt("MaxX", maxX);
            tag.putInt("MaxY", maxY);
            tag.putInt("MaxZ", maxZ);
            return tag;
        }

        static Plot load(CompoundTag tag) {
            if (!tag.hasUUID("Player")) {
                return null;
            }
            Plot plot = new Plot(tag.getUUID("Player"), tag.getInt("Index"));
            if (tag.contains("ReturnDimension", Tag.TAG_STRING)) {
                ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("ReturnDimension"));
                plot.returnDimension = dimension;
            }
            plot.returnX = tag.getDouble("ReturnX");
            plot.returnY = tag.getDouble("ReturnY");
            plot.returnZ = tag.getDouble("ReturnZ");
            plot.returnYaw = tag.getFloat("ReturnYaw");
            plot.returnPitch = tag.getFloat("ReturnPitch");
            plot.shipId = tag.getString("ShipId");
            if (tag.contains("OriginX")) {
                plot.originX = tag.getInt("OriginX");
                plot.originY = tag.getInt("OriginY");
                plot.originZ = tag.getInt("OriginZ");
                plot.resetBounds();
            }
            plot.dirty = tag.getBoolean("Dirty");
            plot.lastVisit = tag.getLong("LastVisit");
            if (tag.contains("MinX")) {
                plot.minX = tag.getInt("MinX");
                plot.minY = tag.getInt("MinY");
                plot.minZ = tag.getInt("MinZ");
                plot.maxX = tag.getInt("MaxX");
                plot.maxY = tag.getInt("MaxY");
                plot.maxZ = tag.getInt("MaxZ");
            }
            return plot;
        }
    }
}
