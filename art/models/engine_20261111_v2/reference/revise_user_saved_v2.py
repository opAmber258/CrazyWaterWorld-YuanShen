from pathlib import Path
import base64
import copy
import io
import json
import os
import uuid

from revise_user_saved_v2_specs import CUBE_SPECS, FACE_KEYS, ATLAS_SIZE, ATLAS_GAP, C, REVISION_NAMES, build_layout
from revise_user_saved_v2_paint import render_atlas

ROOT = Path(__file__).resolve().parent.parent
REF = Path(__file__).resolve().parent
PROJECT = ROOT / 'engine.bbmodel'
TEXTURE = ROOT / 'engine_tex.png'
UV_LAYOUT = REF / 'uv_layout.json'
PALETTE = REF / 'palette.json'
BASE_MANIFEST = REF / 'user_saved_geometry_manifest.json'
REVISION_AUDIT = REF / 'user_pass_revision_audit.json'
REVISION_NAMESPACE = 'cwys-engine/user-pass-v2/'
GROUP_ORDER = ['engine','housing','front_vents','control_panel','black_duct','base_skirt','top_rigging','rear_rotor']

def load_project():
    return json.loads(PROJECT.read_text(encoding='utf-8'))

def verify_base(elements, manifest):
    by_uuid = {e['uuid']: e for e in elements}
    for rec in manifest['elements']:
        e = by_uuid.get(rec['uuid'])
        if e is None or e.get('name') != rec['name']:
            raise RuntimeError(f"base element missing or renamed: {rec['name']}")
        if e.get('from') != rec['from'] or e.get('to') != rec['to']:
            raise RuntimeError(f"base bounds changed: {rec['name']}")
        if e.get('rotation', [0,0,0]) != rec['rotation']:
            raise RuntimeError(f"base rotation changed: {rec['name']}")
        if e.get('origin', []) != rec['origin']:
            raise RuntimeError(f"base origin changed: {rec['name']}")

def strip_revision(data):
    remove_names = set(REVISION_NAMES) | {'side_right_frame_front'}
    old_uuids = {e['uuid'] for e in data['elements'] if e.get('name') in remove_names}
    data['elements'] = [e for e in data['elements'] if e.get('name') not in remove_names]
    def clean(nodes):
        out = []
        for node in nodes:
            if isinstance(node, str):
                if node not in old_uuids: out.append(node)
            elif isinstance(node, dict):
                node = copy.deepcopy(node)
                node['children'] = clean(node.get('children', []))
                out.append(node)
        return out
    data['outliner'] = clean(data.get('outliner', []))
    return data

def find_group(nodes, group_uuid):
    for node in nodes:
        if isinstance(node, dict):
            if node.get('uuid') == group_uuid: return node
            found = find_group(node.get('children', []), group_uuid)
            if found is not None: return found
    return None

def add_revision(data):
    groups = {g['name']: g for g in data['groups']}
    for spec in CUBE_SPECS:
        uid = str(uuid.uuid5(uuid.NAMESPACE_URL, REVISION_NAMESPACE + spec['name']))
        element = {
            'name': spec['name'], 'box_uv': False, 'render_order': 'default', 'locked': False,
            'export': True, 'scope': 0, 'allow_mirror_modeling': True,
            'from': spec['from'], 'to': spec['to'], 'autouv': 0,
            'color': GROUP_ORDER.index(spec['group']) % 8,
            'origin': spec['origin'], 'faces': {}, 'type': 'cube', 'uuid': uid,
        }
        if any(abs(v) > 1e-9 for v in spec['rotation']):
            element['rotation'] = spec['rotation']
        data['elements'].append(element)
        parent = find_group(data['outliner'], groups[spec['group']]['uuid'])
        if parent is None: raise RuntimeError(f"missing outliner group: {spec['group']}")
        parent.setdefault('children', []).append(uid)

def apply_uvs(elements, layout):
    for e in elements:
        for face in FACE_KEYS:
            info = layout['faces'][f"{e['name']}|{face}"]
            f = e.setdefault('faces', {}).setdefault(face, {})
            f['uv'] = info['rect']
            f['texture'] = 0

