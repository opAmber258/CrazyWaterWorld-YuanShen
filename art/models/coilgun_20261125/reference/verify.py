"""Confirm side-wall UV isolation and assemble Blockbench playbacks."""
import json
from base64 import b64decode
from collections import Counter

from PIL import Image, ImageChops

from split_uv import ROOT, BASE, FILES, read, write, texels

FRAMES = ROOT/'reference/frames'
# 用户在脚本产出后手工加深的锈蚀区域（图集坐标，右开区间）。只允许在这里与 20 条侧壁外出现贴图差异。
HUMAN_RETOUCH = (113, 8, 222, 197)


def normalize(model):
    """Blockbench 5.2.2 重存工程时会把空 relative_path 写成 null，与脚本早期输出不等价。"""
    for texture in model.get('textures', []):
        if texture.get('relative_path') is None:
            texture['relative_path'] = ''
    return model


def matte(path):
    with Image.open(path) as source:
        frame=source.convert('RGBA').resize((680,510),Image.Resampling.LANCZOS)
    bg=Image.new('RGBA',frame.size,(29,38,47,255))
    bg.alpha_composite(frame)
    return bg.convert('RGB')


def main():
    mapping=read(ROOT/'reference','split_uv.json')
    patches={tuple(m['after']):m for m in mapping}
    assert len(patches)==20
    atlas=(ROOT/'coilgun_tex.png').read_bytes()
    for filename in FILES:
        old=normalize(read(BASE,filename))
        new=normalize(read(ROOT,filename))
        assert b64decode(new['textures'][0]['source'].split(',',1)[1])==atlas
        new['textures'][0]['source']=old['textures'][0]['source']
        occupancy=Counter(point for e in new['elements'] if e['type']=='cube'
                          for f in e['faces'].values() if f['texture']==0 for point in texels(f['uv']))
        for e in new['elements']:
            if '_grille_' not in e['name']:
                continue
            for face in ('north','south'):
                rect=e['faces'][face]['uv']
                info=patches[tuple(rect)]
                assert (e['name'],face)==(info['part'],info['face'])
                assert all(occupancy[point]==1 for point in texels(rect))
                e['faces'][face]['uv']=info['before']
        assert new==old
    new_geo=read(ROOT,'coilgun.geo.json')
    old_geo=read(BASE,'coilgun.geo.json')
    count=0
    for b0,b1 in zip(old_geo['minecraft:geometry'][0]['bones'],new_geo['minecraft:geometry'][0]['bones'],strict=True):
        for c0,c1 in zip(b0.get('cubes',[]),b1.get('cubes',[]),strict=True):
            for side in ('north','south'):
                f1=c1['uv'].get(side)
                if f1 and f1!=c0['uv'][side]:
                    u,v=f1['uv']
                    w,h=f1['uv_size']
                    assert (u,v,u+w,v+h) in patches
                    c1['uv'][side]=c0['uv'][side]
                    count+=1
    assert count==20 and new_geo==old_geo
    assert read(ROOT,'coilgun.animation.json')==read(BASE,'coilgun.animation.json')
    with Image.open(ROOT/'coilgun_tex.png') as revised, Image.open(BASE/'coilgun_tex.png') as initial:
        for m in mapping:
            x,y=m['before'][:2]
            initial_value=initial.getpixel((x,y))
            assert all(revised.getpixel(p)[0] < initial_value[0]-15 and revised.getpixel(p)[3]==255
                       for p in texels(m['after']))
        difference=ImageChops.difference(initial.convert('RGB'),revised.convert('RGB'))
        changed={(x,y) for y in range(difference.height) for x in range(difference.width)
                 if difference.getpixel((x,y))!=(0,0,0)}
        strip_texels=set().union(*(texels([x0-1,y0-1,x1+1,y1+1]) for x0,y0,x1,y1 in
                                   (m['after'] for m in mapping)))  # 含各条 1 像素填充边
        retouch=texels(HUMAN_RETOUCH)
        assert strip_texels<=changed, 'the 20 new side strips must differ from R8'
        assert changed & retouch, 'the human rust retouch must be present'
        assert changed<=strip_texels|retouch, 'unexpected texture change outside strips and retouch'

    for key in ('front','back','side','oblique','armor_left','armor_right'):
        with Image.open(ROOT/f'preview_{key}.png') as image:
            assert image.size==(1200,900) and image.mode=='RGBA' and image.getbbox()
    with Image.open(ROOT/'preview_armor_right.png') as preview, Image.open(FRAMES/'runtime_right.png') as rt:
        difference=ImageChops.difference(preview.convert('RGB'),rt.convert('RGB'))
        differing_pixels=sum(any(p) for p in difference.get_flattened_data())
    with Image.open(ROOT/'preview_armor_right.png') as preview, Image.open(BASE/'preview_armor_right.png') as baseline:
        assert ImageChops.difference(preview.convert('RGB'),baseline.convert('RGB')).getbbox()
    native=read(ROOT/'reference','native_check.json')
    for name in ('single','burst'):
        paths=sorted(FRAMES.glob(f'{name}_[0-9][0-9].png'))
        assert len(paths)==len(native[name]['points'])-1
        frames=[matte(f) for f in paths]
        rest=matte(FRAMES/f'{name}_rest.png')
        durations=[20 if i%2==0 else 30 for i in range(len(frames))]
        rest.save(ROOT/f'preview_{name}.gif',save_all=True,append_images=frames+[rest],
                  duration=[500]+durations+[650],loop=0)
    report_path=ROOT/'validation.json'
    if report_path.exists():
        baseline_report=json.loads(report_path.read_text(encoding='utf-8'))
    else:
        baseline_report=read(BASE,'validation.json')
    baseline_report.pop('user_cube_uvs_preserved',None)
    write(ROOT,'validation.json',baseline_report | {
        'independent_slanted_wall_uv_count': count,'affected_slats':10,
        'other_faces_and_cube_geometry_verified_unchanged':True,
        'runtime_motion_verified_unchanged':True,
        'generic_bedrock_closeup_differing_pixels':differing_pixels,
        'human_retouch_bbox':list(HUMAN_RETOUCH),
        'human_retouch_texel_count':len(changed & retouch),
    })


if __name__=='__main__':
    main()
