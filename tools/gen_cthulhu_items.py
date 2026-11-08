"""生成「克苏鲁的注视触发器」的 16x16 图标。

用法：python tools/gen_cthulhu_items.py
输出：src/main/resources/assets/barotrauma/textures/item/cthulhu_trigger.png

极简像素眼：暗紫底 + 苍白眼白 + 品红虹膜 + 黑瞳 + 下方几根触须。
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "barotrauma" / "textures" / "item"

SHELL = (58, 34, 78, 255)
SHELL_DARK = (38, 20, 52, 255)
SCLERA = (226, 214, 240, 255)
IRIS = (176, 58, 160, 255)
PUPIL = (18, 10, 24, 255)
TENTACLE = (120, 70, 150, 255)


def trigger() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 外壳（一个竖着的椭圆状轮廓）
    d.ellipse([2, 1, 13, 14], fill=SHELL, outline=SHELL_DARK)
    # 眼白（横向梭形）
    d.polygon([(3, 7), (7, 4), (12, 7), (7, 10)], fill=SCLERA)
    # 虹膜 + 瞳孔
    d.ellipse([6, 5, 9, 9], fill=IRIS)
    d.ellipse([7, 6, 8, 8], fill=PUPIL)
    # 下方触须
    for x in (4, 6, 8, 10):
        d.line([x, 12, x, 14], fill=TENTACLE)
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / "cthulhu_trigger.png"
    trigger().save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    main()
