package com.opamb.barotrauma.world.shipyard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 造船厂编辑器共用的纯几何、镜像、旋转与施工平面交点算法。
 *
 * <p>客户端预览与服务端落地共用这一份实现，保证所见即所得，且不依赖客户端类以便 L1 单测。</p>
 */
public final class ShipyardShapes {

    public static final byte MODE_FILL_SOLID = 0;
    public static final byte MODE_FILL_HOLLOW = 1;
    public static final byte MODE_FILL_WALLS = 2;
    public static final byte MODE_CLEAR = 3;
    public static final byte MODE_LINE = 4;
    public static final byte MODE_COPY = 5;
    public static final byte MODE_REPLACE = 6;
    public static final byte MODE_ELLIPSOID = 7;

    public static final byte MIRROR_NONE = 0;
    public static final byte MIRROR_X = 1;
    public static final byte MIRROR_Z = 2;

    public static final int MAX_VOLUME = 4096;

    private ShipyardShapes() {
    }

    /** 估算给定两端点与模式将会覆盖的方块格数（O(1)，用于限制与 HUD 提示）。 */
    public static long estimateCount(BlockPos a, BlockPos b, byte mode) {
        if (a == null || b == null) {
            return 0L;
        }
        long dx = Math.abs((long) b.getX() - a.getX());
        long dy = Math.abs((long) b.getY() - a.getY());
        long dz = Math.abs((long) b.getZ() - a.getZ());
        if (mode == MODE_LINE) {
            return Math.max(dx, Math.max(dy, dz)) + 1L;
        }
        long w = dx + 1L;
        long h = dy + 1L;
        long d = dz + 1L;
        long total = w * h * d;
        if (mode == MODE_FILL_HOLLOW) {
            if (w <= 2L || h <= 2L || d <= 2L) {
                return total;
            }
            return total - (w - 2L) * (h - 2L) * (d - 2L);
        }
        if (mode == MODE_FILL_WALLS) {
            if (w <= 2L || d <= 2L) {
                return total;
            }
            return (w * d - (w - 2L) * (d - 2L)) * h;
        }
        if (mode == MODE_ELLIPSOID) {
            // 仅作快速上界估算（实际格数不超过外接盒体积），具体以 collectEllipsoid 为准。
            return total;
        }
        return total;
    }

    /** 收集给定模式下的所有目标方块格；若超过 {@code maxCells} 则返回空列表。 */
    public static List<BlockPos> collectCells(BlockPos a, BlockPos b, byte mode, int maxCells) {
        if (a == null || b == null || maxCells <= 0) {
            return List.of();
        }
        long estimated = estimateCount(a, b, mode);
        if (estimated <= 0L) {
            return List.of();
        }
        if (mode != MODE_ELLIPSOID && estimated > maxCells) {
            return List.of();
        }
        if (mode == MODE_LINE) {
            return collectLine(a, b, maxCells);
        }
        if (mode == MODE_ELLIPSOID) {
            return collectEllipsoidShell(a, b, maxCells);
        }
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());

