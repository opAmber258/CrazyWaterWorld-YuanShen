"""Offline checks of the final crawler files; no game compatibility certification."""
from pathlib import Path
import json,math,itertools,base64,io
from collections import Counter
from PIL import Image,ImageChops

root=Path(__file__).resolve().parents[1]
p=json.loads((root/'crawler.bbmodel').read_text(encoding='utf-8'))
document=json.loads((root/'crawler.geo.json').read_text(encoding='utf-8'))
geo=document['minecraft:geometry'][0]
group_names={g['uuid']:g['name'] for g in p['groups']}
parents={};bone_parents={}
def walk(nodes,parent=None):
    for item in nodes:
        if isinstance(item,str):parents[item]=parent
        else:
            bone_parents[item['uuid']]=parent
            walk(item.get('children',[]),item['uuid'])
walk(p['outliner'])
valid_hierarchy=all(e['uuid'] in parents and parents[e['uuid']] in group_names for e in p['elements'])
valid_hierarchy &= all(parent is None or parent in group_names for parent in bone_parents.values())
for gid in group_names:
    seen=set();current=gid
    while current is not None:
        if current in seen:valid_hierarchy=False;break
        seen.add(current);current=bone_parents.get(current)
bb_counts=Counter(group_names[parents[e['uuid']]] for e in p['elements'])
geo_counts={b['name']:len(b.get('cubes',[])) for b in geo['bones']}
bone_names={b['name'] for b in geo['bones']}
neutral_groups=all(not any(g.get('rotation',[0,0,0])) for g in p['groups'])
assert neutral_groups,'This asset checker assumes neutral group rotations.'

points=[]
for e in p['elements']:
    rx,ry,rz=[math.radians(v) for v in e.get('rotation',[0,0,0])]
    pivot=e.get('origin',[0,0,0])
    # Three.js/Blockbench ZYX Euler: X, then Y, then Z applied to each vertex.
    for pt in itertools.product(*zip(e['from'],e['to'])):
        x,y,z=[pt[i]-pivot[i] for i in range(3)]
        y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
        x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
        x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
        points.append([x+pivot[0],y+pivot[1],z+pivot[2]])
bounds=[[min(v[i] for v in points) for i in range(3)],[max(v[i] for v in points) for i in range(3)]]
native=json.loads((root/'reference/native_bounds.json').read_text())['bounds_bb']
bounds_error=max(abs(bounds[j][i]-native[j][i]) for j in range(2) for i in range(3))
size=[bounds[1][i]-bounds[0][i] for i in range(3)]
png=Image.open(root/'crawler_tex.png').convert('RGBA')
embedded=Image.open(io.BytesIO(base64.b64decode(p['textures'][0]['source'].split(',',1)[1]))).convert('RGBA')
diff=ImageChops.difference(png,embedded)
texture_equal=not any(c.getbbox() for c in diff.split())
invalid_uv=[];invalid_texture=[];alpha_holes=[];disabled=0
for e in p['elements']:
    for face,data in e['faces'].items():
        ref=data.get('texture')
        if ref is None:disabled+=1;continue
        if ref not in (0,False):invalid_texture.append((e['name'],face))
        u1,v1,u2,v2=data['uv'];u0,u3=sorted([u1,u2]);v0,v3=sorted([v1,v2])
        if not(0<=u0<u3<=256 and 0<=v0<v3<=256):invalid_uv.append((e['name'],face));continue
        rect=(int(u0),int(v0),int(u3),int(v3))
        if png.getchannel('A').crop(rect).getextrema()[0]!=255:alpha_holes.append((e['name'],face))
previews={}
for name in ['front','back','side','oblique','top']:
    im=Image.open(root/f'preview_{name}.png').convert('RGBA');bb=im.getchannel('A').getbbox()
    previews[name]={'size':list(im.size),'visible_bbox':list(bb) if bb else None,'margin_ok':bool(bb and min(bb[0],bb[1],im.width-bb[2],im.height-bb[3])>=8)}
names=[e['name'] for e in p['elements']]
duplicate_keys=[(parents[e['uuid']],tuple(e['from']),tuple(e['to']),tuple(e.get('origin',[0,0,0])),tuple(e.get('rotation',[0,0,0]))) for e in p['elements']]
declared_faces=sum(len(c['uv']) for b in geo['bones'] for c in b.get('cubes',[]))
checks={
 'identifier':geo['description']['identifier']=='geometry.crawler',
 'format':document['format_version']=='1.12.0' and p['meta']['model_format']=='bedrock',
 'counts_match':len(p['elements'])==91 and len(p['groups'])==16 and all(bb_counts.get(n,0)==geo_counts.get(n,0) for n in bone_names),
 'hierarchy':valid_hierarchy and len(set(group_names.values()))==16 and bone_names==set(group_names.values()),
 'component_budget':len(p['elements'])<=120,
 'no_duplicate_cuboids_in_same_bone':len(set(duplicate_keys))==len(duplicate_keys),
 'positive_cube_dimensions':all(all(b>a for a,b in zip(e['from'],e['to'])) for e in p['elements']),
 'eight_eyes':sum('_Eye_' in n for n in names)==8,
 'six_tail_segments':sum(n.startswith('Tail_Segment_') for n in names)==6,
 'sixteen_fangs':sum('Fang_' in n for n in names)==16,
 'single_256_rgba_texture':len(p['textures'])==1 and png.size==(256,256),
 'embedded_texture_matches_png':texture_equal,
 'uv_bounds':not invalid_uv,
 'opaque_used_faces':not alpha_holes,
 'texture_references':not invalid_texture,
 'disabled_internal_faces':disabled==8 and declared_faces==546-disabled,
 'no_animations':not p.get('animations'),
 'native_obj_bounds_agree':bounds_error<1e-5,
 'uncropped_five_previews':all(v['size']==[1200,900] and v['margin_ok'] for v in previews.values()),
}
report={'asset':'藻鬃爬行者 Crawler','source':'AI / Codex + Blockbench MCP','blockbench_version':'5.2.2','mcp_version':'1.10.1','format':'bedrock','format_version':'1.12.0','identifier':'geometry.crawler','cubes':len(p['elements']),'bones':len(p['groups']),'declared_faces':declared_faces,'disabled_internal_faces':disabled,'texture_size':list(png.size),'nominal_px_per_bb':3,'transformed_bounds':bounds,'size_bb':size,'size_blocks':[x/16 for x in size],'bounds_native_obj_max_error_bb':bounds_error,'checks':checks,'previews':previews,'passed':all(checks.values()),'project_reopen_checked':True,'final_native_geometry_reexport_equal':True,'minecraft_tested':False,'mod_code_changed':False,'limitations':['Neutral static pose only; no locomotion, bite, swimming, runtime bone mapping or collision tests.','Finite structural checks do not certify all geometry interactions or game compatibility.','Generic audit AABBs omit rotations; transformed bounds here are independently matched to the native OBJ exporter.','No draw call or FPS measurement.','MCP offscreen cleanup reported onContextMenu is not defined; tool list showed only main viewport afterwards.']}
(root/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'passed':report['passed'],'checks':checks,'size_bb':size,'faces':declared_faces},ensure_ascii=False))
if not report['passed']:raise SystemExit(1)
