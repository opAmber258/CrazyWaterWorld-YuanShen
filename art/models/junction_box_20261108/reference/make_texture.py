# -*- coding: utf-8 -*-
"""
junction_box_20261108 — deterministic pixel texture painter
Reference: Barotrauma 'junctionbox' sprite (signalcomp.png, sourcerect 264,1,110,176)
Output: ../junction_box_tex.png (512x512 RGBA), palette.json

Model space: 10 BB wide x 16 BB tall, UV space 128x128 (1 UV px = 1 BB),
bitmap density 4x (1 UV px = 4 bitmap px).  Front face = +Z (south), ground Y=0.

Rules (guide V2 §7): fixed palette, hard edges, whole pixels, no noise,
no gradients, wear only as a few fixed short marks. Repaint by editing
coordinates below; randomness is fixed at zero.
"""
import json
import os
from PIL import Image

SIZE = 512          # bitmap size (4x of 128 UV)
DENSITY = 4         # bitmap px per UV px (= per BB)

# ---------------------------------------------------------------- palette --
P = {
    "steel_hi":     "#c7c6c7",   # top rim highlight / wear chips
    "steel_light":  "#a4a6a8",   # bright right frame, rim base
    "steel_mid":    "#949596",   # door field mid
    "steel_warm":   "#a8a39b",   # door field upper warm
    "steel_shade":  "#77787a",   # frame shade rows
    "steel_dark":   "#4a4a4e",   # door bottom band
    "steel_back":   "#8e8f91",   # back plate / outer sides
    "steel_side_d": "#55565a",   # undersides
    "steel_side_w": "#777874",   # warm outer left side
    "steel_side_wd":"#55524e",   # warm dark underside / bottom band base
    "steel_back_d": "#6f7072",
    "silhouette":   "#17181a",
    "seam":         "#0a0a0a",
    "strip_bg_u":   "#26251f",   # dark strip upper (olive residue zone)
    "strip_bg_l":   "#171716",   # dark strip lower
    "strip_deep":   "#121211",
    "olive":        "#6b6630",   # glow residue accent
    "warn_yellow":  "#e9b002",
    "warn_black":   "#0c0c0c",
    "hazard_frame": "#141414",
    "label_base":   "#b9bbbd",
    "label_red":    "#a83a32",
    "label_text":   "#3a3a3c",
    "rust_a":       "#49402f",
    "rust_b":       "#371e0c",
    "skirt":        "#050505",
    "skirt_edge":   "#3f3f41",
    "cable_dark":   "#202020",
    "cable_ring":   "#454545",
    "conduit_ring": "#3a3b3d",
    "micro_dark":   "#26262a",
    "recess_shadow":"#1f1f22",
    "recess_deep":  "#17181a",
    "bolt":         "#3a3b3d",
    "bolt_hi":      "#c7c6c7",
    "hinge_hi":     "#b9babd",
    "hinge_mid":    "#97989a",
    "hinge_knuckle":"#a8a9ab",
    "hinge_waist":  "#6a6b6d",
    "hinge_shadow": "#5f6062",
}

RGB = {k: tuple(int(v[i:i+2], 16) for i in (1, 3, 5)) for k, v in P.items()}

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
px = img.load()


def rect(x0, y0, x1, y1, color):
    """fill inclusive-exclusive bitmap px rect"""
    for y in range(max(0, y0), min(SIZE, y1)):
        for x in range(max(0, x0), min(SIZE, x1)):
            px[x, y] = color + (255,)


