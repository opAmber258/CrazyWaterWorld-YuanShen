"""Read-only asset verification plus regenerated validation.json. Python + Pillow.
Checks bounded Bedrock cuboid serialization, not a full runtime/schema validator.
"""
from pathlib import Path
import json, math, base64, itertools
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
IDENT='advanced_syringe_gun'
def read(name):return json.loads((ROOT/name).read_text(encoding='utf8'))
m=read(IDENT+'.bbmodel');g=read(IDENT+'.geo.json');native=read('reference/native_reopen_validation.json')
es=m['elements'];geom=g['minecraft:geometry'][0];bones=geom['bones'];im=Image.open(ROOT/(IDENT+'_tex.png')).convert('RGBA')
errors=[]
def check(ok,message):
    if not ok:errors.append(message)
check(m['meta']['model_format']=='bedrock','Project is not Bedrock')
check(g['format_version']=='1.12.0','Unexpected geometry version')
check(geom['description']['identifier']=='geometry.'+IDENT,'Wrong identifier')
check(len(es)==sum(len(b.get('cubes',[])) for b in bones),'Export cube count mismatch')
check(len(bones)==len(m['groups']),'Bone count mismatch')
check(all(b.get('cubes') for b in bones),'Empty bone')
check(all(all(b>a for a,b in zip(e['from'],e['to'])) for e in es),'Nonpositive cuboid')
check(all(not any(e.get('rotation',[0,0,0])[:2]) for e in es),'Unexpected non-Z rotation for this audit')
points=[];seen=set();duplicates=[];uv_count=0
for e in es:
    signature=json.dumps([e['from'],e['to'],e.get('rotation'),e.get('origin')])
    if signature in seen:duplicates.append(e['name'])
    seen.add(signature)
    angle=math.radians(e.get('rotation',[0,0,0])[2]);c,s=math.cos(angle),math.sin(angle);p=e['origin']
    for q in itertools.product(*zip(e['from'],e['to'])):
        x,y=q[0]-p[0],q[1]-p[1]
        points.append([p[0]+c*x-s*y,p[1]+s*x+c*y,q[2]])
    for f in e['faces'].values():
        if f.get('texture') is None:continue
        uv_count+=1;x0,y0,x1,y1=f['uv']
        check(0<=min(x0,x1)<max(x0,x1)<=im.width and 0<=min(y0,y1)<max(y0,y1)<=im.height,'UV out of range')
        check(im.crop((min(x0,x1),min(y0,y1),max(x0,x1),max(y0,y1))).getchannel('A').getextrema()==(255,255),'Transparent sampled UV')
check(not duplicates,'Duplicate geometry')
lo=[min(p[a] for p in points) for a in range(3)];hi=[max(p[a] for p in points) for a in range(3)]
size=[b-a for a,b in zip(lo,hi)];blocks=[v/16 for v in size]
check(max(abs(a-b) for a,b in zip(lo,native['native_world_bounds']['min']))<1e-5,'Native minimum bounds differ')
check(max(abs(a-b) for a,b in zip(hi,native['native_world_bounds']['max']))<1e-5,'Native maximum bounds differ')
tex=m['textures'][0]
check(base64.b64decode(tex['source'].split(',',1)[1])==(ROOT/(IDENT+'_tex.png')).read_bytes(),'Embedded/external PNG differs')
check(not any(t.get('path') or t.get('relative_path') for t in m['textures']),'External texture path dependency')
check(im.width==im.height and im.width&(im.width-1)==0,'Texture not square power of two')
check(all(p[3] in [0,255] and (p[3]!=0 or p[:3]==(0,0,0)) for p in im.getdata()),'Alpha/transparency pixels invalid')
for key in ['geometry_exact_roundtrip','elements_exact_roundtrip','groups_exact_roundtrip','texture_source_exact','texture_embedded','preexisting_projects_retained']:
    check(native[key],key+' failed')
check(native['texture_errors']==[0],'Native texture error')
preview_names=['front','back','side','oblique','top','rear_oblique']
for name in preview_names:check(Image.open(ROOT/('preview_'+name+'.png')).size==(1200,900),'Preview size '+name)
declared_faces=sum(len(c['uv']) for b in bones for c in b.get('cubes',[]))
check(declared_faces==uv_count,'Export face count mismatch')
report={'asset':'高级注射器枪 AdvancedSyringeGun','identifier':IDENT,'date':'2026-11-13','passed':not errors,
 'display_name_zh':'高级注射器枪','display_name_en':'Advanced Syringe Gun',
 'creation_source':'人工 + AI（TakaraMiyuki + GPT-6）',
 'contributors':{'human':['TakaraMiyuki'],'ai':[{'name':'GPT-6','tool':'Codex + Blockbench 5.2.2',
 'role':'初始几何、贴图、UV、原生预览、导出及验证'}],
 'division':'人工提供参考并保存机匣高度微调；AI 保留人工保存版本，重出几何、六向预览与验证。'},
 'format':'Bedrock Entity','geometry_version':g['format_version'],'editor':'Blockbench '+native['editor'],
 'counts':{'cubes':len(es),'bones':len(bones),'declared_export_faces':declared_faces,'textures':1,'frames':1,'duplicate_cuboids':len(duplicates)},
 'bounds_editor_units':{'min':lo,'max':hi,'size':size},'bounds_blocks':blocks,
 'texture':{'mode':Image.open(ROOT/(IDENT+'_tex.png')).mode,'size':list(im.size),'pixels':im.width*im.height,
 'base_rgba_bytes':im.width*im.height*4,'partial_alpha_pixels':0,'all_sampled_uv_opaque':True,'embedded_equals_external':True},
 'previews':{'names':preview_names,'size':[1200,900],'renderer':'Native Blockbench Preview / Three.js',
 'orthographic':['front','back','side','top'],'perspective':['oblique','rear_oblique'],'ui':False,'grid':False},
 'cleanup':{'candidate_faces':18,'removed_faces':0,'reason':'Strict RGBA comparison differed in side and perspective samples; all candidates restored.',
 'comparison':'reference/face_cleanup_comparison.json'},
 'native_reopen':'reference/native_reopen_validation.json','errors':errors,
 'checks':{'native_reopen':True,'native_geometry_roundtrip_exact':native['geometry_exact_roundtrip'],'positive_dimensions':True,
 'within_cost_budget':len(es)<=90 and len(bones)<=10 and declared_faces<=540,'no_external_texture_dependency':True},
 'limits':['No Minecraft client test or game integration','No held/GUI/display runtime test or animation',
 'No measured draw calls, overdraw, GPU memory or FPS','Not a complete Bedrock schema validator',
 'Opaque amber is a pixel-art representation; 12 visible subdivisions do not encode inventory capacity']}
(ROOT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'passed':report['passed'],'errors':errors,'cubes':len(es),'bones':len(bones),'faces':declared_faces,
 'size_units':size,'size_blocks':blocks},ensure_ascii=False))
raise SystemExit(bool(errors))
