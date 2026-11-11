from pathlib import Path
import base64
import copy
import hashlib
import io
import json
import os
import sys

REF = Path(__file__).resolve().parent
ROOT = REF.parent
sys.path.insert(0, str(REF))

import revise_user_saved_v2_specs as base

PALETTE_V3 = {
    'case_dark': '#414742',
    'case_mid': '#5c625a',
    'case_mid2': '#555b54',
    'case_shadow': '#515752',
    'case_light': '#747b71',
    'case_high': '#858c81',
    'seam': '#3d433e',
    'steel': '#616862',
    'steel_dark': '#4a514c',
    'steel_light': '#7c8379',
    'vent_dark': '#363c37',
    'vent_mid': '#575e57',
    'vent_high': '#7d857b',
    'panel': '#2d332f',
    'panel_edge': '#414842',
    'key': '#979a8e',
    'key_dark': '#50564f',
    'green': '#829c83',
    'teal': '#799c98',
    'window': '#607a7b',
    'amber': '#bd9568',
    'red': '#69322e',
    'red_light': '#87463d',
    'red_dark': '#50302d',
    'rust': '#715342',
    'rust_dark': '#5a4236',
    'rubber': '#313632',
    'label': '#aa9564',
    'white': '#b5b6a7',
    'black': '#2b312d',
    'black_mid': '#333934',
    'black_frame': '#444b45',
    'black_bar': '#535b54',
    'soot': '#3b413c',
    'oil': '#333934',
    'dust': '#625c4f',
    'aux_shell': '#686e65',
    'aux_shell_light': '#80877c',
    'aux_shell_shadow': '#555c55',
    'aux_seam': '#4b524c',
    'aux_panel': '#4f574f',
    'aux_panel_light': '#737b72',
    'aux_panel_dark': '#353c37',
}
base.C.update(PALETTE_V3)

import revise_user_saved_v2_paint as paint

ATLAS_SIZE = 512
ATLAS_GAP = 2
FACE_KEYS = base.FACE_KEYS
AUX_SHELL_UUID = '776dbec3-3345-d831-3a10-455fffaccf49'
AUX_DOOR_NAME = 'black_duct_side_service_door'
AUX_VENT_NAME = 'black_duct_side_vent'

def exact_style_v3(element, face):
    uid = element['uuid']
    name = element['name']
    if uid == AUX_SHELL_UUID:
        return 'aux_shell'
    if name == AUX_DOOR_NAME:
        return 'aux_service_door' if face == 'west' else 'aux_shell'
    if name == AUX_VENT_NAME:
        return 'aux_vent' if face == 'west' else 'aux_shell'
    return base.exact_style(name, face)

def solid_color_v3(style):
    if style == 'aux_shell':
        return base.C['aux_shell']
    if style == 'aux_service_door':
        return base.C['aux_panel']
    if style == 'aux_vent':
        return base.C['aux_panel_dark']
    return base.base_color_for_style(style)

def build_layout_v3(elements):
    pattern_items = []
    shared = {}
    info = {}
    for element in elements:
        uid = element['uuid']
        name = element['name']
        for face in FACE_KEYS:
            key = f'{uid}|{face}'
            wb, hb = base.face_dimensions(element['from'], element['to'], face)
            style = exact_style_v3(element, face)
            pattern = base.is_pattern_face(name, face, style, wb, hb)
            if style in ('aux_shell', 'aux_service_door', 'aux_vent') and wb >= 0.75 and hb >= 0.75 and wb * hb >= 2.0:
                pattern = True
            if pattern:
                scale = base.scale_for_face(name, face, style, wb, hb)
                w, h, err = base.pixel_dimensions(wb, hb, scale)
                pattern_items.append((key, name, uid, face, style, wb, hb, scale, w, h, err))
                info[key] = {
                    'uuid': uid, 'name': name, 'rect': None, 'shared': False, 'style': style,
                    'width_bb': round(wb, 3), 'height_bb': round(hb, 3),
                    'width_px': w, 'height_px': h, 'px_per_bb': round(scale, 3),
                    'aspect_error_percent': round(err, 4),
                }
            else:
                color = solid_color_v3(style)
                shared[(style, color)] = color
                info[key] = {
                    'uuid': uid, 'name': name, 'rect': None, 'shared': True, 'style': style,
                    'width_bb': round(wb, 3), 'height_bb': round(hb, 3),
                    'width_px': 2, 'height_px': 2, 'px_per_bb': None,
                    'aspect_error_percent': None, 'solid_color': color,
                }
    pattern_items.sort(key=lambda item: (-item[9], -item[8], item[1], item[3]))
    x = 0
    y = 0
    row = 0
    pattern_regions = {}
    for key, name, uid, face, style, wb, hb, scale, w, h, err in pattern_items:
        if x + w > ATLAS_SIZE:
            x = 0
            y += row + ATLAS_GAP
            row = 0
        if y + h > ATLAS_SIZE:
            raise RuntimeError(f'UV atlas overflow at {name}|{face}: y={y}, h={h}')
        r = [x, y, x + w, y + h]
        pattern_regions[key] = r
        info[key]['rect'] = r
        x += w + ATLAS_GAP
        row = max(row, h)
    y += row + ATLAS_GAP if row else 0
    x = 0
    shared_regions = {}
    for (style, color), _ in sorted(shared.items()):
        if x + 2 > ATLAS_SIZE:
            x = 0
            y += 4
        if y + 2 > ATLAS_SIZE:
            raise RuntimeError('shared UV atlas overflow')
        r = [x, y, x + 2, y + 2]
        shared_regions[f'{style}|{color}'] = r
        x += 4
    for item in info.values():
        if item['shared']:
            item['rect'] = shared_regions[f"{item['style']}|{item['solid_color']}"]
    return {
        'texture_size': ATLAS_SIZE, 'gap_px': ATLAS_GAP,
        'pattern_island_count': len(pattern_regions), 'shared_solid_island_count': len(shared_regions),
        'face_count': len(info), 'atlas_occupied_height': y + 2,
        'pattern_regions': pattern_regions, 'shared_solid_regions': shared_regions, 'faces': info,
    }

