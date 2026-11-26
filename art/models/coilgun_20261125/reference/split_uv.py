"""Separate the slat side faces without repacking the user-authored atlas."""
from base64 import b64decode, b64encode
from collections import Counter
from io import BytesIO
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT.parent / 'coilgun_scaled_aged_r8_20261125'
FILES = ('coilgun.bbmodel','coilgun_runtime.bbmodel')


def read(root, name):
    return json.loads((root / name).read_text(encoding='utf-8-sig'))


def write(root, name, value):
    (root/name).write_text(json.dumps(value, ensure_ascii=False, separators=(',',':'))+'\n',encoding='utf-8')


def texels(rect):
    x0,y0,x1,y1 = rect
    return {(x,y) for x in range(min(x0,x1),max(x0,x1)) for y in range(min(y0,y1),max(y0,y1))}


def colors(source, height, direction, index):
    for i in range(height):
        level = i / (height - 1)
        drop = 23 + (3 if direction == 'north' else 0)
        if level < .17:
            drop -= 5
        elif level >= .8:
            drop += 12
        if i == height - 3 - index % 3:
            drop += 4
        yield (*(max(0,channel-drop) for channel in source[:3]),source[3])


def main():
    preview,runtime = [read(BASE,name) for name in FILES]
    raw = (BASE/'coilgun_tex.png').read_bytes()
    atlas = Image.open(BytesIO(raw)).convert('RGBA')
    for model in (preview,runtime):
        assert b64decode(model['textures'][0]['source'].split(',',1)[1]) == raw
    parts = sorted((e for e in runtime['elements'] if '_grille_' in e['name']),key=lambda e:e['name'])
    assert len(parts) == 10
    used = set().union(*(texels(f['uv']) for e in runtime['elements'] if e['type']=='cube'
                         for f in e['faces'].values() if f['texture']==0))
    occupied = set()
    report = []
    changes = {}
    for i,part in enumerate(parts):
        side_changes = {}
        for j,direction in enumerate(('north','south')):
            old = part['faces'][direction]['uv']
            height = round(part['to'][1]-part['from'][1])
            u,v = 2 + 4*(2*i+j), 319
            rect = [u,v,u+1,v+height]
            padded = texels([u-1,v-1,u+2,v+height+1])
            assert not padded & (used | occupied) and all(atlas.getpixel(p)[3]==0 for p in padded)
            occupied.update(padded)
            base_color=atlas.getpixel(tuple(old[:2]))
            strip=list(colors(base_color,height,direction,i))
            for x,y in padded:
                atlas.putpixel((x,y),strip[min(height-1,max(0,y-v))])
            side_changes[direction] = rect
            report.append({'part':part['name'],'face':direction,'before':old,'after':rect})
        changes[part['uuid']] = side_changes

    changed_atlas=BytesIO()
    atlas.save(changed_atlas,format='PNG')
    image=changed_atlas.getvalue()
    for name,model in zip(FILES,(preview,runtime)):
        for e in model['elements']:
            for face,uv in changes.get(e['uuid'],{}).items():
                assert e['faces'][face]['texture']==0
                e['faces'][face]['uv'] = uv
        model['textures'][0]['source']='data:image/png;base64,' + b64encode(image).decode('ascii')
        write(ROOT,name,model)
    (ROOT/'coilgun_tex.png').write_bytes(image)
    write(ROOT/'reference','split_uv.json',report)
    assert len(report) == len({tuple(r['after']) for r in report}) == 20


if __name__=='__main__':
    main()
