"""Validate this bounded static cuboid revision; Python + Pillow."""
from pathlib import Path
import json,base64
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];REF=ROOT/'reference'
def read(p):return json.loads(p.read_text(encoding='utf8'))
m=read(ROOT/'advanced_syringe_gun.bbmodel');b=read(REF/'baseline_advanced_syringe_gun.bbmodel')
g=read(ROOT/'advanced_syringe_gun.geo.json');n=read(REF/'native_reopen_validation.json');plan=read(REF/'revision_manifest.json')
im=Image.open(ROOT/'advanced_syringe_gun_tex.png').convert('RGBA');oldim=Image.open(REF/'baseline_advanced_syringe_gun_tex.png').convert('RGBA')
errors=[]
def check(ok,msg):
    if not ok:errors.append(msg)
base={e['uuid']:e for e in b['elements']};es=m['elements'];kept=[e for e in es if e['uuid'] in base]
check(len(kept)==64 and all(e==base[e['uuid']] for e in kept),'Non-tail elements changed')
check(all(im.getpixel((x,y))==oldim.getpixel((x,y)) for y in range(64) for x in range(64) if oldim.getpixel((x,y))[3]),'Existing texture pixels changed')
check(base64.b64decode(m['textures'][0]['source'].split(',',1)[1])==(ROOT/'advanced_syringe_gun_tex.png').read_bytes(),'Embedded PNG differs')
check(im.size==(64,64),'Texture size')
check(not any(t.get('path') or t.get('relative_path') for t in m['textures']),'Absolute texture dependency')
check(all(all(y>x for x,y in zip(e['from'],e['to'])) for e in es),'Nonpositive volume')
signatures=[json.dumps([e['from'],e['to'],e['origin'],e.get('rotation')]) for e in es]
check(len(set(signatures))==len(signatures),'Duplicate geometry')
for e in es:
    for f in e['faces'].values():
        if f.get('texture') is None:continue
        a,c,d,z=f['uv'];x0,x1=sorted([a,d]);y0,y1=sorted([c,z])
        check(0<=x0<x1<=64 and 0<=y0<y1<=64,'UV bounds')
        check(im.crop((x0,y0,x1,y1)).getchannel('A').getextrema()==(255,255),'Transparent UV sample')
for y in range(64):
    for x in range(64):
        p=im.getpixel((x,y));check(p[3] in [0,255] and (p[3] or p[:3]==(0,0,0)),'Invalid alpha/RGB')
geom=g['minecraft:geometry'][0];bones=geom['bones'];count=sum(len(v.get('cubes',[])) for v in bones)
faces=sum(len(c['uv']) for v in bones for c in v.get('cubes',[]))
check(geom['description']['identifier']=='geometry.advanced_syringe_gun','Identifier')
check(count==len(es)==80 and len(bones)==8 and faces==480,'Geometry counts')
check(all(v.get('cubes') for v in bones),'Empty bone')
for key in ['geometry_exact_roundtrip','elements_exact_roundtrip','groups_exact_roundtrip','texture_source_exact','texture_embedded']:
    check(n[key],key)
check(n['texture_errors']==[0],'Native texture error')
circle=read(REF/'true_circle_check.json') if (REF/'true_circle_check.json').exists() else None
if circle:
    check(circle['equal_diameters_within_float_error'] and abs(circle['width_height_ratio']-1)<1e-6,'Rear ring is not circular in orthographic view')
for view in ['front','back','side','top','oblique','rear_oblique']:
    check(Image.open(ROOT/('preview_'+view+'.png')).size==(1200,900),'Preview '+view)
bounds=n['native_world_bounds'];dims=[h-l for l,h in zip(bounds['min'],bounds['max'])]
report={'display_name_zh':'高级注射器枪','display_name_en':'Advanced Syringe Gun','date':'2026-11-14',
 'revision':'rear circular structure','creation_source':'人工 + AI（TakaraMiyuki + GPT-6）',
 'contributors':{'human':['TakaraMiyuki'],'ai':[{'name':'GPT-6','tool':'Codex + Blockbench 5.2.2',
 'role':'尾部结构修订、局部像素补充、原生导出与验证'}],'division':'以当前人工保存工程为基线，仅重建尾部圆形结构。'},
 'passed':not errors,'errors':errors,'format':'bedrock','geometry_version':g['format_version'],
 'counts':{'cubes':count,'bones':len(bones),'faces':faces,'textures':1,'frames':1},
 'bounds_editor_units':bounds,'size_units':dims,'size_blocks':[v/16 for v in dims],
 'texture':{'size':[64,64],'mode':'RGBA','base_rgba_bytes':16384,'old_opaque_pixels_preserved':True},
 'preserved_non_tail_elements':len(kept),'budget':{'max_cubes':90,'max_faces':540,'within_budget':count<=90 and faces<=540},
 'native_reopen':'reference/native_reopen_validation.json','previews':'six native Blockbench 1200x900 views',
 'cleanup':'No exact duplicate cuboids, nonpositive volumes or empty bones. Two intersecting inset plates form the recessed faceted backing; retained deliberately. No hidden faces deleted.',
 'orthographic_circle_check':circle,
 'game_client_tested':False,'published':False,'limitations':['Not a full Bedrock schema validator','No game render, display/holding, animation or FPS measurement','Unknown original function; circular shape and depth are visual interpretation']}
(ROOT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'passed':not errors,'errors':errors,'size_units':dims,'cubes':count,'faces':faces}))
raise SystemExit(bool(errors))
