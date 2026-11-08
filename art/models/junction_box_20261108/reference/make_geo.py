# -*- coding: utf-8 -*-
"""
junction_box_20261108 — independent Bedrock geometry generator.

Generates junction_box.geo.json directly from the saved .bbmodel data.
Used because Blockbench's native bedrock exporter crashed in the session
that produced this delivery (see README / validation.json incident notes).

Mapping convention was reverse-engineered from the known-good native pair
navigation_terminal_20261108 (bbmodel + native geo.json export):
  - cube: origin = from, size = to - from (no coordinate transform)
  - north/south/east/west: uv = [u1, v1], uv_size = [u2-u1, v2-v1]
  - up/down: uv = [u2, v2], uv_size = [-(u2-u1), -(v2-v1)]  (rect reversed)
  - bones follow the group tree with their pivot origins
Generator is verified by running it against navigation_terminal.bbmodel and
diffing against its native export (see validate.py / README).
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


def generate(bbmodel_path, out_path, identifier=None):
    with open(bbmodel_path, encoding="utf-8") as f:
        bb = json.load(f)

    res_w = bb.get("resolution", {}).get("width", 128)
    res_h = bb.get("resolution", {}).get("height", 128)
    elements = {e["uuid"]: e for e in bb.get("elements", [])}
    groups = bb.get("groups", [])

    if identifier is None:
        identifier = "geometry." + bb.get("name", "model").replace(" ", "_").lower()

    # group tree: collect cube children per group, keep subgroup order
    def cube_of(uuid):
        el = elements.get(uuid)
        if el is None or el.get("type") != "cube":
            return None
        fx, fy, fz = el["from"]
        tx, ty, tz = el["to"]
        cube = {
            "origin": [round(fx, 6), round(fy, 6), round(fz, 6)],
            "size": [round(tx - fx, 6), round(ty - fy, 6), round(tz - fz, 6)],
        }
        uv = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            r = el["faces"][face]["uv"]
            u1, v1, u2, v2 = r
            if face in ("up", "down"):
                uv[face] = {
                    "uv": [round(u2, 6), round(v2, 6)],
                    "uv_size": [round(-(u2 - u1), 6), round(-(v2 - v1), 6)],
                }
            else:
                uv[face] = {
                    "uv": [round(u1, 6), round(v1, 6)],
                    "uv_size": [round(u2 - u1, 6), round(v2 - v1, 6)],
                }
        cube["uv"] = uv
        rot = el.get("rotation") or [0, 0, 0]
        if any(abs(v) > 1e-9 for v in rot):
            cube["rotation"] = [round(v, 6) for v in rot]
            cube["pivot"] = [round(v, 6) for v in el["origin"]]
        return cube

    bones = []

    def walk(group, parent_name):
        bone = {"name": group["name"]}
        if parent_name is not None:
            bone["parent"] = parent_name
        bone["pivot"] = [round(v, 6) for v in group.get("origin", [0, 0, 0])]
        cubes = []
        for ch in group.get("children", []):
            cube = cube_of(ch)
            if cube is not None:
                cubes.append(cube)
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
        for ch in group.get("children", []):
            sub = next((g for g in groups if g["uuid"] == ch), None)
            if sub is not None:
                walk(sub, group["name"])

    roots = [g for g in groups if not any(
        ch == g["uuid"] for other in groups for ch in other.get("children", [])
    )]
    for root in roots:
        walk(root, None)

    total_cubes = sum(len(b.get("cubes", [])) for b in bones)
    if total_cubes == 0 and elements:
        # fallback for hierarchy-less .bbmodel files (flat mode): all cubes
        # go into the first bone so the per-cube mapping stays verifiable
        flat = []
        for el in elements.values():
            if el.get("type") == "cube":
                flat.append(el["uuid"])
        bones = []
        first = roots[0] if roots else {"name": bb.get("name", "model"), "origin": [0, 0, 0]}
        bone = {"name": first.get("name", "model"), "pivot": [round(v, 6) for v in first.get("origin", [0, 0, 0])]}
        bone["cubes"] = [cube_of(u) for u in flat]
        bones.append(bone)

    # visible bounds (editor hint only)
    xs, ys, zs = [], [], []
    for e in elements.values():
        if e.get("type") != "cube":
            continue
        xs += [e["from"][0], e["to"][0]]
        ys += [e["from"][1], e["to"][1]]
        zs += [e["from"][2], e["to"][2]]
    w = max(xs) - min(xs)
    h = max(ys) - min(ys)
    d = max(zs) - min(zs)
    desc = {
        "identifier": identifier,
        "texture_width": res_w,
        "texture_height": res_h,
        "visible_bounds_width": math.ceil(max(w, d) / 16) + 1,
        "visible_bounds_height": math.ceil(h / 16) + 1,
        "visible_bounds_offset": [0, round(h / 2 / 16, 4), 0],
    }

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{"description": desc, "bones": bones}],
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(geo, f, ensure_ascii=False, indent="\t", separators=(",", ": "))
    return geo


def main():
    args = sys.argv[1:]
    if args and args[0] == "--verify-nav":
        # self-check: regenerate navigation_terminal and diff against native export
        nav_dir = os.path.normpath(os.path.join(ROOT, "..", "navigation_terminal_20261108"))
        tmp = os.path.join(HERE, "_nav_regenerated.geo.json")
        generate(os.path.join(nav_dir, "navigation_terminal.bbmodel"), tmp)
        with open(tmp, encoding="utf-8") as f:
            mine = json.load(f)
        with open(os.path.join(nav_dir, "navigation_terminal.geo.json"), encoding="utf-8") as f:
            native = json.load(f)
        diff = diff_geo(native, mine)
        os.remove(tmp)
        print(json.dumps(diff, ensure_ascii=False, indent=2))
        return
    src = os.path.join(ROOT, "junction_box.bbmodel")
    out = os.path.join(ROOT, "junction_box.geo.json")
    generate(src, out)
    print("written:", out)


def diff_geo(native, mine):
    """Flat per-cube diff (matched by origin+size) of two geometry dicts.
    Bone assignment is intentionally ignored: the reference .bbmodel for the
    navigation terminal predates a format that stores group hierarchies, so
    only the per-cube mapping convention is verified here."""
    def flat(geo):
        out = {}
        for b in geo["minecraft:geometry"][0]["bones"]:
            for c in b.get("cubes", []):
                key = (json.dumps(c["origin"]), json.dumps(c["size"]))
                out.setdefault(key, []).append(c)
        return out
    ncubes, mcubes = flat(native), flat(mine)
    report = {"cube_count_native": sum(len(v) for v in ncubes.values()),
              "cube_count_mine": sum(len(v) for v in mcubes.values()),
              "matched": 0, "mismatches": []}
    for k in sorted(set(ncubes) | set(mcubes)):
        a_list, b_list = ncubes.get(k, []), mcubes.get(k, [])
        if not a_list or not b_list:
            report["mismatches"].append({"cube": k, "issue": "missing on one side"})
            continue
        for a, b in zip(sorted(a_list, key=json.dumps), sorted(b_list, key=json.dumps)):
            report["matched"] += 1
            for field in ("origin", "size", "uv"):
                if json.dumps(a.get(field), sort_keys=True) != json.dumps(b.get(field), sort_keys=True):
                    report["mismatches"].append({"cube": k, "field": field,
                                                 "native": a.get(field), "mine": b.get(field)})
    return report


if __name__ == "__main__":
    main()
