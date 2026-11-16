"""Shift the deconstructor project along X so the model is centred on the block origin.

Shifts element from/to, per-element rotation origins, and group pivots by the same
delta, so relative geometry and the rotation-based animation are unaffected.
Requires no external packages.
"""
import argparse
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dx", type=float, required=True, help="X shift in model units")
    ap.add_argument("--out", type=Path, default=None)
    args = ap.parse_args()

    src = ROOT / "deconstructor.bbmodel"
    dst = args.out or (ROOT / "deconstructor.bbmodel")
    model = json.loads(src.read_text(encoding="utf-8-sig"))

    for e in model["elements"]:
        e["from"][0] = round(e["from"][0] + args.dx, 6)
        e["to"][0] = round(e["to"][0] + args.dx, 6)
        if "origin" in e:
            e["origin"][0] = round(e["origin"][0] + args.dx, 6)

    for g in model["groups"]:
        if "origin" in g:
            g["origin"][0] = round(g["origin"][0] + args.dx, 6)

    dst.write_text(json.dumps(model, indent="\t", ensure_ascii=False), encoding="utf-8")
    print(f"shifted X by {args.dx:+g} -> {dst}")


if __name__ == "__main__":
    main()
