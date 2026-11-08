# -*- coding: utf-8 -*-
"""
junction_box_20261108 — deterministic preview renderer (convention-compliant).

Renders the four QA previews straight from junction_box.geo.json +
junction_box_tex.png, per ASSET_DISCUSSION_CONVENTION.md §2:
  - 1200x900, transparent background
  - preview_front: flat light (tint 1.0)
  - preview_side / preview_back: slightly darker (tint 0.82)
  - preview_oblique: fov 38 deg
Software z-buffer rasterizer; nearest-neighbor texture sampling.
"""
import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

W, H = 1200, 900
UV_SPACE = 128.0

with open(os.path.join(ROOT, "junction_box.geo.json"), encoding="utf-8") as f:
    geo = json.load(f)

tex = Image.open(os.path.join(ROOT, "junction_box_tex.png")).convert("RGBA")
TEXW, TEXH = tex.size
tpx = tex.load()

cubes = []
for bone in geo["minecraft:geometry"][0]["bones"]:
    for c in bone.get("cubes", []):
        ox, oy, oz = c["origin"]
        sx, sy, sz = c["size"]
        x1, y1, z1 = ox, oy, oz
        x2, y2, z2 = ox + sx, oy + sy, oz + sz
        faces = {}
        for name, fd in c["uv"].items():
            u, v = fd["uv"]
            du, dv = fd["uv_size"]
            if name in ("up", "down"):
                u1, v1, u2, v2 = u + du, v + dv, u, v
            else:
                u1, v1, u2, v2 = u, v, u + du, v + dv
            faces[name] = (u1, v1, u2, v2)
        cubes.append((c, faces, x1, y1, z1, x2, y2, z2))


def face_quad(name, x1, y1, z1, x2, y2, z2, rect):
    """returns (4 corners xyz, 4 uv pairs) with corner-uv pairing that keeps
    the front view un-mirrored (matches the authored texture)."""
    u1, v1, u2, v2 = rect
    if name == "south":
        pts = [(x1, y1, z2), (x2, y1, z2), (x2, y2, z2), (x1, y2, z2)]
        uvs = [(u1, v2), (u2, v2), (u2, v1), (u1, v1)]
        n = (0, 0, 1)
    elif name == "north":
        pts = [(x2, y1, z1), (x1, y1, z1), (x1, y2, z1), (x2, y2, z1)]
        uvs = [(u1, v2), (u2, v2), (u2, v1), (u1, v1)]
        n = (0, 0, -1)
    elif name == "east":
        pts = [(x2, y1, z2), (x2, y1, z1), (x2, y2, z1), (x2, y2, z2)]
        uvs = [(u1, v2), (u2, v2), (u2, v1), (u1, v1)]
        n = (1, 0, 0)
    elif name == "west":
        pts = [(x1, y1, z1), (x1, y1, z2), (x1, y2, z2), (x1, y2, z1)]
        uvs = [(u1, v2), (u2, v2), (u2, v1), (u1, v1)]
        n = (-1, 0, 0)
    elif name == "up":
        pts = [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)]
        uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
        n = (0, 1, 0)
    else:
        pts = [(x1, y1, z1), (x2, y1, z1), (x2, y1, z2), (x1, y1, z2)]
        uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
        n = (0, -1, 0)
    return pts, uvs, n


def look_at(eye, target, up=(0, 1, 0)):
    def sub(a, b):
        return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
    def cross(a, b):
        return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    def norm(a):
        l = math.sqrt(a[0] ** 2 + a[1] ** 2 + a[2] ** 2) or 1.0
        return (a[0] / l, a[1] / l, a[2] / l)
    f = norm(sub(target, eye))
    r = norm(cross(f, up))
    u = cross(r, f)
    return f, r, u