        List<BlockPos> out = new ArrayList<>((int) estimated);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean include = switch (mode) {
                        case MODE_FILL_HOLLOW -> x == minX || x == maxX
                                || y == minY || y == maxY
                                || z == minZ || z == maxZ;
                        case MODE_FILL_WALLS -> x == minX || x == maxX
                                || z == minZ || z == maxZ;
                        default -> true;
                    };
                    if (include) {
                        if (out.size() >= maxCells) {
                            return List.of();
                        }
                        out.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /** 生成 6-邻接水密的椭球流线型潜艇壳体（仅保留外表面格，内部中空）。 */
    private static List<BlockPos> collectEllipsoidShell(BlockPos a, BlockPos b, int maxCells) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;
        if ((long) w * h * d > (long) MAX_VOLUME * 4L) {
            return List.of();
        }
        double cx = (minX + maxX) * 0.5D;
        double cy = (minY + maxY) * 0.5D;
        double cz = (minZ + maxZ) * 0.5D;
        double rx = Math.max(0.5D, w * 0.5D);
        double ry = Math.max(0.5D, h * 0.5D);
        double rz = Math.max(0.5D, d * 0.5D);

        List<BlockPos> out = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!insideEllipsoid(x, y, z, cx, cy, cz, rx, ry, rz)) {
                        continue;
                    }
                    boolean boundary = x == minX || x == maxX
                            || y == minY || y == maxY
                            || z == minZ || z == maxZ
                            || !insideEllipsoid(x - 1, y, z, cx, cy, cz, rx, ry, rz)
                            || !insideEllipsoid(x + 1, y, z, cx, cy, cz, rx, ry, rz)
                            || !insideEllipsoid(x, y - 1, z, cx, cy, cz, rx, ry, rz)
                            || !insideEllipsoid(x, y + 1, z, cx, cy, cz, rx, ry, rz)
                            || !insideEllipsoid(x, y, z - 1, cx, cy, cz, rx, ry, rz)
                            || !insideEllipsoid(x, y, z + 1, cx, cy, cz, rx, ry, rz);
                    if (boundary) {
                        if (out.size() >= maxCells) {
                            return List.of();
                        }
                        out.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    private static boolean insideEllipsoid(int x, int y, int z,
                                           double cx, double cy, double cz,
                                           double rx, double ry, double rz) {
        double nx = (x - cx) / rx;
        double ny = (y - cy) / ry;
        double nz = (z - cz) / rz;
        return nx * nx + ny * ny + nz * nz <= 1.0D;
    }

    private static List<BlockPos> collectLine(BlockPos a, BlockPos b, int maxCells) {
        int x1 = a.getX();
        int y1 = a.getY();
        int z1 = a.getZ();
        int x2 = b.getX();
        int y2 = b.getY();
        int z2 = b.getZ();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);
        int xs = x2 > x1 ? 1 : -1;
        int ys = y2 > y1 ? 1 : -1;
        int zs = z2 > z1 ? 1 : -1;

        List<BlockPos> out = new ArrayList<>(Math.max(dx, Math.max(dy, dz)) + 1);
        out.add(new BlockPos(x1, y1, z1));

        if (dx >= dy && dx >= dz) {
            int p1 = 2 * dy - dx;
            int p2 = 2 * dz - dx;
            while (x1 != x2) {
                x1 += xs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dx;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dx;
                }
                p1 += 2 * dy;
                p2 += 2 * dz;
                if (out.size() >= maxCells) {
                    return List.of();
                }
                out.add(new BlockPos(x1, y1, z1));
            }
        } else if (dy >= dx && dy >= dz) {
            int p1 = 2 * dx - dy;
            int p2 = 2 * dz - dy;
            while (y1 != y2) {
                y1 += ys;
                if (p1 >= 0) {
                    x1 += xs;
                    p1 -= 2 * dy;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dy;
                }
                p1 += 2 * dx;
                p2 += 2 * dz;
                if (out.size() >= maxCells) {
                    return List.of();
                }
                out.add(new BlockPos(x1, y1, z1));
            }
        } else {
            int p1 = 2 * dy - dz;
            int p2 = 2 * dx - dz;
            while (z1 != z2) {
                z1 += zs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dz;
                }
                if (p2 >= 0) {
                    x1 += xs;
                    p2 -= 2 * dz;
                }
                p1 += 2 * dy;
                p2 += 2 * dx;
                if (out.size() >= maxCells) {
                    return List.of();
                }
                out.add(new BlockPos(x1, y1, z1));
            }
        }
        return out;
    }

    /** 按镜像轴与中心线格坐标镜像方块位置。 */
    public static BlockPos mirrorPos(BlockPos pos, byte mirrorAxis, int mirrorCoord) {
        if (pos == null) {
            return null;
        }
        if (mirrorAxis == MIRROR_X) {
            return new BlockPos(2 * mirrorCoord - pos.getX(), pos.getY(), pos.getZ());
        }
        if (mirrorAxis == MIRROR_Z) {
            return new BlockPos(pos.getX(), pos.getY(), 2 * mirrorCoord - pos.getZ());
        }
        return pos;
    }

    /**
     * 将镜像轴映射到原版 {@link Mirror}：
     * {@code MIRROR_X} 沿 X=c 对称（翻转东西方向，即 {@link Mirror#FRONT_BACK}）；
     * {@code MIRROR_Z} 沿 Z=c 对称（翻转南北方向，即 {@link Mirror#LEFT_RIGHT}）。
     */
    public static Mirror mirrorForAxis(byte mirrorAxis) {
        if (mirrorAxis == MIRROR_X) {
            return Mirror.FRONT_BACK;
        }
        if (mirrorAxis == MIRROR_Z) {
            return Mirror.LEFT_RIGHT;
        }
        return Mirror.NONE;
    }

    @SuppressWarnings("deprecation")
    public static BlockState mirrorState(BlockState state, byte mirrorAxis) {
        if (state == null || mirrorAxis == MIRROR_NONE) {
            return state;
        }
        return state.mirror(mirrorForAxis(mirrorAxis));
    }

    @SuppressWarnings("deprecation")
    public static BlockState rotateState(BlockState state, int quarterTurns) {
        if (state == null) {
            return null;
        }
        int turns = Math.floorMod(quarterTurns, 4);
        Rotation rotation = switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return rotation == Rotation.NONE ? state : state.rotate(rotation);
    }

    /**
     * 当视线未命中任何实心方块时，求视线与当前施工网格平面的交点（用于空域起手建造）。
     *
     * @return 命中的目标放置格；若平行或超出距离则返回 {@code null}。
     */
    public static BlockPos intersectGridPlane(Vec3 eye, Vec3 dir, Direction.Axis axis,
                                              double planeCoord, double maxDistance) {
        if (eye == null || dir == null || axis == null) {
            return null;
        }
        double axisDir = switch (axis) {
            case X -> dir.x;
            case Y -> dir.y;
            case Z -> dir.z;
        };
        if (Math.abs(axisDir) < 1.0E-6D) {
            return null;
        }
        double axisEye = switch (axis) {
            case X -> eye.x;
            case Y -> eye.y;
            case Z -> eye.z;
        };
        double t = (planeCoord - axisEye) / axisDir;
        if (t <= 0.01D || t > maxDistance) {
            return null;
        }
        Vec3 p = eye.add(dir.scale(t));
        return switch (axis) {
            case Y -> new BlockPos(Mth.floor(p.x), (int) Math.round(planeCoord), Mth.floor(p.z));
            case X -> new BlockPos((int) Math.round(planeCoord), Mth.floor(p.y), Mth.floor(p.z));
            case Z -> new BlockPos(Mth.floor(p.x), Mth.floor(p.y), (int) Math.round(planeCoord));
        };
    }

    /** 绕 Y 轴按 90° 步进旋转相对偏移（0=不变, 1=顺时针90°, 2=180°, 3=逆时针90°）。 */
    public static BlockPos rotateOffset(BlockPos offset, int quarterTurns) {
        if (offset == null) {
            return BlockPos.ZERO;
        }
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case 2 -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case 3 -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
            default -> offset;
        };
    }

    /**
     * 返回指定方块状态相对主方块的全部占用偏移（用于多方块虚影与冲突检测）。
     */
    public static List<BlockPos> footprintOffsets(BlockState state) {
        if (state == null) {
            return List.of(BlockPos.ZERO);
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
        return footprintOffsets(path, facing);
    }

    /** 纯坐标版多方块占用偏移表（不依赖注册表，便于 L1 单测）。 */
    public static List<BlockPos> footprintOffsets(String blockPath, Direction facing) {
        if (blockPath == null || facing == null) {
            return List.of(BlockPos.ZERO);
        }
        Direction axis = facing.getClockWise();
        Direction back = facing.getOpposite();
        return switch (blockPath) {
            case "reactor" -> gridFootprint(axis, back, -1, 1, 0, 2, 0, 1);
            case "engine" -> gridFootprint(axis, back, -1, 1, 0, 1, 0, 2);
            case "navigation_terminal" -> gridFootprint(axis, back, -1, 1, 0, 2, 0, 0);
            case "ballast_pump" -> gridFootprint(axis, back, -1, 1, 0, 1, 0, 0);
            case "deconstructor", "fabricator" -> gridFootprint(axis, back, 0, 1, 0, 2, 0, 0);
            case "hatch", "hatch_ladder" -> gridFootprint(axis, back, -1, 1, 0, 0, 0, 0);
            case "bulkhead_door" -> gridFootprint(axis, back, 0, 0, 0, 1, 0, 0);
            default -> List.of(BlockPos.ZERO);
        };
    }

    private static List<BlockPos> gridFootprint(Direction axis, Direction back,
                                                int colMin, int colMax,
                                                int rowMin, int rowMax,
                                                int layerMin, int layerMax) {
        List<BlockPos> out = new ArrayList<>();
        for (int layer = layerMin; layer <= layerMax; layer++) {
            for (int row = rowMin; row <= rowMax; row++) {
                for (int col = colMin; col <= colMax; col++) {
                    out.add(BlockPos.ZERO.relative(axis, col).relative(back, layer).above(row));
                }
            }
        }
        return List.copyOf(out);
    }
}
