"""Compact asset reports. No rendering, native exporting, network, or inferred GUI passes.

Python 3.10+, Pillow (tools/requirements.txt). See docs/ASSET_VALIDATION.md.
"""
from __future__ import annotations

import argparse
import base64
import copy
import hashlib
import io
import json
import math
import re
import sys
import tempfile
from datetime import date, datetime, timezone
from pathlib import Path
from urllib.parse import quote

from PIL import Image

VERSION = 2
PREVIEW_CUTOFF = date(2026, 11, 10)
METADATA = ("asset_id", "display_name_zh", "display_name_en",
            "contributors", "sources", "tracked_files", "preview_legacy")
DEPENDENCIES = {
    "editor_reopen": ("geometry", "textures"),
    "native_reexport": ("geometry",),
    "visual_preview": ("geometry", "textures", "previews"),
}
ROOT = Path(__file__).resolve().parents[1]


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                     separators=(",", ":"), allow_nan=False).encode()).hexdigest()


def file_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_label(report):
    contributors = report.get("contributors", {})
    human = contributors.get("human", [])
    ai = [entry.get("name", "") for entry in contributors.get("ai", [])]
    category = "人工 + AI" if human and ai else "人工" if human else "AI" if ai else "未登记"
    return category + "（" + " + ".join(human + ai) + "）"


def legacy_preview_applies(proof, inputs):
    """An explicit historical claim applies only to the original geometry and previews."""
    if not isinstance(proof, dict):
        return False
    if not all(isinstance(proof.get(k), str) and proof[k].strip()
               for k in ("published_on", "reference", "by", "note")):
        return False
    try:
        historical = date.fromisoformat(proof["published_on"]) < PREVIEW_CUTOFF
    except ValueError:
        return False
    return historical and all(proof.get("inputs", {}).get(k) == inputs[k]
                              for k in ("geometry", "previews"))


