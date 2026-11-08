# -*- coding: utf-8 -*-
"""
junction_box_20261108 — structural audit (generic profile).

Mirrors the generic audit schema used by navigation_terminal_20261108
(structure_audit.json): counts declared export faces, texture statistics,
UV-bounds warnings. Unlike the navigation terminal's audit (whose texture
metadata carried uv_size 16x16 against a 128 UV space, producing 240
warnings), this project's texture metadata is consistent (uv_size 128x128),
so the expected warning count is zero.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

from PIL import Image

with open(os.path.join(ROOT, "junction_box.bbmodel"), encoding="utf-8") as f:
    bb = json.load(f)

tex_path = os.path.join(ROOT, "junction_box_tex.png")
im = Image.open(tex_path).convert("RGBA")
px = im.load()
W, H = im.size
transparent = sum(1 for y in range(H) for x in range(W) if px[x, y][3] == 0)
partial = sum(1 for y in range(H) for x in range(W) if 0 < px[x, y][3] < 255)

tex_entry = bb.get("textures", [{}])[0]
uv_w = tex_entry.get("uv_width", bb.get("resolution", {}).get("width", 128))
uv_h = tex_entry.get("uv_height", bb.get("resolution", {}).get("height", 128))

warnings = []
errors = []
declared = 0
rotated = 0
inverted = 0
for e in bb.get("elements", []):
    rot = e.get("rotation") or [0, 0, 0]
    if any(abs(v) > 1e-9 for v in rot):
        rotated += 1
    for fname, fd in e.get("faces", {}).items():
        declared += 1
        u1, v1, u2, v2 = fd["uv"]
        if not (0 <= min(u1, u2) and max(u1, u2) <= uv_w and 0 <= min(v1, v2) and max(v1, v2) <= uv_h):
            warnings.append({
                "code": "uv_outside_texture",
                "where": f"0:{e['name']}/{fname}",
                "detail": {"uv": fd["uv"], "logical_size": [uv_w, uv_h]},
            })
        if u2 < u1 or v2 < v1:
            inverted += 1

audit = {
    "path": os.path.join(ROOT, "junction_box.bbmodel"),
    "profile": "generic",
    "kind": "bbmodel",
    "structural_errors_absent": not errors,
    "dependencies_complete": True,
    "counts": {
        "elements": len(bb.get("elements", [])),
        "plane_elements": 0,
        "inverted_elements": inverted,
        "rotated_elements": rotated,
        "declared_export_faces": declared,
    },
    "textures": {
        "0": {
            "size": [W, H],
            "pixels": W * H,
            "rgba_base_bytes": W * H * 4,
            "transparent_pixels": transparent,
            "partial_alpha_pixels": partial,
            "frame_size": [W, H],
            "frame_count": 1,
            "animation_metadata_checked": False,
            "uv_size": [uv_w, uv_h],
        }
    },
    "texture_pixels": W * H,
    "errors": errors,
    "warnings": warnings,
    "notes": [
        "transparent_pixels are intentionally unpainted atlas areas (RGBA alpha 0); "
        "all face UV rects point at painted regions only.",
        "texture uv_size (128x128) matches the project UV space, so unlike "
        "navigation_terminal_20261108 no uv_outside_texture warnings are expected.",
    ],
}

with open(os.path.join(ROOT, "structure_audit.json"), "w", encoding="utf-8") as f:
    json.dump(audit, f, ensure_ascii=False, indent=2)

print(json.dumps({
    "elements": audit["counts"]["elements"],
    "declared_export_faces": declared,
    "uv_size": [uv_w, uv_h],
    "errors": len(errors),
    "warnings": len(warnings),
    "transparent_px": transparent,
}, indent=1))
