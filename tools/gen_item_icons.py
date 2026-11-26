"""生成 16x16 物品图标（RGBA，纯标准库 zlib 写 PNG）。

输出三张贴图：
- textures/item/brightness_debugger.png       开发者物品「深海亮度调试器」
- textures/item/station_marker_dummy.png      训练设施牌匾：人偶剪影
- textures/item/station_marker_balloon.png    训练设施牌匾：气球 + 短线

用法（仓库根目录）：
    python -I tools/gen_item_icons.py

像素全部写死，无随机数、无 Pillow 依赖；重跑结果逐字节一致。
"""

from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/barotrauma/textures/item"
SIZE = 16

# --- 深海亮度调试器调色板 -------------------------------------------------
OUTLINE = (16, 18, 22, 255)       # 黑石色描边
STEEL = (52, 58, 64, 255)         # 机身金属
STEEL_LIGHT = (90, 98, 106, 255)  # 表圈/铆钉高光
STEEL_DARK = (34, 38, 44, 255)    # 握把/底座阴影
AMBER_DIM = (168, 78, 28, 255)    # 琥珀外环（暗）
AMBER = (224, 118, 47, 255)       # #E0762F，模组强调色
AMBER_HOT = (250, 190, 100, 255)  # 透镜亮心
LENS_HILITE = (255, 236, 190, 255)

# --- 牌匾调色板（不透明石板底） ------------------------------------------
SLAB = (28, 32, 38, 255)          # 整张贴图底色
PANEL = (42, 48, 56, 255)         # 内嵌石板
RIM = (104, 110, 118, 255)        # 外细边框
INSET = (18, 20, 24, 255)         # 内细边框（凹陷感）
ICON = (238, 231, 212, 255)       # 中央米白图标
ICON_SHADE = (196, 188, 168, 255)  # 图标次要像素（气球高光/绳结阴影）
ICON_HILITE = (255, 250, 235, 255)


class Canvas:
    """固定大小 RGBA 像素画布，提供绘制小图标需要的几种图元。"""

    def __init__(self, size=SIZE, bg=(0, 0, 0, 0)):
        self.size = size
        self.px = [[bg for _ in range(size)] for _ in range(size)]

    def put(self, x, y, color):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[y][x] = color

    def fill(self, color):
        for y in range(self.size):
            for x in range(self.size):
                self.put(x, y, color)

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, color)

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
        """实心像素圆；用半整数圆心保证 16px 网格左右对称。"""
        for y in range(self.size):
            for x in range(self.size):
                if (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius:
                    self.put(x, y, color)


def encode_png(canvas):
    """与 tools/gen_training_dummy_texture.py 相同的 PNG 编码写法。"""
    raw = bytearray()
    for row in canvas.px:
        raw.append(0)  # filter type 0 (None)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", canvas.size, canvas.size, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def draw_brightness_debugger():
    """深海亮度调试器：透明底，深色仪器外壳 + 琥珀发光圆窗 + 亮度刻度。

    分区（16x16）：
      x=2..13, y=1..14  手持机身（描边 + 金属壳 + 两侧握把）
      x=3..12, y=2..13  表盘区域；圆心 (7.5, 6.0) 的 4 层同心圆是发光观察窗
      x=4..11, y=11..12 底部亮度刻度条（三格由暗到亮 + 一个旋钮）
    """
    c = Canvas()
    # 机身：黑石色描边包住深灰金属壳
    c.rect(2, 1, 13, 14, OUTLINE)
    c.rect(3, 2, 12, 13, STEEL)
    c.hline(3, 12, 2, STEEL_LIGHT)   # 顶沿卡边
    c.hline(3, 12, 13, STEEL_DARK)   # 底沿阴影
    c.vline(3, 4, 11, STEEL_DARK)    # 左握把
    c.vline(12, 4, 11, STEEL_DARK)   # 右握把
    c.put(3, 3, STEEL_LIGHT)         # 两颗装配铆钉
    c.put(12, 3, STEEL_LIGHT)

    # 琥珀圆窗：表圈 → 内圈阴影 → 暗琥珀 → 强调色 → 亮心 → 偏心高光
    c.disc(7.5, 6.0, 4.2, STEEL_LIGHT)
    c.disc(7.5, 6.0, 3.6, OUTLINE)
    c.disc(7.5, 6.0, 3.1, AMBER_DIM)
    c.disc(7.5, 6.0, 2.5, AMBER)
    c.disc(7.5, 6.0, 1.4, AMBER_HOT)
    c.put(6, 5, LENS_HILITE)
    c.put(7, 4, LENS_HILITE)

    # 亮度刻度：三格由暗到亮，右端一个凸起旋钮（与医疗调试器的红十字屏区分）
    c.rect(4, 11, 11, 12, OUTLINE)
    c.rect(5, 11, 10, 12, STEEL_DARK)
    for x, color in ((5, AMBER_DIM), (7, AMBER), (9, AMBER_HOT)):
        c.vline(x, 11, 12, color)
    c.put(11, 11, STEEL_LIGHT)
    return c


def draw_marker_base():
    """两种牌匾共用的不透明底：深色石板 + 双层细边框，内部 12x12 留给图标。"""
    c = Canvas(bg=SLAB)
    c.fill(SLAB)
    c.rect(1, 1, 14, 14, PANEL)
    c.border(0, 0, 15, 15, RIM)
    c.border(1, 1, 14, 14, INSET)
    return c


def draw_marker_dummy():
    """人偶牌匾：中央米白剪影 = 方头 + 躯干 + 平举双臂 + 两条短腿。

    分区：头 y=3..5 (x=7..9)；躯干 y=6..10 (x=7..9)；
    双臂 y=7..8 (x=4..11)；短腿 y=11..13 (x=7 与 x=9)。
    """
    c = draw_marker_base()
    c.rect(7, 3, 9, 5, ICON)     # 头
    c.rect(7, 6, 9, 10, ICON)    # 躯干
    c.hline(4, 11, 7, ICON)      # 两臂（一整条横杆）
    c.hline(4, 11, 8, ICON)
    c.rect(7, 11, 7, 13, ICON)   # 左腿
    c.rect(9, 11, 9, 13, ICON)   # 右腿
    return c


def draw_marker_balloon():
    """气球牌匾：中央米白圆形气球 + 收口小结 + 一条略弯的短线。

    分区：球体以 (7.5, 5.5) 为心、半径 3.2；结在 y=9 (x=7..8)；
    绳 y=10..13，末端向右错一格表示飘动，与人偶的 T 形剪影明显不同。
    """
    c = draw_marker_base()
    c.disc(7.5, 5.5, 3.2, ICON)
    c.rect(7, 9, 8, 9, ICON)              # 气球结
    c.put(7, 9, ICON_SHADE)               # 结的暗面
    c.vline(8, 10, 12, ICON)              # 短绳
    c.put(9, 13, ICON)
    c.put(6, 4, ICON_HILITE)              # 球体左上高光
    c.put(6, 5, ICON_HILITE)
    c.put(7, 4, ICON)
    return c


def emit(name, canvas):
    path = OUT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encode_png(canvas))
    print(f"wrote {path.relative_to(ROOT)} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    emit("brightness_debugger.png", draw_brightness_debugger())
    emit("station_marker_dummy.png", draw_marker_dummy())
    emit("station_marker_balloon.png", draw_marker_balloon())