def scan(folder, tracked_files=None, preview_legacy=None):
    """Read actual files; omit UI state and image bytes from returned context."""
    groups = {"geometry": {}, "textures": {}, "previews": {}}
    measured = {"projects": [], "exports": [], "java_exports": [], "textures": [], "previews": []}
    issues = []
    preview_size_issues = []
    projects = sorted(folder.glob("*.bbmodel"))
    textures = sorted(folder.glob("*_tex.png"))
    for path in projects:
        model = read_json(path)
        # Texture pixels are tracked separately, so a repaint need not invalidate export geometry.
        # Unknown root fields may affect native export (e.g. name as identifier fallback).
        # Exclude only known editor-only data; an exporter/plugin extension must invalidate.
        geometry = {k: v for k, v in model.items() if k not in
                    ("textures", "reference_images", "editor_state", "history", "history_index")}
        geometry["texture_layout"] = [{k: texture.get(k) for k in
            ("uuid", "name", "id", "uv_width", "uv_height", "width", "height")}
            for texture in model.get("textures", [])]
        groups["geometry"][path.name] = digest(geometry)
        groups["textures"][path.name] = digest(model.get("textures", []))
        elements = [e for e in model.get("elements", []) if e.get("export", True)]
        bones = model.get("groups")
        if bones is None:
            def count(nodes):
                return sum(1 + count(n.get("children", [])) for n in nodes if isinstance(n, dict))
            bone_count = count(model.get("outliner", []))
        else:
            bone_count = len(bones)
        measured["projects"].append({"path": path.name,
            "format": model.get("meta", {}).get("model_format", "unknown"),
            "cubes": sum(e.get("type", "cube") == "cube" for e in elements),
            "meshes": sum(e.get("type") == "mesh" for e in elements), "groups": bone_count})
        if not model.get("textures") or any(not t.get("source", "").startswith("data:image/")
                                             for t in model.get("textures", [])):
            issues.append(f"{path.name}: texture is not embedded")
        # Only compare an unambiguous one-texture pair. Variants need native acceptance.
        if len(projects) == len(textures) == len(model.get("textures", [])) == 1:
            source = model["textures"][0].get("source", "")
            if source.startswith("data:image/"):
                with Image.open(io.BytesIO(base64.b64decode(source.split(",", 1)[1]))) as embedded:
                    with Image.open(textures[0]) as external:
                        if embedded.size != external.size or embedded.convert("RGBA").tobytes() != external.convert("RGBA").tobytes():
                            issues.append("embedded texture differs from external PNG")
    exports = sorted(folder.glob("*.geo.json"))
    for path in exports:
        data = read_json(path)
        groups["geometry"][path.name] = digest(data)
        geometries = data.get("minecraft:geometry", [])
        if not geometries:
            issues.append(f"{path.name}: missing minecraft:geometry")
        for geo in geometries:
            bones = geo.get("bones", [])
            measured["exports"].append({"path": path.name,
                "identifier": geo.get("description", {}).get("identifier"),
                "bones": len(bones), "cubes": sum(len(b.get("cubes", [])) for b in bones)})
    # Native Java exports and animations also invalidate native acceptance when changed.
    for path in sorted(folder.glob("*.json")):
        if path.name in {"validation.json", "structure_audit.json"} or path in exports:
            continue
        data = read_json(path)
        if any(k in data for k in ("elements", "animations", "parent")):
            groups["geometry"][path.name] = digest(data)
        if (isinstance(data.get("elements"), list)
                or isinstance(data.get("parent"), str) and data["parent"].strip()):
            measured["java_exports"].append({"path": path.name,
                                              "elements": len(data.get("elements", []))})
    for category, paths in (("textures", textures), ("previews", sorted(folder.glob("preview_*.png")))):
        for path in paths:
            groups[category][path.name] = file_hash(path)
            with Image.open(path) as im:
                im.load()
                width, height = im.size
                measured[category].append({"path": path.name, "width": width,
                                           "height": height, "mode": im.mode})
                if category == "textures":
                    if im.mode != "RGBA" or any(n <= 0 or n & (n - 1) for n in im.size):
                        issues.append(f"{path.name}: expected RGBA, power-of-two dimensions")
                else:
                    if im.size != (1200, 900):
                        preview_size_issues.append(f"{path.name}: expected 1200x900 preview")
                    if im.convert("RGBA").getbbox() is None:
                        issues.append(f"{path.name}: empty preview")
    for category, paths in (tracked_files or {}).items():
        if category not in groups:
            raise ValueError(f"Unknown tracked_files category: {category}")
        for name in paths:
            path = (folder / name).resolve()
            if not path.is_relative_to(folder.resolve()):
                raise ValueError("Tracked files must stay inside the asset directory.")
            if not path.is_file():
                issues.append(f"missing tracked file {name}")
                continue
            groups[category][path.relative_to(folder.resolve()).as_posix()] = file_hash(path)
            if category == "textures" and path.suffix.lower() == ".png":
                with Image.open(path) as im:
                    im.load()
                    if im.mode != "RGBA" or any(n <= 0 or n & (n - 1) for n in im.size):
                        issues.append(f"{name}: expected RGBA, power-of-two dimensions")
    required = ["README.md", "reference/palette.json"] + [f"preview_{v}.png" for v in ("front", "back", "side", "oblique")]
    issues.extend(f"missing {p}" for p in required if not (folder / p).is_file())
    if not projects:
        issues.append("missing .bbmodel")
    if not textures:
        issues.append("missing *_tex.png")
    if any(p["format"] in ("bedrock", "bedrock_block") for p in measured["projects"]) and not exports:
        issues.append("Bedrock project has no .geo.json")
    java_exports = {p["path"] for p in measured["java_exports"]}
    for project in measured["projects"]:
        if project["format"] == "java_block":
            expected = Path(project["path"]).with_suffix(".json").name
            if expected not in java_exports:
                issues.append(f"{project['path']}: missing Java model export {expected}")
    inputs = {k: digest(v) for k, v in groups.items()}
    if not legacy_preview_applies(preview_legacy, inputs):
        issues.extend(preview_size_issues)
    return inputs, measured, issues


def effective(report, inputs):
    states = {}
    for name, dependencies in DEPENDENCIES.items():
        evidence = report.get("acceptance", {}).get(name, {})
        state = evidence.get("result", "unverified")
        if state not in ("passed", "failed", "unverified"):
            state = "unverified"
        if state != "unverified" and (not all(evidence.get(k) for k in ("by", "at", "tool", "note"))):
            state = "unverified"
        elif name == "native_reexport" and state == "passed" and not valid_native_details(evidence.get("details", {})):
            state = "unverified"
        elif state != "unverified" and any(evidence.get("inputs", {}).get(k) != inputs[k] for k in dependencies):
            state = "stale"
        states[name] = state
    return states


