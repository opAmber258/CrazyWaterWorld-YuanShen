"""Exact deletion audit against the current revision checkpoint."""
import base64,hashlib,json
from pathlib import Path
from PIL import Image
HERE=Path(__file__).resolve().parent;OUT=HERE.parent.parent
read=lambda p:json.loads(p.read_text(encoding='utf-8'))
before=read(HERE/'baseline_engine.bbmodel');after=read(OUT/'engine.bbmodel');removal=read(HERE/'removal_manifest.json');native=read(OUT/'reference/native_roundtrip.json')
ids=set(removal['removed_uuids']);remaining=[e for e in before['elements'] if e['uuid'] not in ids]
assert after['elements']==remaining,'Retained geometry/UV changed'
assert len(ids)==228 and len(after['elements'])==200 and len(after['groups'])==35
assert after['animations']==before['animations']
assert after['textures']==before['textures']
assert (OUT/'engine_tex.png').read_bytes()==(HERE/'baseline_engine_tex.png').read_bytes()
assert base64.b64decode(after['textures'][0]['source'].split(',')[1])==(OUT/'engine_tex.png').read_bytes()
def prune(nodes):
    result=[]
    for n in nodes:
        if isinstance(n,str):
            if n not in ids:result.append(n)
        else:
            n=dict(n);n['children']=prune(n['children'])
            if n['children']:result.append(n)
    return result
assert after['outliner']==prune(before['outliner'])
empty={g['uuid'] for g in removal['removed_empty_groups']}
assert after['groups']==[g for g in before['groups'] if g['uuid'] not in empty]
assert all(after[k]==v for k,v in before.items() if k not in ['elements','groups','outliner'])
for k in ['reopened_project_geometry_match','reopened_project_animation_match','geo_import_reexport_match','animation_import_reexport_match']:assert native[k],k
assert native['imported_cubes']==200 and native['imported_bones']==35
geo=read(OUT/'engine.geo.json')['minecraft:geometry'][0]
assert sum(len(g.get('cubes',[])) for g in geo['bones'])==200
previews={}
for v in ['front','back','side','oblique']:
    im=Image.open(OUT/f'preview_{v}.png');assert im.size==(1200,900)
    previews[v]={'size':list(im.size),'alpha_bbox':im.getchannel('A').getbbox()}
for v,n,size in [('running',60,(960,720)),('rotor',20,(600,600)),('pistons',30,(600,600))]:
    im=Image.open(OUT/f'preview_{v}.gif');assert im.n_frames==n and im.size==size
    previews[v]={'size':list(im.size),'frames':n}
audit={'stage':'remove AI-added modeled screws and washers','errors':[],'warnings':[],'cubes':200,'bones':35,'declared_faces':1200,'removed_fasteners':76,'removed_cubes':228,'removed_empty_groups':2,'retained_new_plate_cubes':20,'remaining_elements_exactly_preserved':True,'textures_and_animations_exactly_preserved':True,'logical_uv_size':[256,256],'physical_png_size':[512,512],'transformed_bbox':native['bbox'],'size_blocks':[v/16 for v in native['bbox']['size']],'native_roundtrip':native,'previews':previews,'notes':['Only the 228 UUID-listed washer/head cubes and two now-empty rear-fastener groups were removed.','All retained cubes, their UVs, the PNG, and all animations are identical to the frozen input.','Intentional UV sharing by the 20 retained panel cubes remains.']}
(OUT/'structure_audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8')
validation={'asset':'引擎 Engine','identifier':'geometry.engine','date':'2026-11-15','creation_source':'人工 + AI（TakaraMiyuki + Codex / GPT-6）','revision':'Delete all previously AI-added modeled screws and washers; retain inspection covers','status':'complete local revision','baseline':'reference/remove_screws_20261115/baseline_engine.bbmodel','metrics':{k:audit[k] for k in ['cubes','bones','declared_faces','removed_fasteners','removed_cubes','retained_new_plate_cubes','logical_uv_size','physical_png_size','transformed_bbox','size_blocks']},'checks':{'exact_deletion_only':True,'retained_geometry_uv_texture_animation_unchanged':True,'native_roundtrip':native},'errors':[],'warnings':[],'previews':previews,'runtime_verified':False,'publishing':{'committed':False,'pushed':False,'discussion_updated':False},'files_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in OUT.iterdir() if p.is_file() and p.name!='validation.json'}}
(OUT/'validation.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'cubes':200,'bones':35,'removed_cubes':228,'exact_deletion_only':True,'native_roundtrip':True}))
