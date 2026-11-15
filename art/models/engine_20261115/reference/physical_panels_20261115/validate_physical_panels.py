"""Validate the additive revision against its frozen user-edit baseline."""
import base64,hashlib,json
from pathlib import Path
from PIL import Image
HERE=Path(__file__).resolve().parent;OUT=HERE.parent.parent
read=lambda p:json.loads(p.read_text(encoding='utf-8'))
baseline=read(HERE/'baseline_engine.bbmodel');model=read(OUT/'engine.bbmodel')
manifest=read(HERE/'physical_panel_manifest.json');native=read(OUT/'reference/native_roundtrip.json');motion=read(HERE/'motion_clearance.json')
errors=[]
def check(ok,message):
    if not ok:errors.append(message)
elements={e['uuid']:e for e in model['elements']};groups={g['uuid']:g for g in model['groups']};names={e['name']:e for e in model['elements']}
def members(outliner):
    result={}
    def walk(n,parent=None):
        if isinstance(n,str):result[n]=parent
        else:
            for ch in n['children']:walk(ch,n['uuid'])
    for n in outliner:walk(n)
    return result
original_members=members(baseline['outliner']);current_members=members(model['outliner'])
check(all(elements.get(e['uuid'])==e for e in baseline['elements']),'Original cube geometry/UV/metadata changed')
check(all(groups.get(g['uuid'])==g for g in baseline['groups']),'Original bone definitions changed')
check(all(current_members.get(k)==v for k,v in original_members.items()),'Original cube parent changed')
check(model['animations']==baseline['animations'],'Original animations changed')
for k,v in baseline.items():
    if k not in ['elements','groups','outliner','textures']:check(model.get(k)==v,'Original project field changed: '+k)
check({k:v for k,v in model['textures'][0].items() if k!='source'}=={k:v for k,v in baseline['textures'][0].items() if k!='source'},'Texture metadata changed')
check(len(elements)==428 and len(groups)==37,'Wrong final cube/bone count')
check(len(current_members)==len(elements) and set(current_members)==set(elements),'Unparented/unused cube')
check(len(set(elements)|set(groups))==len(elements)+len(groups),'Duplicate UUID')
check(len(manifest['fasteners'])==76,'Fastener count')
for b in manifest['fasteners']:
    washer,ha,hb=[names[b['name']+'_'+s] for s in ['washer','head_a','head_b']]
    check(all(current_members[e['uuid']]==b['parent_group'] for e in [washer,ha,hb]),'Fastener parent '+b['name'])
    gap=[max(ha['from'][i],hb['from'][i])-min(ha['to'][i],hb['to'][i]) for i in range(3)]
    check(abs(max(gap)-.12)<.00011,'Physical slot missing '+b['name'])
check(sum(p['new_plate_cubes'] for p in manifest['panels'])==20,'Plate cube count')
for e in model['elements']:
    check(all(e['to'][i]>e['from'][i] for i in range(3)),'Non-positive cube '+e['name'])
    for f in e['faces'].values():check(f['texture']==0 and all(0<=v<=256 for v in f['uv']),'Invalid UV/texture '+e['name'])
before=Image.open(HERE/'baseline_engine_tex.png').convert('RGBA');after=Image.open(OUT/'engine_tex.png')
check(after.mode=='RGBA' and after.size==(512,512),'PNG dimensions/format')
check(model['resolution']=={'width':256,'height':256},'Logical UV resolution')
check(base64.b64decode(model['textures'][0]['source'].split(',')[1])==(OUT/'engine_tex.png').read_bytes(),'Embedded texture mismatch')
check(set(after.getchannel('A').tobytes())<={0,255},'Partial alpha')
changed={(x,y) for y in range(512) for x in range(512) if before.getpixel((x,y))!=after.getpixel((x,y))}
check(changed==set(map(tuple,manifest['texture_cleanup_pixels'])),'Unregistered texture change')
for k in ['reopened_project_geometry_match','reopened_project_animation_match','geo_import_reexport_match','animation_import_reexport_match']:check(native[k],k)
check(native['imported_cubes']==428 and native['imported_bones']==37,'Native counts')
check(not motion['intersections'] and motion['added_static_cubes']==248,'New components intersect moving parts')
check(abs(native['bbox']['min'][1])<1e-5,'Ground contact changed')
previews={}
for view in ['front','back','side','oblique']:
    im=Image.open(OUT/f'preview_{view}.png');b=im.getchannel('A').getbbox()
    check(im.size==(1200,900) and b and b[0]>0 and b[1]>0 and b[2]<1200 and b[3]<900,'Preview size/clipping '+view)
    previews[view]={'size':list(im.size),'alpha_bbox':b}
