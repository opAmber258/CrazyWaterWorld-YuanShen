from pathlib import Path
from PIL import Image, ImageDraw
import json
import engine_spec

ROOT = Path(__file__).resolve().parent.parent
REF = Path(__file__).resolve().parent
TEX = ROOT / 'engine_tex.png'
PALETTE = REF / 'palette.json'
UV_LAYOUT = REF / 'uv_layout.json'

C = {
    'case_dark': '#343a36', 'case_mid': '#5f655c', 'case_shadow': '#474d47',
    'case_light': '#777b6f', 'seam': '#272c29', 'steel': '#525a56',
    'steel_dark': '#353c39', 'steel_light': '#85897d', 'vent_dark': '#252a27',
    'vent_mid': '#505850', 'vent_high': '#899084', 'panel': '#171d1b',
    'panel_edge': '#303733', 'key': '#a0a292', 'key_dark': '#4a4f48',
    'green': '#8eaf8d', 'teal': '#80aaa5', 'window': '#5f7d7e',
    'amber': '#cf9d63', 'red': '#6c302c', 'red_light': '#92453a',
    'red_dark': '#432725', 'rust': '#7d4e38', 'rust_dark': '#593a2e',
    'rubber': '#202421', 'label': '#b99f63', 'white': '#c2c1aa',
    'black': '#111615', 'black_frame': '#2a302d', 'black_bar': '#3b413c',
}

LAYOUT = engine_spec.build_face_layout()
SIZE = LAYOUT['texture_size']
IMG = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))

def fill(d, w, h, color):
    d.rectangle((0, 0, w - 1, h - 1), fill=color)

def box(d, x1, y1, x2, y2, color):
    if x1 <= x2 and y1 <= y2:
        d.rectangle((x1, y1, x2, y2), fill=color)

def line(d, x1, y1, x2, y2, color):
    d.line((x1, y1, x2, y2), fill=color)

def px(d, x, y, color):
    if x >= 0 and y >= 0:
        d.point((x, y), fill=color)

def shade(face, dark=False):
    if dark:
        return {'up': C['case_shadow'], 'down': C['case_dark']}.get(face, C['case_shadow'])
    return {'up': C['case_light'], 'down': C['case_dark'], 'east': C['case_shadow'], 'west': C['case_shadow']}.get(face, C['case_mid'])

