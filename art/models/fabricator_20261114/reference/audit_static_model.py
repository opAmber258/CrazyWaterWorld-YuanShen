#!/usr/bin/env python3
"""Read-only structural preflight for static Blockbench, Bedrock and Java models."""

from __future__ import annotations

import argparse
import base64
import json
import math
import struct
import sys
from pathlib import Path
from typing import Any


class DuplicateKeyError(ValueError):
    pass


def no_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKeyError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=no_duplicate_keys)


def png_size_bytes(data: bytes) -> tuple[int, int]:
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("not a readable PNG header")
    return struct.unpack(">II", data[16:24])


def png_size_file(path: Path) -> tuple[int, int]:
    with path.open("rb") as handle:
        return png_size_bytes(handle.read(24))


def embedded_png_size(texture: dict[str, Any]) -> tuple[int, int] | None:
    source = texture.get("source")
    if not isinstance(source, str) or not source.startswith("data:image/png;base64,"):
        return None
    payload = source.split(",", 1)[1]
    return png_size_bytes(base64.b64decode(payload, validate=True))


def finite_vector(value: Any, length: int) -> bool:
    return (
        isinstance(value, list)
        and len(value) == length
        and all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) for v in value)
    )


class Audit:
    def __init__(self, source: Path, kind: str) -> None:
        self.report: dict[str, Any] = {
            "input": str(source),
            "kind": kind,
            "errors": [],
            "warnings": [],
            "limitations": [
                "Limited static-model preflight only; not a complete runtime schema or client compatibility test.",
                "Does not solve visibility, z-fighting, texture sampling, resource registration, collision or performance.",
                "Bounds are unrotated source-coordinate extents; bone/element transforms and inflate are not applied.",
            ],
        }

    def error(self, code: str, message: str) -> None:
        self.report["errors"].append({"code": code, "message": message})

    def warning(self, code: str, message: str) -> None:
        self.report["warnings"].append({"code": code, "message": message})

    def require_identifier(self, actual: Any, expected: str | None) -> None:
        self.report["identifier"] = actual
        if expected is not None and actual != expected:
            self.error("identifier_mismatch", f"expected {expected!r}, found {actual!r}")


def update_bounds(bounds: list[list[float]] | None, low: list[float], high: list[float]) -> list[list[float]]:
    if bounds is None:
        return [low.copy(), high.copy()]
    return [
        [min(bounds[0][i], low[i]) for i in range(3)],
        [max(bounds[1][i], high[i]) for i in range(3)],
    ]


def check_uv_rect(
    audit: Audit,
    uv: Any,
    size: Any,
    width: int | float | None,
    height: int | float | None,
    label: str,
) -> None:
    if not finite_vector(uv, 2) or not finite_vector(size, 2):
        audit.error("invalid_uv", f"{label}: expected finite uv and uv_size pairs")
        return
    if width is None or height is None:
        return
    us = [uv[0], uv[0] + size[0]]
    vs = [uv[1], uv[1] + size[1]]
    if min(us) < 0 or max(us) > width or min(vs) < 0 or max(vs) > height:
        audit.error(
            "uv_outside_texture",
            f"{label}: {uv} + {size} exceeds 0..{width} / 0..{height}",
        )


def check_uv4(
    audit: Audit,
    uv: Any,
    width: int | float | None,
    height: int | float | None,
    label: str,
) -> None:
    if not finite_vector(uv, 4):
        audit.error("invalid_uv", f"{label}: expected four finite UV coordinates")
        return
    if width is None or height is None:
        return
    us = [uv[0], uv[2]]
    vs = [uv[1], uv[3]]
    if min(us) < 0 or max(us) > width or min(vs) < 0 or max(vs) > height:
        audit.error("uv_outside_texture", f"{label}: {uv} exceeds 0..{width} / 0..{height}")


def supplied_texture_size(path: str | None) -> tuple[int, int] | None:
    return png_size_file(Path(path)) if path else None


