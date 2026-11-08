"""生成医疗调试器（开发者工具）的 16x16 图标。

用法：python tools/gen_debug_item.py
输出：src/main/resources/assets/barotrauma/textures/item/medical_debugger.png

纯几何绘制，不依赖任何美术资源；换正式美术时直接替换同名 png 即可。
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "barotrauma" / "textures" / "item"

OUTLINE = (24, 28, 32, 255)
STEEL = (58, 66, 72, 255)
STEEL_DARK = (44, 50, 55, 255)
SCREEN = (38, 52, 56, 255)
CROSS = (206, 62, 62, 255)
GREEN = (110, 200, 120, 255)
AMBER = (224, 118, 47, 255)


def debugger() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 机身
    d.rectangle([3, 2, 12, 13], fill=STEEL, outline=OUTLINE)
    # 两侧握把
    d.rectangle([3, 4, 4, 11], fill=STEEL_DARK)
    d.rectangle([11, 4, 12, 11], fill=STEEL_DARK)
    # 屏幕
    d.rectangle([4, 3, 11, 8], fill=SCREEN, outline=OUTLINE)
    # 屏幕上的医疗十字
    d.rectangle([7, 4, 8, 7], fill=CROSS)
    d.rectangle([6, 5, 9, 6], fill=CROSS)
    # 底部三个调节键（对应面板里的增减按钮）
    d.rectangle([4, 10, 5, 11], fill=GREEN)
    d.rectangle([7, 10, 8, 11], fill=STEEL_DARK)
    d.rectangle([10, 10, 11, 11], fill=AMBER)
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / "medical_debugger.png"
    debugger().save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    main()
