"""Regenerate structure_audit.json from the delivered Bedrock geometry.

Bounds are unrotated source-coordinate extents: bone/element transforms and
inflate are not applied, matching the limitations recorded in the report.
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
NAME = 'medical_fabricator'

geometry = json.loads((ROOT / (NAME + '.geo.json')).read_text(encoding='utf8'))
model = json.loads((ROOT / (NAME + '.bbmodel')).read_text(encoding='utf8'))
with Image.open(ROOT / (NAME + '_tex.png')) as im:
    actual = list(im.size)

errors, warnings = [], []
geometries = geometry.get('minecraft:geometry', [])
if len(geometries) != 1:
    errors.append(dict(code='geometry_count', detail=len(geometries)))
geo = geometries[0]
identifier = geo['description'].get('identifier')
if identifier != 'geometry.' + NAME:
    errors.append(dict(code='identifier', detail=identifier))
uv_w = geo['description'].get('texture_width')
uv_h = geo['description'].get('texture_height')
if [uv_w, uv_h] != actual:
    warnings.append(dict(code='uv_space_vs_png', detail=dict(uv=[uv_w, uv_h], png=actual)))

bones = geo.get('bones', [])
cubes = 0
declared_faces = 0
low = [float('inf')] * 3
high = [float('-inf')] * 3
for bone in bones:
    if not bone.get('cubes') and not bone.get('parent'):
        warnings.append(dict(code='empty_root_bone', detail=bone['name']))
    for cube in bone.get('cubes', []):
        cubes += 1
        origin, size = cube['origin'], cube['size']
        # Bedrock stores origin as the minimum corner, with x mirrored against
        # the editor's coordinate system. Report bounds in editor coordinates.
        low[0] = min(low[0], -origin[0] - size[0])
        high[0] = max(high[0], -origin[0])
        for axis in (1, 2):
            low[axis] = min(low[axis], origin[axis])
            high[axis] = max(high[axis], origin[axis] + size[axis])
        for side, face in cube['uv'].items():
            declared_faces += 1
            u, v = face['uv']
            w, h = face.get('uv_size', [0, 0])
            if not (0 <= u and 0 <= v and 0 <= u + w <= uv_w and 0 <= v + h <= uv_h):
                warnings.append(dict(code='uv_outside_texture',
                                     detail=dict(where=f"{bone['name']}/{side}", uv=[u, v, w, h])))

used = {cube['uuid'] if 'uuid' in cube else None for bone in bones for cube in bone.get('cubes', [])}
exported = [e for e in model['elements'] if e.get('export', True)]
if len(exported) != cubes:
    errors.append(dict(code='cube_count_mismatch', detail=dict(project=len(exported), geometry=cubes)))

report = dict(
    input=NAME + '.geo.json',
    kind='bedrock_geometry',
    errors=errors,
    warnings=warnings,
    limitations=[
        'Limited static-model preflight only; not a complete runtime schema or client compatibility test.',
        'Does not solve visibility, z-fighting, texture sampling, resource registration, collision or performance.',
        'Bounds are unrotated source-coordinate extents; bone/element transforms and inflate are not applied.',
    ],
    identifier=identifier,
    uv_space=dict(width=uv_w, height=uv_h, source='Bedrock description'),
    texture_size=dict(width=actual[0], height=actual[1]),
    metrics=dict(geometries=len(geometries), bones=len(bones), cubes=cubes, declared_faces=declared_faces),
    bounds=[[round(v, 5) for v in low], [round(v, 5) for v in high]],
    error_count=len(errors),
    warning_count=len(warnings),
)
(ROOT / 'structure_audit.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
print('bones', len(bones), 'cubes', cubes, 'faces', declared_faces,
      'errors', len(errors), 'warnings', len(warnings), 'bounds', report['bounds'])
