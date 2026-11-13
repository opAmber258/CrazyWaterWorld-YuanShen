"""Independent offline checks of the editable project and native Bedrock export."""
from pathlib import Path
import json, math, itertools, hashlib, base64, io
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
b=json.loads((ROOT/'large_steel_cabinet.bbmodel').read_text(encoding='utf8'))
g=json.loads((ROOT/'large_steel_cabinet.geo.json').read_text(encoding='utf8'))
geo=g['minecraft:geometry'][0];errors=[];checks={}
def check(name,ok):
 checks[name]=bool(ok)
 if not ok:errors.append(name)
def near(a,b,tol=1e-4):return len(a)==len(b) and all(abs(x-y)<=tol for x,y in zip(a,b))
check('geometry_identifier',geo['description']['identifier']=='geometry.large_steel_cabinet')
check('format',b['meta']['model_format']=='bedrock' and g['format_version']=='1.12.0')
elems={e['uuid']:e for e in b['elements']};groups={x['uuid']:x for x in b['groups']};bymap={}
def walk(node,parent=None):
 if isinstance(node,str):bymap.setdefault(parent,[]).append(elems[node]);return
 group=groups[node['uuid']]
 for child in node.get('children',[]):walk(child,group['name'])
for node in b['outliner']:walk(node)
nativebones={z['name']:z for z in geo['bones']}
check('cube_count',len(elems)==sum(len(z.get('cubes',[])) for z in geo['bones'])==103)
check('bone_count',len(groups)==len(nativebones)==10)
for name,cubes in bymap.items():
 out=nativebones[name]['cubes'];check('bone_'+name+'_count',len(cubes)==len(out))
 for e,c in zip(cubes,out):
  a=e['from'];z=e['to']
  check(e['name']+'_origin',near(c['origin'],[-z[0],a[1],a[2]]))
  check(e['name']+'_size',near(c['size'],[z[i]-a[i] for i in range(3)]))
  check(e['name']+'_positive_size',all(z[i]>a[i] for i in range(3)))
  if e.get('rotation'):
   check(e['name']+'_rotation',near(c['rotation'],[-e['rotation'][0],-e['rotation'][1],e['rotation'][2]]))
   check(e['name']+'_pivot',near(c['pivot'],[-e['origin'][0],e['origin'][1],e['origin'][2]]))
  for face,uv in e['faces'].items():
   r=uv['uv'];v=c['uv'][face];p=v['uv'];s=v['uv_size']
   norm=[min(p[0],p[0]+s[0]),min(p[1],p[1]+s[1]),max(p[0],p[0]+s[0]),max(p[1],p[1]+s[1])]
   check(e['name']+'_'+face+'_UV',near(r,norm) and min(r)>=0 and max(r)<=512)
check('embedded_texture_path_free',len(b['textures'])==1 and not b['textures'][0].get('path') and b['textures'][0]['source'].startswith('data:image/png;base64,'))
png=(ROOT/'large_steel_cabinet_tex.png').read_bytes();im=Image.open(io.BytesIO(png))
check('texture_rgba_power_of_two',im.mode=='RGBA' and im.size==(512,512))
embedded=Image.open(io.BytesIO(base64.b64decode(b['textures'][0]['source'].split(',',1)[1]))).convert('RGBA')
check('embedded_texture_matches_external',embedded.size==im.size and embedded.tobytes()==im.tobytes())
check('transparent_pixels_are_zero_rgba',all(px==(0,0,0,0) for px in im.get_flattened_data() if px[3]==0))
pts=[]
for e in elems.values():
 for corner in itertools.product(*zip(e['from'],e['to'])):
  x,y,z=corner;ox,oy,oz=e['origin'];ang=math.radians(e.get('rotation',[0,0,0])[2])
  pts.append((ox+(x-ox)*math.cos(ang)-(y-oy)*math.sin(ang),oy+(x-ox)*math.sin(ang)+(y-oy)*math.cos(ang),z))
lo=[min(p[i] for p in pts) for i in range(3)];hi=[max(p[i] for p in pts) for i in range(3)]
size=[hi[i]-lo[i] for i in range(3)]
check('user_size_3_by_2_5',near([size[0],size[1]],[48,40]))
check('centered_x_grounded',abs(lo[0]+hi[0])<1e-4 and abs(lo[1])<1e-4)
for n in ['front','back','side','oblique']:
 with Image.open(ROOT/f'preview_{n}.png') as p:check('preview_'+n+'_1200x900',p.size==(1200,900))
warnings=[
 'Shared UV tiles for identical plain material faces are intentional; editing one shared tile changes all matching faces.',
 'Initial native save/reopen changed some numeric values by serialization rounding; normalized geometry and UV checks use 0.0001 BB tolerance.',
 'Five door pivots are prepared for future animation, but no opening animation, interior presentation, interaction, collision or Minecraft runtime integration is delivered.',
 'Window is opaque stylized dark glass on a closed door; true transparency and internal contents are outside this asset scope.'
]
if abs(lo[2]+hi[2])>=1e-4:warnings.append('User-saved rear mount extension is preserved: Z center offset is '+str((lo[2]+hi[2])/2)+' BB. No automatic recentering was applied.')
baseline=ROOT/'reference/user_revision_baseline.json'
if baseline.exists():
 user=json.loads(baseline.read_text(encoding='utf8'));old={e['uuid']:e for e in user['elements']}
 def comparable(e):return {k:e.get(k) for k in ['name','from','to','origin','rotation','faces','box_uv']}
 check('user_saved_geometry_uv_preserved',set(old)==set(elems) and all(comparable(old[k])==comparable(elems[k]) for k in old))
report={'asset':'large_steel_cabinet','status':'passed' if not errors else 'failed','bbox_bb':{'min':lo,'max':hi,'size_xyz':size},'size_blocks_xyz':[v/16 for v in size],'cubes':len(elems),'bones':len(groups),'declared_faces':len(elems)*6,'texture':{'size':list(im.size),'mode':im.mode,'nominal_pixels_per_block':32},'checks_total':len(checks),'failed_checks':errors,'warnings':warnings,'native_reopen':json.loads((ROOT/'reference/reopen_native_result.json').read_text(encoding='utf8')),'minecraft_tested':False,'previews':'Native Blockbench 5.2.2 offscreen viewport: front/back/side orthographic; oblique perspective','sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in ROOT.iterdir() if p.suffix in ['.png','.bbmodel','.json'] and p.name not in ['validation.json','structure_audit.json']}}
(ROOT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
(ROOT/'structure_audit.json').write_text(json.dumps({'errors':errors,'warnings':warnings,'checks':checks},ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({k:report[k] for k in ['status','bbox_bb','size_blocks_xyz','cubes','bones','checks_total','failed_checks']},ensure_ascii=False))
if errors:raise SystemExit(1)
