package com.opamb.barotrauma.client;

import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ShipyardRequestPacket;
import com.opamb.barotrauma.network.ShipyardStatePacket;
import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.shipyard.ShipyardShapes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

/**
 * 造船厂编辑器客户端状态：轨道相机、目标方块、工具模式、对称轴、工作平面与打开/关闭。
 */
@Mod.EventBusSubscriber(modid = com.opamb.barotrauma.BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class ShipyardClient {

    public static final KeyMapping TOGGLE_EDITOR = new KeyMapping(
            "key.barotrauma.shipyard_editor", GLFW.GLFW_KEY_TAB, "key.categories.barotrauma");

    public enum ViewMode {
        PERSPECTIVE,
        FRONT,
        BACK,
        SIDE,
        LEFT,
        TOP,
        BOTTOM
    }

    public enum GridMode {
        OFF,
        LOCAL,
        GLOBAL
    }

    public enum ToolMode {
        SINGLE,
        BOX_FILL,
        BOX_HOLLOW,
        ELLIPSOID,
        BOX_WALLS,
        LINE,
        REPLACE,
        BOX_ERASE,
        COPY_SELECT,
        CUT_SELECT,
        PASTE,
        WIRE
    }

    public record ClipboardBlock(BlockPos rel, BlockState state) {
    }

    private static ShipyardStatePacket state;
    private static com.opamb.barotrauma.network.ShipyardStatusPacket status;
    private static boolean editorOpen;
    private static Vec3 focus = Vec3.ZERO;
    private static float yaw = 45.0F;
    private static float pitch = 25.0F;
    private static double distance = 48.0D;
    private static BlockPos target;
    private static BlockPos targetFace;
    private static BlockPos hitFace;
    private static boolean hitOnPlane;
    private static BlockState ghostState;
    private static boolean autoOpened;
    private static boolean hideGuiPrevious;
    private static java.util.List<com.opamb.barotrauma.network.ShipyardLibraryListPacket.Entry> library
            = java.util.List.of();

    private static ViewMode viewMode = ViewMode.PERSPECTIVE;
    private static GridMode gridMode = GridMode.LOCAL;
    private static ToolMode toolMode = ToolMode.SINGLE;
    private static int rotationSteps;
    private static byte mirrorAxis = ShipyardShapes.MIRROR_NONE;
    private static int workPlaneOffset;
    private static boolean pointerOverUi;
    private static boolean showDiagnostics = true;
    private static boolean showRuler = true;
    private static boolean showCompartments;
    private static boolean showWiring = true;
    private static boolean sliceEnabled;
    private static int diagnosticCursor;
    private static BlockState replaceFilter;
    private static BlockPos wireAnchor;
    private static BlockPos pasteNudgeOffset = BlockPos.ZERO;
    private static java.util.List<ClipboardBlock> clipboard = java.util.List.of();
    private static int pasteRotation;

    private static boolean dragActive;
    private static BlockPos dragAnchor;
    private static BlockPos dragCurrent;
    private static byte dragMode;

    private static float perspectiveYaw = 45.0F;
    private static float perspectivePitch = 25.0F;
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3 lastCamera = Vec3.ZERO;
    private static boolean matricesReady;
    private static int lastMouseX;
    private static int lastMouseY;

    private ShipyardClient() {
    }

    public static void accept(ShipyardStatePacket packet) {
        boolean first = state == null;
        state = packet;
        if (first) {
            focus = new Vec3(packet.originX() + 0.5D, packet.originY() + 6.0D,
                    packet.originZ() + 0.5D);
            distance = 48.0D;
        }
        // 进入造船厂默认直接打开编辑器；玩家按 Tab/Esc 退出后不会被再次自动打开。
        if (!autoOpened && inShipyard() && Minecraft.getInstance().screen == null) {
            autoOpened = true;
            openEditor();
        }
    }

    public static void acceptStatus(com.opamb.barotrauma.network.ShipyardStatusPacket packet) {
        status = packet;
    }

    public static com.opamb.barotrauma.network.ShipyardStatusPacket status() {
        return status;
    }

    public static boolean inShipyard() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().equals(ModDimensions.SHIPYARD);
    }

    public static void acceptLibrary(com.opamb.barotrauma.network.ShipyardLibraryListPacket packet) {
        library = packet.entries();
        if (Minecraft.getInstance().screen instanceof ShipyardLibraryScreen screen) {
            screen.refresh();
        }
    }

    public static java.util.List<com.opamb.barotrauma.network.ShipyardLibraryListPacket.Entry> library() {
        return library;
    }

    public static void openLibrary() {
        if (!inShipyard()) {
            return;
        }
        ModNetwork.sendToServer(com.opamb.barotrauma.network.ShipyardLibraryActionPacket.list());
        Minecraft.getInstance().setScreen(new ShipyardLibraryScreen());
    }

    public static void returnToEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (!inShipyard()) {
            // 被传送/命令带离船厂后，船库界面也必须能关掉，不能把玩家卡在 Screen 里。
            mc.setScreen(null);
            return;
        }
        mc.setScreen(new ShipyardEditorScreen());
    }

    public static boolean inTest() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().equals(ModDimensions.SHIPYARD_TEST);
    }

    public static boolean editorActive() {
        return editorOpen && inShipyard();
    }

    public static ShipyardStatePacket state() {
        return state;
    }

    public static BlockPos target() {
        return target;
    }

    public static BlockPos placePos() {
        return targetFace;
    }

    public static BlockPos hitFace() {
        return hitFace;
    }

    public static boolean hitOnPlane() {
        return hitOnPlane;
    }

    public static void clearTarget() {
        target = null;
        targetFace = null;
        hitFace = null;
        hitOnPlane = false;
    }

    public static void setPointerOverUi(boolean over) {
        pointerOverUi = over;
    }

    public static boolean isPointerOverUi() {
        return pointerOverUi;
    }

    public static Vec3 focus() {
        return focus;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static double distance() {
        return distance;
    }

    public static ViewMode viewMode() {
        return viewMode;
    }

    public static void setViewMode(ViewMode mode) {
        ViewMode next = mode == null ? ViewMode.PERSPECTIVE : mode;
        if (viewMode != ViewMode.PERSPECTIVE && next == ViewMode.PERSPECTIVE) {
            // 回到透视时恢复进入正交前的轨道角度，而不是停在 0/90/180。
            yaw = perspectiveYaw;
            pitch = perspectivePitch;
        } else if (viewMode == ViewMode.PERSPECTIVE && next != ViewMode.PERSPECTIVE) {
            perspectiveYaw = yaw;
            perspectivePitch = pitch;
        }
        viewMode = next;
        switch (viewMode) {
            case FRONT -> {
                yaw = 180.0F;
                pitch = 0.0F;
            }
            case BACK -> {
                yaw = 0.0F;
                pitch = 0.0F;
            }
            case SIDE -> {
                yaw = 90.0F;
                pitch = 0.0F;
            }
            case LEFT -> {
                yaw = -90.0F;
                pitch = 0.0F;
            }
            case TOP -> {
                yaw = 0.0F;
                // Minecraft 的 pitch 正值向下；相机在焦点上方，必须向下看。
                pitch = 90.0F;
            }
            case BOTTOM -> {
                yaw = 0.0F;
                pitch = -90.0F;
            }
            case PERSPECTIVE -> {
            }
        }
    }

    public static void resetView() {
        if (state != null) {
            focus = new Vec3(state.originX() + 0.5D, state.originY() + 6.0D,
                    state.originZ() + 0.5D);
        }
        distance = 48.0D;
        workPlaneOffset = 0;
        viewMode = ViewMode.PERSPECTIVE;
        yaw = 45.0F;
        pitch = 25.0F;
        perspectiveYaw = yaw;
        perspectivePitch = pitch;
    }

    public static void focusOnHovered() {
        if (target != null) {
            setFocus(target);
        } else if (targetFace != null) {
            setFocus(targetFace);
        }
    }

    public static boolean orthographic() {
        return viewMode != ViewMode.PERSPECTIVE;
    }

    public static float orthoHalfHeight() {
        // 与透视相机同一 FOV 基准，切换时不会突然放大/缩小。
        double fov = Minecraft.getInstance().options.fov().get();
        double half = Math.tan(Math.toRadians(fov * 0.5D)) * distance;
        return (float) Math.max(4.0D, half);
    }

    public static GridMode gridMode() {
        return gridMode;
    }

    public static void cycleGrid() {
        gridMode = switch (gridMode) {
            case OFF -> GridMode.LOCAL;
            case LOCAL -> GridMode.GLOBAL;
            case GLOBAL -> GridMode.OFF;
        };
    }

    public static ToolMode toolMode() {
        return toolMode;
    }

    public static void setToolMode(ToolMode mode) {
        toolMode = mode == null ? ToolMode.SINGLE : mode;
        wireAnchor = null;
        pasteNudgeOffset = BlockPos.ZERO;
        cancelDrag();
    }

    public static int rotationSteps() {
        return toolMode == ToolMode.PASTE ? pasteRotation : rotationSteps;
    }

    public static void rotatePlacement(boolean clockwise) {
        int delta = clockwise ? 1 : -1;
        if (toolMode == ToolMode.PASTE) {
            pasteRotation = Math.floorMod(pasteRotation + delta, 4);
        } else {
            rotationSteps = Math.floorMod(rotationSteps + delta, 4);
        }
    }

    public static BlockState applyPlacementRotation(BlockState base) {
        return ShipyardShapes.rotateState(base, rotationSteps);
    }

    public static String rotationLabel() {
        return switch (Math.floorMod(rotationSteps(), 4)) {
            case 1 -> "东";
            case 2 -> "南";
            case 3 -> "西";
            default -> "北";
        };
    }

    public static boolean showDiagnostics() {
        return showDiagnostics;
    }

    public static void toggleDiagnostics() {
        showDiagnostics = !showDiagnostics;
    }

    public static boolean showRuler() {
        return showRuler;
    }

    public static void toggleRuler() {
        showRuler = !showRuler;
    }

    public static BlockState replaceFilter() {
        return replaceFilter;
    }

    public static void setReplaceFilter(BlockState filter) {
        replaceFilter = filter;
    }

    public static boolean focusNextIssue() {
        if (status == null) {
            return false;
        }
        java.util.List<BlockPos> targets = new java.util.ArrayList<>();
        targets.addAll(status.disconnectedPos());
        targets.addAll(status.leakPos());
        for (var req : status.requirements()) {
            if (req.target() != null && req.state() != com.opamb.barotrauma.world.shipyard.ShipyardValidator.State.OK) {
                targets.add(req.target());
            }
        }
        if (targets.isEmpty()) {
            return false;
        }
        diagnosticCursor = Math.floorMod(diagnosticCursor, targets.size());
        setFocus(targets.get(diagnosticCursor));
        diagnosticCursor = (diagnosticCursor + 1) % targets.size();
        return true;
    }

    public static void flipClipboard(boolean flipX) {
        if (clipboard.isEmpty()) {
            return;
        }
        byte axis = flipX ? ShipyardShapes.MIRROR_X : ShipyardShapes.MIRROR_Z;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        java.util.List<ClipboardBlock> flipped = new java.util.ArrayList<>(clipboard.size());
        for (ClipboardBlock entry : clipboard) {
            BlockPos r = entry.rel();
            BlockPos fPos = flipX
                    ? new BlockPos(-r.getX(), r.getY(), r.getZ())
                    : new BlockPos(r.getX(), r.getY(), -r.getZ());
            BlockState fState = ShipyardShapes.mirrorState(entry.state(), axis);
            minX = Math.min(minX, fPos.getX());
            minZ = Math.min(minZ, fPos.getZ());
            flipped.add(new ClipboardBlock(fPos, fState));
        }
        java.util.List<ClipboardBlock> normalized = new java.util.ArrayList<>(flipped.size());
        for (ClipboardBlock entry : flipped) {
            normalized.add(new ClipboardBlock(
                    new BlockPos(entry.rel().getX() - minX, entry.rel().getY(),
                            entry.rel().getZ() - minZ),
                    entry.state()));
        }
        clipboard = java.util.List.copyOf(normalized);
        pasteRotation = 0;
    }

    public static void clearClipboard() {
        clipboard = java.util.List.of();
        pasteRotation = 0;
        if (toolMode == ToolMode.PASTE) {
            toolMode = ToolMode.SINGLE;
        }
    }

    public static boolean hasClipboard() {
        return !clipboard.isEmpty();
    }

    public static int clipboardSize() {
        return clipboard.size();
    }

    public static boolean captureClipboard(BlockPos a, BlockPos b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || a == null || b == null) {
            return false;
        }
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume <= 0 || volume > ShipyardShapes.MAX_VOLUME) {
            return false;
        }
        java.util.List<ClipboardBlock> captured = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState blockState = mc.level.getBlockState(pos);
            if (blockState.isAir()
                    || (!com.opamb.barotrauma.registry.ModShipBlocks.isShipBlock(blockState)
                    && !com.opamb.barotrauma.registry.ModShipBlocks.isEquipment(blockState))) {
                continue;
            }
            if (!isMasterOrSinglePart(blockState)) {
                continue;
            }
            captured.add(new ClipboardBlock(
                    new BlockPos(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ),
                    blockState));
        }
        if (captured.isEmpty()) {
            return false;
        }
        clipboard = java.util.List.copyOf(captured);
        pasteRotation = 0;
        toolMode = ToolMode.PASTE;
        return true;
    }

    public static void loadClipboardEntries(java.util.List<ClipboardBlock> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        clipboard = java.util.List.copyOf(entries);
        pasteRotation = 0;
        toolMode = ToolMode.PASTE;
    }

    private static boolean isMasterOrSinglePart(BlockState state) {
        for (var prop : state.getProperties()) {
            if ("part".equals(prop.getName())) {
                return java.util.Objects.equals(state.getValue(prop),
                        state.getBlock().defaultBlockState().getValue(prop));
            }
        }
        return true;
    }

    public static java.util.List<ClipboardBlock> rotatedClipboard() {
        if (clipboard.isEmpty()) {
            return java.util.List.of();
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        java.util.List<ClipboardBlock> rotated = new java.util.ArrayList<>(clipboard.size());
        for (ClipboardBlock entry : clipboard) {
            BlockPos rPos = ShipyardShapes.rotateOffset(entry.rel(), pasteRotation);
            BlockState rState = ShipyardShapes.rotateState(entry.state(), pasteRotation);
            minX = Math.min(minX, rPos.getX());
            minZ = Math.min(minZ, rPos.getZ());
            rotated.add(new ClipboardBlock(rPos, rState));
        }
        java.util.List<ClipboardBlock> normalized = new java.util.ArrayList<>(rotated.size());
        for (ClipboardBlock entry : rotated) {
            normalized.add(new ClipboardBlock(
                    new BlockPos(entry.rel().getX() - minX, entry.rel().getY(),
                            entry.rel().getZ() - minZ),
                    entry.state()));
        }
        return java.util.List.copyOf(normalized);
    }

    public static void syncRotationFromBlock(BlockState picked) {
        if (picked == null) {
            return;
        }
        BlockState base = picked.getBlock().defaultBlockState();
        if (picked.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction want = picked.getValue(BlockStateProperties.HORIZONTAL_FACING);
            for (int turns = 0; turns < 4; turns++) {
                if (ShipyardShapes.rotateState(base, turns)
                        .getValue(BlockStateProperties.HORIZONTAL_FACING) == want) {
                    rotationSteps = turns;
                    return;
                }
            }
        } else if (picked.hasProperty(BlockStateProperties.FACING)) {
            Direction want = picked.getValue(BlockStateProperties.FACING);
            for (int turns = 0; turns < 4; turns++) {
                if (ShipyardShapes.rotateState(base, turns)
                        .getValue(BlockStateProperties.FACING) == want) {
                    rotationSteps = turns;
                    return;
                }
            }
        }
    }

    public static byte mirrorAxis() {
        return mirrorAxis;
    }

    public static void cycleMirror() {
        mirrorAxis = switch (mirrorAxis) {
            case ShipyardShapes.MIRROR_NONE -> ShipyardShapes.MIRROR_X;
            case ShipyardShapes.MIRROR_X -> ShipyardShapes.MIRROR_Z;
            default -> ShipyardShapes.MIRROR_NONE;
        };
    }

    public static int mirrorCoord() {
        if (state == null) {
            return 0;
        }
        if (mirrorAxis == ShipyardShapes.MIRROR_X) {
            return state.originX();
        }
        if (mirrorAxis == ShipyardShapes.MIRROR_Z) {
            return state.originZ();
        }
        return 0;
    }

    public static int workPlaneOffset() {
        return workPlaneOffset;
    }

    public static int workPlaneY() {
        return (state != null ? state.originY() : 0) + workPlaneOffset;
    }

    public static void adjustWorkPlane(int delta) {
        int next = Mth.clamp(workPlaneOffset + delta, -8, ModDimensions.SHIPYARD_PLOT_RADIUS);
        if (next != workPlaneOffset) {
            workPlaneOffset = next;
            if (sliceEnabled) {
                refreshSliceChunks();
            }
        }
    }

    public static boolean showCompartments() {
        return showCompartments;
    }

    public static void toggleCompartments() {
        showCompartments = !showCompartments;
    }

    public static boolean showWiring() {
        return showWiring;
    }

    public static void toggleWiring() {
        showWiring = !showWiring;
    }

    public static boolean sliceEnabled() {
        return sliceEnabled;
    }

    public static void toggleSlice() {
        sliceEnabled = !sliceEnabled;
        refreshSliceChunks();
    }

    public static boolean isSlicedOut(BlockPos pos) {
        return sliceEnabled && editorActive() && pos != null && pos.getY() > workPlaneY();
    }

    public static void refreshSliceChunks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.levelRenderer == null || state == null) {
            return;
        }
        int cx = state.originX() >> 4;
        int cz = state.originZ() >> 4;
        int minY = Math.max(mc.level.getMinSection(), (state.originY() - 8) >> 4);
        int maxY = Math.min(mc.level.getMaxSection() - 1, (state.originY() + 48) >> 4);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int sy = minY; sy <= maxY; sy++) {
                    mc.levelRenderer.setSectionDirty(cx + dx, sy, cz + dz);
                }
            }
        }
    }

    public static BlockPos wireAnchor() {
        return wireAnchor;
    }

    public static void setWireAnchor(BlockPos pos) {
        wireAnchor = pos == null ? null : pos.immutable();
    }

    public static BlockPos pasteNudgeOffset() {
        return pasteNudgeOffset;
    }

    public static void nudgePaste(int dx, int dy, int dz) {
        pasteNudgeOffset = pasteNudgeOffset.offset(dx, dy, dz);
    }

    public static void resetPasteNudge() {
        pasteNudgeOffset = BlockPos.ZERO;
    }

    public static void beginDrag(BlockPos anchor, byte mode) {
        if (anchor == null) {
            return;
        }
        dragActive = true;
        dragAnchor = anchor.immutable();
        dragCurrent = anchor.immutable();
        dragMode = mode;
    }

    public static void updateDrag(BlockPos current) {
        if (dragActive && current != null) {
            dragCurrent = current.immutable();
        }
    }

    public static void cancelDrag() {
        dragActive = false;
        dragAnchor = null;
        dragCurrent = null;
    }

    public static boolean dragActive() {
        return dragActive && dragAnchor != null && dragCurrent != null;
    }

    public static BlockPos dragAnchor() {
        return dragAnchor;
    }

    public static BlockPos dragCurrent() {
        return dragCurrent;
    }

    public static byte dragMode() {
        return dragMode;
    }

    public static void orbit(double dx, double dy) {
        if (viewMode != ViewMode.PERSPECTIVE) {
            return;
        }
        yaw = (float) (yaw - dx * 0.45D);
        pitch = (float) Mth.clamp(pitch + dy * 0.35D, -80.0D, 80.0D);
    }

    /**
     * 右下角方向 gizmo 的拖拽：始终改变视角，不移动焦点。
     *
     * <p>正交视图下先以当前正交方向为起点切到透视，再继续轨道旋转，
     * 这样拖起来不会突然跳到上一次透视角度。</p>
     */
    public static void orbitFromGizmo(double dx, double dy) {
        if (viewMode == ViewMode.FRONT) {
            yaw = 0.0F;
            pitch = 0.0F;
            viewMode = ViewMode.PERSPECTIVE;
        } else if (viewMode == ViewMode.BACK) {
            yaw = 180.0F;
            pitch = 0.0F;
            viewMode = ViewMode.PERSPECTIVE;
        } else if (viewMode == ViewMode.SIDE) {
            yaw = 90.0F;
            pitch = 0.0F;
            viewMode = ViewMode.PERSPECTIVE;
        } else if (viewMode == ViewMode.LEFT) {
            yaw = -90.0F;
            pitch = 0.0F;
            viewMode = ViewMode.PERSPECTIVE;
        } else if (viewMode == ViewMode.TOP) {
            yaw = 0.0F;
            // 留一点俯角，避免正上方时 yaw 失去意义。
            pitch = 80.0F;
            viewMode = ViewMode.PERSPECTIVE;
        } else if (viewMode == ViewMode.BOTTOM) {
            yaw = 0.0F;
            pitch = -80.0F;
            viewMode = ViewMode.PERSPECTIVE;
        }
        perspectiveYaw = yaw;
        perspectivePitch = pitch;
        yaw = (float) (yaw - dx * 0.45D);
        pitch = (float) Mth.clamp(pitch + dy * 0.35D, -80.0D, 80.0D);
    }

    /** 单击 gizmo 轴：吸附到对应正交视图；重复点击同一轴则在正反视图间切换。 */
    public static void snapToAxis(int axis) {
        if (axis == 0) {
            setViewMode(viewMode == ViewMode.SIDE ? ViewMode.LEFT : ViewMode.SIDE);
        } else if (axis == 1) {
            setViewMode(viewMode == ViewMode.TOP ? ViewMode.BOTTOM : ViewMode.TOP);
        } else if (axis == 2) {
            setViewMode(viewMode == ViewMode.FRONT ? ViewMode.BACK : ViewMode.FRONT);
        }
    }

    /**
     * 世界轴在屏幕上的方向（单位：像素方向，未归一化）。
     *
     * @return {dx, dy, 投影长度, 相机空间深度}；投影长度接近 0 表示轴正对镜头。
     */
    public static double[] projectAxis(double x, double y, double z) {
        double vx;
        double vy;
        double vz;
        if (matricesReady) {
            org.joml.Vector3f dir = VIEW.transformDirection(
                    new org.joml.Vector3f((float) x, (float) y, (float) z));
            vx = dir.x;
            vy = dir.y;
            vz = dir.z;
        } else {
            double ry = Math.toRadians(yaw);
            double rp = Math.toRadians(pitch);
            Vec3 forward = new Vec3(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp),
                    -Math.cos(ry) * Math.cos(rp));
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x).normalize();
            Vec3 up = new Vec3(-Math.sin(ry) * Math.sin(rp), Math.cos(rp),
                    -Math.cos(ry) * Math.sin(rp)).normalize();
            vx = x * right.x + y * right.y + z * right.z;
            vy = x * up.x + y * up.y + z * up.z;
            vz = -(x * forward.x + y * forward.y + z * forward.z);
        }
        return new double[]{vx, -vy, Math.hypot(vx, vy), vz};
    }

    public static void zoom(double delta) {
        distance = Mth.clamp(distance * (delta > 0 ? 0.88D : 1.14D), 6.0D, 160.0D);
    }

    /** WASD/空格/Shift 的连续移动；正交视图按屏幕方向，透视按当前水平视角。 */
    public static void moveFocus(double rightAmount, double forwardAmount, double upAmount) {
        Vec3 forward;
        Vec3 right;
        switch (viewMode) {
            case FRONT -> {
                forward = new Vec3(0.0D, 0.0D, -1.0D);
                right = new Vec3(1.0D, 0.0D, 0.0D);
            }
            case BACK -> {
                forward = new Vec3(0.0D, 0.0D, 1.0D);
                right = new Vec3(-1.0D, 0.0D, 0.0D);
            }
            case SIDE -> {
                forward = new Vec3(-1.0D, 0.0D, 0.0D);
                right = new Vec3(0.0D, 0.0D, -1.0D);
            }
            case LEFT -> {
                forward = new Vec3(1.0D, 0.0D, 0.0D);
                right = new Vec3(0.0D, 0.0D, 1.0D);
            }
            case TOP -> {
                forward = new Vec3(0.0D, 0.0D, -1.0D);
                right = new Vec3(1.0D, 0.0D, 0.0D);
            }
            case BOTTOM -> {
                forward = new Vec3(0.0D, 0.0D, 1.0D);
                right = new Vec3(1.0D, 0.0D, 0.0D);
            }
            default -> {
                double ry = Math.toRadians(yaw);
                forward = new Vec3(-Math.sin(ry), 0.0D, -Math.cos(ry));
                right = new Vec3(-forward.z, 0.0D, forward.x);
            }
        }
        focus = focus.add(right.scale(rightAmount))
                .add(forward.scale(forwardAmount))
                .add(0.0D, upAmount, 0.0D);
    }

    public static void pan(double dx, double dy) {
        Vec3 right;
        Vec3 up;
        switch (viewMode) {
            case FRONT -> {
                right = new Vec3(1.0D, 0.0D, 0.0D);
                up = new Vec3(0.0D, 1.0D, 0.0D);
            }
            case BACK -> {
                right = new Vec3(-1.0D, 0.0D, 0.0D);
                up = new Vec3(0.0D, 1.0D, 0.0D);
            }
            case SIDE -> {
                right = new Vec3(0.0D, 0.0D, -1.0D);
                up = new Vec3(0.0D, 1.0D, 0.0D);
            }
            case LEFT -> {
                right = new Vec3(0.0D, 0.0D, 1.0D);
                up = new Vec3(0.0D, 1.0D, 0.0D);
            }
            case TOP -> {
                // 顶视图的“屏幕上方”是世界 -Z，否则纵向拖拽只会沿相机轴（世界 Y）移动而看不出变化。
                right = new Vec3(1.0D, 0.0D, 0.0D);
                up = new Vec3(0.0D, 0.0D, -1.0D);
            }
            case BOTTOM -> {
                right = new Vec3(1.0D, 0.0D, 0.0D);
                up = new Vec3(0.0D, 0.0D, 1.0D);
            }
            default -> {
                double ry = Math.toRadians(yaw);
                Vec3 forward = new Vec3(-Math.sin(ry), 0.0D, -Math.cos(ry));
                right = new Vec3(-forward.z, 0.0D, forward.x).normalize();
                // 屏幕 up 还包含俯仰分量，否则高俯角时纵向拖拽方向不对。
                double rp = Math.toRadians(pitch);
                up = new Vec3(-Math.sin(ry) * Math.sin(rp), Math.cos(rp),
                        -Math.cos(ry) * Math.sin(rp)).normalize();
            }
        }
        double scale = (orthographic() ? orthoHalfHeight() : distance) * 0.002D;
        focus = focus.add(right.scale(dx * scale)).add(up.scale(-dy * scale));
    }

    public static void setFocus(BlockPos pos) {
        if (pos != null) {
            focus = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        }
    }

    /** 相机实际位置：透视轨道球面，或六向正交视图的轴向相机。 */
    public static Vec3 cameraPosition() {
        return switch (viewMode) {
            case FRONT -> focus.add(0.0D, 0.0D, distance);
            case BACK -> focus.add(0.0D, 0.0D, -distance);
            case SIDE -> focus.add(distance, 0.0D, 0.0D);
            case LEFT -> focus.add(-distance, 0.0D, 0.0D);
            case TOP -> focus.add(0.0D, distance, 0.001D);
            case BOTTOM -> focus.add(0.0D, -distance, 0.001D);
            case PERSPECTIVE -> {
                double ry = Math.toRadians(yaw);
                double rp = Math.toRadians(pitch);
                yield focus.add(Math.sin(ry) * Math.cos(rp) * distance,
                        Math.sin(rp) * distance,
                        Math.cos(ry) * Math.cos(rp) * distance);
            }
        };
    }

    /** 相机看向 focus 的 yaw/pitch。 */
    public static float cameraYaw() {
        return switch (viewMode) {
            case FRONT -> 180.0F;
            case BACK, TOP, BOTTOM -> 0.0F;
            case SIDE -> 90.0F;
            case LEFT -> -90.0F;
            case PERSPECTIVE -> {
                Vec3 eye = cameraPosition();
                double dx = focus.x - eye.x;
                double dz = focus.z - eye.z;
                yield (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            }
        };
    }

    public static float cameraPitch() {
        return switch (viewMode) {
            case TOP -> 90.0F;
            case BOTTOM -> -90.0F;
            case FRONT, BACK, SIDE, LEFT -> 0.0F;
            case PERSPECTIVE -> {
                Vec3 eye = cameraPosition();
                double dx = focus.x - eye.x;
                double dy = focus.y - eye.y;
                double dz = focus.z - eye.z;
                yield (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            }
        };
    }

    /** 从世界渲染阶段抓取本帧的 view/projection，用于正交和透视统一的鼠标拾取。 */
    public static void setCameraMatrices(Matrix4f view, Matrix4f projection, Vec3 camera) {
        VIEW.set(view);
        PROJECTION.set(projection);
        lastCamera = camera;
        matricesReady = true;
    }

    public static boolean matricesReady() {
        return matricesReady;
    }

    public static int lastMouseX() {
        return lastMouseX;
    }

    public static int lastMouseY() {
        return lastMouseY;
    }

    /** 屏幕鼠标 → 世界射线 → 目标方块（或当前施工网格平面的回退交点）。 */
    public static void updateTarget(int mouseX, int mouseY, int width, int height) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clearTarget();
            return;
        }
        double ndcX = mouseX / (double) Math.max(1, width) * 2.0D - 1.0D;
        double ndcY = 1.0D - mouseY / (double) Math.max(1, height) * 2.0D;
        Vec3 eye;
        Vec3 dir;
        if (matricesReady) {
            Matrix4f inverse = new Matrix4f(PROJECTION).mul(VIEW).invert();
            Vector4f near = inverse.transform(new Vector4f((float) ndcX, (float) ndcY, -1.0F, 1.0F));
            Vector4f far = inverse.transform(new Vector4f((float) ndcX, (float) ndcY, 1.0F, 1.0F));
            near.div(near.w);
            far.div(far.w);
            eye = new Vec3(near.x, near.y, near.z).add(lastCamera);
            Vec3 end = new Vec3(far.x, far.y, far.z).add(lastCamera);
            dir = end.subtract(eye).normalize();
        } else {
            // 首帧/矩阵尚未捕获时的保守回退。
            eye = cameraPosition();
            Vec3 forward = Vec3.directionFromRotation(cameraPitch(), cameraYaw());
            Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (right.lengthSqr() < 1.0E-6D) {
                right = new Vec3(1.0D, 0.0D, 0.0D);
            }
            right = right.normalize();
            Vec3 up = right.cross(forward).normalize();
            double tan = Math.tan(Math.toRadians(mc.options.fov().get() * 0.5D));
            double aspect = width / (double) Math.max(1, height);
            dir = forward.add(right.scale(ndcX * tan * aspect))
                    .add(up.scale(ndcY * tan)).normalize();
        }
        Vec3 reachEnd = eye.add(dir.scale(512.0D));
        BlockHitResult hit = sliceEnabled
                ? clipSkippingSliced(mc.level, eye, reachEnd)
                : mc.level.clip(new ClipContext(eye, reachEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            target = hit.getBlockPos();
            hitFace = hit.getBlockPos();
            targetFace = hit.getBlockPos().relative(hit.getDirection());
            hitOnPlane = false;
            return;
        }
        BlockPos planePos = fallbackPlaneHit(eye, dir);
        if (planePos != null && insideClientPlot(planePos)) {
            target = null;
            hitFace = null;
            targetFace = planePos;
            hitOnPlane = true;
        } else {
            clearTarget();
        }
    }

    private static BlockHitResult clipSkippingSliced(net.minecraft.world.level.Level level, Vec3 start, Vec3 end) {
        return net.minecraft.world.level.BlockGetter.traverseBlocks(start, end, null, (ignored, pos) -> {
            if (isSlicedOut(pos)) {
                return null;
            }
            BlockState blockState = level.getBlockState(pos);
            if (blockState.isAir()) {
                return null;
            }
            BlockHitResult hit = blockState.getShape(level, pos).clip(start, end, pos);
            return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
        }, ignored -> BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end)));
    }

    private static BlockPos fallbackPlaneHit(Vec3 eye, Vec3 dir) {
        if (viewMode == ViewMode.FRONT || viewMode == ViewMode.BACK) {
            return ShipyardShapes.intersectGridPlane(eye, dir, Direction.Axis.Z,
                    Math.floor(focus.z), 512.0D);
        }
        if (viewMode == ViewMode.SIDE || viewMode == ViewMode.LEFT) {
            return ShipyardShapes.intersectGridPlane(eye, dir, Direction.Axis.X,
                    Math.floor(focus.x), 512.0D);
        }
        return ShipyardShapes.intersectGridPlane(eye, dir, Direction.Axis.Y,
                workPlaneY(), 512.0D);
    }

    private static boolean insideClientPlot(BlockPos pos) {
        if (state == null) {
            return true;
        }
        int r = ModDimensions.SHIPYARD_PLOT_RADIUS;
        return Math.abs(pos.getX() - state.originX()) <= r
                && Math.abs(pos.getZ() - state.originZ()) <= r
                && pos.getY() >= state.originY() - 8
                && pos.getY() <= state.originY() + r;
    }

    public static BlockState ghostState() {
        return ghostState;
    }

    public static void setGhostState(BlockState state) {
        ghostState = state;
    }

    public static void openEditor() {
        if (!inShipyard()) {
            return;
        }
        ModNetwork.sendToServer(new ShipyardRequestPacket());
        Minecraft mc = Minecraft.getInstance();
        if (!editorOpen) {
            hideGuiPrevious = mc.options.hideGui;
            mc.options.hideGui = true;
        }
        editorOpen = true;
        mc.setScreen(new ShipyardEditorScreen());
    }

    public static void closeEditor() {
        closeEditor(false);
    }

    /** @param teleportToFocus true 只在玩家明确按 Tab 切第一人称时使用；Esc 不应移动玩家。 */
    public static void closeEditor(boolean teleportToFocus) {
        if (sliceEnabled) {
            sliceEnabled = false;
            refreshSliceChunks();
        }
        wireAnchor = null;
        pasteNudgeOffset = BlockPos.ZERO;
        cancelDrag();
        pointerOverUi = false;
        if (teleportToFocus && inShipyard()) {
            BlockPos focusPos = BlockPos.containing(focus);
            ModNetwork.sendToServer(new com.opamb.barotrauma.network.ShipyardTeleportPacket(
                    focusPos, cameraYaw(), cameraPitch()));
        }
        editorOpen = false;
        ghostState = null;
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = hideGuiPrevious;
        mc.setScreen(null);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (inTest()) {
            if (editorOpen) {
                editorOpen = false;
                ghostState = null;
                cancelDrag();
                pointerOverUi = false;
                mc.options.hideGui = hideGuiPrevious;
            }
            if (mc.screen instanceof ShipyardEditorScreen
                    || mc.screen instanceof ShipyardLibraryScreen) {
                mc.setScreen(null);
            }
            // 试航维度没有编辑器；Tab 直接请求返回船厂，避免只能手打命令。
            while (TOGGLE_EDITOR.consumeClick()) {
                ModNetwork.sendToServer(new com.opamb.barotrauma.network.ShipyardTestPacket(false));
            }
            return;
        }
        if (!inShipyard()) {
            state = null;
            status = null;
            autoOpened = false;
            ghostState = null;
            cancelDrag();
            pointerOverUi = false;
            if (editorOpen) {
                editorOpen = false;
                mc.options.hideGui = hideGuiPrevious;
            }
            if (mc.screen instanceof ShipyardEditorScreen
                    || mc.screen instanceof ShipyardLibraryScreen) {
                mc.setScreen(null);
            }
            TOGGLE_EDITOR.consumeClick();
            return;
        }
        if (mc.screen != null) {
            // 编辑器被其它 Screen（死亡、容器等）替换后，轨道相机不应继续生效。
            if (editorOpen && !(mc.screen instanceof ShipyardEditorScreen)
                    && !(mc.screen instanceof ShipyardLibraryScreen)) {
                editorOpen = false;
                mc.options.hideGui = hideGuiPrevious;
            }
            TOGGLE_EDITOR.consumeClick();
            return;
        }
        while (TOGGLE_EDITOR.consumeClick()) {
            openEditor();
        }
    }
}