def main():
    data = load_project()
    manifest = json.loads(BASE_MANIFEST.read_text(encoding='utf-8'))
    verify_base(data['elements'], manifest)
    data = strip_revision(data)
    add_revision(data)
    layout = build_layout(data['elements'])
    image = render_atlas(layout)
    bio = io.BytesIO(); image.save(bio, format='PNG'); texture_bytes = bio.getvalue()
    apply_uvs(data['elements'], layout)
    verify_base(data['elements'], manifest)
    if len(data['groups']) != 8:
        raise RuntimeError('group count changed')
    data['resolution'] = {'width': ATLAS_SIZE, 'height': ATLAS_SIZE}
    data['ai_agents'] = 'Codex + Blockbench 5.2.2; user hand-edit preserved'
    tex = data['textures'][0]
    tex['width'] = ATLAS_SIZE; tex['height'] = ATLAS_SIZE
    tex['uv_width'] = ATLAS_SIZE; tex['uv_height'] = ATLAS_SIZE
    tex['path'] = ''; tex['relative_path'] = ''
    tex['internal'] = True; tex['saved'] = True
    tex['source'] = 'data:image/png;base64,' + base64.b64encode(texture_bytes).decode('ascii')
    uv_out = json.dumps(layout, ensure_ascii=False, indent=2) + '\n'
    bb_out = json.dumps(data, ensure_ascii=False, indent=2) + '\n'
    palette = {
        'size': [ATLAS_SIZE, ATLAS_SIZE], 'mode': 'RGBA',
        'style': 'low-poly industrial olive-grey engine with user-preserved asymmetry, restrained 1 BB chamfers and directional wear',
        'default_px_per_bb': 2.0, 'hero_px_per_bb': 2.5, 'edge_px_per_bb': 1.5,
        'atlas_gap_px': ATLAS_GAP,
        'pattern_face_islands': layout['pattern_island_count'],
        'shared_solid_islands': layout['shared_solid_island_count'],
        'colors': C,
        'notes': [
            'Patterned faces use independent islands sized from the current user-edited face dimensions.',
            'Hidden and narrow faces share small solid islands; no patterned face is stretched across a different aspect ratio.',
            'Surface detail follows seams, ribs, handles, edges and gravity; there is no random speckle or gradient.',
            'The user-saved 75-cube geometry is preserved; this revision adds detail cubes and remaps UVs only.',
        ],
    }
    max_error = max((v['aspect_error_percent'] for v in layout['faces'].values() if not v['shared']), default=0.0)
    audit = {
        'method': 'additive revision from user-saved 75-cube project',
        'baseline_manifest': BASE_MANIFEST.name,
        'baseline_aggregate_geometry_sha256': manifest['aggregate_geometry_sha256'],
        'base_elements_verified_unchanged': True,
        'preserved_base_cubes': len(manifest['elements']),
        'added_elements': len(CUBE_SPECS),
        'final_elements': len(data['elements']),
        'final_groups': len(data['groups']),
        'pattern_islands': layout['pattern_island_count'],
        'shared_solid_islands': layout['shared_solid_island_count'],
        'faces': layout['face_count'],
        'atlas_size': ATLAS_SIZE,
        'atlas_occupied_height': layout['atlas_occupied_height'],
        'max_pattern_aspect_error_percent': round(max_error, 4),
    }
    texture_tmp = TEXTURE.with_suffix('.png.new')
    project_tmp = PROJECT.with_suffix('.bbmodel.new')
    texture_tmp.write_bytes(texture_bytes)
    project_tmp.write_text(bb_out, encoding='utf-8')
    os.replace(texture_tmp, TEXTURE)
    os.replace(project_tmp, PROJECT)
    UV_LAYOUT.write_text(uv_out, encoding='utf-8')
    PALETTE.write_text(json.dumps(palette, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    REVISION_AUDIT.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(audit, ensure_ascii=False, indent=2))
    print(TEXTURE); print(PROJECT); print(UV_LAYOUT); print(PALETTE)

if __name__ == '__main__':
    main()
