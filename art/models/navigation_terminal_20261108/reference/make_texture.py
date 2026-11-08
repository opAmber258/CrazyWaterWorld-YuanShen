from PIL import Image, ImageDraw
from pathlib import Path
import json

OUT = Path(r'F:\BarotraumaMinecraft\CrazyWaterWorld-YuanShen\art\models\navigation_terminal_20261108')
TEX = OUT / 'navigation_terminal_tex.png'
S = 128

P = {
    # Neutral aged grey-green housing, balanced by charcoal functional parts.
    'case_dark': '#454842',
    'case_mid': '#5f6058',
    'case_light': '#74766d',
    'edge': '#85877b',
    'seam': '#2e332f',
    'steel': '#4c514b',
    'steel_light': '#696d65',
    'screen': '#182223',
    'screen_hi': '#2b3836',
    'screen_grid': '#2c3d38',
    'screen_line': '#43594e',
    'phosphor': '#7f9682',
    'phosphor_dim': '#566d5e',
    'amber': '#aa9c70',
    'amber_dark': '#6c684f',
    'cable': '#202522',
    'key': '#686a60',
    'key_light': '#808278',
    'key_dark': '#424640',
    'wear': '#696b60',
}

im = Image.new('RGBA', (S, S), (24, 30, 29, 255))
d = ImageDraw.Draw(im)


def rect(box, fill, outline=None):
    d.rectangle(box, fill=fill, outline=outline)


def line(points, fill, width=1):
    d.line(points, fill=fill, width=width)


def bolt(x, y, dark=P['seam'], light=P['case_light']):
    rect((x, y, x + 1, y + 1), dark)
    d.point((x, y), fill=light)


def metal(box, base=P['case_mid'], light=P['case_light'], dark=P['case_dark'], bolts=False):
    """Clean one-pixel machined edge; no random noise or photographic shading."""
    x0, y0, x1, y1 = box
    rect(box, base)
    line((x0, y0, x1, y0), light)
    line((x0, y0, x0, y1), light)
    line((x0, y1, x1, y1), dark)
    line((x1, y0, x1, y1), dark)
    if bolts and x1 - x0 >= 7 and y1 - y0 >= 7:
        for x, y in ((x0 + 2, y0 + 2), (x1 - 3, y0 + 2),
                     (x0 + 2, y1 - 3), (x1 - 3, y1 - 3)):
            bolt(x, y, dark, light)


def louver(x0, y0, x1, rows, spacing=3):
    for i in range(rows):
        y = y0 + i * spacing
        line((x0, y, x1, y), P['seam'])
        if y + 1 < 128:
            line((x0, y + 1, x1, y + 1), P['steel_light'])


# ---------------------------------------------------------------------------
# Main sonar display [0,0,56,36]
# ---------------------------------------------------------------------------
rect((0, 0, 55, 35), P['case_dark'])
rect((1, 1, 54, 34), P['screen'], P['screen_hi'])
for x in (9, 20, 31, 42):
    line((x, 4, x, 30), P['screen_grid'])
for y in (8, 15, 22, 29):
    line((3, y, 52, y), P['screen_grid'])
# Quiet upper readout and range marks.
rect((4, 3, 12, 3), P['phosphor_dim'])
rect((14, 3, 18, 3), P['screen_line'])
for x in range(37, 51, 3):
    d.point((x, 3), fill=P['phosphor_dim'])
# Sweep arcs and bearing marker.
cx, cy = 27, 18
for r in (6, 12, 18):
    d.arc((cx - r, cy - r, cx + r, cy + r), 198, 342, fill=P['screen_line'])
line((27, 18, 44, 7), P['phosphor_dim'])
d.point((27, 18), fill=P['phosphor'])
# Submarine silhouette and a handful of deliberate contacts.
rect((23, 17, 31, 19), P['phosphor_dim'])
rect((26, 15, 28, 16), P['phosphor_dim'])
rect((21, 18, 22, 18), P['phosphor_dim'])
d.point((32, 18), fill=P['phosphor'])
for x, y in ((15, 12), (39, 23), (44, 10), (12, 25)):
    rect((x, y, x + 1, y + 1), P['phosphor'])
for x in range(5, 51, 4):
    rect((x, 32, x + 1, 32), P['phosphor_dim'])

# Auxiliary display [60,0,88,20]
rect((60, 0, 87, 19), P['case_dark'])
rect((61, 1, 86, 18), P['screen'], P['screen_hi'])
for y in (6, 11, 15):
    line((63, y, 84, y), P['screen_grid'])
line((64, 14, 68, 14, 68, 10, 72, 10, 72, 12, 77, 12, 77, 6, 82, 6), P['phosphor'])
rect((64, 3, 72, 3), P['phosphor_dim'])

