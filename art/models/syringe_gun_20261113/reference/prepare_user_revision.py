"""Prepare the saved human revision without changing geometry, UV or active pixels.
The snapshot is read-only. No initial prototype reconstruction is executed.
"""
from pathlib import Path
import base64, json, hashlib, collections
from PIL import Image
import io

ROOT=Path(__file__).resolve().parents[1]
snapshot=ROOT/'reference/snapshot_before_publish/user_saved.bbmodel'
m=json.loads(snapshot.read_text(encoding='utf-8-sig'))
assert m['meta']['model_format']=='bedrock'
usage=collections.Counter(f.get('texture') for e in m['elements'] for f in e.get('faces',{}).values() if f.get('texture') is not None)
assert set(usage)=={1},f'Unexpected used texture slots: {usage}'
active=m['textures'][1]
png=base64.b64decode(active['source'].split(',')[1])
assert png==(ROOT/'reference/snapshot_before_publish/user_saved_tex.png').read_bytes()
for e in m['elements']:
    for f in e.get('faces',{}).values():
        if f.get('texture')==1:f['texture']=0
for key in ['path','relative_path','file_path']:active.pop(key,None)
active['name']='syringe_gun_tex.png';active['id']='0';active['internal']=True
m['textures']=[active]
(ROOT/'syringe_gun.bbmodel').write_text(json.dumps(m,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
(ROOT/'syringe_gun_tex.png').write_bytes(png)
report={'source_sha256':hashlib.sha256(snapshot.read_bytes()).hexdigest(),'active_png_sha256':hashlib.sha256(png).hexdigest(),'cubes':len(m['elements']),'groups':len(m['groups']),'png_size':list(Image.open(io.BytesIO(png)).size),'logical_uv':m['resolution'],'removed_unused_texture_slot':0,'texture_index_remap':{'1':0},'geometry_changed':False,'uv_changed':False,'active_pixels_changed':False,'removed_external_texture_paths':True,'source':'User-saved Blockbench model; author: TakaraMiyuki'}
(ROOT/'reference/user_revision_preparation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=True,indent=2))
