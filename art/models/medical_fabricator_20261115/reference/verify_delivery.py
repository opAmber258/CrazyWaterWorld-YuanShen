"""Verify native project/Bedrock face mappings and create the delivery manifest."""
import base64
import hashlib
import json
import math
from pathlib import Path
from PIL import Image

root=Path(__file__).resolve().parents[1]
name='medical_fabricator'
def read(file):
    return json.loads((root/file).read_text(encoding='utf8'))
def near(left,right):
    assert len(left)==len(right) and all(math.isclose(a,b,abs_tol=.0001) for a,b in zip(left,right)),(left,right)

model=read(name+'.bbmodel')
geometry=read(name+'.geo.json')['minecraft:geometry'][0]
native=read('reference/native_check.json')
desc=geometry['description']
assert desc['identifier']=='geometry.'+name
elements={e['uuid']:e for e in model['elements']}
groups={g['uuid']:g for g in model['groups']}
bones={b['name']:b for b in geometry['bones']}
visited=set()
total=0
for branch in model['outliner']:
    cubes=bones[groups[branch['uuid']]['name']]['cubes']
    assert len(cubes)==len(branch['children'])
    for key,c in zip(branch['children'],cubes):
        assert key not in visited
        visited.add(key)
        e=elements[key]
        a,b=e['from'],e['to']
        near(c['origin'],[-b[0],a[1],a[2]])
        near(c['size'],[y-x for x,y in zip(a,b)])
        if 'rotation' in e:
            rx,ry,rz=e['rotation']
            near(c['rotation'],[-rx,-ry,rz])
            x,y,z=e['origin']
            near(c['pivot'],[-x,y,z])
        assert set(c['uv'])==set(e['faces'])
        for side,f in e['faces'].items():
            u,v,U,V=f['uv']
            tex=c['uv'][side]
            if side in ('up','down'): u,U,v,V=U,u,V,v
            near(tex['uv'],[u,v])
            near(tex['uv_size'],[U-u,V-v])
            total+=1
assert visited==set(elements)
png=root/(name+'_tex.png')
assert base64.b64decode(model['textures'][0]['source'].split(',')[1])==png.read_bytes()
with Image.open(png) as im:
    assert im.mode=='RGBA' and im.size==(512,512) and im.getchannel('A').getextrema()==(0,255)
previews={}
for view in ['front','back','side','oblique']:
    file='preview_'+view+'.png'
    with Image.open(root/file) as im:
        assert im.size==(1200,900) and im.mode=='RGBA'
        x,y,X,Y=im.getbbox()
        assert 0<x<X<1200 and 0<y<Y<900
        previews[view]=[x,y,X,Y]
size=[round(b-a,5) for a,b in zip(native['min'],native['max'])]
audit=read('structure_audit.json')
uv=read('reference/uv_density.json')
assert not (audit['errors'] or audit['warnings'] or uv['errors'] or uv['warnings'])
assert native['reopened_export_equal'] and native['texture_error']==0
report=dict(identifier=desc['identifier'],native=native,world_extent_BB=size,
    extent_blocks_xyz=[round(s/16,5) for s in size],geometry_cubes=len(visited),bones=len(bones),
    editor_export_equivalent_faces=total,actual_and_embedded_png_identical=True,
    texture=dict(width=512,height=512,channels='RGBA',target_pixels_per_block=32,
                 uv_density=uv['summary']),preview_size=[1200,900],preview_alpha_bounds=previews,
    projection=dict(front='orthographic',back='orthographic',side='orthographic',oblique='perspective'),
    structure_errors=0,structure_warnings=0,uv_errors=0,uv_warnings=0,
    minecraft_client_tested=False,files_sha256={})
for file in [name+'.bbmodel',name+'.geo.json',name+'_tex.png']+['preview_'+v+'.png' for v in previews]:
    report['files_sha256'][file]=hashlib.sha256((root/file).read_bytes()).hexdigest()
(root/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print('Verified',len(visited),'cubes,',total,'face mappings and four previews.')

