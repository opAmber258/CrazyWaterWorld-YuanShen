"""生成三段式炮塔（固定底座 / 水平旋转基座 / 俯仰炮管）的 Blockbench 工程与运行时贴图。

产物：
    art/models/turret_20261119/turret.bbmodel
    art/models/turret_20261119/turret_tex.png          # 512x256 图集（正常涂装）
    src/main/resources/assets/barotrauma/textures/block/turret_atlas{,_freeaim,_tracking}.png
    src/main/resources/assets/barotrauma/models/block/{turret,free_aim_turret,tracking_turret}.json
    src/main/resources/assets/barotrauma/models/item/{turret,free_aim_turret,tracking_turret}.json

用法（仓库根目录）：
    python tools/gen_turret_model.py

图集是 512x256（2 的幂）的 8x4 瓦片表，每个 cube 有独立的 64x64 瓦片：
    每块瓦片再分成 4 个 32x32 象限：
        左上 = 顶面（up/down）
        右上 = Z 面（north/south）
        左下 = X 面（east/west）
        右下 = 留空（当前没有面采样）
    这样每个 cube 的每个面取不同像素区域，不再把整张图重复贴到所有面。
"""

from __future__ import annotations

import base64
import json
import pathlib
import uuid

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "art/models/turret_20261119"
TEX_DIR = ROOT / "src/main/resources/assets/barotrauma/textures/block"
MODEL_DIR = ROOT / "src/main/resources/assets/barotrauma/models/block"

NS = uuid.UUID("6f9619ff-8b86-d011-b42d-00c04fc964ff")
ATLAS_W = 512
ATLAS_H = 256
TILE = 64
TILES_PER_ROW = 8

# 16 模型单位 = 1 格；炮管俯仰轴与 TurretBlockEntity.PIVOT_Y = 0.75 对齐。
PIVOT = [8.0, 12.0, 8.0]

# (名字, from, to, 瓦片号)
BASE_CUBES = [
    ("base_plate", [1.0, 0.0, 1.0], [15.0, 2.5, 15.0], 0),
    ("base_step_front", [2.0, 2.5, 12.5], [14.0, 3.5, 15.0], 1),
    ("base_step_back", [2.0, 2.5, 1.0], [14.0, 3.5, 3.5], 2),
    ("base_ring", [3.5, 2.5, 3.5], [12.5, 5.0, 12.5], 3),
    ("base_collar", [5.0, 4.5, 5.0], [11.0, 6.0, 11.0], 4),
    ("base_bolt_nw", [1.2, 2.5, 1.2], [2.8, 4.0, 2.8], 5),
    ("base_bolt_ne", [13.2, 2.5, 1.2], [14.8, 4.0, 2.8], 5),
    ("base_bolt_sw", [1.2, 2.5, 13.2], [2.8, 4.0, 14.8], 5),
    ("base_bolt_se", [13.2, 2.5, 13.2], [14.8, 4.0, 14.8], 5),
    ("base_cable_left", [0.5, 0.5, 5.0], [2.0, 2.0, 11.0], 6),
    ("base_cable_right", [14.0, 0.5, 5.0], [15.5, 2.0, 11.0], 6),
]

TURNTABLE_CUBES = [
    ("turntable_race", [2.8, 5.0, 2.8], [13.2, 7.0, 13.2], 7),
    ("turntable_hub", [5.5, 7.0, 5.5], [10.5, 9.5, 10.5], 8),
    # 叉臂在炮管外侧，形成清楚的 U 形炮架。
    ("yoke_left", [2.5, 8.0, 5.5], [4.5, 14.5, 10.0], 9),
    ("yoke_right", [11.5, 8.0, 5.5], [13.5, 14.5, 10.0], 9),
    ("trunnion_left", [1.5, 10.5, 6.5], [3.5, 13.5, 9.5], 10),
    ("trunnion_right", [12.5, 10.5, 6.5], [14.5, 13.5, 9.5], 10),
    ("ammo_box", [5.5, 7.5, 2.5], [10.5, 10.5, 5.5], 11),
    ("piston_left", [4.2, 8.5, 6.5], [5.2, 12.5, 7.5], 12),
    ("piston_right", [10.8, 8.5, 6.5], [11.8, 12.5, 7.5], 12),
    ("sight_bracket", [7.0, 13.5, 6.0], [9.0, 15.0, 8.0], 13),
]

