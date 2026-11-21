"""生成炮塔瞄准站方块资产 + 炮台绑定器物品图标。

用法：python tools/gen_turret_station_assets.py

输出：
  assets/barotrauma/textures/block/turret_station.png   64x64 控制台材质
  assets/barotrauma/textures/item/turret_binder.png     16x16 绑定器图标
  assets/barotrauma/models/block/turret_station.json    方块模型（正面 +Z）
  assets/barotrauma/blockstates/turret_station.json     四朝向变体
  assets/barotrauma/models/item/turret_station.json     方块物品模型
  assets/barotrauma/models/item/turret_binder.json      绑定器物品模型
  data/barotrauma/loot_tables/blocks/turret_station.json

贴图区域（64x64，按 1 UV = 4px 换算到 0..16）：
  body    (0,0)-(32,32)   深色装甲板
  plate   (32,0)-(64,32)  浅色带筋板
  screen  (0,32)-(32,48)  屏幕
  keys    (32,32)-(64,48) 键盘/按钮
  top     (0,48)-(32,64)  顶板格栅
  hazard  (32,48)-(48,64) 警示条纹
  lens    (48,48)-(64,64) 潜望镜镜头
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "barotrauma"
DATA = ROOT / "src" / "main" / "resources" / "data" / "barotrauma"

OUTLINE = (18, 21, 24, 255)
BODY = (42, 48, 54, 255)
BODY_MID = (50, 57, 64, 255)
BODY_DARK = (27, 31, 36, 255)
BODY_LIGHT = (76, 86, 95, 255)
PLATE = (60, 68, 76, 255)
PLATE_DARK = (36, 41, 47, 255)
PLATE_LIGHT = (96, 107, 118, 255)
SCREEN_BG = (8, 16, 18, 255)
SCREEN_GLASS = (13, 34, 36, 255)
SCREEN_GRID = (24, 78, 76, 255)
SCREEN_LINE = (86, 236, 214, 255)
AMBER = (224, 150, 42, 255)
RED = (210, 62, 48, 255)
GREEN = (96, 210, 118, 255)
HAZARD = (188, 146, 30, 255)
HAZARD_DARK = (32, 28, 18, 255)
LENS_RING = (78, 84, 90, 255)


def draw_body() -> Image.Image:
    img = Image.new("RGBA", (32, 32), BODY)
    d = ImageDraw.Draw(img)
    # 大块面板 + 接缝
    d.rectangle([0, 0, 31, 31], outline=BODY_DARK)
    d.rectangle([3, 3, 28, 28], fill=BODY_MID, outline=BODY_DARK)
    d.rectangle([7, 7, 24, 24], fill=BODY, outline=BODY_DARK)
    for x, y in ((4, 4), (27, 4), (4, 27), (27, 27), (16, 4), (16, 27), (4, 16), (27, 16)):
        d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=BODY_LIGHT)
    # 几道划痕，避免整面纯色
    d.line([(9, 12), (13, 10)], fill=BODY_DARK)
    d.line([(18, 21), (22, 23)], fill=BODY_LIGHT)
    return img


def draw_plate() -> Image.Image:
    img = Image.new("RGBA", (32, 32), PLATE)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 31, 31], outline=OUTLINE)
    for x in range(3, 32, 4):
        d.line([(x, 1), (x, 30)], fill=PLATE_DARK)
        d.line([(x + 1, 1), (x + 1, 30)], fill=PLATE_LIGHT)
    for y in (2, 29):
        d.line([(1, y), (30, y)], fill=OUTLINE)
    for x, y in ((3, 3), (28, 3), (3, 28), (28, 28)):
        d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=PLATE_LIGHT, outline=OUTLINE)
    return img


def draw_screen() -> Image.Image:
    img = Image.new("RGBA", (32, 16), SCREEN_BG)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 31, 15], outline=OUTLINE)
    d.rectangle([1, 1, 30, 14], fill=SCREEN_GLASS)
    for x in range(3, 31, 4):
        d.line([(x, 2), (x, 13)], fill=SCREEN_GRID)
    for y in range(3, 14, 3):
        d.line([(2, y), (29, y)], fill=SCREEN_GRID)
    # 波形 + 目标点
    points = [(2, 11), (5, 11), (7, 7), (9, 9), (12, 5), (15, 8), (18, 6),
              (21, 10), (24, 7), (27, 9), (29, 6)]
    d.line(points, fill=SCREEN_LINE, width=1)
    d.rectangle([24, 3, 26, 5], fill=AMBER)
    d.rectangle([5, 3, 6, 4], fill=GREEN)
    # 扫描线
    for y in range(2, 14, 2):
        d.line([(1, y), (30, y)], fill=(20, 58, 56))
    return img


def draw_keys() -> Image.Image:
    img = Image.new("RGBA", (32, 16), (24, 28, 32, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 31, 15], outline=OUTLINE)
    for row in range(3):
        for col in range(9):
            x = 2 + col * 3
            y = 2 + row * 4
            d.rectangle([x, y, x + 2, y + 2], fill=PLATE, outline=(14, 16, 18))
    # 右侧功能键
    d.rectangle([28, 2, 30, 4], fill=GREEN, outline=OUTLINE)
    d.rectangle([28, 6, 30, 8], fill=AMBER, outline=OUTLINE)
    d.rectangle([28, 10, 30, 12], fill=RED, outline=OUTLINE)
    return img


def draw_top() -> Image.Image:
    img = Image.new("RGBA", (32, 16), PLATE_DARK)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 31, 15], outline=OUTLINE)
    for y in range(3, 16, 3):
        d.line([(2, y), (29, y)], fill=(22, 25, 28))
        d.line([(2, y + 1), (29, y + 1)], fill=(44, 50, 56))
    for x, y in ((2, 2), (29, 2), (2, 13), (29, 13)):
        d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=PLATE_LIGHT)
    return img


def draw_hazard() -> Image.Image:
    img = Image.new("RGBA", (16, 16), HAZARD)
    d = ImageDraw.Draw(img)
    for offset in range(-16, 32, 8):
        d.line([(offset, 15), (offset + 15, 0)], fill=HAZARD_DARK, width=4)
    d.rectangle([0, 0, 15, 15], outline=OUTLINE)
    return img


def draw_lens() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (20, 22, 24, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=OUTLINE)
    d.ellipse([1, 1, 14, 14], fill=LENS_RING, outline=OUTLINE)
    d.ellipse([3, 3, 12, 12], fill=(58, 20, 12, 255))
    d.ellipse([4, 4, 11, 11], fill=(214, 84, 30, 255))
    d.ellipse([6, 6, 9, 9], fill=(255, 214, 150, 255))
    return img


def build_block_texture() -> Image.Image:
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    img.paste(draw_body(), (0, 0))
    img.paste(draw_plate(), (32, 0))
    img.paste(draw_screen(), (0, 32))
    img.paste(draw_keys(), (32, 32))
    img.paste(draw_top(), (0, 48))
    img.paste(draw_hazard(), (32, 48))
    img.paste(draw_lens(), (48, 48))
    return img


def build_binder_texture() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 机身
    d.rectangle([3, 4, 12, 13], fill=PLATE, outline=OUTLINE)
    d.rectangle([4, 10, 11, 13], fill=PLATE_DARK)
    # 屏幕 + 链接图标
    d.rectangle([4, 5, 10, 8], fill=SCREEN_BG, outline=OUTLINE)
    d.line([(5, 7), (9, 7)], fill=SCREEN_LINE)
    d.rectangle([5, 6, 6, 7], fill=GREEN)
    d.rectangle([8, 6, 9, 7], fill=GREEN)
    # 顶部两根探针
    d.line([(5, 1), (5, 4)], fill=PLATE_LIGHT)
    d.line([(10, 1), (10, 4)], fill=PLATE_LIGHT)
    d.rectangle([4, 0, 6, 1], fill=AMBER, outline=OUTLINE)
    d.rectangle([9, 0, 11, 1], fill=AMBER, outline=OUTLINE)
    # 侧面线缆盘
    d.arc([10, 8, 15, 13], 250, 110, fill=AMBER)
    d.ellipse([12, 10, 14, 12], fill=PLATE_LIGHT, outline=OUTLINE)
    # 状态灯
    d.rectangle([4, 11, 5, 12], fill=RED)
    d.rectangle([6, 11, 7, 12], fill=GREEN)
    d.point((9, 12), fill=SCREEN_LINE)
    return img


# UV 区域（0..16，1 单位 = 4px）
BODY_UV = [0, 0, 8, 8]
PLATE_UV = [8, 0, 16, 8]
SCREEN_UV = [0, 8, 8, 12]
KEYS_UV = [8, 8, 16, 12]
TOP_UV = [0, 12, 8, 16]
HAZARD_UV = [8, 12, 12, 16]
LENS_UV = [12, 12, 16, 16]


def faces(**overrides) -> dict:
    """所有面先给 body，再按面覆盖。"""
    result = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        result[face] = {"uv": list(BODY_UV), "texture": "#0"}
    for face, uv in overrides.items():
        result[face] = {"uv": list(uv), "texture": "#0"}
    return result


def cube(frm, to, face_map) -> dict:
    return {"from": frm, "to": to, "faces": face_map}


def build_block_model() -> dict:
    return {
        "credit": "Generated by tools/gen_turret_station_assets.py",
        "textures": {
            "0": "barotrauma:block/turret_station",
            "particle": "barotrauma:block/turret_station",
        },
        "elements": [
            # 底座：正面一条警示条纹
            cube([1, 0, 1], [15, 2, 15], faces(
                up=TOP_UV, down=PLATE_UV, north=PLATE_UV, south=HAZARD_UV,
                east=PLATE_UV, west=PLATE_UV)),
            # 台体
            cube([2, 2, 2], [14, 10, 14], faces(
                up=TOP_UV, down=BODY_UV, north=BODY_UV, south=BODY_UV,
                east=BODY_UV, west=BODY_UV)),
            # 屏幕外框（凸出正面）
            cube([3, 3.5, 13.7], [13, 9, 14.6], faces(
                up=PLATE_UV, down=PLATE_UV, north=BODY_UV, south=PLATE_UV,
                east=PLATE_UV, west=PLATE_UV)),
            # 屏幕玻璃（再凸出一点，避免和边框共面）
            cube([3.7, 4.2, 14.55], [12.3, 8.3, 14.85], faces(
                up=SCREEN_UV, down=SCREEN_UV, north=SCREEN_UV, south=SCREEN_UV,
                east=SCREEN_UV, west=SCREEN_UV)),
            # 操作台面（向玩家方向伸出）
            cube([2.5, 9, 8], [13.5, 10.5, 15.5], faces(
                up=KEYS_UV, down=BODY_UV, north=BODY_UV, south=BODY_UV,
                east=BODY_UV, west=BODY_UV)),
            # 左侧按键条
            cube([3.2, 10.5, 9.2], [6, 11.1, 12.8], faces(
                up=KEYS_UV, down=BODY_UV, north=BODY_UV, south=BODY_UV,
                east=BODY_UV, west=BODY_UV)),
            # 潜望镜立柱
            cube([7, 10.5, 7], [9, 13, 9], faces(
                up=PLATE_UV, down=PLATE_UV, north=PLATE_UV, south=PLATE_UV,
                east=PLATE_UV, west=PLATE_UV)),
            # 潜望镜头；正面是镜头
            cube([6, 13, 6], [10, 15.5, 10], faces(
                up=PLATE_UV, down=PLATE_UV, north=BODY_UV, south=LENS_UV,
                east=BODY_UV, west=BODY_UV)),
        ],
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0],
                    "scale": [0.625, 0.625, 0.625]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0],
                       "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0],
                      "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                      "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                                      "scale": [0.4, 0.4, 0.4]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0],
                                     "scale": [0.4, 0.4, 0.4]},
        },
    }


def build_blockstate() -> dict:
    # 模型正面朝 +Z（south）：south=0°，west=90°，north=180°，east=270°。
    rotations = {"south": 0, "west": 90, "north": 180, "east": 270}
    return {
        "variants": {
            f"facing={facing}": {
                "model": "barotrauma:block/turret_station",
                "y": rotation,
            }
            for facing, rotation in rotations.items()
        }
    }


def build_loot_table() -> dict:
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": "barotrauma:turret_station"}],
        }],
    }


def write_json(path: Path, value) -> None:
    import json
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {path.relative_to(ROOT)}")


def main() -> None:
    texture_dir = ASSETS / "textures"
    texture_dir.mkdir(parents=True, exist_ok=True)
    build_block_texture().save(texture_dir / "block" / "turret_station.png")
    print("wrote", (texture_dir / "block" / "turret_station.png").relative_to(ROOT))
    build_binder_texture().save(texture_dir / "item" / "turret_binder.png")
    print("wrote", (texture_dir / "item" / "turret_binder.png").relative_to(ROOT))

    write_json(ASSETS / "models" / "block" / "turret_station.json", build_block_model())
    write_json(ASSETS / "blockstates" / "turret_station.json", build_blockstate())
    write_json(ASSETS / "models" / "item" / "turret_station.json",
               {"parent": "barotrauma:block/turret_station"})
    write_json(ASSETS / "models" / "item" / "turret_binder.json",
               {"parent": "item/generated",
                "textures": {"layer0": "barotrauma:item/turret_binder"}})
    write_json(DATA / "loot_tables" / "blocks" / "turret_station.json", build_loot_table())


if __name__ == "__main__":
    main()
