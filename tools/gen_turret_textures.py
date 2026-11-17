"""生成炮塔原型的两张 16×16 贴图（底座 / 炮管）。

贴图是纯程序化金属噪点，和仓库里其他占位资产一样不依赖外部素材；
重跑会得到完全一致的结果（固定随机种子）。
"""
from pathlib import Path
import random

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/barotrauma/textures/block"
PARTICLE_OUT = ROOT / "src/main/resources/assets/barotrauma/textures/particle"


def clamp(value: int) -> int:
    return max(0, min(255, value))


def noisy_metal(base, spread, seed, *, border, shadow_bottom, highlight_top):
    rnd = random.Random(seed)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = image.load()
    for y in range(16):
        for x in range(16):
            shade = rnd.randint(-spread, spread)
            if y >= shadow_bottom:
                shade -= 10
            if y <= highlight_top:
                shade += 8
            pixels[x, y] = (clamp(base[0] + shade), clamp(base[1] + shade),
                            clamp(base[2] + shade), 255)
    for i in range(16):
        pixels[i, 0] = border
        pixels[i, 15] = border
        pixels[0, i] = border
        pixels[15, i] = border
    return image


def rivet(pixels, x, y, color):
    for dx in range(2):
        for dy in range(2):
            pixels[x + dx, y + dy] = color


def turret_base():
    image = noisy_metal((86, 94, 102), 7, 6703,
                        border=(58, 64, 70), shadow_bottom=13, highlight_top=2)
    pixels = image.load()
    # 底座面板接缝
    for x in range(2, 14):
        pixels[x, 7] = (62, 68, 74, 255)
        pixels[x, 8] = (104, 112, 120, 255)
    # 四角铆钉
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(pixels, x, y, (44, 49, 54, 255))
    # 底座顶面警示色小块（放在贴图中心，模型大多数面能看到一点工业感）
    pixels[7, 3] = (186, 152, 58, 255)
    pixels[8, 3] = (186, 152, 58, 255)
    pixels[7, 4] = (40, 42, 46, 255)
    pixels[8, 4] = (40, 42, 46, 255)
    image.save(OUT / "turret_base.png")


def turret_barrel():
    image = noisy_metal((58, 64, 70), 6, 6700,
                        border=(38, 42, 46), shadow_bottom=12, highlight_top=3)
    pixels = image.load()
    # 炮管侧面的高光沟槽
    for x in range(2, 14):
        pixels[x, 6] = (92, 100, 108, 255)
        pixels[x, 7] = (34, 38, 42, 255)
    # 炮口一圈深色
    for x in range(4, 12):
        pixels[x, 1] = (24, 27, 30, 255)
        pixels[x, 14] = (24, 27, 30, 255)
    image.save(OUT / "turret_barrel.png")


def turret_base_freeaim():
    """自由瞄准炮塔的底座：琥珀色警示涂装 + 青绿色传感器指示块。"""
    image = noisy_metal((112, 88, 56), 7, 6711,
                        border=(74, 58, 38), shadow_bottom=13, highlight_top=2)
    pixels = image.load()
    # 底座面板接缝
    for x in range(2, 14):
        pixels[x, 7] = (78, 60, 38, 255)
        pixels[x, 8] = (140, 112, 74, 255)
    # 四角铆钉
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(pixels, x, y, (56, 44, 30, 255))
    # 顶面传感器指示块
    pixels[7, 3] = (86, 196, 176, 255)
    pixels[8, 3] = (86, 196, 176, 255)
    pixels[7, 4] = (38, 42, 40, 255)
    pixels[8, 4] = (38, 42, 40, 255)
    image.save(OUT / "turret_base_freeaim.png")


def turret_base_tracking():
    """跟随炮塔的底座：钢蓝色涂装 + 红色传感器指示块。"""
    image = noisy_metal((64, 82, 112), 7, 6717,
                        border=(42, 54, 76), shadow_bottom=13, highlight_top=2)
    pixels = image.load()
    # 底座面板接缝
    for x in range(2, 14):
        pixels[x, 7] = (44, 58, 80, 255)
        pixels[x, 8] = (86, 106, 140, 255)
    # 四角铆钉
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(pixels, x, y, (34, 44, 62, 255))
    # 顶面传感器指示块
    pixels[7, 3] = (214, 90, 62, 255)
    pixels[8, 3] = (214, 90, 62, 255)
    pixels[7, 4] = (36, 40, 48, 255)
    pixels[8, 4] = (36, 40, 48, 255)
    image.save(OUT / "turret_base_tracking.png")


def turret_tracer():
    """开火粒子束的单点贴图（8×8）：中心一颗白点 + 两层透明光晕。

    粒子自身 quadSize 很小（见 TurretTracerParticle），这张贴图只负责让点有
    一点柔边、不要糊成方块。
    """
    image = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    pixels = image.load()
    for y in range(8):
        for x in range(8):
            distance = ((x - 3.5) ** 2 + (y - 3.5) ** 2) ** 0.5
            if distance <= 0.8:
                alpha = 255
            elif distance <= 1.6:
                alpha = 170
            elif distance <= 2.6:
                alpha = 90
            elif distance <= 3.2:
                alpha = 40
            else:
                alpha = 0
            pixels[x, y] = (222, 242, 255, alpha)
    image.save(PARTICLE_OUT / "turret_tracer.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    PARTICLE_OUT.mkdir(parents=True, exist_ok=True)
    turret_base()
    turret_barrel()
    turret_base_freeaim()
    turret_base_tracking()
    turret_tracer()
    print("wrote", OUT / "turret_base.png")
    print("wrote", OUT / "turret_barrel.png")
    print("wrote", OUT / "turret_base_freeaim.png")
    print("wrote", OUT / "turret_base_tracking.png")
    print("wrote", PARTICLE_OUT / "turret_tracer.png")