def shell(d, w, h, name, face, dark=False):
    fill(d, w, h, shade(face, dark))
    if w < 4 or h < 4:
        line(d, 0, 0, w - 1, 0, C['case_light'])
        return
    line(d, 1, 1, w - 2, 1, C['steel_light'] if not dark else C['steel'])
    line(d, 1, h - 2, w - 2, h - 2, C['seam'])
    if w >= 18:
        line(d, w // 3, 2, w // 3, h - 3, C['case_shadow'])
        line(d, (w * 2) // 3, 2, (w * 2) // 3, h - 3, C['case_shadow'])
    if h >= 18:
        line(d, 2, h // 2, w - 3, h // 2, C['case_shadow'])
    for x, y in ((3, 3), (w - 5, 3), (3, h - 5), (w - 5, h - 5)):
        if 0 < x < w - 1 and 0 < y < h - 1:
            px(d, x, y, C['steel_dark'])
    px(d, max(2, w // 3), max(2, h - 4), C['rust_dark'])
    px(d, max(3, (w * 2) // 3), 2, C['rust'])

def steel(d, w, h, name, face):
    fill(d, w, h, C['steel'])
    if w > 2 and h > 2:
        line(d, 1, 1, w - 2, 1, C['steel_light'])
        line(d, 1, h - 2, w - 2, h - 2, C['steel_dark'])
    if w >= 18:
        line(d, w // 2, 2, w // 2, h - 3, C['steel_dark'])
    px(d, max(2, w // 3), max(2, h - 4), C['rust'])

def bumper(d, w, h, name, face):
    steel(d, w, h, name, face)
    if w >= 36:
        line(d, 2, h // 2, w - 3, h // 2, C['seam'])
        for x in (w // 5, (w * 4) // 5):
            box(d, x - 4, max(2, h // 2 - 1), x + 4, min(h - 3, h // 2 + 1), C['case_dark'])

def dark(d, w, h, name, face):
    fill(d, w, h, C['steel_dark'])
    if w > 2 and h > 2:
        line(d, 1, 1, w - 2, 1, C['steel'])
        line(d, 1, h - 2, w - 2, h - 2, C['seam'])
    if w >= 12:
        for x in range(3, w - 2, 8):
            line(d, x, 2, x, h - 3, C['steel'])

def panel(d, w, h, name, face):
    fill(d, w, h, C['panel_edge'])
    if w > 2 and h > 2:
        line(d, 1, 1, w - 2, 1, C['steel_dark'])
        line(d, 1, h - 2, w - 2, h - 2, C['seam'])

def vent(d, w, h, name, face):
    fill(d, w, h, C['vent_dark'])
    line(d, 1, 1, w - 2, 1, C['panel_edge'])
    line(d, 1, h - 2, w - 2, h - 2, C['panel_edge'])
    line(d, 1, 1, 1, h - 2, C['panel_edge'])
    line(d, w - 2, 1, w - 2, h - 2, C['panel_edge'])
    if w >= 16 and h >= 26:
        box(d, 3, 3, w - 4, 8, C['panel'])
        mid = w // 2
        box(d, 4, 4, mid - 2, 7, C['window'])
        box(d, mid + 1, 4, w - 5, 7, C['window'])
        line(d, mid, 4, mid, 7, C['seam'])
        top = 12
    else:
        top = 4
    box(d, 2, top, w - 3, h - 3, C['vent_mid'])
    for y in range(top + 2, h - 3, 4):
        line(d, 3, y, w - 4, y, C['vent_high'])
        line(d, 3, min(h - 3, y + 1), w - 4, min(h - 3, y + 1), C['seam'])
    if w >= 12:
        line(d, w // 2, top + 1, w // 2, h - 4, C['seam'])
    px(d, max(3, w - 5), min(h - 5, top + 9), C['rust_dark'])

def vent_edge(d, w, h, name, face):
    fill(d, w, h, C['case_dark'])
    line(d, 0, 0, w - 1, 0, C['steel_dark'])
    if h > 8:
        line(d, 1, 3, w - 2, 3, C['seam'])

def control(d, w, h, name, face):
    fill(d, w, h, C['panel'])
    line(d, 1, 1, w - 2, 1, C['steel_light'])
    line(d, 1, h - 2, w - 2, h - 2, C['steel_light'])
    if w >= 16 and h >= 26:
        colors = (C['green'], C['teal'], C['green'])
        bw = max(3, (w - 8) // 3 - 1)
        for i, color in enumerate(colors):
            x = 3 + i * (bw + 2)
            box(d, x, 4, min(w - 4, x + bw), 6, color)
        size, gap = 3, 4
        for row in range(3):
            for col in range(3):
                x = 4 + col * (size + gap)
                y = 10 + row * (size + gap)
                if x + size < w - 3 and y + size < h - 11:
                    box(d, x, y, x + size, y + size, C['key'])
                    px(d, x, y, C['white'])
                    line(d, x + size, y, x + size, y + size, C['key_dark'])
    else:
        box(d, 3, 3, w - 4, min(h - 6, 7), C['panel_edge'])
        for x in range(3, max(4, w - 3), 5):
            box(d, x, min(h - 6, 7), min(w - 3, x + 2), min(h - 3, 10), C['key'])
    for i, color in enumerate((C['rust'], C['amber'], C['steel_light'])):
        x = 3 + i * 3
        if x + 1 < w - 2:
            box(d, x, h - 7, x + 1, h - 6, color)
    if h >= 18:
        box(d, max(3, w // 3), h - 5, min(w - 4, (w * 2) // 3), h - 4, C['rubber'])

def black_grille(d, w, h, name, face):
    fill(d, w, h, C['black'])
    line(d, 0, 0, w - 1, 0, C['black_frame'])
    line(d, 0, h - 1, w - 1, h - 1, C['black_frame'])
    line(d, 0, 0, 0, h - 1, C['black_frame'])
    line(d, w - 1, 0, w - 1, h - 1, C['black_frame'])
    start = 3 if h > 12 else 1
    for y in range(start, max(start, h - 2), 4):
        line(d, 2, y, w - 3, y, C['black_bar'])
        line(d, 2, min(h - 2, y + 1), w - 3, min(h - 2, y + 1), C['black'])
    if w >= 10:
        line(d, w // 2, 1, w // 2, h - 2, C['black_frame'])
    px(d, max(2, w - 3), min(h - 3, 4), C['rust'])
    px(d, w - 4, min(h - 3, 9), C['rust_dark'])

def black_frame(d, w, h, name, face):
    fill(d, w, h, C['black_frame'])
    if w > 2 and h > 2:
        line(d, 1, 1, w - 2, 1, C['steel'])
        line(d, 1, h - 2, w - 2, h - 2, C['black'])
    px(d, max(2, w // 2), max(2, h // 2), C['rust'])

def red(d, w, h, name, face):
    fill(d, w, h, C['red'])
    if w > 2 and h > 2:
        line(d, 1, 1, w - 2, 1, C['red_light'])
        line(d, 1, h - 2, w - 2, h - 2, C['red_dark'])
    if w >= 20:
        for x in range(8, w - 4, 18):
            line(d, x, 2, x, h - 3, C['red_dark'])
    px(d, max(2, w // 3), max(2, h - 4), C['rust'])
    px(d, max(3, w // 3 + 2), max(2, h - 4), C['rust_dark'])

def rail(d, w, h, name, face):
    fill(d, w, h, C['steel'])
    if w > 2 and h > 2:
        if w >= h:
            line(d, 1, 1, w - 2, 1, C['steel_light'])
            line(d, 1, h - 2, w - 2, h - 2, C['steel_dark'])
        else:
            line(d, 1, 1, 1, h - 2, C['steel_light'])
            line(d, w - 2, 1, w - 2, h - 2, C['steel_dark'])
    px(d, max(2, w // 3), max(2, h - 4), C['rust'])

def fan_metal(d, w, h, name, face):
    fill(d, w, h, C['steel_dark'])
    line(d, 1, 1, w - 2, 1, C['steel'])
    if w >= 12:
        for x in range(4, w - 3, 8):
            line(d, x, 2, x, h - 3, C['steel'])
    px(d, max(2, w - 4), 3, C['rust_dark'])

def blade(d, w, h, name, face):
    fill(d, w, h, C['steel_dark'])
    if w >= h:
        line(d, 2, max(1, h // 3), w - 3, max(1, h // 3), C['steel_light'])
        line(d, 2, min(h - 2, (h * 2) // 3), w - 3, min(h - 2, (h * 2) // 3), C['steel'])
    else:
        line(d, max(1, w // 3), 2, max(1, w // 3), h - 3, C['steel_light'])
        line(d, min(w - 2, (w * 2) // 3), 2, min(w - 2, (w * 2) // 3), h - 3, C['steel'])

def hub(d, w, h, name, face):
    fill(d, w, h, C['steel_light'])
    line(d, 1, 1, w - 2, 1, C['white'])
    box(d, w // 3, h // 3, (w * 2) // 3, (h * 2) // 3, C['case_dark'])
    box(d, max(2, w // 2 - 1), max(2, h // 2 - 1), min(w - 3, w // 2), min(h - 3, h // 2), C['steel_light'])

def hatch(d, w, h, name, face):
    fill(d, w, h, C['case_shadow'])
    if w > 3 and h > 3:
        line(d, 1, 1, w - 2, 1, C['steel_light'])
        line(d, 1, h - 2, w - 2, h - 2, C['seam'])
        line(d, 1, 1, 1, h - 2, C['steel_light'])
        line(d, w - 2, 1, w - 2, h - 2, C['steel_light'])
    for x, y in ((3, 3), (w - 5, 3), (3, h - 5), (w - 5, h - 5)):
        if 0 < x < w - 1 and 0 < y < h - 1:
            px(d, x, y, C['steel_light'])
    if w > 8 and h > 8:
        box(d, w - 6, h // 2 - 2, w - 5, h // 2 + 2, C['rust'])

def rubber(d, w, h, name, face):
    fill(d, w, h, C['rubber'])
    line(d, 1, 1, w - 2, 1, C['steel_dark'])
    line(d, 1, h - 2, w - 2, h - 2, C['seam'])

def outlet(d, w, h, name, face):
    fill(d, w, h, C['rubber'])
    line(d, 0, 0, w - 1, 0, C['panel_edge'])
    line(d, 0, h - 1, w - 1, h - 1, C['panel_edge'])
    if w >= 8 and h >= 8:
        box(d, 3, 3, w - 4, max(3, h // 3), C['case_dark'])
        box(d, 3, max(4, (h * 2) // 3), w - 4, h - 4, C['case_dark'])

def trim(d, w, h, name, face):
    fill(d, w, h, C['steel_light'])
    line(d, 1, 1, w - 2, 1, C['white'])
    line(d, 1, h - 2, w - 2, h - 2, C['seam'])


def black_metal(d, w, h, name, face):
    fill(d, w, h, C['black'])
    if w > 3 and h > 3:
        line(d, 1, 1, w - 2, 1, C['black_frame'])
        line(d, 1, h - 2, w - 2, h - 2, C['seam'])
        line(d, 1, 1, 1, h - 2, C['black_frame'])
        line(d, w - 2, 1, w - 2, h - 2, C['black_frame'])
        if face == 'up':
            for y in (h // 3, (h * 2) // 3):
                line(d, 2, y, w - 3, y, C['black_frame'])
            line(d, w // 2, 2, w // 2, h - 3, C['black_frame'])
            for x in (5, w - 6):
                box(d, x, 4, x + 4, 7, C['steel_dark'])
                line(d, x, 4, x + 4, 4, C['steel'])
        elif face in ('west', 'east'):
            line(d, w // 2, 2, w // 2, h - 3, C['black_frame'])
            for y in (h // 3, (h * 2) // 3):
                line(d, 2, y, w - 3, y, C['black_frame'])
            for y in range(5, max(6, h - 4), 9):
                line(d, max(2, w // 3), y, min(w - 3, (w * 2) // 3), y, C['steel_dark'])
        else:
            if w >= 12:
                line(d, w // 3, 2, w // 3, h - 3, C['black_frame'])
                line(d, (w * 2) // 3, 2, (w * 2) // 3, h - 3, C['black_frame'])
            if h >= 18:
                line(d, 2, h // 2, w - 3, h // 2, C['black_frame'])
    for x, y in ((3, max(3, h - 5)), (max(3, w - 5), 3), (4, 4)):
        px(d, x, y, C['rust_dark'])


def black_door(d, w, h, name, face):
    fill(d, w, h, C['black'])
    if w > 4 and h > 4:
        line(d, 1, 1, w - 2, 1, C['steel'])
        line(d, 1, h - 2, w - 2, h - 2, C['black_frame'])
        line(d, 1, 1, 1, h - 2, C['black_frame'])
        line(d, w - 2, 1, w - 2, h - 2, C['black_frame'])
        box(d, 3, 3, w - 4, min(h - 4, max(5, h // 3)), C['black_frame'])
        line(d, 3, (h * 2) // 3, w - 4, (h * 2) // 3, C['black_frame'])
        for y in (max(4, h // 4), (h * 2) // 3):
            box(d, 4, y, 5, min(h - 4, y + 1), C['steel_dark'])
        box(d, max(3, w - 9), h // 2 - 1, max(3, w - 6), h // 2 + 2, C['steel'])
        px(d, max(3, w - 8), h // 2, C['steel_light'])
        box(d, max(3, w - 8), h - 7, max(3, w - 5), h - 4, C['rust'])

P = {
    'shell': shell, 'shell_dark': lambda d,w,h,n,f: shell(d,w,h,n,f,True),
    'steel': steel, 'bumper': bumper, 'dark_metal': dark, 'panel_edge': panel,
    'vent': vent, 'vent_edge': vent_edge, 'control': control,
    'black_grille': black_grille, 'black_frame': black_frame, 'black_metal': black_metal, 'black_door': black_door, 'red': red,
    'rail': rail, 'fan_metal': fan_metal, 'fan_blade': blade, 'hub': hub,
    'hatch': hatch, 'rubber': rubber, 'outlet': outlet, 'trim': trim,
}

for spec in engine_spec.CUBE_SPECS:
    for face in engine_spec.FACE_KEYS:
        data = LAYOUT['regions'][f"{spec['name']}|{face}"]
        x1, y1, x2, y2 = data['rect']
        w, h = x2 - x1, y2 - y1
        tile = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        draw = ImageDraw.Draw(tile)
        P.get(data['style'], steel)(draw, w, h, spec['name'], face)
        IMG.alpha_composite(tile, (x1, y1))

IMG.save(TEX)
PALETTE.write_text(json.dumps({
    'size': [SIZE, SIZE], 'mode': 'RGBA',
    'style': 'low-poly industrial olive-grey machinery with a dark rectangular left service duct and restrained wear',
    'default_px_per_bb': LAYOUT['default_px_per_bb'], 'hero_px_per_bb': LAYOUT['hero_px_per_bb'], 'edge_px_per_bb': LAYOUT['edge_px_per_bb'], 'atlas_gap_px': LAYOUT['gap_px'],
    'face_islands': len(LAYOUT['regions']), 'colors': C,
    'notes': ['Every cuboid face has its own atlas island sized from its real face dimensions.',
              'Hero faces use 2.5 px/BB, secondary faces 2.0 px/BB, and hidden/edge faces 1.5 px/BB.', 'No patterned cell is stretched across a different face aspect ratio.',
              'All details use integer pixel coordinates and the fixed palette.']
}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
UV_LAYOUT.write_text(json.dumps(LAYOUT, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(TEX)
print(PALETTE)
print(UV_LAYOUT)