BARREL_CUBES = [
    ("breech", [4.5, 9.0, 1.5], [11.5, 15.0, 8.5], 14),
    ("breech_cap", [4.8, 9.3, 0.8], [11.2, 14.7, 1.8], 15),
    ("barrel_sleeve", [5.5, 9.5, 8.0], [10.5, 14.5, 12.0], 16),
    ("barrel_tube", [6.0, 10.0, 11.5], [10.0, 14.0, 23.0], 17),
    ("barrel_ring_rear", [5.2, 9.4, 13.5], [10.8, 14.6, 15.0], 18),
    ("barrel_ring_front", [5.2, 9.4, 19.5], [10.8, 14.6, 21.0], 18),
    ("muzzle_brake", [5.0, 9.5, 22.5], [11.0, 14.5, 26.24], 19),
]

# 吊顶底座：贴顶法兰 + 一根细主轴接到转盘上沿（不是平台）。
CEILING_CUBES = [
    ("ceiling_plate", [2.0, 13.0, 2.0], [14.0, 16.0, 14.0], 0),
    ("ceiling_spindle", [6.5, 7.0, 6.5], [9.5, 13.0, 9.5], 4),
]

# 墙面底座（默认支撑在 -Z/北侧）：贴墙法兰 + 竖向短颈接到转盘（不是水平托架）。
WALL_CUBES = [
    ("wall_plate", [2.0, 2.0, 0.0], [14.0, 14.0, 2.0], 0),
    ("wall_neck", [6.0, 4.5, 2.0], [10.0, 7.0, 4.5], 4),
]

FACES = ("north", "east", "south", "west", "up", "down")

# 20 块瓦片的绘制风格。
TILE_STYLES = {
    0: "hazard", 1: "plate", 2: "plate", 3: "ring", 4: "hatch", 5: "metal",
    6: "vent", 7: "ring", 8: "hatch", 9: "plate_vent", 10: "end",
    11: "label", 12: "metal", 13: "metal", 14: "plate", 15: "end",
    16: "vent", 17: "barrel", 18: "ring", 19: "barrel_vent",
}

BASE_PALETTE = {
    "metal": (58, 62, 66),
    "panel": (74, 78, 82),
    "dark": (32, 34, 36),
    "light": (126, 130, 134),
    "accent": (196, 154, 44),
}
FREEAIM_PALETTE = {
    "metal": (54, 60, 68),
    "panel": (70, 78, 88),
    "dark": (28, 32, 38),
    "light": (126, 136, 148),
    "accent": (214, 120, 56),
}
TRACKING_PALETTE = {
    "metal": (46, 62, 62),
    "panel": (60, 80, 80),
    "dark": (22, 34, 34),
    "light": (110, 148, 148),
    "accent": (64, 200, 198),
}
TURNTABLE_PALETTE = {
    "metal": (72, 76, 80),
    "panel": (90, 94, 98),
    "dark": (40, 43, 46),
    "light": (134, 138, 142),
    "accent": (196, 154, 44),
}
BARREL_PALETTE = {
    "metal": (50, 53, 56),
    "panel": (64, 67, 70),
    "dark": (28, 30, 32),
    "light": (116, 120, 124),
    "accent": (196, 154, 44),
}


def make_uuid(*parts: str) -> str:
    return str(uuid.uuid5(NS, ":".join(parts)))