def audit_bbmodel(data: dict[str, Any], source: Path, texture_arg: str | None, expected: str | None) -> Audit:
    audit = Audit(source, "bbmodel")
    audit.require_identifier(data.get("model_identifier"), expected)
    resolution = data.get("resolution") if isinstance(data.get("resolution"), dict) else {}
    width = resolution.get("width") if isinstance(resolution.get("width"), (int, float)) else None
    height = resolution.get("height") if isinstance(resolution.get("height"), (int, float)) else None
    audit.report["uv_space"] = {"width": width, "height": height, "source": "project resolution"}

    actual_size = supplied_texture_size(texture_arg)
    if actual_size is None:
        for texture in data.get("textures", []):
            if isinstance(texture, dict):
                try:
                    actual_size = embedded_png_size(texture)
                except (ValueError, base64.binascii.Error) as exc:
                    audit.warning("embedded_texture_unreadable", str(exc))
                if actual_size is not None:
                    break
    if actual_size is not None:
        audit.report["texture_size"] = {"width": actual_size[0], "height": actual_size[1]}
        if width is not None and height is not None and actual_size != (width, height):
            audit.warning(
                "texture_project_size_mismatch",
                f"actual PNG is {actual_size}, project UV resolution is {(width, height)}; UV checked in project space",
            )
    else:
        audit.warning("texture_size_unknown", "supply --texture or embed a PNG to cross-check actual texture dimensions")

    elements = [e for e in data.get("elements", []) if isinstance(e, dict) and e.get("export", True)]
    cubes = [e for e in elements if e.get("type", "cube") == "cube"]
    bounds = None
    face_count = 0
    missing_uv = 0
    missing_texture = 0
    rotated = 0
    for index, cube in enumerate(cubes):
        label = f"elements[{index}] ({cube.get('name', cube.get('uuid', 'unnamed'))})"
        low, high = cube.get("from"), cube.get("to")
        if not finite_vector(low, 3) or not finite_vector(high, 3):
            audit.error("invalid_cube_bounds", f"{label}: from/to must be finite XYZ vectors")
            continue
        if any(high[i] < low[i] for i in range(3)):
            audit.error("inverted_cube", f"{label}: negative extent; verify target-specific geometry semantics")
        elif any(high[i] == low[i] for i in range(3)):
            audit.warning("zero_thickness_geometry", f"{label}: verify intentional plane/marker and target format; do not automatically thicken or delete")
        bounds = update_bounds(bounds, low, high)
        rotation = cube.get("rotation")
        if isinstance(rotation, list) and any(isinstance(v, (int, float)) and v != 0 for v in rotation):
            rotated += 1
        faces = cube.get("faces")
        if not isinstance(faces, dict):
            audit.error("faces_missing", f"{label}: faces object is missing")
            continue
        for face_name, face in faces.items():
            face_count += 1
            if not isinstance(face, dict):
                audit.error("invalid_face", f"{label}.{face_name}: expected an object")
                continue
            if face.get("texture") is None:
                missing_texture += 1
            if face.get("uv") is None:
                missing_uv += 1
            else:
                check_uv4(audit, face.get("uv"), width, height, f"{label}.{face_name}")

    def count_groups(nodes: Any) -> int:
        if not isinstance(nodes, list):
            return 0
        total = 0
        for node in nodes:
            if isinstance(node, dict):
                total += 1 + count_groups(node.get("children"))
        return total

    audit.report["metrics"] = {
        "elements": len(elements),
        "cubes": len(cubes),
        "groups": count_groups(data.get("outliner")),
        "declared_faces": face_count,
        "rotated_cubes": rotated,
        "animations": len(data.get("animations", [])) if isinstance(data.get("animations"), list) else None,
        "missing_uv_faces": missing_uv,
        "missing_texture_faces": missing_texture,
    }
    audit.report["bounds"] = bounds
    if missing_uv:
        audit.warning("missing_uv", f"{missing_uv} declared faces have no UV rectangle")
    if missing_texture:
        audit.warning("missing_texture", f"{missing_texture} declared faces have no texture reference")
    return audit