def valid_native_details(details):
    """Bounds are supplied by native measurement, never guessed by a second renderer."""
    bounds = details.get("bounds", {})
    lo, hi = bounds.get("min", []), bounds.get("max", [])
    return (details.get("comparison") == "equal" and bool(bounds.get("unit"))
            and len(lo) == len(hi) == 3
            and all(type(x) in (int, float) and math.isfinite(x) for x in lo + hi)
            and all(a <= b for a, b in zip(lo, hi)))


def evaluate(folder, report):
    if report.get("schema_version") != 2:
        return {"asset_id": folder.name, "status": "legacy", "unresolved": ["legacy_report"],
                "hint": "Refresh with --legacy-backup outside the asset directory to migrate metadata only."}
    inputs, measured, issues = scan(folder, report.get("tracked_files"), report.get("preview_legacy"))
    if not all(report.get(k) for k in ("asset_id", "display_name_zh", "display_name_en")):
        issues.append("missing asset name metadata")
    if not re.fullmatch(r"[a-z][a-z0-9_]*", report.get("asset_id", "")):
        issues.append("invalid asset_id")
    if not all(report.get("sources", {}).get(k) for k in ("zh", "en")):
        issues.append("missing independent Chinese/English name sources")
    for exported in measured["exports"]:
        expected = "geometry." + exported["path"].removesuffix(".geo.json")
        if exported["identifier"] != expected:
            issues.append(f"{exported['path']}: expected identifier {expected}")
    contributors = report.get("contributors", {})
    if not (contributors.get("human") or contributors.get("ai")) or not contributors.get("division"):
        issues.append("missing contributors or division")
    if any(not all(ai.get(k) for k in ("name", "tool", "role")) for ai in contributors.get("ai", [])):
        issues.append("incomplete AI contributor metadata")
    states = effective(report, inputs)
    unresolved = [f"{k}:{v}" for k, v in states.items() if v != "passed"]
    unresolved.extend(issues)
    return {"asset_id": report["asset_id"], "status": "incomplete" if unresolved else "complete",
            "inputs": inputs, "measurements": measured, "checks": states, "unresolved": unresolved}


def write_report(folder, report):
    # Replace only validation.json, never an engineering or image file.
    target = folder / "validation.json"
    with tempfile.NamedTemporaryFile(dir=folder, prefix=".validation-", suffix=".tmp", delete=False) as handle:
        temporary = Path(handle.name)
    try:
        temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)


def refresh(folder, metadata=None, legacy_backup=None):
    path = folder / "validation.json"
    previous = read_json(path) if path.exists() else {}
    legacy = previous and previous.get("schema_version") != 2
    if legacy and legacy_backup is None:
        raise ValueError("Legacy report unchanged: supply --legacy-backup outside the asset directory.")
    report = copy.deepcopy(previous) if not legacy else {k: previous[k] for k in METADATA if k in previous}
    if metadata:
        report.update({k: v for k, v in metadata.items() if k in METADATA})
    report.setdefault("asset_id", previous.get("identifier") or re.sub(r"_\d{8}$", "", folder.name))
    report["schema_version"] = 2
    report["creation_source"] = source_label(report)
    report.setdefault("acceptance", {})
    if legacy and report.get("preview_legacy"):
        proof = copy.deepcopy(report["preview_legacy"])
        inputs, _, _ = scan(folder, report.get("tracked_files"))
        proof["inputs"] = {k: inputs[k] for k in ("geometry", "previews")}
        if not legacy_preview_applies(proof, inputs):
            raise ValueError("Historical preview claim needs a pre-2026-11-10 publication date, reference, inspector and note.")
        report["preview_legacy"] = proof
    result = evaluate(folder, report)
    report.update({k: result[k] for k in ("inputs", "measurements", "checks", "status")})
    report["unresolved"] = result["unresolved"]
    report["validator"] = {"version": VERSION, "sha256": file_hash(Path(__file__))}
    if legacy:
        backup = legacy_backup.resolve()
        if backup.is_relative_to(folder.resolve()):
            raise ValueError("Legacy backup must be outside the asset directory.")
        backup.parent.mkdir(parents=True, exist_ok=True)
        with backup.open("xb") as handle:
            handle.write(path.read_bytes())
    write_report(folder, report)
    return report