def tile_origin(tile: int) -> tuple[int, int]:
    return (tile % TILES_PER_ROW) * TILE, (tile // TILES_PER_ROW) * TILE


def face_rect(tile: int, face: str) -> tuple[int, int, int, int]:
    """瓦片内的面区域：顶面 / Z 面 / X 面三个象限，右下留空。"""
    tx, ty = tile_origin(tile)
    if face in ("up", "down"):
        return tx, ty, tx + 32, ty + 32
    if face in ("north", "south"):
        return tx + 32, ty, tx + 64, ty + 32
    return tx, ty + 32, tx + 32, ty + 64


def model_uv(tile: int, face: str) -> list[float]:
    """Java 模型 UV（0…16）；u 按 512 宽、v 按 256 高归一化。"""
    x0, y0, x1, y1 = face_rect(tile, face)
    return [x0 / 32.0, y0 / 16.0, x1 / 32.0, y1 / 16.0]


def draw_quad(draw: ImageDraw.ImageDraw, style: str, p: dict,
              x0: int, y0: int, x1: int, y1: int, face: str) -> None:
    """画一个 32x32 象限；顶面 / Z 面 / X 面用不同版式，避免所有面一个样。"""
    draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=p["metal"])
    draw.rectangle([x0 + 2, y0 + 2, x1 - 3, y1 - 3], fill=p["panel"])
    draw.rectangle([x0 + 2, y0 + 2, x1 - 3, y1 - 3], outline=p["dark"], width=1)

    if face == "up":
        # 顶面：装甲板 + 四角螺栓 + 中央舱盖/散热槽。
        draw.rectangle([x0 + 5, y0 + 5, x1 - 6, y1 - 6], outline=p["dark"], width=2)
        for bx, by in ((x0 + 4, y0 + 4), (x1 - 7, y0 + 4),
                       (x0 + 4, y1 - 7), (x1 - 7, y1 - 7)):
            draw.rectangle([bx, by, bx + 2, by + 2], fill=p["light"])
        if style in ("hazard", "plate_vent"):
            for i in range(x0 + 6, x1 - 6, 7):
                draw.rectangle([i, y0 + 6, i + 3, y0 + 8], fill=p["accent"])
        elif style in ("hatch", "ring", "end"):
            draw.rectangle([x0 + 10, y0 + 10, x1 - 11, y1 - 11], fill=p["dark"])
            draw.rectangle([x0 + 13, y0 + 13, x1 - 14, y1 - 14], fill=p["light"])
        else:
            draw.rectangle([x0 + 9, y0 + 13, x1 - 10, y1 - 14], fill=p["dark"])
    elif face == "north":
        # Z 面：横向长面板 + 中缝 + 底部识别条。
        draw.line([x0 + 4, y0 + 16, x1 - 5, y0 + 16], fill=p["dark"])
        draw.rectangle([x0 + 5, y0 + 6, x1 - 6, y0 + 14], outline=p["dark"], width=1)
        draw.rectangle([x0 + 5, y1 - 9, x1 - 6, y1 - 7], fill=p["accent"])
        for bx in (x0 + 5, x1 - 8):
            draw.rectangle([bx, y0 + 18, bx + 2, y0 + 20], fill=p["light"])
        if style in ("vent", "plate_vent", "barrel_vent"):
            for vy in range(y0 + 7, y0 + 14, 3):
                draw.rectangle([x0 + 8, vy, x1 - 10, vy + 1], fill=p["dark"])
    else:
        # X 面：竖向凹槽 + 上下螺栓，和 Z 面明显不同。
        draw.rectangle([x0 + 8, y0 + 5, x1 - 9, y1 - 6], fill=p["dark"])
        draw.rectangle([x0 + 10, y0 + 7, x1 - 11, y1 - 8], fill=p["panel"])
        draw.line([x0 + 16, y0 + 8, x0 + 16, y1 - 9], fill=p["light"])
        for by in (y0 + 5, y1 - 8):
            draw.rectangle([x0 + 5, by, x0 + 7, by + 2], fill=p["light"])
            draw.rectangle([x1 - 8, by, x1 - 6, by + 2], fill=p["light"])
        if style in ("barrel", "barrel_vent"):
            draw.rectangle([x0 + 20, y0 + 8, x0 + 22, y1 - 9], fill=p["accent"])

    if style in ("ring", "end"):
        for inset, color in ((4, p["dark"]), (7, p["panel"]),
                             (11, p["dark"]), (14, p["metal"])):
            draw.rectangle([x0 + inset, y0 + inset, x1 - inset - 1, y1 - inset - 1],
                           outline=color, width=2)
        draw.rectangle([x0 + 15, y0 + 15, x1 - 16, y1 - 16], fill=p["light"])
    if style == "label":
        draw.rectangle([x0 + 11, y0 + 13, x0 + 14, y0 + 14], fill=p["light"])
        draw.rectangle([x0 + 16, y0 + 13, x0 + 19, y0 + 14], fill=p["light"])


def build_atlas(variant: dict) -> Image.Image:
    image = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for tile, style in TILE_STYLES.items():
        if tile <= 6:
            palette = variant
        elif tile <= 13:
            palette = {**TURNTABLE_PALETTE, "accent": variant["accent"]}
        else:
            palette = {**BARREL_PALETTE, "accent": variant["accent"]}
        tx, ty = tile_origin(tile)
        for face in ("up", "north", "east"):
            x0, y0, x1, y1 = face_rect(tile, face)
            draw_quad(draw, style, palette, x0, y0, x1, y1, face)
    return image