for name,count,size in [('running',60,(960,720)),('rotor',20,(600,600)),('pistons',30,(600,600))]:
    im=Image.open(OUT/f'preview_{name}.gif');check(im.n_frames==count and im.size==size and im.info.get('loop')==0,'GIF '+name)
    previews[name]={'size':list(im.size),'frames':count,'loop':True}
notes=['Original 180 cubes, 18 bone definitions, face UVs, memberships and all three animation clips are unchanged.','17 new cover assemblies use 20 cubes, including four strips around the shaft opening; two existing rear covers are reused.','76 fasteners each have one washer and two head halves separated by a real 0.12-BB slot.','New skin UVs intentionally reuse host face subregions; fasteners share constant-color patches. These overlaps are intentional.','The user-authored 256 logical UV / 512 physical PNG relationship is preserved.']
audit={'stage':'physical inspection covers and fasteners','errors':errors,'warnings':[],'cubes':428,'bones':37,'declared_faces':sum(len(e['faces']) for e in model['elements']),'unchanged_cube_count':180,'unchanged_original_bones':18,'original_uv_and_animation_preserved':True,'new_cover_assemblies':17,'new_cover_cubes':20,'existing_covers_reused':2,'independent_fasteners':76,'fastener_cubes':228,'logical_uv_size':[256,256],'physical_png_size':[512,512],'texture_cleanup_pixels':len(changed),'transformed_bbox':native['bbox'],'size_blocks':[v/16 for v in native['bbox']['size']],'native_roundtrip':native,'motion_clearance':motion,'previews':previews,'notes':notes}
(OUT/'structure_audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8')
validation={'asset':'引擎 Engine','identifier':'geometry.engine','date':'2026-11-15','creation_source':'人工 + AI（TakaraMiyuki + Codex / GPT-6）','contributors':{'human':['TakaraMiyuki'],'ai':[{'name':'GPT-6 (Codex)','tool':'Blockbench 5.2.2 / MCP / Python / Pillow','role':'实体检修板、独立垫片与开槽螺钉，纹理清理和原生导出检查'}],'division':'保留人工调整后的180个cube、原骨骼、UV和动画；本轮增补248个cube和19个静态分组。'},'status':'complete local revision; awaiting user visual review','baseline':{'directory':'reference/physical_panels_20261115','model_sha256':hashlib.sha256((HERE/'baseline_engine.bbmodel').read_bytes()).hexdigest()},'metrics':{k:audit[k] for k in ['cubes','bones','declared_faces','transformed_bbox','size_blocks','logical_uv_size','physical_png_size','new_cover_assemblies','new_cover_cubes','independent_fasteners']},'checks':{'original_cube_uv_bone_and_animation_preserved':True,'embedded_external_texture_match':True,'original_texture_preserved_outside_cleanup':True,'native_roundtrip':native,'sampled_added_part_motion_clearance':motion},'errors':errors,'warnings':[],'previews':previews,'notes':notes,'runtime_verified':False,'not_tested':['Minecraft runtime rendering, animation controllers, placement/collision, propulsion/interactions and performance'],'publishing':{'committed':False,'pushed':False,'discussion_updated':False},'files_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in OUT.iterdir() if p.is_file() and p.name!='validation.json'},'boundary':'已在 Blockbench 原生渲染并导出核验，未进入 Minecraft 验证。'}
(OUT/'validation.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:audit[k] for k in ['errors','warnings','cubes','bones','new_cover_cubes','independent_fasteners','texture_cleanup_pixels']},ensure_ascii=False))
if errors:raise SystemExit(1)
