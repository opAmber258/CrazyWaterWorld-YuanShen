"""Validate the frozen user-edit texture pass, not old geometry assumptions."""
import base64,hashlib,json
from pathlib import Path
from PIL import Image
HERE=Path(__file__).resolve().parent;OUT=HERE.parent.parent
baseline=json.loads((HERE/'user_baseline.bbmodel').read_text(encoding='utf-8'))
model=json.loads((OUT/'engine.bbmodel').read_text(encoding='utf-8'))
report=json.loads((HERE/'texture_detail_report.json').read_text(encoding='utf-8'))
native=json.loads((OUT/'reference/native_roundtrip.json').read_text(encoding='utf-8'))
before=Image.open(HERE/'user_baseline_tex.png').convert('RGBA');after=Image.open(OUT/'engine_tex.png')
errors=[];warnings=[]
def check(ok,message):
    if not ok:errors.append(message)
check(all(model[k]==baseline[k] for k in baseline if k!='textures'),'Non-texture project data changed')
check({k:v for k,v in model['textures'][0].items() if k!='source'}=={k:v for k,v in baseline['textures'][0].items() if k!='source'},'Texture metadata changed')
check(len(model['elements'])==180 and len(model['groups'])==18,'Geometry counts changed')
check(model['animations']==baseline['animations'],'User animation data changed')
check(base64.b64decode(model['textures'][0]['source'].split(',')[1])==(OUT/'engine_tex.png').read_bytes(),'Embedded/external PNG mismatch')
check(after.mode=='RGBA' and after.size==(512,512),'Texture format changed')
check(model['resolution']=={'width':256,'height':256},'Logical UV dimensions changed')
check(set(after.getchannel('A').tobytes())<={0,255},'Unexpected partial transparency')
changed=set();ap,bp=after.tobytes(),before.tobytes();stride=after.width
for i in range(0,len(ap),4):
    if ap[i:i+4]!=bp[i:i+4]:changed.add((i//4%stride,i//4//stride))
allowed=set();existing_strokes_preserved=True
for t in report['targets']:
    x1,y1,x2,y2=t['pixel_rect'];base=tuple(t['base_color']);pixel_before=before.load()
    for y in range(y1,y2):
        for x in range(x1,x2):
            if (x,y) in changed:
                allowed.add((x,y))
                if pixel_before[x,y]!=base:existing_strokes_preserved=False
check(changed<=allowed,'Changed pixels outside selected face regions')
check(existing_strokes_preserved,'Existing non-base user strokes changed')
check(len(changed)==report['changed_pixels'],'Changed pixel report mismatch')
uv_rects=[];face_count=0
for e in model['elements']:
    for face,f in e['faces'].items():
        face_count+=1;uv=f['uv'];check(all(0<=v<=256 for v in uv),'UV out of logical bounds '+e['name'])
        check(f['texture']==0,'Unassigned face texture '+e['name'])
        a,b,c,d=min(uv[0],uv[2]),min(uv[1],uv[3]),max(uv[0],uv[2]),max(uv[1],uv[3]);uv_rects.append((a,b,c,d))
overlaps=0
for i,a in enumerate(uv_rects):
    for b in uv_rects[i+1:]:
        if max(a[0],b[0])<min(a[2],b[2]) and max(a[1],b[1])<min(a[3],b[3]):overlaps+=1
check(overlaps==0,'User UV islands overlap')
for key in ['reopened_project_geometry_match','reopened_project_animation_match','geo_import_reexport_match','animation_import_reexport_match']:check(native[key],key)
previews={}
for view in ['front','back','side','oblique']:
    im=Image.open(OUT/f'preview_{view}.png');bounds=im.getchannel('A').getbbox()
    check(im.size==(1200,900),'Preview dimensions '+view);check(bounds and bounds[0]>0 and bounds[1]>0 and bounds[2]<1200 and bounds[3]<900,'Preview clipping '+view)
    previews[view]={'size':list(im.size),'alpha_bbox':bounds,'projection':'perspective' if view=='oblique' else 'orthographic'}
for name,count,size in [('running',60,(960,720)),('rotor',20,(600,600)),('pistons',30,(600,600))]:
    im=Image.open(OUT/f'preview_{name}.gif');check(im.size==size and im.n_frames==count,'GIF dimensions/frame count '+name);check(im.info.get('loop')==0,'GIF loop '+name)
    duration=0
    for i in range(im.n_frames):im.seek(i);duration+=im.info.get('duration',0)
    previews[name]={'size':list(im.size),'frames':count,'duration_ms':duration,'loop':True}
audit={'stage':'user-edited surface-detail pass 2026-11-15','errors':errors,'warnings':warnings,'cubes':180,'bones':18,'declared_faces':face_count,'geometry_uv_groups_and_animations_unchanged':True,'unchanged_cube_count':180,'logical_uv_size':[256,256],'physical_png_size':[512,512],'physical_pixels_per_logical_uv':2,'distinct_face_uvs':len(set(uv_rects)),'overlapping_uv_pairs':overlaps,'modified_faces':report['modified_faces'],'modified_pixels':len(changed),'pixels_outside_registered_regions_preserved':True,'original_non_base_user_strokes_preserved':existing_strokes_preserved,'native_roundtrip':native,'transformed_bbox':native['bbox'],'size_blocks':[v/16 for v in native['bbox']['size']],'previews':previews,'notes':['The user-authored 256px logical UV atlas / 512px PNG relationship is preserved.','No geometry, UV or animation changes were made.']}
(OUT/'structure_audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
files=['engine.bbmodel','engine.geo.json','engine.animation.json','engine_tex.png','preview_front.png','preview_back.png','preview_side.png','preview_oblique.png','preview_running.gif','preview_rotor.gif','preview_pistons.gif','README.md','structure_audit.json']
validation={'asset':'引擎 Engine','identifier':'geometry.engine','display_name_zh':'引擎','display_name_en':'Engine','date':'2026-11-15','creation_source':'人工 + AI（TakaraMiyuki + Codex / GPT-6）','contributors':{'human':['TakaraMiyuki'],'ai':[{'name':'GPT-6 (Codex)','tool':'Blockbench 5.2.2 / MCP / Python / Pillow','role':'在手改贴图的底色区域增补检修板等纹理，刷新原生预览与验证'}],'division':'人工完成当前 180-cube 几何、UV 重排与贴图修改；本轮 AI 不改变几何、UV、骨骼或动画。'},'status':'complete local texture pass; awaiting user visual review','baseline':{'directory':'reference/texture_detail_pass_20261115','model_sha256':hashlib.sha256((HERE/'user_baseline.bbmodel').read_bytes()).hexdigest(),'texture_sha256':hashlib.sha256((HERE/'user_baseline_tex.png').read_bytes()).hexdigest()},'metrics':{k:audit[k] for k in ['cubes','bones','declared_faces','transformed_bbox','size_blocks','logical_uv_size','physical_png_size','modified_faces','modified_pixels']},'native_roundtrip':native,'preservation':{k:audit[k] for k in ['geometry_uv_groups_and_animations_unchanged','unchanged_cube_count','pixels_outside_registered_regions_preserved','original_non_base_user_strokes_preserved']},'previews':previews,'errors':errors,'warnings':warnings,'files_sha256':{f:hashlib.sha256((OUT/f).read_bytes()).hexdigest() for f in files},'runtime_verified':False,'not_tested':['Minecraft / GeckoLib runtime rendering and animation playback','collision, placement, propulsion and interactions','lighting and performance'],'publishing':{'committed':False,'pushed':False,'discussion_updated':False},'boundary':'已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。'}
(OUT/'validation.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
print(json.dumps({k:audit[k] for k in ['errors','warnings','cubes','bones','declared_faces','modified_faces','modified_pixels','logical_uv_size','physical_png_size','overlapping_uv_pairs','geometry_uv_groups_and_animations_unchanged']},ensure_ascii=False))
if errors:raise SystemExit(1)