def attest(folder, check, by, tool, note, result="passed", details=None):
    if not all(value and value.strip() for value in (by, tool, note)):
        raise ValueError("Acceptance requires an inspector, tool version and a meaningful note.")
    if check == "native_reexport" and result == "passed" and not valid_native_details(details or {}):
        raise ValueError("Native acceptance needs --details with comparison=equal and native bounds (min/max/unit).")
    report = read_json(folder / "validation.json")
    if report.get("schema_version") != 2:
        raise ValueError("Refresh/migrate the report before recording acceptance.")
    current = evaluate(folder, report)
    dependencies = DEPENDENCIES[check]
    if any(report.get("inputs", {}).get(k) != current["inputs"][k] for k in dependencies):
        raise ValueError("Inputs changed since refresh; refresh and inspect the changed files first.")
    report.setdefault("acceptance", {})[check] = {
        "result": result, "by": by, "tool": tool, "note": note,
        "at": datetime.now(timezone.utc).isoformat(),
        "inputs": {k: current["inputs"][k] for k in dependencies},
    }
    if details is not None:
        report["acceptance"][check]["details"] = details
    current = evaluate(folder, report)
    report["creation_source"] = source_label(report)
    # Only refresh owns the input/measurement baseline. A geometry-only attestation
    # must not silently acknowledge a texture change for subsequent attestations.
    report.update({k: current[k] for k in ("checks", "status", "unresolved")})
    write_report(folder, report)
    return report


def summary(folder, report):
    state = evaluate(folder, report)
    unresolved = state["unresolved"]
    return {"asset_id": state["asset_id"], "status": state["status"],
            "unresolved": [item[:240] for item in unresolved[:8]],
            "unresolved_count": len(unresolved),
            "specs": state.get("measurements", {}).get("projects", [])[:4]}


def card(folder, report, commit, repo):
    state = evaluate(folder, report)
    if state["status"] != "complete":
        raise ValueError("Acceptance is incomplete; use status to inspect unresolved checks.")
    if not re.fullmatch(r"[0-9a-fA-F]{7,40}", commit):
        raise ValueError("Use a Git commit hash, not a moving branch name.")
    relative = folder.resolve().relative_to(ROOT).as_posix()
    base = repo.rstrip("/") + "/blob/" + commit + "/" + quote(relative)
    label = (report["display_name_zh"] + " " + report["display_name_en"]).replace("\n", " ")
    mark = "🤖 " if report.get("contributors", {}).get("ai") else ""
    return (f"- [ ] {mark}{label} · `{commit}` · [资产 / README]({base}/README.md)"
            f" · [规格与验收]({base}/validation.json) · [预览]({base}/preview_oblique.png)\n"
            f"  制作来源：{source_label(report)}\n")


def main(argv=None):
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("status", "refresh", "attest", "card"))
    parser.add_argument("asset", type=Path)
    parser.add_argument("--metadata", type=Path)
    parser.add_argument("--legacy-backup", type=Path)
    parser.add_argument("--check", choices=tuple(DEPENDENCIES))
    parser.add_argument("--by")
    parser.add_argument("--tool")
    parser.add_argument("--note")
    parser.add_argument("--result", choices=("passed", "failed"), default="passed")
    parser.add_argument("--details", type=Path, help="Explicit evidence, e.g. native bounds and comparison details")
    parser.add_argument("--full", action="store_true")
    parser.add_argument("--commit")
    parser.add_argument("--repo", default="https://github.com/opAmber258/CrazyWaterWorld-YuanShen")
    args = parser.parse_args(argv)
    try:
        folder = args.asset.resolve(strict=True)
        if not folder.is_dir():
            raise ValueError("Asset must be a directory.")
        if args.command == "refresh":
            report = refresh(folder, read_json(args.metadata) if args.metadata else None, args.legacy_backup)
        else:
            report = read_json(folder / "validation.json") if (folder / "validation.json").exists() else {}
        if args.command == "attest":
            if not all((args.check, args.by, args.tool, args.note)):
                raise ValueError("attest requires --check, --by, --tool and --note; never infer a pass.")
            report = attest(folder, args.check, args.by, args.tool, args.note, args.result,
                            read_json(args.details) if args.details else None)
        if args.command == "card":
            if not args.commit:
                raise ValueError("card requires --commit")
            print(card(folder, report, args.commit, args.repo), end="")
            return 0
        if args.full:
            current = evaluate(folder, report)
            state = report | {k: current[k] for k in ("status", "unresolved")}
            state.update({"checks": current.get("checks", {}),
                          "current_inputs": current.get("inputs", {}),
                          "current_measurements": current.get("measurements", {})})
        else:
            state = summary(folder, report)
        print(json.dumps(state, ensure_ascii=False, indent=2 if args.full else None))
        return 0 if state["status"] == "complete" else 1
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        print(json.dumps({"status": "error", "error": str(error)}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