def audit_bedrock(data: dict[str, Any], source: Path, texture_arg: str | None, expected: str | None) -> Audit:
    audit = Audit(source, "bedrock_geometry")
    geometries = data.get("minecraft:geometry")
    if not isinstance(geometries, list) or not geometries:
        audit.error("geometry_missing", "minecraft:geometry must be a non-empty array")
        return audit
    if len(geometries) != 1:
        audit.warning("multiple_geometries", f"file contains {len(geometries)} geometries; metrics cover all, identifier check covers the first")
    geometry = geometries[0]
    description = geometry.get("description") if isinstance(geometry, dict) else {}
    audit.require_identifier(description.get("identifier"), expected)
    width = description.get("texture_width")
    height = description.get("texture_height")
    if not isinstance(width, (int, float)) or not isinstance(height, (int, float)):
        audit.error("texture_space_missing", "description.texture_width/height must be numeric")
        width = height = None
    audit.report["uv_space"] = {"width": width, "height": height, "source": "Bedrock description"}
    actual_size = supplied_texture_size(texture_arg)
    if actual_size is not None:
        audit.report["texture_size"] = {"width": actual_size[0], "height": actual_size[1]}
        if width is not None and height is not None and actual_size != (width, height):
            audit.warning("texture_export_size_mismatch", f"actual PNG is {actual_size}, export description is {(width, height)}")
    else:
        audit.warning("texture_size_unknown", "supply --texture to compare the actual PNG with export metadata")

    bones: list[dict[str, Any]] = []
    cube_count = 0
    face_count = 0
    bounds = None
    for geometry_index, geometry in enumerate(geometries):
        if not isinstance(geometry, dict) or not isinstance(geometry.get("bones"), list):
            audit.error("bones_missing", f"minecraft:geometry[{geometry_index}].bones must be an array")
            continue
        for bone_index, bone in enumerate(geometry["bones"]):
            if not isinstance(bone, dict):
                continue
            bones.append(bone)
            for cube_index, cube in enumerate(bone.get("cubes", [])):
                if not isinstance(cube, dict):
                    continue
                cube_count += 1
                label = f"geometry[{geometry_index}].bones[{bone_index}].cubes[{cube_index}]"
                origin, size = cube.get("origin"), cube.get("size")
                if not finite_vector(origin, 3) or not finite_vector(size, 3):
                    audit.error("invalid_cube_bounds", f"{label}: origin/size must be finite XYZ vectors")
                    continue
                if any(v < 0 for v in size):
                    audit.error("inverted_cube", f"{label}: negative extent; verify target-specific geometry semantics")
                elif any(v == 0 for v in size):
                    audit.warning("zero_thickness_geometry", f"{label}: verify intentional plane/marker and target format; do not automatically thicken or delete")
                bounds = update_bounds(bounds, origin, [origin[i] + size[i] for i in range(3)])
                uv = cube.get("uv")
                if isinstance(uv, dict):
                    for face_name, face in uv.items():
                        face_count += 1
                        if not isinstance(face, dict):
                            audit.error("invalid_face", f"{label}.{face_name}: expected an object")
                            continue
                        check_uv_rect(audit, face.get("uv"), face.get("uv_size"), width, height, f"{label}.{face_name}")
                elif isinstance(uv, list):
                    face_count += 6
                    audit.warning("box_uv_not_expanded", f"{label}: box UV uses shared uv/size; per-face rectangles were not checked")
                else:
                    audit.warning("missing_uv", f"{label}: cube has no UV mapping")
    audit.report["metrics"] = {"geometries": len(geometries), "bones": len(bones), "cubes": cube_count, "declared_faces": face_count}
    audit.report["bounds"] = bounds
    return audit


