"""Offline structural/coverage check; does not certify the Minecraft renderer."""
import base64, io, itertools, json, math
from pathlib import Path
from PIL import Image, ImageChops

root=Path(__file__).resolve().parents[1]
p=json.loads((root/'diving_suit.bbmodel').read_text(encoding='utf-8'))
geo=json.loads((root/'diving_suit.geo.json').read_text(encoding='utf-8'))['minecraft:geometry'][0]
names={g['uuid']:g['name'] for g in p['groups']}
parents={child:names[g['uuid']] for g in p['outliner'] for child in g['children']}
classic={'armorHead':([-4,24,-4],[4,32,4]),'armorBody':([-4,12,-2],[4,24,2]),'armorRightArm':([-8,12,-2],[-4,24,2]),'armorLeftArm':([4,12,-2],[8,24,2]),'armorRightLeg':([-4,0,-2],[0,12,2]),'armorLeftLeg':([0,0,-2],[4,12,2])}
coverage={}
for bone,(a,b) in classic.items():
    cubes=[e for e in p['elements'] if parents[e['uuid']]==bone and not any(e.get('rotation',[0,0,0]))]
    axes=[sorted(set([a[i],b[i]]+[v for e in cubes for v in [e['from'][i],e['to'][i]] if a[i]<v<b[i]])) for i in range(3)]
    midpoints=[[(x+y)/2 for x,y in zip(ax,ax[1:])] for ax in axes]
    missing=sum(not any(all(e['from'][i]<=pt[i]<=e['to'][i] for i in range(3)) for e in cubes) for pt in itertools.product(*midpoints))
    coverage[bone]={'classic_box':[a,b],'uncovered_cells':missing,'passed':missing==0}

points=[]
for e in p['elements']:
    for corner in itertools.product(*zip(e['from'],e['to'])):
        origin=e.get('origin',[0,0,0]);v=[corner[i]-origin[i] for i in range(3)]
        # All rotated pieces in this asset rotate around X only.
        rx,ry,rz=e.get('rotation',[0,0,0]);assert ry==rz==0
        r=math.radians(rx);v=[v[0],v[1]*math.cos(r)-v[2]*math.sin(r),v[1]*math.sin(r)+v[2]*math.cos(r)]
        points.append([v[i]+origin[i] for i in range(3)])
bounds=[[min(v[i] for v in points) for i in range(3)],[max(v[i] for v in points) for i in range(3)]]
size=[bounds[1][i]-bounds[0][i] for i in range(3)]
png=Image.open(root/'diving_suit_tex.png').convert('RGBA')
embedded=Image.open(io.BytesIO(base64.b64decode(p['textures'][0]['source'].split(',',1)[1]))).convert('RGBA')
diff=ImageChops.difference(png,embedded)
same=not any(ch.getbbox() for ch in diff.split())
out_uv=[(e['name'],f) for e in p['elements'] for f,data in e['faces'].items() if any(not 0<=x<=256 for x in data['uv'])]
disabled=sum(data.get('texture') is None for e in p['elements'] for data in e['faces'].values())
fallback=sum(data.get('texture') is False for e in p['elements'] for data in e['faces'].values())
active=sum(len(c['uv']) for b in geo['bones'] for c in b.get('cubes',[]))
checks={'player_body_coverage':all(c['passed'] for c in coverage.values()),'embedded_texture_matches_png':same,'uv_within_atlas':not out_uv,'positive_cube_sizes':all(all(b>a for a,b in zip(e['from'],e['to'])) for e in p['elements']),'identifier':geo['description']['identifier']=='geometry.diving_suit','cube_counts_match':len(p['elements'])==sum(len(b.get('cubes',[])) for b in geo['bones']),'texture_rgba_power_of_two':png.size==(256,256),'four_previews':all(Image.open(root/f'preview_{s}.png').size==(1200,900) for s in ['front','back','side','oblique'])}
report={'asset':'潜水服 DivingSuit','source':'AI / Codex + Blockbench MCP','blockbench':'5.2.2','mcp':'1.10.1','format':'bedrock','format_version':'1.12.0','identifier':geo['description']['identifier'],'cubes':len(p['elements']),'bones':len(geo['bones']),'declared_faces':active,'disabled_internal_faces':disabled,'single_texture_fallback_faces':fallback,'texture_size':list(png.size),'transformed_bounds':bounds,'size_bb':size,'size_blocks':[v/16 for v in size],'coverage':coverage,'checks':checks,'passed':all(checks.values()),'editor_views_checked':['front','side','back','oblique'],'project_reopen_checked':True,'minecraft_tested':False,'code_integrated':False,'limitations':['Coverage is a geometric check of classic wide-arm base body boxes in neutral pose; not a renderer or skin-layer visibility test.','Slim, running, crouching, swimming and other equipment interactions need game testing.','Static audit AABBs omit rotated corners; transformed bounds above include the three X-rotated visor elements.','No draw-call or FPS measurements.','Native offscreen cleanup reported onContextMenu is not defined; tool registry showed only main viewport afterwards.']}
(root/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'passed':report['passed'],'checks':checks,'size_bb':size,'declared_faces':active,'disabled_internal_faces':disabled},ensure_ascii=False))
if not report['passed']:raise SystemExit(1)
