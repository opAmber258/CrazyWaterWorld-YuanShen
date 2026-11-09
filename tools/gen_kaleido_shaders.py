"""生成「万花筒」核心顶点着色器：从原版 1.20.1 客户端 jar 提取，注入视空间六瓣折叠。

用法：
    python tools/gen_kaleido_shaders.py "C:/path/to/client.jar"

输出：
    src/main/resources/assets/minecraft/shaders/core/<name>.vsh

为什么覆盖原版路径：Minecraft 的世界几何走核心 shader，要“真实”折叠几何就必须在
顶点阶段改 gl_Position。覆盖同路径文件、用 Kaleido uniform 控制（0 = 完全等价原版），
比在渲染线程里逐顶点改数据便宜得多，也不需要改 RenderType。

注意：这是资源覆盖，游戏里所有世界几何都会经过这段代码；Kaleido=0 时必须与
原版逐字节等价（直接 return view），否则会影响其它模组的观感。
"""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "minecraft" / "shaders" / "core"

# 需要折叠的几何类别：地形、实体、粒子、掉落物。GUI/文字/天空不碰。
# 只覆盖地形与粒子。entity 系列不覆盖：GUI 物品图标和掉落物都走 entity
# RenderType，覆盖它会连背包/快捷栏一起折（2026-11-09 实机确认）。
TARGETS = [
    "rendertype_solid",
    "rendertype_cutout",
    "rendertype_cutout_mipped",
    "rendertype_translucent",
    "particle",
]

INJECT_GLSL = """
uniform float Kaleido;

// Continuous space fold: radial sine folds (non-monotonic at strong tiers) plus a
// six-lobe angular bend. No mirroring, so the mesh never cracks at sector seams.
vec4 barotrauma_kaleido(vec4 view) {
    if (Kaleido <= 0.001) {
        return view;
    }
    float r = length(view.xy);
    if (r < 0.0001) {
        return view;
    }
    float ang = atan(view.y, view.x);
    // Six-lobe angular bend: continuous in angle, so adjacent sectors join smoothly.
    float lobe = sin(ang * 6.0 + r * 0.35 + view.z * 0.04);
    float a = ang + Kaleido * 0.18 * lobe;
    // Radial fold: r stays monotonic while A < P/(2*pi); stronger tiers make it
    // fold back on itself, so distant geometry repeats at nearer radii.
    const float PERIOD = 12.0;
    float amplitude = Kaleido * 2.4;
    float radial = r + amplitude * sin(r * 6.2831853 / PERIOD - view.z * 0.03);
    radial = max(radial, 0.0);
    float scale = 1.0 + Kaleido * 0.08 * lobe;
    float z = view.z * (1.0 - 0.06 * Kaleido);
    return vec4(cos(a) * radial * scale, sin(a) * radial * scale, z, 1.0);
}

"""

GL_POSITION = re.compile(
    r"gl_Position\s*=\s*ProjMat\s*\*\s*ModelViewMat\s*\*\s*vec4\((?P<expr>[^;]+),\s*1\.0\);"
)


def patch(name: str, source: str) -> str:
    if "barotrauma_kaleido" in source:
        return source
    if "void main()" not in source:
        raise ValueError(f"{name}: 没有 void main()")
    source = source.replace("void main()", INJECT_GLSL + "void main()", 1)
    replaced, count = GL_POSITION.subn(
        lambda m: (
            f"vec4 kaleidoView = ModelViewMat * vec4({m.group('expr')}, 1.0);\n"
            f"    gl_Position = ProjMat * barotrauma_kaleido(kaleidoView);"
        ),
        source,
    )
    if count != 1:
        raise ValueError(f"{name}: 匹配到 {count} 处 gl_Position，期望 1 处")
    return replaced


def main() -> None:
    if len(sys.argv) != 2:
        print(__doc__)
        raise SystemExit(2)
    jar = Path(sys.argv[1])
    if not jar.is_file():
        raise SystemExit(f"找不到客户端 jar：{jar}")

    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar) as zf:
        for name in TARGETS:
            entry = f"assets/minecraft/shaders/core/{name}.vsh"
            try:
                raw = zf.read(entry).decode("utf-8")
            except KeyError:
                raise SystemExit(f"jar 里没有 {entry}") from None
            patched = patch(name, raw)
            target = OUT / f"{name}.vsh"
            target.write_text(patched, encoding="utf-8", newline="\n")
            print(f"wrote {target.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
