"""Recompute reference/uv_density.json from the current project.

Target density is 2 px/BB in the 512x512 atlas (32 px per block of 16 BB).
Face extents come from each cube's local size; parent group scale is not
applied, matching the report's own limitation note.
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
NAME = 'medical_fabricator'
TARGET = 2.0

model = json.loads((ROOT / (NAME + '.bbmodel')).read_text(encoding='utf8'))
with Image.open(ROOT / (NAME + '_tex.png')) as im:
    actual_w, actual_h = im.size
logical_w = model['resolution']['width']
logical_h = model['resolution']['height']
scale = actual_w / logical_w

faces, errors, warnings = [], [], []
for element in model['elements']:
    if element.get('type') != 'cube' or element.get('box_uv'):
        continue
    low, high = element['from'], element['to']
    extent = [high[i] - low[i] for i in range(3)]
    for side, face in element.get('faces', {}).items():
        u1, v1, u2, v2 = face['uv']
        if u2 < u1 or v2 < v1:
            errors.append(dict(code='inverted_uv', where=f"{element['name']}/{side}",
                               detail=dict(uv=face['uv'])))
        if not (0 <= min(u1, u2) and max(u1, u2) <= logical_w
                and 0 <= min(v1, v2) and max(v1, v2) <= logical_h):
            errors.append(dict(code='uv_outside_texture', where=f"{element['name']}/{side}",
                               detail=dict(uv=face['uv'], logical_size=[logical_w, logical_h])))
        if side in ('north', 'south'):
            face_size = [extent[0], extent[1]]
        elif side in ('east', 'west'):
            face_size = [extent[2], extent[1]]
        else:
            face_size = [extent[0], extent[2]]
        rect = [u1 * scale, v1 * scale, u2 * scale, v2 * scale]
        pixels = [rect[2] - rect[0], rect[3] - rect[1]]
        density = [pixels[i] / face_size[i] if face_size[i] else None for i in range(2)]
        aligned = all(abs(value - round(value)) < 1e-9 for value in rect)
        if not aligned:
            warnings.append(dict(code='fractional_pixel_boundary', where=f"{element['name']}/{side}",
                                 detail=dict(actual_pixel_rect=rect)))
        target_px = [max(1, round(face_size[i] * TARGET)) for i in range(2)]
        ratio = max(density) / min(density) if all(density) else None
        faces.append(dict(
            uuid=element['uuid'], cube=element['name'], face=side, texture=face['texture'],
            face_size_BB=[round(v, 4) for v in face_size],
            logical_uv=[u1, v1, u2, v2],
            actual_pixel_rect=[round(v, 4) for v in rect],
            pixels_per_axis=[round(v, 4) for v in pixels],
            density_px_per_BB=[round(v, 4) for v in density] if all(density) else density,
            axis_density_ratio=round(ratio, 4) if ratio is not None else None,
            pixel_aligned=aligned,
            target_rounded_pixels=target_px,
            target_match=[add_up == round(value) for add_up, value in zip(target_px, pixels)],
        ))

ratios = [f['axis_density_ratio'] for f in faces if f['axis_density_ratio']]
report = dict(
    errors=errors,
    warnings=warnings,
    texture_spaces=[dict(index=0, uuid=model['textures'][0]['uuid'],
                         logical=[logical_w, logical_h], actual=[actual_w, actual_h])],
    faces=faces,
    skipped=[],
    summary=dict(
        faces_checked=len(faces),
        fractional_pixel_boundaries=sum(1 for f in faces if not f['pixel_aligned']),
        target_density=TARGET,
        density_basis='local cube extent including inflate; group scale not applied',
        max_axis_density_ratio=max(ratios) if ratios else None,
    ),
    limitations=[
        'Cuboids with explicit per-face UV only; box UV/mesh/rescale/stretches need another inspection path.',
        'Parent group scale is not applied; a scale warning makes physical-density conclusions incomplete.',
        'Only embedded PNG headers or one explicitly supplied PNG provide actual dimensions.',
        'Integer boundaries and target matching do not prove readable materials, no occlusion, gutter safety or perfect square texels on sub-pixel-width geometry.',
    ],
)
(ROOT / 'reference' / 'uv_density.json').write_text(
    json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
print('audited', len(faces), 'faces; errors', len(errors), 'warnings', len(warnings),
      'max ratio', report['summary']['max_axis_density_ratio'])