# Analogue meter [92,0,108,10]
rect((92, 0, 107, 9), P['seam'])
rect((93, 1, 106, 8), P['amber_dark'], P['steel_light'])
line((95, 7, 104, 7), P['amber'])
for x, y in ((96, 4), (98, 3), (100, 2), (102, 3), (104, 4)):
    d.point((x, y), fill=P['amber'])
line((100, 7, 103, 3), '#c2b486')

# Dial [112,0,128,16]
metal((112, 0, 127, 15), P['steel'], P['case_light'])
rect((114, 2, 125, 13), P['seam'], P['case_dark'])
rect((116, 3, 123, 12), P['steel'])
line((117, 11, 119, 7), P['case_light'], 2)
line((122, 11, 120, 7), P['case_light'], 2)
rect((118, 2, 121, 3), P['cable'])

# Top cap [0,36,96,48]
metal((0, 36, 95, 47), P['case_mid'])
louver(4, 39, 21, 3, 3)
line((45, 38, 45, 45), P['case_dark'])
line((48, 38, 89, 38), P['case_light'])
rect((52, 41, 69, 42), P['steel'])
bolt(91, 41)
# Two restrained edge chips, not surface noise.
line((28, 36, 31, 36), P['wear'])
d.point((75, 47), fill=P['seam'])

# Keyboard front lip [0,48,96,60]
metal((0, 48, 95, 59), P['case_mid'])
for x in range(5, 27, 3):
    line((x, 52, x + 1, 52), P['seam'])
    line((x, 53, x + 1, 53), P['steel'])
rect((38, 52, 59, 55), P['case_dark'])
line((40, 52, 57, 52), P['steel_light'])
for x in (33, 78, 89):
    bolt(x, 52)
rect((68, 51, 73, 52), P['wear'])

# ---------------------------------------------------------------------------
# Rear service atlas [0,60,80,104]
# Large covers, narrow utility bay, sparse seams and fasteners.
# ---------------------------------------------------------------------------
metal((0, 60, 79, 103), P['case_dark'], P['steel_light'], P['seam'])
rect((2, 62, 77, 101), P['steel'], P['case_dark'])
# Left main access cover.
metal((4, 64, 40, 96), P['case_mid'], P['case_light'], P['case_dark'], bolts=True)
line((7, 68, 35, 68), P['wear'])
rect((8, 71, 20, 72), P['steel'])
rect((8, 74, 15, 74), P['case_dark'])
louver(10, 84, 31, 4, 3)
rect((29, 91, 35, 93), P['case_dark'])
line((30, 91, 34, 91), P['case_light'])
# Central secondary cover.
metal((43, 64, 60, 96), P['case_mid'], P['case_light'], P['case_dark'], bolts=True)
rect((46, 68, 56, 69), P['steel'])
rect((46, 72, 52, 72), P['case_dark'])
rect((46, 77, 50, 80), P['steel'], P['case_dark'])
line((53, 78, 56, 78), P['case_light'])
line((53, 81, 56, 81), P['case_dark'])
rect((48, 90, 55, 92), P['case_dark'])
d.point((49, 90), fill=P['case_light'])
# Narrow rear utility / radiator bay.
metal((63, 64, 75, 96), P['steel'], P['steel_light'], P['seam'])
rect((65, 67, 73, 69), P['case_dark'])
louver(66, 73, 72, 6, 3)
bolt(65, 92)
bolt(72, 92)
# Shared lower cable channel and two small clips.
line((6, 99, 73, 99), P['seam'])
line((6, 100, 73, 100), P['steel_light'])
for x in (18, 55):
    rect((x, 98, x + 2, 101), P['case_dark'])
    line((x, 98, x + 2, 98), P['case_light'])
# Deliberate, sparse paint wear around the exterior only.
line((1, 77, 1, 80), P['wear'])
line((25, 61, 29, 61), P['wear'])
line((76, 86, 76, 89), P['case_dark'])

# Keyboard deck [0,108,80,128]
metal((0, 108, 79, 127), P['case_mid'], P['case_light'], P['case_dark'])
# Left key bank, UV-shared by the left raised key field.
for y in (111, 114, 117, 120):
    for x in (4, 8, 12, 16, 20, 24, 28):
        rect((x, y, x + 2, y + 1), P['key_dark'])
        line((x, y, x + 1, y), P['key_light'])
rect((7, 124, 25, 125), P['key_dark'])
line((8, 124, 24, 124), P['key'])
# Center command strip.
rect((33, 109, 46, 114), P['screen'], P['steel_light'])
line((35, 111, 40, 111), P['phosphor_dim'])
line((42, 111, 44, 111), P['phosphor'])
for x in (35, 40, 44):
    rect((x, 117, x + 1, 119), P['key_dark'], P['key'])
