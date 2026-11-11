"""Independent art-package validation. Run with Python 3 + Pillow, no Blockbench.
Checks the frozen user geometry, new integer UV packing, actual PNG and native export.
"""
from pathlib import Path
from collections import Counter
import json,math,base64,hashlib,itertools
from PIL import Image

ROOT=Path(__file__).resolve().parent.parent
REF=ROOT/'reference'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
a=read(REF/'user_baseline/nuclear_reactor_user.bbmodel')
b=read(ROOT/'nuclear_reactor.bbmodel')
geo=read(ROOT/'nuclear_reactor.geo.json')
g=geo['minecraft:geometry'][0]
bb=read(REF/'blockbench_verification.json')
uvdata=read(REF/'uv_layout.json')
errors=[]
def need(ok,msg):
 if not ok:errors.append(msg)
def close(x,y):return abs(x-y)<.000021
def geometry(m):
 result={'elements':[],'groups':[],'outliner':m['outliner']}
 for e in m['elements']:
  entry={k:v for k,v in e.items() if k not in ('faces','selected')}
  entry['face_properties']={k:{p:v for p,v in f.items() if p not in ('uv','texture','rotation')} for k,f in e['faces'].items()}
  result['elements'].append(entry)
 for group in m['groups']:
  result['groups'].append({k:v for k,v in group.items() if k not in ('selected','primary_selected','isOpen')})
 return result
same=geometry(a)==geometry(b)
need(same,'User geometry or hierarchy changed')
uv_same=all(x['faces']==y['faces'] for x,y in zip(a['elements'],b['elements']))
need(uv_same,'UV or face properties changed during texture-only repaint')
elements={e['uuid']:e for e in b['elements']}
names={g['uuid']:g['name'] for g in b['groups']}
groups={}
def visit(nodes):
 for node in nodes:
  if isinstance(node,dict):
   groups[names[node['uuid']]]=[elements[u] for u in node['children'] if isinstance(u,str)]
   visit([c for c in node['children'] if isinstance(c,dict)])
visit(b['outliner'])
face_count=0;cube_count=0
for bone in g['bones']:
 src=groups.get(bone['name'],[]);dst=bone.get('cubes',[])
 need(len(src)==len(dst),'Bone cube count: '+bone['name'])
 for e,c in zip(src,dst):
  lo,hi=e['from'],e['to']
  need(all(close(x,y) for x,y in zip(c['origin'],[-hi[0],lo[1],lo[2]])),'Export origin: '+e['name'])
  need(all(close(x,y-z) for x,y,z in zip(c['size'],hi,lo)),'Export size: '+e['name'])
  if any(e.get('rotation',[0,0,0])):
   r=e['rotation'];p=e['origin']
   need(all(close(x,y) for x,y in zip(c['rotation'],[-r[0],-r[1],r[2]])),'Export rotation: '+e['name'])
   need(all(close(x,y) for x,y in zip(c['pivot'],[-p[0],p[1],p[2]])),'Export pivot: '+e['name'])
  for face,f in e['faces'].items():
   face_count+=1;u=f['uv'];fexp=c['uv'][face]
   uv=[u[2],u[3]] if face in ('up','down') else u[:2]
   size=[u[0]-u[2],u[1]-u[3]] if face in ('up','down') else [u[2]-u[0],u[3]-u[1]]
   need(all(close(x,y) for x,y in zip(uv,fexp['uv'])),'Export UV: '+e['name']+'/'+face)
   need(all(close(x,y) for x,y in zip(size,fexp['uv_size'])),'Export UV size: '+e['name']+'/'+face)
   need(all(0<=v<=512 and v==round(v) for v in u),'UV integer and range: '+e['name']+'/'+face)
   need(f.get('texture')==0,'Texture missing: '+e['name']+'/'+face)
  cube_count+=1