def render(eye, fov_deg, tint, out_name):
    target = (0.0, 8.0, 0.0)
    f, r, u = look_at(eye, target)
    fov = math.radians(fov_deg)
    focal = (H / 2.0) / math.tan(fov / 2.0)
    aspect = W / H
    focal_x = (W / 2.0) / (math.tan(fov / 2.0) * aspect)

    zbuf = [float("inf")] * (W * H)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ipx = img.load()

    ex, ey, ez = eye

    for _, faces, x1, y1, z1, x2, y2, z2 in cubes:
        for name, rect in faces.items():
            pts, uvs, n = face_quad(name, x1, y1, z1, x2, y2, z2, rect)
            cx, cy, cz = (pts[0][0] + pts[2][0]) / 2, (pts[0][1] + pts[2][1]) / 2, (pts[0][2] + pts[2][2]) / 2
            if n[0] * (cx - ex) + n[1] * (cy - ey) + n[2] * (cz - ez) >= 0:
                continue  # backface
            scr = []
            vis = True
            for (px_, py_, pz_) in pts:
                dx, dy, dz = px_ - ex, py_ - ey, pz_ - ez
                cz_ = f[0] * dx + f[1] * dy + f[2] * dz
                cx_ = r[0] * dx + r[1] * dy + r[2] * dz
                cy_ = u[0] * dx + u[1] * dy + u[2] * dz
                if cz_ <= 0.01:
                    vis = False
                    break
                sxp = W / 2 + cx_ * focal_x / cz_
                syp = H / 2 - cy_ * focal / cz_
                scr.append((sxp, syp, cz_))
            if not vis:
                continue
            uvs_px = [(u1 / UV_SPACE * TEXW, v1 / UV_SPACE * TEXH) for (u1, v1) in
                      [(uvs[0][0], uvs[0][1]), (uvs[1][0], uvs[1][1]),
                       (uvs[2][0], uvs[2][1]), (uvs[3][0], uvs[3][1])]]

            def draw_tri(i0, i1, i2):
                (ax, ay, az), (bx, by, bz), (cxx, cyy, czz) = scr[i0], scr[i1], scr[i2]
                (au, av), (bu, bv), (cuu, cvv) = uvs_px[i0], uvs_px[i1], uvs_px[i2]
                area = (bx - ax) * (cyy - ay) - (by - ay) * (cxx - ax)
                if abs(area) < 1e-9:
                    return
                minx = max(0, int(min(ax, bx, cxx)))
                maxx = min(W - 1, int(max(ax, bx, cxx)) + 1)
                miny = max(0, int(min(ay, by, cyy)))
                maxy = min(H - 1, int(max(ay, by, cyy)) + 1)
                inv = 1.0 / area
                for yy in range(miny, maxy + 1):
                    for xx in range(minx, maxx + 1):
                        px_ = xx + 0.5
                        py_ = yy + 0.5
                        l1 = ((px_ - ax) * (cyy - ay) - (py_ - ay) * (cxx - ax)) * inv
                        l2 = ((px_ - ax) * (ay - by) - (py_ - ay) * (ax - bx)) * inv
                        l0 = 1.0 - l1 - l2
                        if l0 < -1e-6 or l1 < -1e-6 or l2 < -1e-6:
                            continue
                        z = l0 * az + l1 * bz + l2 * czz
                        idx = yy * W + xx
                        if z >= zbuf[idx]:
                            continue
                        uu = l0 * au + l1 * bu + l2 * cuu
                        vv = l0 * av + l1 * bv + l2 * cvv
                        tx = int(uu) % TEXW
                        ty = int(vv) % TEXH
                        tr, tg, tb, ta = tpx[tx, ty]
                        if ta == 0:
                            continue
                        zbuf[idx] = z
                        ipx[xx, yy] = (int(tr * tint), int(tg * tint), int(tb * tint), ta)

            draw_tri(0, 1, 2)
            draw_tri(0, 2, 3)

    img.save(os.path.join(ROOT, out_name))
    opaque = sum(1 for yy in range(H) for xx in range(0, W, 3) if ipx[xx, yy][3] > 0)
    return out_name, opaque * 3


views = [
    ((0.0, 8.0, 56.7), 20.0, 1.0, "preview_front.png"),
    ((56.7, 8.0, 0.0), 20.0, 0.82, "preview_side.png"),
    ((0.0, 8.0, -56.7), 20.0, 0.82, "preview_back.png"),
    ((34.0, 26.0, 42.0), 38.0, 1.0, "preview_oblique.png"),
]
for eye, fov, tint, name in views:
    print(render(eye, fov, tint, name))
