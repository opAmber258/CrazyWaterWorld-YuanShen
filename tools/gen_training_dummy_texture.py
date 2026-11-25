"""生成训练人偶/训练气球实体贴图（64×64，RGBA）。

人偶模型 `TrainingDummyModel` 用的是原版 box UV，本脚本按同一套 texOffs 分区绘制：
头 0..32/0..16、身体 0..24/16..32、左右臂 24..40/40..56/16..32、
立柱 0..8/32..46、底座 8..40/32..42。改模型 texOffs 时必须同步改这里。

气球模型 `BalloonDummyModel` 的分区：中层 0..40/0..16、顶/底 40..64/0..18、
结 0..8/16..20、绳 8..12/16..26。

两套调色板复用同一套画法：
- `training_dummy.png` / `balloon_dummy.png`：稻草/麻布/木头/铁、白色气球。
- `advanced_training_dummy.png` / `advanced_balloon_dummy.png`：
  深灰装甲/黄铜箍/红靶心/发光红眼、深灰气球。

只用标准库（zlib）写 PNG，不依赖 Pillow，重跑结果一致（固定随机种子）。
"""
from dataclasses import dataclass
from pathlib import Path
import random
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/barotrauma/textures/entity"
BASIC_OUT = OUT / "training_dummy.png"
ADVANCED_OUT = OUT / "advanced_training_dummy.png"
BALLOON_OUT = OUT / "balloon_dummy.png"
ADVANCED_BALLOON_OUT = OUT / "advanced_balloon_dummy.png"


@dataclass(frozen=True)
class Palette:
    straw: tuple
    straw_dark: tuple
    straw_light: tuple
    straw_shadow: tuple
    wood: tuple
    wood_dark: tuple
    wood_deep: tuple
    steel: tuple
    steel_dark: tuple
    steel_light: tuple
    red: tuple
    red_dark: tuple
    cloth: tuple
    rope: tuple
    thread: tuple
    eye: tuple
    balloon: tuple
    balloon_dark: tuple
    balloon_light: tuple


BASIC = Palette(
    straw=(203, 169, 112),
    straw_dark=(166, 132, 80),
    straw_light=(226, 197, 120),
    straw_shadow=(146, 113, 66),
    wood=(168, 132, 82),
    wood_dark=(126, 98, 55),
    wood_deep=(96, 72, 38),
    steel=(78, 83, 90),
    steel_dark=(47, 51, 56),
    steel_light=(108, 116, 124),
    red=(176, 48, 40),
    red_dark=(128, 32, 28),
    cloth=(238, 231, 210),
    rope=(138, 106, 60),
    thread=(74, 50, 30),
    eye=(74, 50, 30),
    balloon=(238, 231, 210),
    balloon_dark=(204, 196, 174),
    balloon_light=(248, 242, 224),
)

ADVANCED = Palette(
    straw=(96, 100, 108),
    straw_dark=(66, 70, 78),
    straw_light=(128, 134, 144),
    straw_shadow=(46, 50, 58),
    wood=(78, 82, 90),
    wood_dark=(56, 60, 68),
    wood_deep=(38, 42, 50),
    steel=(172, 138, 66),
    steel_dark=(104, 80, 36),
    steel_light=(208, 174, 98),
    red=(202, 46, 38),
    red_dark=(118, 24, 20),
    cloth=(232, 226, 208),
    rope=(172, 140, 72),
    thread=(26, 26, 30),
    eye=(240, 76, 56),
    balloon=(96, 100, 108),
    balloon_dark=(66, 70, 78),
    balloon_light=(128, 134, 144),
)


def shade(color, delta):
    return tuple(max(0, min(255, channel + delta)) for channel in color)


class Canvas:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.px = [[(0, 0, 0, 0)] * width for _ in range(height)]

    def put(self, x, y, color):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.px[y][x] = (color[0], color[1], color[2], 255)

    def rect(self, x0, y0, x1, y1, color, *, noise=0, rnd=None):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                delta = rnd.randint(-noise, noise) if noise and rnd else 0
                self.put(x, y, shade(color, delta))

    def hline(self, x0, x1, y, color):
        for x in range(x0, x1 + 1):
            self.put(x, y, color)

    def vline(self, x, y0, y1, color):
        for y in range(y0, y1 + 1):
            self.put(x, y, color)

    def border(self, x0, y0, x1, y1, color):
        self.hline(x0, x1, y0, color)
        self.hline(x0, x1, y1, color)
        self.vline(x0, y0, y1, color)
        self.vline(x1, y0, y1, color)

    def disc(self, cx, cy, radius, color):
        """画一个实心像素圆（像素中心在整数坐标）。"""
        for y in range(int(cy - radius) - 1, int(cy + radius) + 2):
            for x in range(int(cx - radius) - 1, int(cx + radius) + 2):
                if ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 <= radius:
                    self.put(x, y, color)