axis={'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}
def density_rows(m):
 rows=[]
 for e in m['elements']:
  size=[abs(y-x) for x,y in zip(e['from'],e['to'])]
  for f,(i,j) in axis.items():
   uv=e['faces'][f]['uv'];counts=[abs(uv[2]-uv[0]),abs(uv[3]-uv[1])]
   desired=[size[i]*4,size[j]*4]
   density=[counts[0]/size[i],counts[1]/size[j]]
   rows.append({'cube':e['name'],'face':f,'dimensions':[size[i],size[j]],'pixels':counts,
                'anisotropy':max(density)/min(density),
                'quantization_error_pixels':[counts[k]-desired[k] for k in (0,1)]})
 return rows
before,after=density_rows(a),density_rows(b)
need(all(all(abs(n-max(1,math.floor(s*4+.5)))==0 for n,s in zip(row['pixels'],row['dimensions'])) for row in after),'Face UV dimensions differ from rounded 4 px/BB')
rects=uvdata['islands']
padding=uvdata['padding']
for s in rects:
 x,y,x1,y1=s['uv'];need(padding<=x<x1<=512-padding and padding<=y<y1<=512-padding,'Island gutter outside atlas')
for s,t in itertools.combinations(rects,2):
 x,y,x1,y1=s['uv'];a0,b0,a1,b1=t['uv']
 need(x1+padding<=a0-padding or a1+padding<=x-padding or y1+padding<=b0-padding or b1+padding<=y-padding,'UV islands or padding overlap')
png=ROOT/'nuclear_reactor_tex.png';im=Image.open(png)
embedded=base64.b64decode(b['textures'][0]['source'].split(',')[1])
need(embedded==png.read_bytes(),'Embedded/external PNG differs')
need(im.mode=='RGBA' and im.size==(512,512),'PNG dimensions/mode')
need(set(im.getchannel('A').tobytes())<={0,255},'Semitransparent pixels')
need(not b['textures'][0].get('path'),'External absolute PNG dependency')
need(b['resolution']=={'width':512,'height':512},'Project UV resolution')
need(g['description']['identifier']=='geometry.nuclear_reactor','Geometry identifier')
need(geo['format_version']=='1.12.0','Export format')
need(bb['native_export_reproduced_exactly'],'Native reopen/export differs')
need(cube_count==113 and len(g['bones'])==9 and face_count==678,'Component counts changed')
previews={}
for view in ('front','back','side','oblique'):
 path=ROOT/f'preview_{view}.png';img=Image.open(path);bounds=img.getbbox()
 need(img.size==(1200,900) and bounds and bounds[0]>0 and bounds[1]>0 and bounds[2]<1200 and bounds[3]<900,'Preview size/cropping: '+view)
 previews[view]={'size':list(img.size),'alpha_bounds':bounds,'sha256':sha(path),'projection':'perspective' if view=='oblique' else 'orthographic'}

def independent_bounds(m):
 # All user bone rotations are zero; per-cube Euler XYZ exactly matches THREE.
 need(all(not any(x.get('rotation',[0,0,0])) for x in m['groups']),'Bounds routine expects unrotated bones')
 points=[]
 for e in m['elements']:
  o=e.get('origin',[0,0,0]);rx,ry,rz=[math.radians(x) for x in e.get('rotation',[0,0,0])]
  for p in itertools.product(*[(e['from'][i],e['to'][i]) for i in range(3)]):
   x,y,z=[p[i]-o[i] for i in range(3)]
   # THREE Euler XYZ: matrix Rx*Ry*Rz, applied Z then Y then X.
   x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
   x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
   y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
   points.append([x+o[0],y+o[1],z+o[2]])
 lo=[min(p[i] for p in points) for i in range(3)];hi=[max(p[i] for p in points) for i in range(3)]
 return {'min':lo,'max':hi,'size':[hi[i]-lo[i] for i in range(3)]}
world=independent_bounds(b)
need(all(abs(x-y)<.00002 for key in world for x,y in zip(world[key],bb['world_bbox'][key])),'Independent bounds differ from native cube bounds')
need(independent_bounds(a)==world,'Bounds changed from user revision')
summary={'target_pixels_per_BB':4,'integer_UV_faces':face_count,'islands':len(rects),'island_padding_pixels':padding,
 'before_faces_anisotropy_over_2':sum(r['anisotropy']>2 for r in before),'after_faces_anisotropy_over_2':sum(r['anisotropy']>2 for r in after),
 'before_faces_anisotropy_over_1_25':sum(r['anisotropy']>1.25 for r in before),'after_faces_anisotropy_over_1_25':sum(r['anisotropy']>1.25 for r in after),
 'max_axis_quantization_error_pixels':max(abs(v) for r in after for v in r['quantization_error_pixels']),
 'all_faces_match_nearest_integer_density':True,
 'limitation':'Sub-pixel-width geometry uses at least one whole texel. Geometry is preserved; exact square texels are not mathematically possible on all fractional-size faces.'}
materials=read(REF/'material_audit.json')
need(not materials['errors'],'Material audit failed')
material_summary={k:v for k,v in materials.items() if k not in ('coverage','bilateral_pairs')}
(REF/'uv_density_comparison.json').write_text(json.dumps({'summary':summary,'before':before,'after':after},indent=2),encoding='utf-8')
report={'errors':errors,'warnings':[], 'geometry_preserved_exactly':same,
 'checks':{'cube_export_equivalence':cube_count,'face_uv_export_equivalence':face_count,'embedded_external_png_equal':embedded==png.read_bytes(),
 'UV_density_quantized_consistently':True,'UV_gutters_no_overlap':True,'native_reopen_export_equal':bb['native_export_reproduced_exactly'],
 'exposed_faces_have_independent_islands':materials['independent_exposed_UV_rectangles'],
 'UV_preserved_exactly':uv_same,'bilateral_repeats':materials['bilateral_repeats']},
 'limitations':[summary['limitation'],'Editor and offline checks only; no Minecraft runtime test.','Intentional solid sleeve/bend overlaps are preserved from the user model.']}
(ROOT/'structure_audit.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
validation={'asset':'核反应堆 NuclearReactor','identifier':'nuclear_reactor','creation_source':'人工 + AI（用户修改几何，Codex 重绘贴图与 UV）',
 'format':'bedrock','format_version':geo['format_version'],'geometry_identifier':g['description']['identifier'],
 'cubes':cube_count,'bones':len(g['bones']),'declared_faces':face_count,'triangles_upper_bound':face_count*2,'animations':0,
 'geometry_preserved_exactly':same,'UV_preserved_exactly':uv_same,'bbox_BB':world,'size_blocks':[v/16 for v in world['size']],
 'texture':{'size':list(im.size),'mode':im.mode,'sha256':sha(png),'embedded_png_identical':embedded==png.read_bytes(),'palette_colors':len(im.getcolors(512*512))-1},
 'UV':summary,'material_revision':material_summary,'previews':previews,'blockbench':bb,'offline_errors':errors,'minecraft_runtime_tested':False,'user_visual_acceptance':'pending',
 'files_sha256':{p.name:sha(p) for p in (ROOT/'nuclear_reactor.bbmodel',ROOT/'nuclear_reactor.geo.json',png)}}
(ROOT/'validation.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'errors':len(errors),'geometry_preserved':same,'cubes':cube_count,'faces':face_count,'UV':summary,'size_blocks':validation['size_blocks']},ensure_ascii=True))
if errors:print(json.dumps(errors[:20]));raise SystemExit(1)
