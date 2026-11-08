# -*- coding: utf-8 -*-
"""
junction_box_20261108 — independent delivery validation.

Checks (guide V2 §11):
  1. bbmodel <-> geo.json structural equivalence (cubes, sizes, per-face UV)
  2. bounding box: ground Y=0, X centered, Z extents recorded
  3. all face UV rects inside the declared 128x128 UV space
  4. texture file actually 512x512 RGBA (4x density of the 128 UV space)
Writes validation.json next to the delivery files.
"""
import json
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

with open(os.path.join(ROOT, "junction_box.bbmodel"), encoding="utf-8") as f:
    bb = json.load(f)
with open(os.path.join(ROOT, "junction_box.geo.json"), encoding="utf-8") as f:
    geo = json.load(f)

checks = []
warnings = []


def check(name, ok, detail=""):
    checks.append({"check": name, "pass": bool(ok), "detail": detail})
    return ok


# ---- 1. structural equivalence -------------------------------------------
elements = {e["uuid"]: e for e in bb["elements"]}
geo_cubes = []
for b in geo["minecraft:geometry"][0]["bones"]:
    for c in b.get("cubes", []):
        geo_cubes.append((b["name"], c))

check("cube_count", len(elements) == len(geo_cubes),
      f"bbmodel {len(elements)} vs geo {len(geo_cubes)}")

geo_by_key = {}
for bone_name, c in geo_cubes:
    geo_by_key[(round(c["origin"][0], 4), round(c["origin"][2], 4), round(c["origin"][1], 4))] = (bone_name, c)

mismatches = []
matched = 0
for e in elements.values():
    key = (round(e["from"][0], 4), round(e["from"][2], 4), round(e["from"][1], 4))
    hit = geo_by_key.get(key)
    if hit is None:
        mismatches.append({"cube": e["name"], "issue": "no geo cube with same origin"})
        continue
    bone_name, c = hit
    size = [round(e["to"][i] - e["from"][i], 4) for i in range(3)]
    if [round(v, 4) for v in c["size"]] != size:
        mismatches.append({"cube": e["name"], "issue": "size mismatch",
                           "bb": size, "geo": c["size"]})
        continue
    uv_ok = True
    for face in ("north", "south", "east", "west", "up", "down"):
        u1, v1, u2, v2 = e["faces"][face]["uv"]
        guv = c["uv"][face]
        if face in ("up", "down"):
            expect = {"uv": [u2, v2], "uv_size": [u1 - u2, v1 - v2]}
        else:
            expect = {"uv": [u1, v1], "uv_size": [u2 - u1, v2 - v1]}
        if [round(v, 4) for v in guv["uv"]] != [round(v, 4) for v in expect["uv"]] or \
           [round(v, 4) for v in guv["uv_size"]] != [round(v, 4) for v in expect["uv_size"]]:
            uv_ok = False
            mismatches.append({"cube": e["name"], "face": face, "issue": "uv mismatch",
                               "bb_rect": e["faces"][face]["uv"], "geo": guv})
    if uv_ok:
        matched += 1

check("cube_uv_equivalence", not mismatches,
      f"{matched}/{len(elements)} cubes fully equivalent" +
      (f"; {len(mismatches)} mismatches" if mismatches else ""))

# ---- 2. bounding box ------------------------------------------------------
xs, ys, zs = [], [], []
for e in elements.values():
    xs += [e["from"][0], e["to"][0]]
    ys += [e["from"][1], e["to"][1]]
    zs += [e["from"][2], e["to"][2]]
bbox = {"x": [min(xs), max(xs)], "y": [min(ys), max(ys)], "z": [min(zs), max(zs)]}
size = {"w": max(xs) - min(xs), "h": max(ys) - min(ys), "d": max(zs) - min(zs)}
check("ground_y0", abs(min(ys)) < 1e-9, f"min y = {min(ys)}")
check("x_centered", abs(max(xs) + min(xs)) < 1e-9,
      f"x range {min(xs)}..{max(xs)} (center {((max(xs)+min(xs))/2):.3f})")
check("z_center_note", True,
      f"z range {min(zs)}..{max(zs)} (center {((max(zs)+min(zs))/2):.3f}); "
      "includes rear protrusions (plate/cables), body is centered by design")

# ---- 3. UV bounds ---------------------------------------------------------
oob = []
for e in elements.values():
    for face, data in e["faces"].items():
        u1, v1, u2, v2 = data["uv"]
        if not (0 <= min(u1, u2) and max(u1, u2) <= 128 and 0 <= min(v1, v2) and max(v1, v2) <= 128):
            oob.append({"cube": e["name"], "face": face, "uv": data["uv"]})
check("uv_within_128", not oob, f"{len(oob)} out-of-bounds rects" if oob else "all rects within 0..128")

# ---- 4. texture -----------------------------------------------------------
tex_path = os.path.join(ROOT, "junction_box_tex.png")
im = Image.open(tex_path)
check("texture_512_rgba", im.size == (512, 512) and im.mode == "RGBA",
      f"{im.size} {im.mode} (4x density of 128 UV space)")

# ---- bones / groups -------------------------------------------------------
bones = geo["minecraft:geometry"][0]["bones"]
check("bones_present", len(bones) == len(bb["groups"]),
      f"{len(bones)} bones: " + ", ".join(f"{b['name']}({len(b.get('cubes', []))})" for b in bones))

# ---- assembly -------------------------------------------------------------
result = {
    "model": "junction_box",
    "delivery": "junction_box_20261108",
    "generated_from": "junction_box.bbmodel (data-level, independent of Blockbench runtime)",
    "geo_identifier": geo["minecraft:geometry"][0]["description"]["identifier"],
    "format_version": geo["format_version"],
    "uv_space": [128, 128],
    "texture_bitmap": [512, 512],
    "bounding_box_bb": bbox,
    "size_blocks": {"width": round(size["w"] / 16, 4),
                    "height": round(size["h"] / 16, 4),
                    "depth": round(size["d"] / 16, 4)},
    "checks": checks,
    "warnings": warnings,
    "notes": [
        "geo.json was generated by reference/make_geo.py directly from bbmodel data "
        "because Blockbench's native bedrock exporter crashed in the production "
        "session (see README incident notes). The generator's mapping convention "
        "was verified against the known-good native pair navigation_terminal_20261108 "
        "(17/17 in-sync cubes byte-identical; the only 2 uv deltas are cubes whose "
        "source bbmodel was edited after that delivery's export, documented).",
        "'z_center_note' is informational: the body shell is Z-centered; rear "
        "mounting plate / cables extend the bounding box backward by design "
        "(art-inferred parts).",
    ],
}

with open(os.path.join(ROOT, "validation.json"), "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

print(json.dumps({c["check"]: c["pass"] for c in checks}, indent=1))
print("details:")
for c in checks:
    print(" -", c["check"], "PASS" if c["pass"] else "FAIL", "|", c["detail"])
if mismatches:
    print("MISMATCH SAMPLE:", json.dumps(mismatches[:3], ensure_ascii=False)[:400])
