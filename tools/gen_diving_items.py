"""生成潜水服与深潜许可的 16x16 占位贴图。

用法：python tools/gen_diving_items.py
输出：
  src/main/resources/assets/barotrauma/textures/item/diving_suit.png
  src/main/resources/assets/barotrauma/textures/item/deep_permit.png

纯几何绘制，不依赖任何美术资源；换正式美术时直接替换同名 png 即可。
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "barotrauma" / "textures" / "item"

SUIT = (214, 106, 32, 255)      # 潜水服橙
SUIT_DARK = (150, 74, 33, 255)  # 阴影/束带
VISOR = (40, 60, 80, 255)       # 面罩蓝黑
OUTLINE = (28, 32, 36, 255)
PAPER = (232, 226, 200, 255)    # 许可证纸面
BAND = (60, 96, 140, 255)       # 许可证蓝带
STAMP = (178, 48, 48, 255)      # 许可证红章


def diving_suit() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 胸甲轮廓（左右两片 + 肩带）
    d.rectangle([4, 3, 11, 13], fill=SUIT, outline=OUTLINE)
    d.rectangle([3, 4, 4, 12], fill=SUIT_DARK)
    d.rectangle([11, 4, 12, 12], fill=SUIT_DARK)
    d.rectangle([7, 3, 8, 13], fill=SUIT_DARK)      # 中缝拉链
    d.rectangle([5, 5, 6, 7], fill=VISOR)           # 胸前仪表
    d.rectangle([9, 5, 10, 7], fill=VISOR)
    d.rectangle([4, 3, 11, 4], fill=SUIT_DARK)      # 领口
    return img


def deep_permit() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([3, 2, 12, 13], fill=PAPER, outline=OUTLINE)
    d.rectangle([3, 2, 12, 5], fill=BAND)           # 顶部蓝带
    d.rectangle([5, 7, 10, 8], fill=(90, 96, 104, 255))   # 文字行
    d.rectangle([5, 10, 9, 11], fill=(90, 96, 104, 255))
    d.rectangle([10, 9, 12, 12], outline=STAMP)     # 红章
    return img


def main() -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    diving_suit().save(ASSETS / "diving_suit.png")
    deep_permit().save(ASSETS / "deep_permit.png")
    print("written:", ASSETS / "diving_suit.png", ASSETS / "deep_permit.png")


if __name__ == "__main__":
    main()