def texture_entry(name: str, png: pathlib.Path) -> dict:
    data = base64.b64encode(png.read_bytes()).decode("ascii")
    return {
        "name": name,
        "relative_path": png.name,
        "folder": "",
        "namespace": "",
        "id": "0",
        "group": "",
        "scope": 0,
        "width": ATLAS_W,
        "height": ATLAS_H,
        "uv_width": ATLAS_W,
        "uv_height": ATLAS_H,
        "particle": True,
        "use_as_default": True,
        "layers_enabled": False,
        "sync_to_project": "",
        "file_format": "png",
        "render_mode": "default",
        "render_sides": "auto",
        "wrap_mode": "limited",
        "pbr_channel": "color",
        "fps": 7,
        "force_translucent": False,
        "frame_time": 1,
        "frame_order_type": "loop",
        "frame_order": "",
        "frame_interpolate": False,
        "visible": True,
        "internal": True,
        "saved": True,
        "uuid": make_uuid("texture", name),
        "source": f"data:image/png;base64,{data}",
    }


def cube_entry(name: str, frm: list[float], to: list[float], tile: int) -> dict:
    faces = {
        face: {"uv": list(face_rect(tile, face)), "texture": 0, "rotation": 0}
        for face in FACES
    }
    return {
        "name": name,
        "box_uv": False,
        "render_order": "default",
        "rescale": False,
        "locked": False,
        "shade_direction_override": False,
        "light_emission": 0,
        "export": True,
        "scope": 0,
        "allow_mirror_modeling": True,
        "from": frm,
        "to": to,
        "autouv": 0,
        "color": 0,
        "origin": list(PIVOT),
        "faces": faces,
        "type": "cube",
        "uuid": make_uuid("cube", name),
    }


def group_entry(name: str, children: list[str], origin: list[float]) -> dict:
    return {
        "name": name,
        "uuid": make_uuid("group", name),
        "export": True,
        "locked": False,
        "scope": 0,
        "selected": False,
        "visibility": True,
        "_static": {"properties": {}, "temp_data": {}},
        "origin": origin,
        "rotation": [0, 0, 0],
        "color": 0,
        "children": children,
        "reset": False,
        "shade": True,
        "mirror_uv": False,
        "autouv": 0,
        "isOpen": True,
        "primary_selected": False,
    }


def build_bbmodel(atlas: pathlib.Path) -> dict:
    specs = [("base", n, f, t, tile) for n, f, t, tile in BASE_CUBES]
    specs += [("turntable", n, f, t, tile) for n, f, t, tile in TURNTABLE_CUBES]
    specs += [("barrel", n, f, t, tile) for n, f, t, tile in BARREL_CUBES]
    cubes = {n: cube_entry(n, f, t, tile) for _, n, f, t, tile in specs}

    groups = [
        group_entry("base", [cubes[n]["uuid"] for n, *_ in BASE_CUBES], [8.0, 0.0, 8.0]),
        group_entry("turntable", [cubes[n]["uuid"] for n, *_ in TURNTABLE_CUBES], PIVOT),
        group_entry("barrel", [cubes[n]["uuid"] for n, *_ in BARREL_CUBES], PIVOT),
    ]
    root = group_entry("turret", [], [8.0, 8.0, 8.0])
    root["children"] = [g["uuid"] for g in groups]
    groups.insert(0, root)
    outliner = [{
        "uuid": root["uuid"],
        "isOpen": True,
        "children": [
            {"uuid": g["uuid"], "isOpen": True, "children": list(g["children"])}
            for g in groups[1:]
        ],
    }]

    return {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": "turret",
        "parent": "",
        "java_block_version": "26.3",
        "ambientocclusion": True,
        "front_gui_light": False,
        "visible_box": [16, 16, 16],
        "variable_placeholders": "",
        "multi_file_ruleset": "",
        "variable_placeholder_buttons": [],
        "unhandled_root_fields": {},
        "resolution": {"width": ATLAS_W, "height": ATLAS_H},
        "elements": [cubes[n] for _, n, *_ in specs],
        "groups": groups,
        "outliner": outliner,
        "textures": [texture_entry("turret_atlas", atlas)],
        "display": {},
    }


def elements_for(cubes, texture_key: str) -> list[dict]:
    elements = []
    for name, frm, to, tile in cubes:
        faces = {face: {"uv": model_uv(tile, face), "texture": texture_key}
                 for face in FACES}
        elements.append({"name": name, "from": frm, "to": to, "faces": faces})
    return elements