def dot(x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        px[x, y] = color + (255,)


def hline(x0, x1, y, color):
    rect(x0, y, x1, y + 1, color)


def vline(x, y0, y1, color):
    rect(x, y0, x + 1, y1, color)


# uv rect -> bitmap rect helper
def uv(u1, v1, u2, v2):
    return u1 * DENSITY, v1 * DENSITY, u2 * DENSITY, v2 * DENSITY


# ------------------------------------------------------------ main regions --

# ============ door_panel front  uv[0,0,8,10] -> bitmap 32x40 at (0,0) =========
# door field bands (left of dark strip)
rect(0, 0, 32, 40, RGB["steel_mid"])
rect(0, 0, 18, 10, RGB["steel_warm"])      # warm upper band  (x1..17)
rect(0, 28, 18, 40, RGB["steel_dark"])     # dark lower band
# dark strip column x18..29 (+ bright right column x30..31)
vline(18, 0, 40, RGB["seam"])              # strip left seam
rect(19, 1, 30, 27, RGB["strip_bg_u"])     # strip upper: olive residue zone
rect(19, 27, 30, 35, RGB["strip_bg_l"])    # strip lower
rect(19, 35, 30, 40, RGB["strip_deep"])
rect(30, 1, 32, 39, RGB["steel_light"])    # bright column before frame
# black seam ring: top row, left col, bottom row (right edge stays bright)
hline(0, 32, 0, RGB["seam"])
vline(0, 0, 40, RGB["seam"])
hline(0, 32, 39, RGB["seam"])

# warning triangle: black silhouette + yellow core, apex (24,12), base bar y23
for y in range(12, 23):
    hw = (y - 12) * 3 // 10
    rect(24 - hw - 1, y, 24 + hw + 2, y + 1, RGB["warn_black"])
    rect(24 - hw, y, 24 + hw + 1, y + 1, RGB["warn_yellow"])
hline(20, 29, 23, RGB["warn_black"])       # black base bar
# bolt (black zigzag inside the yellow core)
for bx, by in [(24, 17), (23, 18), (24, 19), (23, 20), (24, 21)]:
    dot(bx, by, RGB["warn_black"])
# olive residue accents around triangle
dot(19, 10, RGB["olive"]); dot(28, 11, RGB["olive"]); dot(20, 25, RGB["olive"])

# label sticker (20,27)-(24,35)
rect(20, 27, 25, 35, RGB["label_base"])
hline(20, 25, 27, RGB["label_red"])
hline(20, 25, 28, RGB["label_red"])
hline(21, 24, 30, RGB["label_text"])
hline(21, 24, 32, RGB["label_text"])
dot(20, 34, RGB["label_text"])

# rust clusters on door lower band (fixed positions)
for x, y in [(2, 31), (3, 31), (2, 32), (4, 32), (3, 33), (15, 37)]:
    dot(x, y, RGB["rust_a"])
for x, y in [(3, 32), (4, 33), (16, 38), (2, 38)]:
    dot(x, y, RGB["rust_b"])
for x, y in [(8, 34), (9, 34), (10, 35), (8, 36), (12, 35)]:
    dot(x, y, RGB["rust_a"])
dot(9, 35, RGB["rust_b"]); dot(9, 36, RGB["rust_b"])
# two wear chips (light metal showing) on door mid band
dot(5, 14, RGB["steel_hi"]); dot(14, 22, RGB["steel_hi"])

# ============ base_body front  uv[10,0,20,4] -> bitmap 40x16 at (40,0) ========
rect(40, 0, 80, 16, RGB["hazard_frame"])
hline(40, 80, 0, RGB["seam"])
rect(40, 1, 80, 2, RGB["seam"])
# hazard stripes 45 deg down-right, sprite-period 30px, band 14px
# sprite mapping: sx = bx*2.75, sy = 128 + by*2.5625 ; yellow iff (sx-sy+6)%30 < 14
for by in range(2, 12):
    for bx in range(3, 37):
        sx = bx * 2.75
        sy = 128 + by * 2.5625
        if int((sx - sy + 6) % 30) < 14:
            dot(40 + bx, by, RGB["warn_yellow"])
        else:
            dot(40 + bx, by, RGB["warn_black"])
# skirt rows
hline(40, 80, 12, RGB["seam"])
hline(40, 80, 13, RGB["skirt_edge"])
rect(40, 14, 80, 15, RGB["skirt"])
hline(40, 80, 15, RGB["seam"])
# rust on hazard frame corners
dot(41, 1, RGB["rust_a"]); dot(42, 1, RGB["rust_a"])
dot(78, 1, RGB["rust_b"])
dot(41, 14, RGB["rust_a"])

# ============ frame fronts =====================================================
# frame_top front uv[10,5,20,6] -> (40,20)-(80,24)
rect(40, 20, 80, 24, RGB["steel_light"])
hline(40, 80, 20, RGB["steel_hi"])
hline(40, 80, 23, RGB["steel_shade"])
dot(45, 21, RGB["rust_a"]); dot(70, 22, RGB["rust_b"])
dot(41, 21, RGB["steel_hi"]); dot(78, 22, RGB["steel_hi"])   # wear chips
# frame_bottom front uv[10,7,20,9] -> (40,28)-(80,36)
rect(40, 28, 80, 36, RGB["steel_side_wd"])
hline(40, 80, 28, RGB["steel_back_d"])
rect(40, 34, 80, 36, RGB["silhouette"])
for x, y in [(44, 31), (45, 31), (44, 32), (70, 32), (71, 32), (70, 33), (57, 30)]:
    dot(x, y, RGB["rust_a"])
for x, y in [(61, 31), (62, 31), (45, 33)]:
    dot(x, y, RGB["rust_b"])
dot(41, 29, RGB["steel_light"]); dot(78, 33, RGB["steel_light"])
# frame_left front uv[22,0,23,10] -> (88,0)-(92,40)  warm dark rim
rect(88, 0, 92, 40, RGB["steel_side_w"])
vline(88, 0, 40, RGB["silhouette"])
vline(91, 0, 40, RGB["seam"])
hline(88, 92, 39, RGB["steel_side_wd"])
# frame_right front uv[24,0,25,10] -> (96,0)-(100,40) bright rim
rect(96, 0, 100, 40, RGB["hinge_mid"])
vline(96, 0, 40, RGB["steel_back_d"])
vline(99, 0, 40, RGB["steel_light"])
hline(96, 100, 39, RGB["steel_shade"])

# ============ hinge barrel / knuckles ==========================================
# barrel front uv[26,0,27,10] -> (104,0)-(108,40)
rect(104, 0, 108, 40, RGB["hinge_waist"])
rect(104, 8, 108, 18, RGB["hinge_knuckle"])    # knuckle top  y13.1..10.8
rect(104, 24, 108, 33, RGB["hinge_knuckle"])   # knuckle bottom y9.2..6.9
vline(104, 0, 40, RGB["hinge_hi"])
vline(107, 0, 40, RGB["hinge_shadow"])
# knuckle fronts uv[28,0,29,3] -> (112,0)-(116,12) ; uv[30,0,31,3] -> (120,0)-(124,12)
for x0 in (112, 120):
    rect(x0, 0, x0 + 4, 12, RGB["hinge_knuckle"])
    vline(x0, 0, 12, RGB["hinge_hi"])
    vline(x0 + 3, 0, 12, RGB["hinge_shadow"])
    hline(x0, x0 + 4, 0, RGB["steel_shade"])
    hline(x0, x0 + 4, 11, RGB["steel_shade"])

# ============ back_plate  uv[32,0,41,12] -> (128,0)-(164,48) ==================
rect(128, 0, 164, 48, RGB["steel_back"])
rect(128, 0, 164, 1, RGB["steel_back_d"])
rect(128, 47, 164, 48, RGB["steel_back_d"])
vline(128, 0, 48, RGB["steel_back_d"])
vline(163, 0, 48, RGB["steel_back_d"])
hline(129, 163, 24, RGB["steel_shade"])
for bx, by in [(3, 3), (31, 3), (3, 42), (31, 42)]:
    rect(128 + bx, by, 128 + bx + 2, by + 2, RGB["bolt"])
    dot(128 + bx, by, RGB["bolt_hi"])
dot(150, 8, RGB["steel_hi"]); dot(140, 40, RGB["steel_hi"])  # wear chips

# ============ panel back uv[44,12,52,22] -> (176,48)-(208,88) =================
rect(176, 48, 208, 88, RGB["steel_back"])
rect(176, 48, 208, 49, RGB["steel_back_d"])
rect(176, 87, 208, 88, RGB["steel_back_d"])
vline(176, 48, 88, RGB["steel_back_d"])
vline(207, 48, 88, RGB["steel_back_d"])
hline(177, 207, 68, RGB["steel_shade"])
rect(179, 51, 181, 53, RGB["bolt"]); rect(203, 51, 205, 53, RGB["bolt"])
dot(190, 60, RGB["steel_hi"])

# ============ cable / conduit ends ==============================================
# cable_end uv[10,11,13,14] -> (40,44)-(52,56)
rect(40, 44, 52, 56, RGB["cable_dark"])
rect(40, 44, 52, 45, RGB["seam"])
rect(40, 55, 52, 56, RGB["seam"])
vline(40, 44, 56, RGB["seam"]); vline(51, 44, 56, RGB["seam"])
for bx, by in [(2, 2), (7, 2), (4, 6)]:
    rect(40 + bx, 44 + by, 40 + bx + 2, 44 + by + 2, RGB["cable_ring"])
    dot(40 + bx, 44 + by, RGB["seam"])
# conduit_end uv[14,11,16,13] -> (56,44)-(64,52)
rect(56, 44, 64, 52, RGB["conduit_ring"])
rect(58, 46, 62, 50, RGB["seam"])

# ============ shared micro / strip patches =====================================
# micro_dark uv[64,44,68,48] -> (256,176)-(272,192)
rect(256, 176, 272, 192, RGB["micro_dark"])
# steel_strip uv[124,0,125,10] -> (496,0)-(500,40)
rect(496, 0, 500, 40, RGB["steel_back_d"])
vline(499, 0, 40, RGB["steel_side_d"])

# ============ plain faces (sides / tops / bottoms / backs) =====================
def plain(x0, y0, x1, y1, fill, top=None, bottom=None, left=None, right=None):
    rect(x0, y0, x1, y1, fill)
    if top:    hline(x0, x1, y0, top)
    if bottom: hline(x0, x1, y1 - 1, bottom)
    if left:   vline(x0, y0, y1, left)
    if right:  vline(x1 - 1, y0, y1, right)

# frame outer sides
plain(176, 0, 204, 40, RGB["steel_side_w"], top=RGB["steel_back"],      # frame_left west
      bottom=RGB["steel_side_wd"], left=RGB["silhouette"])
plain(208, 0, 236, 40, RGB["steel_back"], top=RGB["steel_light"],       # frame_right east
      bottom=RGB["steel_side_d"], left=RGB["steel_light"], right=RGB["steel_shade"])
# frame tops / ends / backs / undersides / inners
plain(0, 104, 40, 132, RGB["hinge_mid"], top=RGB["steel_light"], bottom=None,  # frame_top up
      left=RGB["steel_back_d"], right=RGB["steel_back_d"])
plain(304, 0, 344, 4, RGB["steel_shade"])                                       # frame_top back
plain(368, 0, 396, 4, RGB["steel_back"])                                        # frame_top west end
plain(368, 8, 396, 12, RGB["steel_back"])                                       # frame_top east end
plain(0, 136, 40, 164, RGB["steel_side_d"])                                     # frame_bottom down
plain(304, 8, 344, 16, RGB["steel_side_wd"])                                    # frame_bottom back
plain(368, 16, 396, 24, RGB["steel_side_wd"])                                   # frame_bottom west
plain(368, 28, 396, 36, RGB["steel_side_wd"])                                   # frame_bottom east
plain(352, 0, 356, 40, RGB["steel_shade"])                                      # frame_left back
plain(360, 0, 364, 40, RGB["steel_shade"])                                      # frame_right back
plain(384, 0, 388, 28, RGB["steel_light"])                                      # frame_left up
plain(392, 0, 396, 28, RGB["steel_light"])                                      # frame_right up
plain(400, 0, 404, 28, RGB["steel_side_d"])                                     # frame_left down
plain(408, 0, 412, 28, RGB["steel_side_d"])                                     # frame_right down
plain(416, 0, 420, 40, RGB["recess_deep"])                                      # frame_left inner
plain(424, 0, 428, 40, RGB["recess_deep"])                                      # frame_right inner
# base sides / back / up / down
plain(40, 64, 64, 80, RGB["micro_dark"], top=RGB["hazard_frame"], bottom=RGB["skirt"])   # base west
plain(68, 64, 92, 80, RGB["micro_dark"], top=RGB["hazard_frame"], bottom=RGB["skirt"])   # base east
plain(432, 0, 472, 16, RGB["micro_dark"], bottom=RGB["skirt"])                           # base back
plain(40, 84, 80, 108, RGB["hazard_frame"])                                              # base up
plain(0, 168, 40, 184, RGB["skirt"])                                                     # base down
# panel west / east (recess gap shadow) & up / down
plain(440, 48, 464, 88, RGB["recess_shadow"])
plain(468, 48, 492, 88, RGB["recess_shadow"])
plain(0, 200, 32, 224, RGB["steel_side_d"])    # panel up
plain(0, 228, 32, 252, RGB["steel_side_d"])    # panel down

# ------------------------------------------------------------------- output --
out_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
tex_path = os.path.join(out_dir, "junction_box_tex.png")
img.save(tex_path)

palette_doc = {
    "size": [SIZE, SIZE],
    "mode": "RGBA",
    "uv_space": [128, 128],
    "density": "4x (1 UV px = 1 BB = 4 bitmap px)",
    "style": "cool aged steel housing, charcoal functional strip, saturated amber hazard marks; "
             "deterministic hard-edge bands; no noise, no gradients; few fixed wear marks",
    "colors": P,
    "regions_uv128": {
        "panel_front": [0, 0, 8, 10],        "base_front": [10, 0, 20, 4],
        "frame_top_front": [10, 5, 20, 6],   "frame_bottom_front": [10, 7, 20, 9],
        "frame_left_front": [22, 0, 23, 10], "frame_right_front": [24, 0, 25, 10],
        "barrel_front": [26, 0, 27, 10],     "knuckle_a_front": [28, 0, 29, 3],
        "knuckle_b_front": [30, 0, 31, 3],   "plate_back": [32, 0, 41, 12],
        "cable_end": [10, 11, 13, 14],       "conduit_end": [14, 11, 16, 13],
        "panel_back": [44, 12, 52, 22],      "micro_dark": [64, 44, 68, 48],
        "steel_strip": [124, 0, 125, 10]
    },
}
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "palette.json"), "w", encoding="utf-8") as f:
    json.dump(palette_doc, f, indent=2, ensure_ascii=False)

print("saved:", tex_path)
print("palette.json saved")
