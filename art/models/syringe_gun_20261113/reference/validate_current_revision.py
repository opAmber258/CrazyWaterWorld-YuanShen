"""Validate the published human revision against its read-only source snapshot."""
from pathlib import Path
import base64, collections, hashlib, itertools, json, math
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
def read(name):return json.loads((ROOT/name).read_text(encoding='utf-8-sig'))
def sha(name):return hashlib.sha256((ROOT/name).read_bytes()).hexdigest()
def zrotate(v,origin,angle):
    a=math.radians(angle);c,s=math.cos(a),math.sin(a);x,y=v[0]-origin[0],v[1]-origin[1]
    return [origin[0]+x*c-y*s,origin[1]+x*s+y*c,v[2]]
def bounds(elements,bedrock=False):
    points=[]
    for e in elements:
        if bedrock:
            low=e['origin'];high=[a+b for a,b in zip(low,e['size'])];pivot=e.get('pivot',[0,0,0]);rotation=e.get('rotation',[0,0,0]);angle=-rotation[2]
        else:low=e['from'];high=e['to'];pivot=e.get('origin',[0,0,0]);rotation=e.get('rotation',[0,0,0]);angle=rotation[2]
        assert rotation[:2]==[0,0]
        points.extend(zrotate([(high if bit else low)[axis] for axis,bit in enumerate(bits)],pivot,angle) for bits in itertools.product([0,1],repeat=3))
    low=[min(p[j] for p in points) for j in range(3)];high=[max(p[j] for p in points) for j in range(3)]
    return {'min':low,'max':high,'size':[b-a for a,b in zip(low,high)]}

