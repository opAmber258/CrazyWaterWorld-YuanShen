"""Offline delivery audit. Uses only Python standard library and Pillow.
GeckoLib integration and full game behavior are outside this check.
"""
from pathlib import Path
import json,base64,hashlib,itertools,math,io
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];REF=ROOT/'reference'
m=json.loads((ROOT/'diving_suit.bbmodel').read_text(encoding='utf-8'))
geo=json.loads((ROOT/'diving_suit.geo.json').read_text(encoding='utf-8'))
g=geo['minecraft:geometry'][0];elements=m['elements'];errors=[]
def check(name,condition):
    checks[name]=bool(condition)
    if not condition:errors.append(name)
checks={}
byid={e['uuid']:e for e in elements};groups={x['uuid']:x for x in m['groups']};parts={}
for node in m['outliner']:
    parts[groups[node['uuid']]['name']]=[byid[x] for x in node['children']]
def rotate(p,r):
    x,y,z=p
    for axis,ang in enumerate(r):
        t=math.radians(ang);c,s=math.cos(t),math.sin(t)
        if axis==0:y,z=y*c-z*s,y*s+z*c
        elif axis==1:x,z=x*c+z*s,-x*s+z*c
        else:x,y=x*c-y*s,x*s+y*c
    return [x,y,z]
def vertices(e):
    o=e.get('origin',[0,0,0]);r=e.get('rotation',[0,0,0])
    return [[v[i]+o[i] for i in range(3)] for v in [rotate([p[i]-o[i] for i in range(3)],r) for p in itertools.product(*zip(e['from'],e['to']))]]
verts=[p for e in elements for p in vertices(e)]
lo=[min(p[i] for p in verts) for i in range(3)];hi=[max(p[i] for p in verts) for i in range(3)]
size=[hi[i]-lo[i] for i in range(3)]
check('identifier',g['description']['identifier']=='geometry.diving_suit')
check('format_version_matches_curated_bedrock','1.12.0'==geo['format_version'])
check('bone_set',set(parts)=={'armorHead','armorBody','armorRightArm','armorLeftArm','armorRightLeg','armorLeftLeg','armorRightBoot','armorLeftBoot'})
check('bone_count',len(g['bones'])==len(m['groups'])==8)
check('cube_count',sum(len(b.get('cubes',[])) for b in g['bones'])==len(elements)==104)
check('nonempty_bones',all(parts.values()))
check('unique_element_ids',len(byid)==len(elements))
check('each_cube_owned_once',len([x for node in m['outliner'] for x in node['children']])==len(elements))
check('positive_sizes',all(all(b>a for a,b in zip(e['from'],e['to'])) for e in elements))
check('no_duplicate_geometry',len({json.dumps([e['from'],e['to'],e.get('origin'),e.get('rotation',[0,0,0])]) for e in elements})==len(elements))
check('neutral_saved_pose',all(not any(x['rotation']) for x in m['groups']))
check('embedded_texture',len(m['textures'])==1 and m['textures'][0]['source'].startswith('data:image/png;base64,'))
check('portable_texture',all(not t.get('path') and not t.get('relative_path') for t in m['textures']))
atlas=Image.open(ROOT/'diving_suit_tex.png');embedded=Image.open(io.BytesIO(base64.b64decode(m['textures'][0]['source'].split(',',1)[1])))
check('texture_rgba_power_of_two',atlas.mode=='RGBA' and atlas.size==(256,256))
check('embedded_bitmap_matches_png',embedded.convert('RGBA').tobytes()==atlas.tobytes())
check('no_partial_alpha',set(atlas.getchannel('A').tobytes()) <= {0,255})
faces=[f for e in elements for f in e['faces'].values() if f.get('texture') is not None]
check('uv_in_bounds',all(all(0<=v<=256 for v in f['uv']) for f in faces))
check('uv_visible_regions_opaque',all(atlas.crop(tuple(map(int,[min(f['uv'][0],f['uv'][2]),min(f['uv'][1],f['uv'][3]),max(f['uv'][0],f['uv'][2]),max(f['uv'][1],f['uv'][3])]))).getchannel('A').getextrema()==(255,255) for f in faces))
previews={}
for n in ['front','back','side','oblique']:
    im=Image.open(ROOT/f'preview_{n}.png');bbox=im.getchannel('A').getbbox()
    previews[n]={'size':im.size,'foreground_bounds':bbox,'mode':im.mode}
    check('preview_'+n,im.size==(1200,900) and bbox is not None and bbox[0]>0 and bbox[1]>0 and bbox[2]<1200 and bbox[3]<900)

