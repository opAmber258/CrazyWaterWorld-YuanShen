"""生成 6700X3D CPU 彩蛋方块的贴图（低细节、丝印清晰）。

用法：python tools/gen_cpu_textures.py
输出：
  assets/barotrauma/textures/block/cpu_6700x3d_substrate.png  128x64 基板（左 64x64 顶面设计）
  assets/barotrauma/textures/block/cpu_6700x3d_ihs.png      128x64 顶盖（左 64x64 顶面 + 右上 16x16 侧面）

为什么是 128x64：Minecraft 方块模型的 UV 固定在 0…16 空间，和贴图分辨率无关；
Blockbench 导出时按工程贴图尺寸归一化。两张贴图统一成 128x64，UV 才能既在
Blockbench 里正确预览、又在游戏里采样到正确的像素区域。

设计约束（用户指令）：外观简洁、面数/细节不要多，顶盖有丝印（6700X3D）。
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "barotrauma" / "textures" / "block"

PCB = (36, 82, 54, 255)
PCB_DARK = (26, 60, 40, 255)
PCB_EDGE = (20, 46, 32, 255)
GOLD = (204, 170, 84, 255)
GOLD_DARK = (150, 120, 56, 255)
METAL = (186, 190, 196, 255)
METAL_LIGHT = (206, 210, 215, 255)
METAL_DARK = (150, 156, 163, 255)
METAL_EDGE = (128, 134, 142, 255)
SILK = (58, 60, 64, 255)


def substrate() -> Image.Image:
    """128x64：左 64x64 是基板顶面（16x16 设计按 4 倍最近邻放大），右半留纯色不采样。"""
    base = Image.new("RGBA", (16, 16), PCB)
    d = ImageDraw.Draw(base)
    d.rectangle([0, 0, 15, 15], outline=PCB_EDGE)
    # 内圈走线暗示（只画四边中点，避免细节过多）
    d.rectangle([2, 2, 13, 13], outline=PCB_DARK)
    # 两条边上的金手指（3 段，简单）
    for x in (3, 7, 11):
        d.rectangle([x, 0, x + 1, 1], fill=GOLD)
        d.rectangle([x, 14, x + 1, 15], fill=GOLD)
    # 1 脚三角标记
    d.rectangle([3, 3, 4, 4], fill=GOLD_DARK)

    img = Image.new("RGBA", (128, 64), PCB_DARK)
    img.paste(base.resize((64, 64), Image.NEAREST), (0, 0))
    return img


def ihs() -> Image.Image:
    """128x64：左 64x64 是顶盖丝印面，右上 16x16 是侧面金属条。"""
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # ---- 顶盖 64x64 ----
    d.rectangle([0, 0, 63, 63], fill=METAL)
    # 倒角：外圈深色 + 内侧高光，各 1px，够简洁
    d.rectangle([0, 0, 63, 63], outline=METAL_EDGE)
    d.rectangle([1, 1, 62, 62], outline=METAL_LIGHT)
    d.rectangle([2, 2, 61, 61], outline=METAL_DARK)
    # 拉丝：每隔 8px 一条极浅横线（3 条，别太多）
    for y in (14, 32, 50):
        d.line([6, y, 57, y], fill=(196, 200, 205, 255))
    # 丝印：6700X3D（主字样，居中偏上）
    text = "6700X3D"
    bbox = d.textbbox((0, 0), text)
    tw = bbox[2] - bbox[0]
    d.text(((64 - tw) // 2, 20), text, fill=SILK)
    # 副字样：型号行 + 厂商行（小字，仍用默认位图字体；左右留出边距不贴边）
    sub = "V-CACHE"
    bbox = d.textbbox((0, 0), sub)
    d.text(((64 - (bbox[2] - bbox[0])) // 2, 36), sub, fill=SILK)
    sub2 = "DEEP LABS"
    bbox = d.textbbox((0, 0), sub2)
    d.text(((64 - (bbox[2] - bbox[0])) // 2, 46), sub2, fill=SILK)
    # 1 脚三角（左下角）
    d.polygon([(5, 59), (11, 59), (5, 53)], fill=SILK)

    # ---- 侧面金属条 16x16（放在 64,0） ----
    d.rectangle([64, 0, 79, 15], fill=METAL_DARK)
    d.line([64, 0, 79, 0], fill=METAL_LIGHT)
    d.line([64, 15, 79, 15], fill=METAL_EDGE)
    return img


def item_icon() -> Image.Image:
    """32x32 物品栏图标：极简 CPU 俯视图 + 大字丝印（方块模型在物品栏里太扁，用户反馈）。"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # PCB 基板
    d.rectangle([1, 1, 30, 30], fill=PCB, outline=PCB_EDGE)
    # 四边金手指（每边 3 段）
    for x in (4, 14, 24):
        d.rectangle([x, 0, x + 3, 1], fill=GOLD)
        d.rectangle([x, 30, x + 3, 31], fill=GOLD)
    for y in (4, 14, 24):
        d.rectangle([0, y, 1, y + 3], fill=GOLD)
        d.rectangle([30, y, 31, y + 3], fill=GOLD)
    # 顶盖
    d.rectangle([3, 3, 28, 28], fill=METAL, outline=METAL_EDGE)
    d.rectangle([4, 4, 27, 27], outline=METAL_LIGHT)
    # 大字丝印：6700 / X3D
    for text, y in (("6700", 9), ("X3D", 18)):
        bbox = d.textbbox((0, 0), text)
        d.text(((32 - (bbox[2] - bbox[0])) // 2, y), text, fill=SILK)
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, image in (("cpu_6700x3d_substrate", substrate()), ("cpu_6700x3d_ihs", ihs())):
        path = OUT / f"{name}.png"
        image.save(path)
        print(f"wrote {path}")
    item_out = ROOT / "src" / "main" / "resources" / "assets" / "barotrauma" / "textures" / "item"
    item_out.mkdir(parents=True, exist_ok=True)
    path = item_out / "cpu_6700x3d.png"
    item_icon().save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    main()
