#!/usr/bin/env python3
"""把 Blockbench java_block 工程转成 GeckoLib 可读的 Bedrock geometry。

转换规则与 `art/models/battery_20261115/INTEGRATION.md` 一致（Blockbench
`bedrock.js` 的 `compileCube` 行为）：

- Java 方块工程的 X/Z 以格角为原点，进入 GeckoLib 时减 8 以方块中心为原点；
- Bedrock 的 X 轴与 Blockbench 镜像：`origin.x = -(x_max)`，pivot 的 X 取反；
- 旋转角 X/Y 取反、Z 保持；
- 保留 inflate，包括用来避免重叠的微小偏移；
- 普通面 `uv=[u1,v1] / uv_size=[u2-u1,v2-v1]`；`up`/`down` 面反向
  `uv=[u2,v2] / uv_size=[u1-u2,v1-v2]`；
- Y 保持原样（作者建模时的安装高度就是游戏内相对方块底面的高度）。

用法：
    python tools/bbmodel_to_geo.py <in.bbmodel> <out.geo.json> --identifier supercapacitor

只读取 bbmodel，不改原工程；输出为单个 root bone 的静态几何。
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

FACES = ("north", "east", "south", "west", "up", "down")


def _num(value: float) -> float:
    value = round(float(value), 5)
    if abs(value) < 1e-9:
        return 0.0
    return value


def _vec(values) -> list[float]:
    return [_num(v) for v in values]


def _face_uv(name: str, uv) -> dict:
    u1, v1, u2, v2 = (float(v) for v in uv)
    if name in ("up", "down"):
        return {
            "uv": [_num(u2), _num(v2)],
            "uv_size": [_num(u1 - u2), _num(v1 - v2)],
        }
    return {
        "uv": [_num(u1), _num(v1)],
        "uv_size": [_num(u2 - u1), _num(v2 - v1)],
    }


def convert_cube(element: dict) -> dict:
    fx, fy, fz = (float(v) for v in element["from"])
    tx, ty, tz = (float(v) for v in element["to"])
    x0, x1 = fx - 8.0, tx - 8.0
    z0, z1 = fz - 8.0, tz - 8.0

    cube = {
        "origin": [_num(-max(x0, x1)), _num(min(fy, ty)), _num(min(z0, z1))],
        "size": [_num(abs(x1 - x0)), _num(abs(ty - fy)), _num(abs(z1 - z0))],
        "uv": {},
    }
    if element.get("inflate"):
        cube["inflate"] = _num(element["inflate"])

    rotation = element.get("rotation")
    if rotation and any(abs(float(v)) > 1e-9 for v in rotation):
        rx, ry, rz = (float(v) for v in rotation)
        pivot = element.get("origin") or [(fx + tx) / 2.0, (fy + ty) / 2.0, (fz + tz) / 2.0]
        px, py, pz = (float(v) for v in pivot)
        cube["pivot"] = [_num(-(px - 8.0)), _num(py), _num(pz - 8.0)]
        cube["rotation"] = [_num(-rx), _num(-ry), _num(rz)]

    for name in FACES:
        face = (element.get("faces") or {}).get(name)
        if not face or "uv" not in face:
            continue
        cube["uv"][name] = _face_uv(name, face["uv"])
    return cube


def _cube_bounds(cube: dict) -> tuple[list[float], list[float]]:
    """GeckoLib 载入后的内部坐标包围盒（X 以方块中心为 0，单位仍为像素）。

    GeckoLib 的 `constructCube` 会把 geo 的 origin 转成
    `(-(origin.x+size.x), origin.y, origin.z)`，pivot 的 X 取反，
    并把旋转的 X/Y 再取反（geo 里已经按 Blockbench 规则取反过一次）。
    这里复现同一套变换，用来核对 `validation.json` 的包围盒。
    """
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    inflate = cube.get("inflate", 0.0)
    x0 = -(ox + sx)
    corners = []
    for dx in (-inflate, sx + inflate):
        for dy in (-inflate, sy + inflate):
            for dz in (-inflate, sz + inflate):
                corners.append([x0 + dx, oy + dy, oz + dz])
    rotation = cube.get("rotation")
    if not rotation:
        lo = [min(c[i] for c in corners) for i in range(3)]
        hi = [max(c[i] for c in corners) for i in range(3)]
        return lo, hi
    rx, ry, rz = (math.radians(v) for v in (-rotation[0], -rotation[1], rotation[2]))
    px, py, pz = cube["pivot"]
    px, py, pz = -px, py, pz
    cos_x, sin_x = math.cos(rx), math.sin(rx)
    cos_y, sin_y = math.cos(ry), math.sin(ry)
    cos_z, sin_z = math.cos(rz), math.sin(rz)
    rotated = []
    for corner in corners:
        x, y, z = corner[0] - px, corner[1] - py, corner[2] - pz
        # Blockbench / GeckoLib 都是 ZYX：先转 X，再 Y，最后 Z。
        # GeckoLib 的 RenderUtils.rotateMatrixAroundCube 依次 mulPose(Z/Y/X)，
        # 作用到顶点上等价于 Rz*Ry*Rx。
        y, z = y * cos_x - z * sin_x, y * sin_x + z * cos_x
        x, z = x * cos_y + z * sin_y, -x * sin_y + z * cos_y
        x, y = x * cos_z - y * sin_z, x * sin_z + y * cos_z
        rotated.append([x + px, y + py, z + pz])
    lo = [min(c[i] for c in rotated) for i in range(3)]
    hi = [max(c[i] for c in rotated) for i in range(3)]
    return lo, hi


def build_geo(bbmodel: dict, identifier: str) -> dict:
    elements = bbmodel.get("elements") or []
    if not elements:
        raise SystemExit("bbmodel 里没有 elements")
    cubes = [convert_cube(element) for element in elements]

    texture = (bbmodel.get("textures") or [{}])[0]
    width = int(texture.get("width") or bbmodel.get("resolution", {}).get("width") or 64)
    height = int(texture.get("height") or bbmodel.get("resolution", {}).get("height") or 64)

    los, his = [], []
    for cube in cubes:
        lo, hi = _cube_bounds(cube)
        los.append(lo)
        his.append(hi)
    lo = [min(v[i] for v in los) for i in range(3)]
    hi = [max(v[i] for v in his) for i in range(3)]
    size = [hi[i] - lo[i] for i in range(3)]

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": f"geometry.{identifier}",
                    "texture_width": width,
                    "texture_height": height,
                    "visible_bounds_width": round(size[0] / 16.0, 3),
                    "visible_bounds_height": round(size[1] / 16.0, 3),
                    "visible_bounds_offset": [0, round((lo[1] + hi[1]) / 32.0, 3), 0],
                },
                "bones": [
                    {
                        "name": identifier,
                        "pivot": [0, 0, 0],
                        "cubes": cubes,
                    }
                ],
            }
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bbmodel", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--identifier", required=True)
    args = parser.parse_args()

    source = json.loads(args.bbmodel.read_text(encoding="utf-8"))
    geo = build_geo(source, args.identifier)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(geo, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    cubes = geo["minecraft:geometry"][0]["bones"][0]["cubes"]
    print(f"{args.bbmodel} -> {args.output}: {len(cubes)} cubes, "
          f"{sum(len(c['uv']) for c in cubes)} faces")


if __name__ == "__main__":
    main()
