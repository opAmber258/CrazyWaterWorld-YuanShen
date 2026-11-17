#!/usr/bin/env python3
"""从本机 Barotrauma 内容生成加工台/解构仪配方表。

用法（在仓库根目录）：
    python tools/gen_fabricator_recipes.py [--content <Barotrauma/Content>]

输出：src/main/resources/data/barotrauma/fabricator/recipes.json

规则（对应原作 Fabricators/fabricators.xml + 各物品 prefab 里的 <Fabricate>/<Deconstruct>）：
  * 只收 `<Fabricate suitablefabricators="fabricator">`。
  * 输出物品和所有必需材料都必须在模组里存在（按标识符去掉下划线后匹配）。
  * 用标签（items="..."）而不是具体 identifier 的需求跳过——本模组没有对应标签体系。
  * 解构要求输入和所有产出都能映射，否则玩家会平白丢材料，跳过。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent
MOD_ITEMS_JAVA = ROOT / "src/main/java/com/opamb/barotrauma/registry/ModItems.java"
OUT_JSON = ROOT / "src/main/resources/data/barotrauma/fabricator/recipes.json"
DEFAULT_CONTENT = Path("D:/Steam/steamapps/common/Barotrauma/Content")


def load_mod_items() -> dict[str, str]:
    """返回 {规范化标识符: 模组物品 id}。"""
    text = MOD_ITEMS_JAVA.read_text(encoding="utf-8")
    ids = set(re.findall(r'ITEMS\.register\("([a-z0-9_]+)"', text))
    ids.update(re.findall(r'mineral\("([a-z0-9_]+)"\)', text))
    # 辅助注册：crate("metalcrate", 20) / cargoPlaceholder("detonator") /
    # blockItem("battery", ...)。漏掉这些会让对应解构配方被静默跳过。
    ids.update(re.findall(r'(?:crate|cargoPlaceholder|blockItem)\("([a-z0-9_]+)"', text))
    mapping: dict[str, str] = {}
    for item_id in ids:
        mapping[item_id.replace("_", "")] = item_id
    return mapping


def self_test() -> None:
    """映射自检：辅助注册的物品也必须进映射（防止有效配方被静默删除）。"""
    mapping = load_mod_items()
    for expected in ("metalcrate", "detonator", "battery"):
        if expected not in mapping:
            raise SystemExit("mapping missing " + expected)
    print("mapping self-test ok:", len(mapping), "items")


def parse_items(content: Path):
    """扫描所有物品 XML，返回 (items, fabricates, deconstructs)。"""
    items: dict[str, dict] = {}
    fabricates: list[dict] = []
    deconstructs: list[dict] = []

    for xml_path in sorted(content.glob("Items/**/*.xml")):
        try:
            root = ElementTree.parse(xml_path).getroot()
        except ElementTree.ParseError:
            continue
        for item in root.iter("Item"):
            identifier = item.get("identifier")
            if not identifier:
                continue
            # 变体（variantof）不单独收配方，避免重复。
            if item.get("variantof"):
                continue
            items[identifier] = {
                "category": item.get("category", ""),
                "tags": item.get("tags", ""),
            }
            for fabricate in item.findall("Fabricate"):
                suitable = fabricate.get("suitablefabricators", "")
                if "fabricator" not in [s.strip() for s in suitable.split(",")]:
                    continue
                required = []
                tag_based = False
                for req in fabricate.findall("RequiredItem"):
                    if req.get("identifier") is None:
                        tag_based = True
                        break
                    required.append({
                        "id": req.get("identifier"),
                        "amount": int(req.get("amount", "1")),
                    })
                if tag_based or not required:
                    continue
                fabricates.append({
                    "output": identifier,
                    "amount": int(fabricate.get("amount", "1")),
                    "time": float(fabricate.get("requiredtime", "10")),
                    "money": int(fabricate.get("requiredmoney", "0")),
                    "required": required,
                    "quality": fabricate.get("quality"),
                    "category": items[identifier]["category"],
                })
            for deconstruct in item.findall("Deconstruct"):
                outputs = []
                for out in deconstruct.findall("Item"):
                    if out.get("identifier") is None:
                        continue
                    outputs.append({
                        "id": out.get("identifier"),
                        "amount": int(out.get("amount", "1")),
                    })
                if outputs:
                    deconstructs.append({
                        "input": identifier,
                        "time": float(deconstruct.get("time", "10")),
                        "outputs": outputs,
                    })
    return items, fabricates, deconstructs


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true",
                        help="只跑物品映射自检")
    parser.add_argument("--content", type=Path, default=DEFAULT_CONTENT)
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if not args.content.is_dir():
        print(f"找不到 Barotrauma Content 目录: {args.content}", file=sys.stderr)
        return 1

    mapping = load_mod_items()
    _items, fabricates, deconstructs = parse_items(args.content)

    def mod_id(identifier: str) -> str | None:
        return mapping.get(identifier.replace("_", ""))

    recipes = []
    skipped = {"output": 0, "ingredient": 0}
    used_ids: dict[str, int] = {}
    for entry in fabricates:
        out = mod_id(entry["output"])
        if out is None:
            skipped["output"] += 1
            continue
        merged: dict[str, int] = {}
        ok = True
        for req in entry["required"]:
            ing = mod_id(req["id"])
            if ing is None:
                ok = False
                break
            merged[ing] = merged.get(ing, 0) + req["amount"]
        if not ok:
            skipped["ingredient"] += 1
            continue
        # 同一个输出可能有多条配方（例如燃料棒的原配方 + 回收配方），
        # 用 #2/#3 后缀保证 id 唯一，界面按 id 排序展示。
        count = used_ids.get(out, 0) + 1
        used_ids[out] = count
        recipe_id = out if count == 1 else f"{out}#{count}"
        category = entry["category"] or "Equipment"
        recipes.append({
            "id": recipe_id,
            "output": f"barotrauma:{out}",
            "count": entry["amount"],
            "time": entry["time"],
            "money": entry["money"],
            "category": category,
            "ingredients": [
                {"item": f"barotrauma:{ing}", "count": amount}
                for ing, amount in merged.items()
            ],
        })

    decon = []
    for entry in deconstructs:
        source = mod_id(entry["input"])
        if source is None:
            continue
        outputs = []
        ok = True
        for out in entry["outputs"]:
            mapped = mod_id(out["id"])
            if mapped is None:
                ok = False
                break
            outputs.append({"item": f"barotrauma:{mapped}", "count": out["amount"]})
        if not ok or not outputs:
            continue
        decon.append({
            "id": entry["input"],
            "input": f"barotrauma:{source}",
            "time": entry["time"],
            "outputs": outputs,
        })

    recipes.sort(key=lambda r: (r["category"], r["id"]))
    decon.sort(key=lambda r: r["id"])
    payload = {
        "//": "由 tools/gen_fabricator_recipes.py 从本机 Barotrauma 内容生成，不要手改。",
        "recipes": recipes,
        "deconstruct": decon,
    }
    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                        encoding="utf-8")
    print(f"recipes={len(recipes)} deconstruct={len(decon)} "
          f"skipped(output={skipped['output']}, ingredient={skipped['ingredient']}) "
          f"-> {OUT_JSON.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