rect((35, 123, 44, 124), P['case_dark'])
rect((38, 122, 40, 125), P['steel_light'])
# Right key bank.
for y in (111, 114, 117, 120):
    for x in (51, 55, 59, 63, 67, 71, 75):
        rect((x, y, x + 2, y + 1), P['key_dark'])
        line((x, y, x + 1, y), P['key_light'])
rect((53, 124, 65, 125), P['key_dark'])
rect((69, 123, 75, 125), P['steel'], P['key'])

# Vent grille [80,16,96,32]
metal((80, 16, 95, 31), P['steel'], P['steel_light'])
louver(82, 19, 93, 4, 3)

# Side console connector panel [96,16,112,32]
metal((96, 16, 111, 31), P['case_dark'], P['steel_light'])
for x in (99, 103, 107):
    rect((x, 20, x + 1, 24), P['cable'], P['steel'])
    d.point((x, 20), fill=P['case_light'])
line((99, 28, 108, 28), P['steel_light'])

# Generic neutral grey metal [112,16,128,40]
metal((112, 16, 127, 39), P['case_mid'], P['case_light'], P['case_dark'])
line((115, 19, 121, 19), P['wear'])
d.point((124, 36), fill=P['case_dark'])

# Generic dark steel [80,32,96,56]
metal((80, 32, 95, 55), P['steel'], P['steel_light'], P['case_dark'])
line((83, 51, 89, 51), P['case_dark'])

# Generic bright edge / bezel [96,32,112,56]
metal((96, 32, 111, 55), P['case_light'], P['edge'], P['case_dark'])
line((99, 35, 106, 35), P['edge'])

# Generic deep metal [112,40,128,64]
metal((112, 40, 127, 63), P['case_dark'], P['steel'], P['seam'])
line((115, 59, 120, 59), P['steel'])

# Cable [80,56,96,80]
metal((80, 56, 95, 79), P['cable'], P['case_dark'], '#161c1c')
line((86, 58, 86, 77), P['steel'])
line((88, 58, 88, 77), P['case_dark'])
for y in (61, 67, 73):
    line((85, y, 89, y), P['case_dark'])

# Auxiliary backplate [96,56,112,80]
metal((96, 56, 111, 79), P['steel'], P['steel_light'], P['case_dark'], bolts=True)
rect((100, 64, 107, 66), P['case_dark'])
line((101, 64, 106, 64), P['case_light'])

# Generic secondary metal [112,64,128,88]
metal((112, 64, 127, 87), P['key_dark'], P['case_mid'], P['case_dark'])
line((115, 67, 122, 67), P['case_mid'])

# Side console top [80,80,96,104]
metal((80, 80, 95, 103), P['case_dark'], P['steel'], P['seam'])
for y in (84, 89, 94):
    for x in (83, 88, 92):
        rect((x, y, x + 1, y + 2), P['key_dark'])
        line((x, y, x + 1, y), P['key'])
line((83, 100, 92, 100), P['steel_light'])

# General key / dark control tile [96,80,112,104]
metal((96, 80, 111, 103), P['key_dark'], P['case_mid'], P['case_dark'])
for y in (85, 91, 97):
    rect((100, y, 107, y + 1), P['case_dark'])
    line((100, y, 106, y), P['steel_light'])

im.save(TEX)

palette = {
    'size': [S, S],
    'mode': 'RGBA',
    'style': 'neutral aged grey-green housing around #5F6058 with charcoal functional parts; restrained wear; deterministic one-pixel details; no noise or gradients',
    'colors': P,
    'regions': {
        'main_sonar': [0, 0, 56, 36],
        'aux_screen': [60, 0, 88, 20],
        'meter': [92, 0, 108, 10],
        'dial': [112, 0, 128, 16],
        'top_cap': [0, 36, 96, 48],
        'keyboard_front_lip': [0, 48, 96, 60],
        'rear_service_panel': [0, 60, 80, 104],
        'keyboard_deck': [0, 108, 80, 128],
        'vent': [80, 16, 96, 32],
        'side_console_front': [96, 16, 112, 32],
        'neutral_metal': [112, 16, 128, 40],
        'dark_steel': [80, 32, 96, 56],
        'bright_trim': [96, 32, 112, 56],
        'deep_metal': [112, 40, 128, 64],
        'cable': [80, 56, 96, 80],
        'aux_backplate': [96, 56, 112, 80],
        'secondary_metal': [112, 64, 128, 88],
        'side_console_top': [80, 80, 96, 104],
        'key_controls': [96, 80, 112, 104],
    },
}
(OUT / 'reference' / 'palette.json').write_text(json.dumps(palette, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Wrote {TEX} ({S}x{S})')