def weave(canvas, x0, y0, x1, y1, base, rnd):
    """麻布/稻草的横竖交织：隔行压暗，再随机加几根亮纤维。"""
    canvas.rect(x0, y0, x1, y1, base, noise=3, rnd=rnd)
    for y in range(y0, y1 + 1, 2):
        for x in range(x0, x1 + 1):
            canvas.put(x, y, shade(base, -7))
    for _ in range((x1 - x0 + 1) * (y1 - y0 + 1) // 8):
        x = rnd.randint(x0, x1)
        y = rnd.randint(y0, y1)
        canvas.put(x, y, shade(base, 8))


def stitch_line(canvas, x0, x1, y, color, step=2):
    for x in range(x0, x1 + 1, step):
        canvas.put(x, y, color)


def draw_head(canvas, p: Palette, rnd):
    # 头顶/底面条：v=0..7；上下面各 8×8，左右两块在 atlas 里是空的
    canvas.rect(0, 0, 31, 7, p.straw, noise=3, rnd=rnd)
    # 顶面：布料收口，从中心向外压几道褶
    canvas.rect(8, 0, 15, 7, p.straw_light, noise=3, rnd=rnd)
    canvas.put(11, 0, p.cloth)
    canvas.put(12, 0, p.cloth)
    for x, y in ((8, 7), (9, 6), (10, 5), (11, 4), (12, 4), (13, 5), (14, 6), (15, 7)):
        canvas.put(x, y, p.straw_shadow)
    # 底面（脖子开口）：更暗，带一圈缝线
    canvas.rect(16, 0, 23, 7, p.straw_dark, noise=3, rnd=rnd)
    canvas.border(16, 0, 23, 7, p.straw_shadow)
    stitch_line(canvas, 17, 22, 6, p.thread)

    # 四个侧面：v=8..15，每列 8 宽
    for column in (0, 16):
        weave(canvas, column, 8, column + 7, 15, p.straw_light, rnd)
        canvas.vline(column, 8, 15, p.straw_shadow)
        canvas.vline(column + 7, 8, 15, p.straw_shadow)
    # 背面：一条竖缝 + 一块补丁
    weave(canvas, 24, 8, 31, 15, p.straw_light, rnd)
    canvas.vline(27, 8, 15, p.straw_shadow)
    canvas.vline(28, 8, 15, p.straw_shadow)
    canvas.rect(25, 10, 30, 13, p.straw_dark, noise=2, rnd=rnd)
    for x in range(25, 31, 2):
        canvas.put(x, 10, p.thread)
        canvas.put(x, 13, p.thread)

    # 正面：麻布头，X 眼 + 缝线嘴；高级版眼睛用发光红
    weave(canvas, 8, 8, 15, 15, p.straw_light, rnd)
    canvas.vline(8, 8, 15, p.straw_shadow)
    canvas.vline(15, 8, 15, p.straw_shadow)
    for ex in (9, 13):
        canvas.put(ex, 10, p.eye)
        canvas.put(ex + 1, 11, p.eye)
        canvas.put(ex + 1, 10, p.eye)
        canvas.put(ex, 11, p.eye)
    # 鼻子：一针短竖线
    canvas.put(12, 12, p.thread)
    canvas.put(12, 13, p.thread)
    # 嘴：虚线
    stitch_line(canvas, 10, 13, 14, p.thread)
    # 脸颊阴影
    for y in range(9, 15):
        canvas.put(9, y, shade(p.straw_light, -6))
        canvas.put(14, y, shade(p.straw_light, -6))


def draw_body(canvas, p: Palette, rnd):
    # 顶/底面条：v=16..19
    canvas.rect(0, 16, 23, 19, p.straw, noise=3, rnd=rnd)
    canvas.rect(4, 16, 11, 19, p.straw_light, noise=3, rnd=rnd)
    canvas.rect(12, 16, 19, 19, p.straw_dark, noise=3, rnd=rnd)
    # 顶面绳结十字
    canvas.hline(6, 9, 17, p.rope)
    canvas.vline(7, 16, 19, p.rope)

    # 侧面 v=20..31：前后各 8 宽，左右各 4 宽
    # 先铺整块稻草，再画竖向草茎
    weave(canvas, 0, 20, 23, 31, p.straw, rnd)
    for x in range(0, 24, 2):
        canvas.vline(x, 20, 31, shade(p.straw, -9))
    # 左右侧面（草捆截面）：短横纹
    for x0 in (0, 12):
        for y in range(20, 32, 3):
            canvas.hline(x0, x0 + 3, y, shade(p.straw_dark, -6))
    # 前后：肩部/腰部两道绳（避开靶心所在的 22..29 行）
    for y, color in ((20, p.rope), (21, shade(p.rope, -14)),
                     (30, p.rope), (31, shade(p.rope, -14))):
        canvas.hline(0, 23, y, color)
    for x in (2, 10, 18):
        canvas.put(x, 20, shade(p.rope, 14))
        canvas.put(x, 31, shade(p.rope, 14))
    # 背面补丁
    canvas.rect(17, 24, 21, 27, p.straw_dark, noise=2, rnd=rnd)
    for x in range(17, 22, 2):
        canvas.put(x, 24, p.thread)
        canvas.put(x, 27, p.thread)

    # 正面靶心：严格限制在正面 8 宽（x=4..11）、绳带之间（y=22..29）
    canvas.disc(7.5, 25.5, 3.6, p.red_dark)
    canvas.disc(7.5, 25.5, 3.05, p.red)
    canvas.disc(7.5, 25.5, 2.0, p.cloth)
    canvas.disc(7.5, 25.5, 1.0, p.red)
    # 靶纸磨损：露出底色
    canvas.put(5, 24, p.straw)
    canvas.put(5, 25, p.straw)
    canvas.put(6, 28, p.straw)


def draw_arm(canvas, p: Palette, x0, rnd):
    # 木臂：texOffs 24/40，宽 16，高 16；上下面条 v=16..19
    canvas.rect(x0, 16, x0 + 15, 19, p.wood, noise=4, rnd=rnd)
    canvas.rect(x0, 20, x0 + 15, 31, p.wood, noise=4, rnd=rnd)
    # 木纹：每列随机竖线
    for x in range(x0, x0 + 16):
        if rnd.random() < 0.45:
            canvas.vline(x, 20, 31, shade(p.wood, -12))
    # 四个侧面的边缘压暗
    for offset in (0, 3, 4, 7, 8, 11, 12, 15):
        canvas.vline(x0 + offset, 20, 31, p.wood_dark)
    # 两道绳捆
    for y, color in ((23, p.rope), (24, shade(p.rope, -14)),
                     (28, p.rope), (29, shade(p.rope, -14))):
        canvas.hline(x0, x0 + 15, y, color)
    # 肩部铁钉
    canvas.put(x0 + 1, 20, p.steel_dark)
    canvas.put(x0 + 14, 20, p.steel_dark)


def draw_pole(canvas, p: Palette, rnd):
    # 立柱：texOffs 0,32，2×12×2 → 宽 8、高 14
    canvas.rect(0, 32, 7, 45, p.wood_dark, noise=4, rnd=rnd)
    for x in range(0, 8):
        if rnd.random() < 0.5:
            canvas.vline(x, 34, 45, p.wood_deep)
    # 顶部接口
    canvas.rect(2, 32, 5, 33, p.steel, noise=3, rnd=rnd)
    canvas.border(2, 32, 5, 33, p.steel_dark)
    # 两道铁箍
    for y in (36, 42):
        canvas.hline(0, 7, y, p.steel)
        canvas.hline(0, 7, y + 1, p.steel_dark)


def draw_base(canvas, p: Palette, rnd):
    # 底座：texOffs 8,32，8×2×8 → 宽 32、高 10
    canvas.rect(8, 32, 39, 41, p.steel, noise=5, rnd=rnd)
    # 上/下面条 v=32..39：上表面 x=16..23，下表面 x=24..31
    canvas.rect(16, 32, 23, 39, p.steel_light, noise=5, rnd=rnd)
    canvas.border(16, 32, 23, 39, p.steel_dark)
    for x, y in ((17, 33), (22, 33), (17, 38), (22, 38)):
        canvas.rect(x, y, x + 1, y + 1, p.steel_dark)
    canvas.disc(19.5, 35.5, 2.6, p.steel_dark)
    canvas.disc(19.5, 35.5, 1.6, p.red)
    canvas.disc(19.5, 35.5, 0.6, p.red_dark)
    # 下表面：交叉加强筋
    canvas.rect(24, 32, 31, 39, p.steel_dark, noise=4, rnd=rnd)
    canvas.hline(24, 31, 35, p.steel)
    canvas.vline(27, 32, 39, p.steel)

    # 四个侧面 v=40..41：红白警示条
    for x0 in (8, 16, 24, 32):
        for x in range(x0, x0 + 8):
            color = p.red if ((x - x0) // 2) % 2 == 0 else p.cloth
            canvas.put(x, 40, color)
            canvas.put(x, 41, shade(color, -28))
        canvas.vline(x0, 40, 41, p.steel_dark)
        canvas.vline(x0 + 7, 40, 41, p.steel_dark)


def build(p: Palette) -> Canvas:
    canvas = Canvas(64, 64)
    draw_head(canvas, p, random.Random(9101))
    draw_body(canvas, p, random.Random(9102))
    draw_arm(canvas, p, 24, random.Random(9103))
    draw_arm(canvas, p, 40, random.Random(9104))
    draw_pole(canvas, p, random.Random(9105))
    draw_base(canvas, p, random.Random(9106))
    return canvas


def encode_png(canvas: Canvas) -> bytes:
    raw = bytearray()
    for row in canvas.px:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", canvas.width, canvas.height, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def emit(path: Path, palette: Palette):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encode_png(build(palette)))
    print(f"wrote {path.relative_to(ROOT)} ({path.stat().st_size} bytes)")


def draw_balloon(canvas, p: Palette, rnd):
    # 中层 10×6×10 @ (0,0)：四个侧面各画一个靶心
    canvas.rect(0, 0, 39, 15, p.balloon, noise=3, rnd=rnd)
    canvas.rect(10, 0, 19, 9, p.balloon_light, noise=3, rnd=rnd)   # 上表面
    canvas.rect(20, 0, 29, 9, p.balloon_dark, noise=3, rnd=rnd)    # 下表面
    for x0 in (0, 10, 20, 30):
        for x in (x0 + 2, x0 + 5, x0 + 8):
            canvas.vline(x, 10, 15, p.balloon_dark)               # 气球分瓣缝
        canvas.disc(x0 + 4.5, 12.5, 2.6, p.red_dark)
        canvas.disc(x0 + 4.5, 12.5, 2.1, p.red)
        canvas.disc(x0 + 4.5, 12.5, 1.4, p.cloth)

    # 顶/底两层 6×3×6 @ (40,0) 与 (40,9)
    for vy in (0, 9):
        canvas.rect(40, vy, 63, vy + 8, p.balloon, noise=3, rnd=rnd)
        canvas.rect(46, vy, 51, vy + 5, p.balloon_light, noise=3, rnd=rnd)
        canvas.rect(52, vy, 57, vy + 5, p.balloon_dark, noise=3, rnd=rnd)
        for x0 in (40, 46, 52, 58):
            canvas.vline(x0, vy + 6, vy + 8, p.balloon_dark)
            canvas.vline(x0 + 5, vy + 6, vy + 8, p.balloon_dark)

    # 气球结 2×2×2 @ (0,16)
    canvas.rect(0, 16, 7, 19, p.balloon_dark, noise=2, rnd=rnd)
    canvas.hline(0, 7, 18, p.rope)
    # 绳子 1×9×1 @ (8,16)
    canvas.rect(8, 16, 11, 25, p.rope, noise=2, rnd=rnd)
    canvas.vline(8, 16, 25, p.thread)
    canvas.vline(11, 16, 25, p.thread)


def build_balloon(p: Palette) -> Canvas:
    canvas = Canvas(64, 64)
    draw_balloon(canvas, p, random.Random(9201))
    return canvas


def emit_balloon(path: Path, palette: Palette):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encode_png(build_balloon(palette)))
    print(f"wrote {path.relative_to(ROOT)} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    emit(BASIC_OUT, BASIC)
    emit(ADVANCED_OUT, ADVANCED)
    emit_balloon(BALLOON_OUT, BASIC)
    emit_balloon(ADVANCED_BALLOON_OUT, ADVANCED)
