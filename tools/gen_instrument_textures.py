"""生成乐器物品贴图（16x16，原创像素画，不复制任何原版/维基素材）。

用法：python tools/gen_instrument_textures.py
输出：src/main/resources/assets/barotrauma/textures/item/{clown_horn,guitar}.png
预览：build/instrument_textures_preview.png（4x 放大）
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "barotrauma" / "textures" / "item"

OUTLINE = (24, 16, 28, 255)
RED = (196, 54, 58, 255)
RED_DARK = (142, 34, 40, 255)
YELLOW = (232, 196, 74, 255)
YELLOW_DARK = (176, 140, 42, 255)
METAL = (168, 176, 188, 255)
METAL_DARK = (104, 112, 126, 255)
WOOD = (150, 96, 52, 255)
WOOD_DARK = (102, 62, 34, 255)
WOOD_LIGHT = (188, 132, 78, 255)
STRING = (226, 220, 206, 255)
HOLE = (38, 24, 20, 255)


def clown_horn() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 喇叭主体：从右下吹嘴斜向左上张开的锥形
    d.polygon([(4, 12), (6, 13), (13, 6), (12, 4)], fill=RED, outline=OUTLINE)
    # 喇叭口（左上）
    d.polygon([(10, 3), (13, 2), (14, 5), (11, 6)], fill=YELLOW, outline=OUTLINE)
    # 黄色箍带
    d.line([(7, 10), (9, 8)], fill=YELLOW, width=2)
    d.line([(10, 6), (11, 5)], fill=YELLOW_DARK, width=1)
    # 吹嘴（右下）
    d.rectangle([3, 12, 5, 13], fill=METAL, outline=OUTLINE)
    d.point((3, 13), fill=METAL_DARK)
    # 高光
    d.point((6, 11), fill=(240, 210, 210, 255))
    d.point((9, 7), fill=(240, 210, 210, 255))
    return img


def guitar() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 琴身：两个叠起来的椭圆（左下发声箱）
    d.ellipse([1, 8, 9, 16], fill=WOOD, outline=OUTLINE)
    d.ellipse([2, 6, 8, 12], fill=WOOD, outline=OUTLINE)
    # 音孔
    d.ellipse([4, 10, 6, 12], fill=HOLE, outline=WOOD_DARK)
    # 琴颈：从琴身右上伸到画面右上
    d.polygon([(7, 7), (14, 1), (15, 2), (8, 8)], fill=WOOD_DARK, outline=OUTLINE)
    # 琴头
    d.rectangle([13, 0, 15, 2], fill=WOOD_LIGHT, outline=OUTLINE)
    # 琴弦
    d.line([(6, 9), (14, 2)], fill=STRING, width=1)
    d.line([(5, 11), (13, 3)], fill=STRING, width=1)
    # 高光
    d.point((3, 10), fill=WOOD_LIGHT)
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    icons = {"clown_horn": clown_horn(), "guitar": guitar()}
    for name, image in icons.items():
        path = OUT / f"{name}.png"
        image.save(path)
        print(f"wrote {path}")
    sheet = Image.new("RGBA", (20 * len(icons), 16), (24, 18, 30, 255))
    for i, image in enumerate(icons.values()):
        sheet.paste(image, (i * 20, 0), image)
    preview = ROOT / "build" / "instrument_textures_preview.png"
    preview.parent.mkdir(parents=True, exist_ok=True)
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(preview)
    print(f"preview {preview}")


if __name__ == "__main__":
    main()
