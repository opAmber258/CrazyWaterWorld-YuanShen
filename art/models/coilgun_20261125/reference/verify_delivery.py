"""交付自查：在仓库内即可重跑，不依赖 Blockbench、R8 基线或本机私有路径。

    python reference/verify_delivery.py

覆盖：四件套命名与格式、geo 标识/计数、贴图规格与内嵌一致性、预览规格、动画骨骼引用、
20 条独立侧壁 UV、validation.json 自洽性、bbmodel 不含本机路径。
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
IDENT = 'coilgun'


def read(name):
    return json.loads((ROOT / name).read_text(encoding='utf-8-sig'))


def fail(message):
    raise AssertionError(message)


def check_files():
    required = [f'{IDENT}.bbmodel', f'{IDENT}.geo.json', f'{IDENT}_tex.png',
                f'{IDENT}.animation.json', 'README.md', 'validation.json',
                'structure_audit.json', 'reference/palette.json', 'reference/split_uv.json']
    required += [f'preview_{k}.png' for k in ('front', 'back', 'side', 'oblique')]
    missing = [name for name in required if not (ROOT / name).exists()]
    assert not missing, f'缺少交付文件: {missing}'


def check_geo(report):
    geo = read(f'{IDENT}.geo.json')
    assert geo['format_version'] == '1.12.0', geo['format_version']
    body = geo['minecraft:geometry'][0]
    assert body['description']['identifier'] == f'geometry.{IDENT}'
    bones = body['bones']
    cubes = sum(len(b.get('cubes', [])) for b in bones)
    faces = sum(len(c.get('uv', {})) for b in bones for c in b.get('cubes', []))
    assert (len(bones), cubes, faces) == (13, 286, 1670), (len(bones), cubes, faces)
    assert (len(bones), cubes) == (report['bedrock_bones'], report['cube_count'])
    return {b['name']: len(b.get('cubes', [])) for b in bones}


def check_runtime(runtime_cubes):
    runtime = read(f'{IDENT}_runtime.bbmodel')
    assert runtime['meta']['model_format'] == 'bedrock', runtime['meta']['model_format']
    groups = {g['uuid']: g['name'] for g in runtime['groups']}
    elements = {e['uuid']: e for e in runtime['elements']}
    counts = {}
    locators = 0

    def walk(node, bone):
        nonlocal locators
        if isinstance(node, str):
            element = elements[node]
            if element.get('type') == 'locator':
                locators += 1
            elif element.get('type') == 'cube' and bone:
                counts[bone] = counts.get(bone, 0) + 1
            return
        for child in node.get('children', []):
            walk(child, groups[node['uuid']])

    for node in runtime['outliner']:
        walk(node, None)
    assert counts == {k: v for k, v in runtime_cubes.items() if v}, (counts, runtime_cubes)
    assert locators == 1, locators
    assert len(elements) == sum(counts.values()) + locators


def check_texture():
    atlas = Image.open(ROOT / f'{IDENT}_tex.png')
    assert atlas.mode == 'RGBA' and atlas.size == (512, 512), (atlas.mode, atlas.size)
    assert atlas.size[0] & (atlas.size[0] - 1) == 0
    assert atlas.getextrema()[3] == (0, 255), '贴图需同时含完全透明与完全不透明像素'
    raw = (ROOT / f'{IDENT}_tex.png').read_bytes()
    assert atlas.getbbox(), '贴图不可为空'
    for name in (f'{IDENT}.bbmodel', f'{IDENT}_runtime.bbmodel'):
        model = read(name)
        source = model['textures'][0]['source']
        assert source.startswith('data:image/png;base64,'), f'{name} 贴图未内嵌'
        assert base64.b64decode(source.split(',', 1)[1]) == raw, f'{name} 内嵌贴图与 {IDENT}_tex.png 不一致'
        assert any(t.get('name') for t in model['textures'])
        blob = (ROOT / name).read_text(encoding='utf-8')
        for bad in ('C:/', 'C:\\\\', 'file://', 'BarotraumaMinecraft', 'art-vault'):
            assert bad not in blob, f'{name} 含本机路径 {bad}'
    embedded = Image.open(io.BytesIO(base64.b64decode(
        read(f'{IDENT}.bbmodel')['textures'][0]['source'].split(',', 1)[1])))
    assert embedded.size == atlas.size


def check_previews():
    for key in ('front', 'back', 'side', 'oblique', 'armor_left', 'armor_right'):
        with Image.open(ROOT / f'preview_{key}.png') as image:
            assert image.size == (1200, 900), (key, image.size)
            assert image.mode == 'RGBA' and image.getbbox(), key


def check_animations(geo_cubes):
    clips = read(f'{IDENT}.animation.json')['animations']
    assert len(clips) == 4, list(clips)
    for name, clip in clips.items():
        assert name.startswith(f'animation.{IDENT}.')
        assert clip['animation_length'] > 0
        unknown = [bone for bone in clip['bones'] if bone not in geo_cubes]
        assert not unknown, (name, unknown)
    # 运行时工程内的动画必须与交付文件同名同骨骼
    runtime = read(f'{IDENT}_runtime.bbmodel')
    groups = {g['uuid']: g['name'] for g in runtime['groups']}
    runtime_anims = {a['name']: {groups.get(u) for u in a['animators']} for a in runtime['animations']}
    assert set(runtime_anims) == set(clips)
    for name, bones in runtime_anims.items():
        assert bones == set(clips[name]['bones']), (name, bones)


def check_split_uv():
    mapping = read('reference/split_uv.json')
    assert len(mapping) == 20
    rects = {tuple(item['after']) for item in mapping}
    assert len(rects) == 20
    for x0, y0, x1, y1 in rects:
        assert x1 - x0 == 1, ('侧壁 UV 岛应为 1 像素宽', x0, y0, x1, y1)
        assert 0 <= x0 and x1 <= 512 and 0 <= y0 and y1 <= 512


def check_report(report):
    (x0, y0, z0), (x1, y1, z1) = report['bounds_bb']
    sizes = [round((x1 - x0) / 16, 4), round((y1 - y0) / 16, 4), round((z1 - z0) / 16, 4)]
    assert sizes == report['size_blocks'], (sizes, report['size_blocks'])
    assert report['texture_size'] == [512, 512]
    assert report['independent_slanted_wall_uv_count'] == 20
    assert report['emissive_glow_verified'] and report['flash_anchor_error_bb'] < 1e-6
    assert report['minecraft_client_verified'] is False, '接入实测后请更新本字段'
    readme = (ROOT / 'README.md').read_text(encoding='utf-8')
    for value in ('3.719', '3.3885', '6.6034', '286', '13'):
        assert value in readme, f'README 未记录 {value}'


def main():
    report = read('validation.json')
    check_files()
    runtime_cubes = check_geo(report)
    check_runtime(runtime_cubes)
    check_texture()
    check_previews()
    check_animations(runtime_cubes)
    check_split_uv()
    check_report(report)
    print('交付自查全部通过：四件套 + 动画 + 预览 + reference')


if __name__ == '__main__':
    try:
        main()
    except AssertionError as error:
        print('自查失败：', error)
        sys.exit(1)
