from pathlib import Path
import base64
import json
import uuid
import engine_spec

ROOT = Path(__file__).resolve().parent.parent
TEXTURE = ROOT / 'engine_tex.png'
BBMODEL = ROOT / 'engine.bbmodel'
GEO = ROOT / 'engine.geo.json'
LAYOUT = engine_spec.build_face_layout()

group_order = engine_spec.GROUP_ORDER
group_uuid = {name: str(uuid.uuid5(uuid.NAMESPACE_URL, f'cwys-engine-group/{name}')) for name in group_order}
element_uuid = {s['name']: str(uuid.uuid5(uuid.NAMESPACE_URL, f"cwys-engine-element/{s['name']}")) for s in engine_spec.CUBE_SPECS}

elements = []
for idx, s in enumerate(engine_spec.CUBE_SPECS):
    frm = s['from']
    to = s['to']
    center = [round((frm[i] + to[i]) / 2, 3) for i in range(3)]
    pivot = list(s['pivot']) if s['pivot'] is not None else center
    faces = {}
    for face in engine_spec.FACE_KEYS:
        rect = LAYOUT['regions'][f"{s['name']}|{face}"]['rect']
        faces[face] = {'uv': rect, 'texture': 0}
    element = {
        'name': s['name'], 'box_uv': False, 'render_order': 'default', 'locked': False,
        'export': True, 'scope': 0, 'allow_mirror_modeling': True,
        'from': frm, 'to': to, 'autouv': 0,
        'color': group_order.index(s['group']) % 8,
        'origin': pivot, 'faces': faces, 'type': 'cube', 'uuid': element_uuid[s['name']],
    }
    if any(abs(v) > 1e-9 for v in s['rotation']):
        element['rotation'] = list(s['rotation'])
    elements.append(element)

outliner_children = []
groups_meta = []
for gname in group_order:
    child_ids = [element_uuid[s['name']] for s in engine_spec.CUBE_SPECS if s['group'] == gname]
    child_group = {'uuid': group_uuid[gname], 'isOpen': gname == 'engine', 'children': child_ids}
    groups_meta.append({
        'name': gname, 'uuid': group_uuid[gname], 'export': True, 'locked': False,
        'scope': 0, 'selected': False, 'visibility': True,
        '_static': {'properties': {}, 'temp_data': {}}, 'origin': [0, 0, 0],
        'rotation': [0, 0, 0], 'bedrock_binding': '', 'color': 0, 'children': [],
        'reset': False, 'shade': True, 'mirror_uv': False, 'autouv': 0,
        'isOpen': gname == 'engine', 'primary_selected': gname == 'engine',
    })
    if gname == 'engine':
        root_group = child_group
    else:
        outliner_children.append(child_group)
root_group['children'] = outliner_children

texture_b64 = base64.b64encode(TEXTURE.read_bytes()).decode('ascii')
bb = {
    'meta': {'format_version': '5.0', 'model_format': 'bedrock', 'box_uv': False},
    'name': 'engine', 'model_identifier': 'engine', 'visible_box': [5.0, 3.5, 1.25],
    'variable_placeholders': '', 'multi_file_ruleset': '', 'variable_placeholder_buttons': [],
    'bedrock_animation_mode': 'entity', 'timeline_setups': [], 'unhandled_root_fields': {},
    'ai_used': True, 'ai_agents': 'Codex + Blockbench_5.2.2',
    'resolution': {'width': 512, 'height': 512}, 'elements': elements,
    'groups': groups_meta, 'outliner': [root_group],
    'textures': [{
        'name': 'engine_tex', 'relative_path': '', 'folder': '', 'namespace': '',
        'id': '0', 'group': '', 'scope': 0, 'width': 512, 'height': 512,
        'uv_width': 512, 'uv_height': 512, 'particle': False, 'use_as_default': False,
        'layers_enabled': False, 'sync_to_project': '', 'file_format': 'png',
        'render_mode': 'default', 'render_sides': 'auto', 'wrap_mode': 'limited',
        'pbr_channel': 'color', 'fps': 7, 'force_translucent': False, 'frame_time': 1,
        'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False,
        'visible': True, 'internal': True, 'saved': True,
        'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cwys-engine-texture-v2')),
        'source': 'data:image/png;base64,' + texture_b64,
    }],
}
BBMODEL.write_text(json.dumps(bb, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

bones = [{'name': 'engine', 'pivot': [0, 0, 0]}]
for gname in group_order[1:]:
    bone = {'name': gname, 'parent': 'engine', 'pivot': [0, 0, 0], 'cubes': []}
    for s in engine_spec.CUBE_SPECS:
        if s['group'] != gname:
            continue
        uv = {}
        for face in engine_spec.FACE_KEYS:
            x1, y1, x2, y2 = LAYOUT['regions'][f"{s['name']}|{face}"]['rect']
            uv[face] = {'uv': [x1, y1], 'uv_size': [x2 - x1, y2 - y1]}
        cube = {
            'origin': s['from'],
            'size': [round(s['to'][i] - s['from'][i], 3) for i in range(3)],
            'uv': uv,
        }
        if any(abs(v) > 1e-9 for v in s['rotation']):
            cube['rotation'] = list(s['rotation'])
            cube['pivot'] = list(s['pivot'])
        bone['cubes'].append(cube)
    bones.append(bone)

geo = {
    'format_version': '1.12.0',
    'minecraft:geometry': [{
        'description': {
            'identifier': 'geometry.engine', 'texture_width': 512, 'texture_height': 512,
            'visible_bounds_width': 5.0, 'visible_bounds_height': 3.5,
            'visible_bounds_offset': [0.0, 1.25, 0.0],
        },
        'bones': bones,
    }],
}
GEO.write_text(json.dumps(geo, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"cubes={len(elements)} bones={len(bones)} faces={len(LAYOUT['regions'])}")
print(BBMODEL)
print(GEO)

