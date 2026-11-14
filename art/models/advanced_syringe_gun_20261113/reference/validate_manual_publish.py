"""Verify the user's saved 82-cuboid publish version without rebuilding geometry."""
from pathlib import Path
import json,base64
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];REF=ROOT/'reference'
def read(p):return json.loads(p.read_text(encoding='utf8'))
m=read(ROOT/'advanced_syringe_gun.bbmodel');source=read(REF/'manual_publish_input.bbmodel')
n=read(REF/'native_reopen_validation.json');g=read(ROOT/'advanced_syringe_gun.geo.json')['minecraft:geometry'][0]
im=Image.open(ROOT/'advanced_syringe_gun_tex.png').convert('RGBA');errors=[]
def check(ok,msg):
 if not ok:errors.append(msg)
check(m['elements']==source['elements'],'Manual geometry or UV changed')
def semantic(groups):return [{k:v for k,v in x.items() if k not in ['primary_selected','selected','isOpen']} for x in groups]
check(semantic(m['groups'])==semantic(source['groups']),'Manual bone data changed')
check(m['textures'][0]['source']==source['textures'][0]['source'],'Manual texture changed')
check(base64.b64decode(m['textures'][0]['source'].split(',',1)[1])==(ROOT/'advanced_syringe_gun_tex.png').read_bytes(),'PNG mismatch')
check(not any(t.get('path') or t.get('relative_path') for t in m['textures']),'External texture dependency')
uv_width=m['textures'][0].get('uv_width',m['resolution']['width']);uv_height=m['textures'][0].get('uv_height',m['resolution']['height'])
check(im.size==(256,256),'Texture dimensions')
es=m['elements'];bones=g['bones'];count=sum(len(b.get('cubes',[])) for b in bones);faces=sum(len(c['uv']) for b in bones for c in b.get('cubes',[]))
check(count==len(es)==82 and len(bones)==8,'Count mismatch');check(all(b.get('cubes') for b in bones),'Empty bone')
check(g['description']['identifier']=='geometry.advanced_syringe_gun','Identifier')
check(all(all(b>a for a,b in zip(e['from'],e['to'])) for e in es),'Nonpositive cuboid')
for e in es:
 for face in e['faces'].values():
  if face.get('texture') is None:continue
  x0,y0,x1,y1=face['uv'];x0,x1=sorted([x0,x1]);y0,y1=sorted([y0,y1])
  check(0<=x0<x1<=uv_width and 0<=y0<y1<=uv_height,'UV bounds')
  check(im.crop((x0*im.width/uv_width,y0*im.height/uv_height,x1*im.width/uv_width,y1*im.height/uv_height)).getchannel('A').getextrema()==(255,255),'Transparent UV sample')
check(all(im.getpixel((x,y))[3] in [0,255] and (im.getpixel((x,y))[3] or im.getpixel((x,y))[:3]==(0,0,0)) for y in range(im.height) for x in range(im.width)),'Invalid transparent pixels')
for k in ['geometry_exact_roundtrip','elements_exact_roundtrip','groups_exact_roundtrip','texture_source_exact','texture_embedded']:check(n[k],k)
check(n['texture_errors']==[0],'Native texture errors')
for view in ['front','back','side','top','oblique','rear_oblique']:check(Image.open(ROOT/('preview_'+view+'.png')).size==(1200,900),'Preview '+view)
bounds=n['native_world_bounds'];dims=[b-a for a,b in zip(bounds['min'],bounds['max'])]
r={'display_name_zh':'高级注射器枪','display_name_en':'Advanced Syringe Gun','date':'2026-11-14',
 'creation_source':'人工 + AI（TakaraMiyuki + GPT-6）','contributors':{'human':['TakaraMiyuki'],
 'ai':[{'name':'GPT-6','tool':'Codex + Blockbench 5.2.2','role':'初始建模和尾环修订；基于最终人工保存版本重出导出、预览和检查'}],
 'division':'人工完成最终几何、UV 和像素贴图调整；本轮 AI 不改变其几何、UV、骨骼变换或贴图像素，仅清理文件路径和编辑器选择标记并重出资源。'},
 'passed':not errors,'errors':errors,'format':'Bedrock Entity 1.12.0','identifier':'geometry.advanced_syringe_gun',
 'counts':{'cubes':count,'bones':len(bones),'faces':faces,'textures':1,'frames':1},'texture':{'mode':'RGBA','size':list(im.size),'logical_uv_size':[uv_width,uv_height],'base_rgba_bytes':im.width*im.height*4},
 'bounds_editor_units':bounds,'size_units':dims,'size_blocks':[v/16 for v in dims],
 'manual_edits_preserved':{'elements_exact':m['elements']==source['elements'],'bone_semantics_exact':semantic(m['groups'])==semantic(source['groups']),
 'embedded_png_exact':m['textures'][0]['source']==source['textures'][0]['source']},
 'normalization':['Removed external texture path / relative_path; retained embedded PNG','Cleared editor-only primary_selected flag'],
 'native_reopen':'reference/native_reopen_validation.json','previews':'six native 1200x900 views, front/back/side/top orthographic, obliques perspective',
 'budget':{'max_cubes':90,'max_faces':540,'max_texture_side':256,'reason':'Preserve the user-authored 256x256 bitmap without downsampling','within_budget':count<=90 and faces<=540 and max(im.size)<=256},'runtime_tested':False,
 'publication_target':'art/models/advanced_syringe_gun_20261113/ (replace existing candidate)',
 'limitations':['No Minecraft render, holding, GUI, gameplay or FPS test','Bounded structural checks; not a complete Bedrock schema validator','Older ring/equality reports are historical; user final edits take precedence']}
(ROOT/'validation.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'passed':not errors,'errors':errors,'counts':r['counts'],'size_blocks':r['size_blocks']}))
raise SystemExit(bool(errors))
