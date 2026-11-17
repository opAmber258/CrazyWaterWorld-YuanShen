"""生成跟随炮塔（tracking_turret）的 Blockbench 工程。

几何与 `assets/barotrauma/models/block/tracking_turret.json` 保持一致，贴图内嵌为
base64，方便直接双击在 Blockbench 里打开、调整、再导出 Java 方块模型。

用法（仓库根目录）：
    python tools/gen_tracking_turret_bbmodel.py

输出：
    art/models/tracking_turret_20261117/tracking_turret.bbmodel
    art/models/tracking_turret_20261117/tracking_turret_tex.png
    art/models/tracking_turret_20261117/tracking_turret_barrel_tex.png
"""

from __future__ import annotations

import base64
import json
import pathlib
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]
TEX_DIR = ROOT / "src/main/resources/assets/barotrauma/textures/block"
OUT_DIR = ROOT / "art/models/tracking_turret_20261117"

NS = uuid.UUID("6f9619ff-8b86-d011-b42d-00c04fc964ff")

BASE_TEX = TEX_DIR / "turret_base_tracking.png"
BARREL_TEX = TEX_DIR / "turret_barrel.png"

# (名字, from, to, 贴图索引)
CUBES = [
    ("base_plate", [1.0, 0.0, 1.0], [15.0, 2.0, 15.0], 0),
    ("pedestal", [5.0, 2.0, 5.0], [11.0, 7.0, 11.0], 0),
    ("head", [3.0, 7.0, 3.0], [13.0, 11.0, 13.0], 0),
    ("mount_left", [3.5, 11.0, 5.0], [5.0, 12.5, 11.0], 0),
    ("mount_right", [11.0, 11.0, 5.0], [12.5, 12.5, 11.0], 0),
    ("sensor", [11.5, 11.0, 11.5], [13.0, 14.0, 13.0], 1),
]

GROUPS = [
    ("base", ["base_plate", "pedestal", "head"]),
    ("mount", ["mount_left", "mount_right"]),
    ("sensor", ["sensor"]),
]

# 炮管不在方块模型里：TurretRenderer 每帧按瞄准角画三块，才能跟着炮塔转。
# 这里按渲染器的尺寸（单位换算 1 格 = 16）复刻一份，只用于预览/改形，导出会重复。
BARREL_PIVOT = [8.0, 12.0, 8.0]  # = renderer 的 translate(0.5, 0.75, 0.5)
BARREL_CUBES = [
    ("barrel_breech", [4.48, 8.96, 2.24], [11.52, 15.04, 11.2]),
    ("barrel_tube", [6.08, 10.08, 10.88], [9.92, 13.92, 24.32]),
    ("barrel_muzzle", [5.28, 9.28, 24.0], [10.72, 14.72, 26.24]),
]

FACES = ("north", "east", "south", "west", "up", "down")


def make_uuid(*parts: str) -> str:
    return str(uuid.uuid5(NS, ":".join(parts)))


def texture_entry(name: str, idx: int, png: pathlib.Path, particle: bool) -> dict:
    data = base64.b64encode(png.read_bytes()).decode("ascii")
    return {
        "name": name,
        "relative_path": png.name,
        "folder": "",
        "namespace": "",
        "id": str(idx),
        "group": "",
        "scope": 0,
        "width": 16,
        "height": 16,
        "uv_width": 16,
        "uv_height": 16,
        "particle": particle,
        "use_as_default": particle,
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


def cube_entry(name: str, frm: list[float], to: list[float], tex: int) -> dict:
    faces = {
        face: {"uv": [0, 0, 16, 16], "texture": tex, "rotation": 0}
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
        "origin": [8.0, 8.0, 8.0],
        "faces": faces,
        "type": "cube",
        "uuid": make_uuid("cube", name),
    }


def group_entry(name: str, children: list[str], origin: list[float] | None = None) -> dict:
    return {
        "name": name,
        "uuid": make_uuid("group", name),
        "export": True,
        "locked": False,
        "scope": 0,
        "selected": False,
        "visibility": True,
        "_static": {"properties": {}, "temp_data": {}},
        "origin": origin or [8.0, 8.0, 8.0],
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


def build(with_barrel: bool = False) -> dict:
    specs = list(CUBES) + ([(n, f, t, 1) for n, f, t in BARREL_CUBES] if with_barrel else [])
    group_specs = list(GROUPS)
    if with_barrel:
        group_specs.append(("barrel_runtime_preview", [n for n, *_ in BARREL_CUBES]))

    cubes = {name: cube_entry(name, frm, to, tex) for name, frm, to, tex in specs}
    groups = []
    for gname, members in group_specs:
        child_uuids = [cubes[m]["uuid"] for m in members]
        origin = BARREL_PIVOT if gname == "barrel_runtime_preview" else None
        groups.append(group_entry(gname, child_uuids, origin))

    # Blockbench 里分组要作为根节点的子节点存在，根节点本身不出现在 groups 列表里；
    # 这里用一个真正的根分组承载子分组，导出时选它即可。
    root = group_entry("tracking_turret", [])
    root["children"] = [g["uuid"] for g in groups]
    root["uuid"] = make_uuid("group", "tracking_turret")

    outliner = [{
        "uuid": root["uuid"],
        "isOpen": True,
        "children": [
            {"uuid": make_uuid("group", gname), "isOpen": True,
             "children": [cubes[m]["uuid"] for m in members]}
            for gname, members in group_specs
        ],
    }]
    groups.insert(0, root)

    return {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": "tracking_turret_with_barrel" if with_barrel else "tracking_turret",
        "parent": "",
        "java_block_version": "26.3",
        "ambientocclusion": True,
        "front_gui_light": False,
        "visible_box": [16, 16, 16],
        "variable_placeholders": "",
        "multi_file_ruleset": "",
        "variable_placeholder_buttons": [],
        "unhandled_root_fields": {},
        "resolution": {"width": 16, "height": 16},
        "elements": [cubes[name] for name, *_ in specs],
        "groups": groups,
        "outliner": outliner,
        "textures": [
            texture_entry("turret_base_tracking", 0, BASE_TEX, True),
            texture_entry("turret_barrel", 1, BARREL_TEX, False),
        ],
        "display": {},
    }


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "tracking_turret_tex.png").write_bytes(BASE_TEX.read_bytes())
    (OUT_DIR / "tracking_turret_barrel_tex.png").write_bytes(BARREL_TEX.read_bytes())
    for name, with_barrel in (("tracking_turret", False),
                              ("tracking_turret_with_barrel", True)):
        target = OUT_DIR / f"{name}.bbmodel"
        model = build(with_barrel)
        target.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n",
                          encoding="utf-8")
        print(f"wrote {target.relative_to(ROOT)} ({target.stat().st_size} bytes)")


if __name__ == "__main__":
    main()