def draw_aux_shell(d, w, h, name, face):
    C = base.C
    d.rectangle((0, 0, w - 1, h - 1), fill=C['aux_shell'])
    if w < 4 or h < 4:
        paint.border(d, w, h, C['aux_shell_light'], C['aux_shell_shadow'])
        return
    paint.border(d, w, h, C['aux_shell_light'], C['aux_shell_shadow'])
    mx = w // 2
    my = h // 2
    paint.rect(d, w, h, 2, 2, mx - 2, my - 2, C['aux_panel'])
    paint.rect(d, w, h, mx + 2, 2, w - 3, my - 2, C['aux_panel'])
    paint.rect(d, w, h, 2, my + 2, mx - 2, h - 3, C['aux_panel'])
    paint.rect(d, w, h, mx + 2, my + 2, w - 3, h - 3, C['aux_shell'])
    paint.line(d, w, h, mx, 2, mx, h - 3, C['aux_seam'])
    paint.line(d, w, h, mx + 1, 2, mx + 1, h - 3, C['aux_shell_light'])
    paint.line(d, w, h, 2, my, w - 3, my, C['aux_seam'])
    paint.line(d, w, h, 2, my + 1, w - 3, my + 1, C['aux_shell_light'])
    paint.rect(d, w, h, 2, 2, w - 3, min(5, h - 4), C['aux_shell_shadow'])
    paint.line(d, w, h, 2, min(6, h - 3), w - 3, min(6, h - 3), C['aux_seam'])
    paint.rect(d, w, h, 2, max(2, h - 6), w - 3, h - 3, C['aux_panel_dark'])
    for x, y in ((4, 4), (w - 6, 4), (4, h - 6), (w - 6, h - 6)):
        paint.rivet(d, w, h, x, y)
    for x in (mx - 5, mx + 5):
        paint.grime(d, w, h, x, 6, max(3, h // 5), 1)
    paint.wear(d, w, h, 3, h - 4, min(6, max(2, w - 3)), True)
    paint.wear(d, w, h, max(3, w - 9), h - 4, min(5, 9), True)

def draw_aux_service_door(d, w, h, name, face):
    C = base.C
    d.rectangle((0, 0, w - 1, h - 1), fill=C['aux_panel'])
    paint.border(d, w, h, C['aux_panel_light'], C['aux_panel_dark'])
    if w < 5 or h < 5:
        return
    paint.rect(d, w, h, 2, 2, w - 3, h - 3, C['aux_panel_dark'])
    paint.rect(d, w, h, 4, 4, w - 5, h - 5, C['aux_panel'])
    paint.line(d, w, h, 4, 3, w - 5, 3, C['aux_panel_light'])
    paint.line(d, w, h, 4, h - 4, w - 5, h - 4, C['aux_panel_dark'])
    for y in range(7, max(8, h - 8), max(8, h // 4)):
        paint.line(d, w, h, 6, y, w - 7, y, C['aux_seam'])
        paint.line(d, w, h, 6, y + 1, w - 7, y + 1, C['aux_shell_light'])
    hx = 7
    paint.rect(d, w, h, hx, h // 2 - 3, hx + 2, h // 2 + 3, C['aux_panel_dark'])
    paint.line(d, w, h, hx, h // 2 - 3, hx + 2, h // 2 - 3, C['aux_shell_light'])
    for x, y in ((3, 3), (w - 5, 3), (3, h - 5), (w - 5, h - 5)):
        paint.rivet(d, w, h, x, y)
    paint.grime(d, w, h, max(5, w // 3), h // 3, max(4, h // 4), 2)
    paint.wear(d, w, h, 4, h - 5, min(6, w - 4), True)

def draw_aux_vent(d, w, h, name, face):
    C = base.C
    d.rectangle((0, 0, w - 1, h - 1), fill=C['aux_panel_dark'])
    paint.border(d, w, h, C['aux_panel_light'], C['aux_seam'])
    if w < 5 or h < 5:
        return
    paint.rect(d, w, h, 3, 3, w - 4, h - 4, C['aux_panel'])
    for y in range(4, max(5, h - 4), max(4, h // 5)):
        paint.line(d, w, h, 4, y, w - 5, y, C['aux_shell_light'])
        paint.line(d, w, h, 4, min(h - 4, y + 1), w - 5, min(h - 4, y + 1), C['aux_seam'])
    paint.line(d, w, h, w // 2, 3, w // 2, h - 4, C['aux_panel_light'])
    paint.wear(d, w, h, max(3, w - 8), h - 4, 4, True)

def draw_style_v3(d, w, h, name, face, style):
    if style == 'aux_shell':
        draw_aux_shell(d, w, h, name, face)
    elif style == 'aux_service_door':
        draw_aux_service_door(d, w, h, name, face)
    elif style == 'aux_vent':
        draw_aux_vent(d, w, h, name, face)
    else:
        paint.draw_style(d, w, h, name, face, style)

def compress_contrast(image):
    px = image.load()
    mix = (95, 99, 94)
    factor = 0.93
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (
                    round(r * factor + mix[0] * (1 - factor)),
                    round(g * factor + mix[1] * (1 - factor)),
                    round(b * factor + mix[2] * (1 - factor)),
                    a,
                )
    return image

def render_atlas_v3(layout):
    from PIL import Image, ImageDraw
    image = Image.new('RGBA', (ATLAS_SIZE, ATLAS_SIZE), (0, 0, 0, 0))
    for key, coords in layout['pattern_regions'].items():
        item = layout['faces'][key]
        w = coords[2] - coords[0]
        h = coords[3] - coords[1]
        tile = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        draw = ImageDraw.Draw(tile)
        draw_style_v3(draw, w, h, item['name'], key.split('|', 1)[1], item['style'])
        image.alpha_composite(tile, (coords[0], coords[1]))
    draw = ImageDraw.Draw(image)
    for key, coords in layout['shared_solid_regions'].items():
        color = key.split('|', 1)[1]
        draw.rectangle((coords[0], coords[1], coords[2] - 1, coords[3] - 1), fill=color)
    return compress_contrast(image)

PROJECT = ROOT / 'engine.bbmodel'
TEXTURE = ROOT / 'engine_tex.png'
UV_LAYOUT = REF / 'uv_layout.json'
PALETTE = REF / 'palette.json'
BASE_MANIFEST = REF / 'user_saved_geometry_manifest_rev2.json'
AUDIT = REF / 'texture_refinement_v3_audit.json'

def verify_geometry(elements, manifest):
    if len(elements) != manifest['element_count']:
        raise RuntimeError(f"element count mismatch: {len(elements)} != {manifest['element_count']}")
    current = {e['uuid']: e for e in elements}
    if len(current) != len(elements):
        raise RuntimeError('duplicate UUIDs in current project')
    for rec in manifest['elements']:
        e = current.get(rec['uuid'])
        if e is None or e.get('name') != rec['name']:
            raise RuntimeError(f"missing/renamed element: {rec['name']} {rec['uuid']}")
        if e.get('from') != rec['from'] or e.get('to') != rec['to']:
            raise RuntimeError(f"bounds changed: {rec['name']}")
        if e.get('rotation', [0, 0, 0]) != rec['rotation']:
            raise RuntimeError(f"rotation changed: {rec['name']}")
        if e.get('origin', []) != rec['origin']:
            raise RuntimeError(f"origin changed: {rec['name']}")

def apply_uvs(elements, layout):
    for e in elements:
        for face in FACE_KEYS:
            item = layout['faces'][f"{e['uuid']}|{face}"]
            target = e.setdefault('faces', {}).setdefault(face, {})
            target['uv'] = item['rect']
            target['texture'] = 0

def main():
    project = json.loads(PROJECT.read_text(encoding='utf-8'))
    manifest = json.loads(BASE_MANIFEST.read_text(encoding='utf-8'))
    verify_geometry(project['elements'], manifest)
    layout = build_layout_v3(project['elements'])
    texture = render_atlas_v3(layout)
    bio = io.BytesIO()
    texture.save(bio, format='PNG')
    texture_bytes = bio.getvalue()
    apply_uvs(project['elements'], layout)
    verify_geometry(project['elements'], manifest)
    if len(project['groups']) != 8:
        raise RuntimeError('group count changed')
    project['resolution'] = {'width': ATLAS_SIZE, 'height': ATLAS_SIZE}
    project['ai_agents'] = 'Codex + Blockbench 5.2.2; user hand-edit preserved; rev2 low-contrast texture pass'
    tex = project['textures'][0]
    tex['width'] = ATLAS_SIZE
    tex['height'] = ATLAS_SIZE
    tex['uv_width'] = ATLAS_SIZE
    tex['uv_height'] = ATLAS_SIZE
    tex['path'] = ''
    tex['relative_path'] = ''
    tex['internal'] = True
    tex['saved'] = True
    tex['source'] = 'data:image/png;base64,' + base64.b64encode(texture_bytes).decode('ascii')
    max_error = max(v['aspect_error_percent'] for v in layout['faces'].values() if not v['shared'])
    aux_faces = [v for v in layout['faces'].values() if v['style'] in ('aux_shell', 'aux_service_door', 'aux_vent')]
    audit = {
        'method': 'texture and UV refinement only; all user geometry preserved',
        'baseline_manifest': BASE_MANIFEST.name,
        'baseline_aggregate_geometry_sha256': manifest['aggregate_geometry_sha256'],
        'geometry_verified_unchanged': True,
        'elements': len(project['elements']),
        'groups': len(project['groups']),
        'faces': layout['face_count'],
        'pattern_islands': layout['pattern_island_count'],
        'shared_solid_islands': layout['shared_solid_island_count'],
        'atlas_size': ATLAS_SIZE,
        'atlas_occupied_height': layout['atlas_occupied_height'],
        'max_pattern_aspect_error_percent': round(max_error, 4),
        'aux_structure': {
            'shell_uuid': AUX_SHELL_UUID,
            'door_element': AUX_DOOR_NAME,
            'vent_element': AUX_VENT_NAME,
            'textured_face_count': len(aux_faces),
            'styles': ['aux_shell', 'aux_service_door', 'aux_vent'],
        },
        'contrast': {
            'dark_palette_floor': PALETTE_V3['black'],
            'dark_structural_line': PALETTE_V3['black_frame'],
            'highlight_ceiling': PALETTE_V3['white'],
            'global_neutral_mix': 0.07,
            'intent': 'raise near-black structural lines, lower bright edge highlights, retain darker recesses only for real cavities and screens',
        },
    }
    palette = {
        'size': [ATLAS_SIZE, ATLAS_SIZE],
        'mode': 'RGBA',
        'style': 'lower-contrast olive-grey industrial low-poly engine; dark green-charcoal structure lines instead of pure black',
        'default_px_per_bb': 2.0,
        'hero_px_per_bb': 2.5,
        'edge_px_per_bb': 1.5,
        'atlas_gap_px': ATLAS_GAP,
        'pattern_face_islands': layout['pattern_island_count'],
        'shared_solid_islands': layout['shared_solid_island_count'],
        'colors': base.C,
        'notes': [
            'Every readable face is mapped from its current hand-edited face dimensions.',
            'Pure-black readings are replaced by dark green-charcoal values; deepest recesses remain only slightly darker.',
            'A global 7 percent neutral blend lowers highlight/shadow separation without flattening material hierarchy.',
            'The user-added auxiliary structure uses olive shell faces plus independent service-door and vent panels.',
            'No random speckle, gradient or camera-dependent lighting is baked into the texture.',
        ],
    }
    uv_text = json.dumps(layout, ensure_ascii=False, indent=2) + '\n'
    model_text = json.dumps(project, ensure_ascii=False, indent=2) + '\n'
    tex_tmp = TEXTURE.with_suffix('.png.new')
    model_tmp = PROJECT.with_suffix('.bbmodel.new')
    tex_tmp.write_bytes(texture_bytes)
    model_tmp.write_text(model_text, encoding='utf-8')
    os.replace(tex_tmp, TEXTURE)
    os.replace(model_tmp, PROJECT)
    UV_LAYOUT.write_text(uv_text, encoding='utf-8')
    PALETTE.write_text(json.dumps(palette, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    AUDIT.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(audit, ensure_ascii=False, indent=2))
    print(TEXTURE)
    print(PROJECT)

if __name__ == '__main__':
    main()