def main():
    m=read('syringe_gun.bbmodel');source=read('reference/snapshot_before_publish/user_saved.bbmodel');geo=read('syringe_gun.geo.json')['minecraft:geometry'][0]
    elements=m['elements'];source_map={e['uuid']:e for e in source['elements']};cubes=[c for b in geo['bones'] for c in b.get('cubes',[])]
    differences=[]
    for e in elements:
        original=source_map[e['uuid']]
        for key in ['name','from','to','origin','rotation','box_uv']:
            if e.get(key)!=original.get(key):differences.append([e['name'],key])
        for face,f in e['faces'].items():
            old=dict(original['faces'][face]);old['texture']=0 if old.get('texture')==1 else old.get('texture')
            if old!=f:differences.append([e['name'],'face:'+face])
    strip_editor=lambda g:{k:v for k,v in g.items() if k not in ['selected','primary_selected']}
    groups_preserved=[strip_editor(g) for g in m['groups']]==[strip_editor(g) for g in source['groups']]
    texture=m['textures'][0];png=(ROOT/'syringe_gun_tex.png').read_bytes();image=Image.open(ROOT/'syringe_gun_tex.png');pixels=list(image.get_flattened_data());w,h=image.size
    embedded=base64.b64decode(texture['source'].split(',')[1]);source_png=base64.b64decode(source['textures'][1]['source'].split(',')[1])
    box=bounds(elements);gbox=bounds(cubes,True);uv_valid=True;transparent_face_samples=0
    for e in elements:
        for f in e['faces'].values():
            if f.get('texture') is None:continue
            u,v,u1,v1=f['uv'];lw,lh=m['resolution']['width'],m['resolution']['height']
            uv_valid &= 0<=min(u,u1)<max(u,u1)<=lw and 0<=min(v,v1)<max(v,v1)<=lh
            x0,y0,x1,y1=[round(c) for c in [min(u,u1)*w/lw,min(v,v1)*h/lh,max(u,u1)*w/lw,max(v,v1)*h/lh]]
            transparent_face_samples+=sum(a<255 for a in image.crop((x0,y0,x1,y1)).getchannel('A').get_flattened_data())
    native=read('reference/native_reopen_validation.json');audit=read('structure_audit.json')
    keyfiles=['syringe_gun.bbmodel','syringe_gun.geo.json','syringe_gun_tex.png','preview_front.png','preview_back.png','preview_side.png','preview_oblique.png','preview_top.png','preview_rear_oblique.png']
    checks={
      'required_files_present':all((ROOT/p).is_file() for p in keyfiles+['README.md','reference/palette.json']),
      'bedrock_identifier':geo['description']['identifier']=='geometry.syringe_gun',
      'editor_export_counts_match':len(elements)==len(cubes)==64 and len(geo['bones'])==len(m['groups'])==7,
      'source_geometry_and_uv_preserved':not differences and set(source_map)=={e['uuid'] for e in elements},
      'source_bones_preserved':groups_preserved and m['outliner']==source['outliner'],
      'source_active_png_exact':png==embedded==source_png,
      'only_used_texture_retained':len(m['textures'])==1 and {f.get('texture') for e in elements for f in e['faces'].values()}=={0},
      'portable_embedded_texture':all(not t.get('path') and not t.get('relative_path') and t.get('source','').startswith('data:') for t in m['textures']),
      'rgba_power_of_two':image.mode=='RGBA' and w>0 and h>0 and (w&(w-1))==0 and (h&(h-1))==0,
      'alpha_binary':all(p[3] in [0,255] for p in pixels),
      'transparent_rgb_zero':all(p==(0,0,0,0) for p in pixels if p[3]==0),
      'uv_in_logical_bounds':bool(uv_valid),
      'source_sampling_alpha_preserved':not differences and png==source_png,
      'no_empty_bones':all(b.get('cubes') for b in geo['bones']),
      'export_bbox_matches_editor':all(abs(a-b)<2e-5 for a,b in zip(box['size'],gbox['size'])),
      'native_reopen_export_exact':all(native[k] for k in ['geometry_exact_roundtrip','elements_exact_roundtrip','groups_exact_roundtrip','texture_source_exact','texture_embedded']) and native['texture_errors']==[0],
      'structure_audit_without_errors':not audit['errors'] and audit['dependencies_complete'],
      'six_native_previews':all(Image.open(ROOT/p).size==(1200,900) and len(Image.open(ROOT/p).getcolors(1080001))>2 for p in keyfiles if p.startswith('preview_')),
    }
    report={
      'asset':'注射器枪 SyringeGun','display_name_zh':'注射器枪','display_name_en':'Syringe Gun','identifier':'syringe_gun','date':'2026-11-14','status':'complete','review_status':'pending_review',
      'creation_source':'制作来源：人工 + AI（TakaraMiyuki + GPT-6 / Codex）',
      'contributors':{'human':['TakaraMiyuki'],'ai':[{'name':'GPT-6','tool':'Codex + Blockbench 5.2.2','role':'初稿；本轮仅整理、原生导出、预览和交付验证'}],'division':'TakaraMiyuki 手动修改最终几何、UV 与贴图；AI 保留人工保存结果，移除未使用旧贴图、清除外部依赖，重出交付文件。'},
      'format':'bedrock','format_version':read('syringe_gun.geo.json')['format_version'],'editor_bbox':box,'export_bbox':gbox,'dimensions_blocks':[s/16 for s in box['size']],
      'cost':{'editor_cubes':64,'editor_groups':7,'export_cubes':len(cubes),'export_bones':len(geo['bones']),'declared_export_faces':sum(len(c['uv']) for c in cubes),'materials':1,'texture_size':[w,h],'texture_logical_size':[m['resolution']['width'],m['resolution']['height']],'texture_pixels':w*h,'frames':1,'base_rgba_bytes':w*h*4,'fps_measured':False,'draw_calls_measured':False},
      'human_preservation':{'geometry_and_uv_differences':differences,'groups_semantically_identical':groups_preserved,'outliner_identical':m['outliner']==source['outliner'],'png_pixels_and_bytes_identical':png==source_png,'source_sha256':sha('reference/snapshot_before_publish/user_saved.bbmodel'),'allowed_changes':['Unused texture slot 0 removed; active slot 1 remapped to 0','External relative paths removed; active texture renamed','Native codec cleared stock.primary_selected editor marker; no geometry or UV change']},
      'checks':checks,'all_delivery_checks_pass':all(checks.values()),
      'native_uv_repack':{'status':'not_performed','reason':'发布既有人工修订版；保留人工 UV，未重新分配或缩放图案。界面换算密度为 64x；未宣称原生重排往返通过。'},
      'visual_review':{'views':['front','back','side','top','oblique','rear_oblique'],'renderer':'Blockbench 5.2.2 native Preview','reviewed':True,'geometry_changed_by_agent':False,'repainted_by_agent':False,'transparent_uv_samples':transparent_face_samples,'alpha_note':'人工纹理包含透明裁切；逐面 UV 和源 PNG 均完全保留，不把所有表面必须不透明作为交付要求。'},
      'client_test':{'performed':False,'reason':'仅静态美术交付；未接入物品渲染、手持姿态或射击逻辑。'},
      'sha256':{p:sha(p) for p in keyfiles},
      'limitations':['Generic audit is not a full Bedrock schema validator; native codec roundtrip checked separately.','No Minecraft runtime or FPS certification.','Initial prototype reconstruction and face-cleanup evidence under reference are historical and do not describe this revision.'],
    }
    (ROOT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
    (ROOT/'reference/human_preservation.json').write_text(json.dumps(report['human_preservation'],ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
    print(json.dumps({'passed':all(checks.values()),'checks':checks,'dimensions_blocks':report['dimensions_blocks'],'cost':report['cost']},ensure_ascii=True,indent=2))
    assert all(checks.values())

if __name__=='__main__':main()