def audit_java(data: dict[str, Any], source: Path, texture_arg: str | None, expected: str | None) -> Audit:
    audit = Audit(source, "java_block_model")
    audit.require_identifier(data.get("id"), expected)
    audit.report["uv_space"] = {"width": 16, "height": 16, "source": "Java model logical UV space"}
    actual_size = supplied_texture_size(texture_arg)
    if actual_size is not None:
        audit.report["texture_size"] = {"width": actual_size[0], "height": actual_size[1]}
    elements = data.get("elements")
    if elements is None and isinstance(data.get("parent"), str):
        elements = []
        audit.warning("inherited_geometry_not_expanded", "parent-only Java model; inherited elements are not resolved")
    if not isinstance(elements, list):
        audit.error("elements_missing", "elements must be an array, or a parent string must be present")
        return audit
    bounds = None
    face_count = 0
    rotated = 0
    for index, element in enumerate(elements):
        if not isinstance(element, dict):
            audit.error("invalid_element", f"elements[{index}] must be an object")
            continue
        label = f"elements[{index}]"
        low, high = element.get("from"), element.get("to")
        if not finite_vector(low, 3) or not finite_vector(high, 3):
            audit.error("invalid_element_bounds", f"{label}: from/to must be finite XYZ vectors")
        else:
            if any(high[i] < low[i] for i in range(3)):
                audit.error("inverted_element", f"{label}: negative extent; verify target-specific geometry semantics")
            elif any(high[i] == low[i] for i in range(3)):
                audit.warning("zero_thickness_geometry", f"{label}: verify intentional plane and target format; do not automatically thicken or delete")
            bounds = update_bounds(bounds, low, high)
        if isinstance(element.get("rotation"), dict):
            rotated += 1
        faces = element.get("faces")
        if not isinstance(faces, dict):
            audit.error("faces_missing", f"{label}: faces object is missing")
            continue
        for face_name, face in faces.items():
            face_count += 1
            if not isinstance(face, dict) or face.get("uv") is None:
                audit.warning("missing_uv", f"{label}.{face_name}: no explicit UV; runtime defaults are not inferred")
            else:
                check_uv4(audit, face.get("uv"), 16, 16, f"{label}.{face_name}")
    audit.report["metrics"] = {"elements": len(elements), "declared_faces": face_count, "rotated_elements": rotated}
    audit.report["bounds"] = bounds
    audit.report["limitations"].append("Java rotation limits, parent/texture dependency resolution and version-specific item/blockstate definitions are not certified.")
    return audit


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", help=".bbmodel, Bedrock .geo.json, or Java block/item model JSON")
    parser.add_argument("--texture", help="optional actual PNG used to cross-check dimensions")
    parser.add_argument("--expected-identifier", help="optional exact Bedrock/BBmodel identifier")
    parser.add_argument("--output", help="write the full JSON report here instead of stdout")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source = Path(args.input)
    try:
        protected = {source.resolve()}
        if args.texture:
            protected.add(Path(args.texture).resolve())
        if args.output and Path(args.output).resolve() in protected:
            raise ValueError("report must not overwrite an input model or texture")
        data = load_json(source)
        if not isinstance(data, dict):
            raise ValueError("model root must be a JSON object")
        if "minecraft:geometry" in data:
            audit = audit_bedrock(data, source, args.texture, args.expected_identifier)
        elif "elements" in data and ("meta" in data or "resolution" in data or "outliner" in data):
            audit = audit_bbmodel(data, source, args.texture, args.expected_identifier)
        elif "elements" in data or "parent" in data:
            audit = audit_java(data, source, args.texture, args.expected_identifier)
        else:
            raise ValueError("unrecognized model shape")
    except (OSError, ValueError, json.JSONDecodeError, base64.binascii.Error) as exc:
        print(f"audit failed: {exc}", file=sys.stderr)
        return 2

    report = audit.report
    report["error_count"] = len(report["errors"])
    report["warning_count"] = len(report["warnings"])
    rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(rendered, encoding="utf-8", newline="\n")
        print(f"{args.output}: errors={report['error_count']} warnings={report['warning_count']}")
    else:
        sys.stdout.write(rendered)
    return 1 if report["error_count"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