def block_model(cubes, texture: str) -> dict:
    """固定底座 Java 方块模型；每个面取自己瓦片的对应象限。"""
    return {
        "credit": "Barotrauma MC turret v2 (fixed mount; rotating parts rendered by TurretRenderer)",
        "textures": {"base": texture, "particle": texture},
        "elements": elements_for(cubes, "#base"),
    }


def blockstate(block_id: str) -> dict:
    """mount/facing → 模型 + y 旋转。墙面模型按 MOUNT 定方向，FACING 只影响初始瞄准。"""
    variants = {}
    for mount in ("up", "down", "north", "south", "east", "west"):
        if mount == "up":
            model = f"barotrauma:block/{block_id}"
            y_for_facing = {"south": 0, "west": 90, "north": 180, "east": 270}
        elif mount == "down":
            model = f"barotrauma:block/{block_id}_ceiling"
            y_for_facing = {"south": 0, "west": 90, "north": 180, "east": 270}
        else:
            model = f"barotrauma:block/{block_id}_wall"
            # 默认模型竖板在北侧（支撑北、朝南）。
            wall_y = {"south": 0, "west": 90, "north": 180, "east": 270}
            y_for_facing = {face: wall_y[mount] for face in ("north", "south", "east", "west")}
        for facing, y in y_for_facing.items():
            variants[f"mount={mount},facing={facing}"] = {"model": model, "y": y}
    return {"variants": variants}


ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.20, 0.20, 0.20]},
    "fixed": {"rotation": [0, 180, 0], "scale": [0.42, 0.42, 0.42]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                              "scale": [0.30, 0.30, 0.30]},
    "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0],
                             "scale": [0.30, 0.30, 0.30]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.32, 0.32, 0.32]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.32, 0.32, 0.32]},
}


def item_model(texture: str) -> dict:
    elements = elements_for(BASE_CUBES + TURNTABLE_CUBES + BARREL_CUBES, "#atlas")
    return {
        "credit": "Barotrauma MC turret v2 item preview (all three parts)",
        "textures": {"atlas": texture, "particle": texture},
        "elements": elements,
        "display": ITEM_DISPLAY,
    }


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)

    normal = build_atlas(BASE_PALETTE)
    freeaim = build_atlas(FREEAIM_PALETTE)
    tracking = build_atlas(TRACKING_PALETTE)

    atlas_path = OUT_DIR / "turret_tex.png"
    normal.save(atlas_path)
    normal.save(TEX_DIR / "turret_atlas.png")
    freeaim.save(TEX_DIR / "turret_atlas_freeaim.png")
    tracking.save(TEX_DIR / "turret_atlas_tracking.png")

    bbmodel = build_bbmodel(atlas_path)
    (OUT_DIR / "turret.bbmodel").write_text(
        json.dumps(bbmodel, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    item_dir = ROOT / "src/main/resources/assets/barotrauma/models/item"
    item_dir.mkdir(parents=True, exist_ok=True)
    blockstate_dir = ROOT / "src/main/resources/assets/barotrauma/blockstates"
    blockstate_dir.mkdir(parents=True, exist_ok=True)
    for block_id, texture in (
            ("turret", "barotrauma:block/turret_atlas"),
            ("free_aim_turret", "barotrauma:block/turret_atlas_freeaim"),
            ("tracking_turret", "barotrauma:block/turret_atlas_tracking")):
        for suffix, cubes in (("", BASE_CUBES), ("_ceiling", CEILING_CUBES),
                              ("_wall", WALL_CUBES)):
            (MODEL_DIR / f"{block_id}{suffix}.json").write_text(
                json.dumps(block_model(cubes, texture), ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8")
        (blockstate_dir / f"{block_id}.json").write_text(
            json.dumps(blockstate(block_id), ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8")

    (item_dir / "turret.json").write_text(
        json.dumps(item_model("barotrauma:block/turret_atlas"), ensure_ascii=False,
                   indent=2) + "\n", encoding="utf-8")
    for file, texture in (
            ("free_aim_turret.json", "barotrauma:block/turret_atlas_freeaim"),
            ("tracking_turret.json", "barotrauma:block/turret_atlas_tracking")):
        parent_model = {"parent": "barotrauma:item/turret",
                        "textures": {"atlas": texture, "particle": texture}}
        (item_dir / file).write_text(
            json.dumps(parent_model, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8")

    print(f"wrote {atlas_path.relative_to(ROOT)}")
    print(f"wrote {OUT_DIR / 'turret.bbmodel'}")
    print("wrote 3 runtime atlases, 3 block models, 3 item models")


if __name__ == "__main__":
    main()
