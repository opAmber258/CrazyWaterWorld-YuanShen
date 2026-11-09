"""生成克苏鲁注视 6 个预设的状态效果图标（18x18，与原版 mob_effect 一致）。

用法：python tools/gen_cthulhu_effect_icons.py
输出：src/main/resources/assets/barotrauma/textures/mob_effect/cthulhu_*.png

统一风格：暗紫圆底 + 苍白眼白 + 品红虹膜，六张只在“眼睛的异常”上做区分，
保证一眼能看出是同一套（同一个触发器/同一个眼睛母题）。
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "barotrauma" / "textures" / "mob_effect"

SHELL = (58, 34, 78, 255)
SHELL_DARK = (30, 16, 44, 255)
RIM = (108, 66, 138, 255)
SCLERA = (228, 216, 242, 255)
IRIS = (176, 58, 160, 255)
PUPIL = (18, 10, 24, 255)
WHITE = (250, 246, 255, 255)

EYE = [(3, 9), (6, 6), (11, 6), (14, 9), (11, 12), (6, 12)]


def base() -> Image.Image:
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 外壳 + 内圈
    d.ellipse([1, 1, 16, 16], fill=SHELL, outline=SHELL_DARK)
    d.ellipse([2, 2, 15, 15], outline=RIM)
    # 眼白 + 虹膜 + 瞳孔
    d.polygon(EYE, fill=SCLERA)
    d.ellipse([7, 7, 11, 11], fill=IRIS)
    d.ellipse([8, 8, 10, 10], fill=PUPIL)
    return img


def fisheye() -> Image.Image:
    img = base()
    d = ImageDraw.Draw(img)
    accent = (96, 200, 220, 255)
    # 鼓胀的虹膜 + 高光
    d.ellipse([6, 6, 12, 12], outline=accent)
    d.ellipse([7, 7, 11, 11], fill=IRIS)
    d.ellipse([8, 8, 10, 10], fill=PUPIL)
    d.point((7, 7), fill=WHITE)
    d.point((12, 12), fill=accent)
    return img


def whisper() -> Image.Image:
    img = base()
    d = ImageDraw.Draw(img)
    accent = (96, 200, 140, 255)
    # 两侧声波（低语）
    for x, y in ((1, 7), (2, 8), (1, 10), (16, 7), (15, 8), (16, 10)):
        d.point((x, y), fill=accent)
    d.line([(0, 9), (1, 9)], fill=accent)
    d.line([(16, 9), (17, 9)], fill=accent)
    # 虹膜下方的小波纹
    d.point((8, 12), fill=accent)
    d.point((10, 12), fill=accent)
    return img


def colour() -> Image.Image:
    img = base()
    d = ImageDraw.Draw(img)
    # 虹膜三色条纹
    d.rectangle([7, 7, 8, 11], fill=(226, 96, 210, 255))
    d.rectangle([9, 7, 10, 11], fill=(110, 210, 130, 255))
    d.rectangle([11, 7, 11, 11], fill=(96, 190, 226, 255))
    d.ellipse([8, 8, 10, 10], fill=PUPIL)
    # 星屑
    for x, y in ((3, 4), (14, 4), (4, 14), (13, 14)):
        d.point((x, y), fill=(240, 230, 255, 255))
    return img


def ripple() -> Image.Image:
    img = base()
    d = ImageDraw.Draw(img)
    accent = (110, 150, 230, 255)
    # 同心涟漪
    d.ellipse([6, 6, 12, 12], outline=accent)
    d.ellipse([4, 4, 14, 14], outline=accent)
    d.ellipse([2, 2, 16, 16], outline=accent)
    # 虹膜/瞳孔盖回中心
    d.ellipse([7, 7, 11, 11], fill=IRIS)
    d.ellipse([8, 8, 10, 10], fill=PUPIL)
    return img


def static() -> Image.Image:
    img = base()
    d = ImageDraw.Draw(img)
    # 扫描线（整张图标都要，效果才像“屏幕坏了”）
    for y in range(2, 16, 3):
        d.line([(2, y), (15, y)], fill=(30, 30, 40, 140))
    # 雪花点
    for x, y in ((3, 3), (5, 13), (12, 3), (14, 12), (9, 2), (4, 9), (13, 9)):
        d.point((x, y), fill=(235, 235, 245, 255))
    # 撕裂：一条错位的白线
    d.line([(2, 11), (6, 11)], fill=(245, 245, 255, 255))
    d.line([(8, 11), (15, 11)], fill=(120, 180, 220, 255))
    return img


def dream() -> Image.Image:
    img = base()
    d = ImageDraw.Draw(img)
    accent = (192, 152, 232, 255)
    # 螺旋/光晕：两圈不完整的弧
    d.arc([5, 5, 13, 13], 200, 340, fill=accent)
    d.arc([3, 3, 15, 15], 20, 160, fill=accent)
    # 眼皮半合（上眼睑）
    d.polygon([(3, 9), (6, 6), (11, 6), (14, 9), (11, 8), (6, 8)], fill=SHELL)
    # 剩余可见的虹膜
    d.ellipse([7, 8, 11, 11], fill=IRIS)
    d.ellipse([8, 9, 10, 10], fill=PUPIL)
    d.point((7, 8), fill=WHITE)
    return img


def flat() -> Image.Image:
    img = base()
    d = ImageDraw.Draw(img)
    accent = (120, 196, 204, 255)
    # 虹膜压成方形（正交/网格感），用直边代替圆弧
    d.rectangle([7, 7, 11, 11], fill=IRIS)
    d.rectangle([8, 8, 10, 10], fill=PUPIL)
    # 上下两条压平线：透视被摊在平面上
    d.line([(4, 5), (13, 5)], fill=accent)
    d.line([(4, 13), (13, 13)], fill=accent)
    d.point((5, 9), fill=accent)
    d.point((12, 9), fill=accent)
    return img


def kaleido() -> Image.Image:
    img = base()
    d = ImageDraw.Draw(img)
    accent = (150, 110, 230, 255)
    accent2 = (96, 200, 220, 255)
    # 六瓣/菱形镜像：两圈对称的菱形轮廓
    d.polygon([(9, 1), (13, 9), (9, 17), (5, 9)], outline=accent)
    d.polygon([(9, 4), (11, 9), (9, 14), (7, 9)], outline=accent2)
    d.ellipse([7, 7, 11, 11], fill=IRIS)
    d.ellipse([8, 8, 10, 10], fill=PUPIL)
    d.point((12, 9), fill=accent)
    d.point((6, 9), fill=accent)
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    icons = {
        "cthulhu_fisheye": fisheye(),
        "cthulhu_whisper": whisper(),
        "cthulhu_colour": colour(),
        "cthulhu_ripple": ripple(),
        "cthulhu_static": static(),
        "cthulhu_dream": dream(),
        "cthulhu_flat": flat(),
        "cthulhu_kaleido": kaleido(),
    }
    for name, image in icons.items():
        path = OUT / f"{name}.png"
        image.save(path)
        print(f"wrote {path}")
    # 预览拼图（方便肉眼检查统一性）
    sheet = Image.new("RGBA", (20 * len(icons), 18), (24, 18, 30, 255))
    for i, image in enumerate(icons.values()):
        sheet.paste(image, (i * 20, 0), image)
    preview = ROOT / "build" / "cthulhu_effect_icons_preview.png"
    preview.parent.mkdir(parents=True, exist_ok=True)
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(preview)
    print(f"preview {preview}")


if __name__ == "__main__":
    main()