# Sample the canonical Steve volume surfaces at 0.25-unit spacing. This checks fit,
# not general collision-free motion. Whole head / torso / rigid limbs move together.
templates={
 'head':(['armorHead'],[-4,24,-4],[4,32,4]),
 'body':(['armorBody'],[-4,12,-2],[4,24,2]),
 'right_arm':(['armorRightArm'],[-8,12,-2],[-4,24,2]),
 'left_arm':(['armorLeftArm'],[4,12,-2],[8,24,2]),
 'right_leg':(['armorRightLeg','armorRightBoot'],[-4,0,-2],[0,12,2]),
 'left_leg':(['armorLeftLeg','armorLeftBoot'],[0,0,-2],[4,12,2]),
}
def inside(p,e):
    o=e.get('origin',[0,0,0]);r=e.get('rotation',[0,0,0]);q=[p[i]-o[i] for i in range(3)]
    # Every rotated design cube has one active axis; inverse order is immaterial.
    q=rotate(q,[-v for v in r]);q=[q[i]+o[i] for i in range(3)]
    return all(e['from'][i]-1e-5<=q[i]<=e['to'][i]+1e-5 for i in range(3))
fit={}
for name,(bones,l,h) in templates.items():
    points=set()
    for axis in range(3):
        other=[i for i in range(3) if i!=axis]
        for bound in [l[axis],h[axis]]:
            for aa in range(round((h[other[0]]-l[other[0]])*4)+1):
                for bb in range(round((h[other[1]]-l[other[1]])*4)+1):
                    p=[0,0,0];p[axis]=bound;p[other[0]]=l[other[0]]+aa/4;p[other[1]]=l[other[1]]+bb/4;points.add(tuple(p))
    cubes=[c for b in bones for c in parts[b]]
    uncovered=[p for p in points if not any(inside(p,e) for e in cubes)]
    fit[name]={'sample_count':len(points),'uncovered_count':len(uncovered),'uncovered_examples':uncovered[:8]}
    check('sampled_fit_'+name,len(uncovered)==0)
live=json.loads((REF/'live_validation.json').read_text(encoding='utf-8')) if (REF/'live_validation.json').exists() else {}
check('native_export_after_reopen_equal',live.get('reexport_equal') is True)
check('temporary_pose_restored',live.get('pose_restored') is True)
report={'asset':'潜水服 DivingSuit','production':'Human + AI: TakaraMiyuki + Codex / GPT-6.1 Sol; Blockbench 5.2.2 MCP 1.10.1','date':'2026-11-12','status':'pass' if not errors else 'fail','checks':checks,'errors':errors,'bounds_blockbench_units':{'min':lo,'max':hi,'size':size},'dimensions_blocks_at_16_units_per_block':[x/16 for x in size],'cost':{'editor_cubes':len(elements),'export_cubes':sum(len(b.get('cubes',[])) for b in g['bones']),'bones':len(g['bones']),'declared_faces':len(faces),'triangles_upper_bound':2*len(faces),'removed_covered_faces':6*len(elements)-len(faces),'textures':1,'texture_size':[256,256],'texture_pixels':65536,'frames':1,'partial_alpha_pixels':0,'mesh_elements':0},'previews':previews,'neutral_steve_surface_sampling':fit,'live':live,'runtime':{'intended':'Minecraft Java 1.20.1 Forge, wearable armor geometry prepared for GeckoLib-style bone mapping','client_tested':False,'game_integration_included':False,'animation_files':0,'slim_arms_tested':False},'limitations':['Generic audit is not a complete Bedrock/GeckoLib schema validator.','Player surface sampling is finite and neutral; it is not a proof for all animation mods or equipment combinations.','Pose test belongs to the initial version; current hand-edited revision has neutral fit sampling only. No game client or FPS measurement.','Opaque stylized visor intentionally conceals the player face.','Built-in lamp is a painted detail; dynamic lighting and oxygen behavior require game integration.']}
(ROOT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'status':report['status'],'errors':errors,'bounds':report['bounds_blockbench_units'],'cost':report['cost'],'fit':fit},ensure_ascii=False,indent=2))
raise SystemExit(bool(errors))
