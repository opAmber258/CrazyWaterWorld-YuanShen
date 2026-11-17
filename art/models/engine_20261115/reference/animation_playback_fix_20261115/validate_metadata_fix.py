"""Verify playback metadata changes without altering art or motion tracks."""
import json,hashlib
from pathlib import Path
HERE=Path(__file__).resolve().parent;OUT=HERE.parent.parent
read=lambda p:json.loads(p.read_text(encoding='utf-8'))
before=read(HERE/'baseline_engine.bbmodel');after=read(OUT/'engine.bbmodel')
assert {k:v for k,v in before.items() if k!='animations'}=={k:v for k,v in after.items() if k!='animations'}
old={a['name']:a for a in before['animations']};new={a['name']:a for a in after['animations']}
assert old.keys()==new.keys()
for name,a in new.items():
    assert {k:v for k,v in a.items() if k not in ['path','selected']}=={k:v for k,v in old[name].items() if k not in ['path','selected']}
    assert a['path']=='engine.animation.json'
assert after['animations'][0]['name']=='animation.engine.running'
assert (OUT/'engine_tex.png').read_bytes()==(HERE/'baseline_engine_tex.png').read_bytes()
assert read(OUT/'engine.geo.json')==read(HERE/'baseline_engine.geo.json')
assert read(OUT/'engine.animation.json')==read(HERE/'baseline_engine.animation.json')
playback=read(HERE/'playback_validation.json');assert not playback['errors']
native=read(OUT/'reference/native_roundtrip.json')
for k in ['reopened_project_geometry_match','reopened_project_animation_match','geo_import_reexport_match','animation_import_reexport_match']:assert native[k]
audit=read(HERE/'baseline_structure_audit.json')
audit.update(stage='combined playback default and animation output path repair',native_roundtrip=native,animation_playback={k:v for k,v in playback.items() if k!='samples'},animation_motion_tracks_unchanged=True,geometry_uv_texture_unchanged=True)
audit['notes'].append('Animation order/selection metadata and output paths repaired; native motion tracks remain byte-equivalent after semantic export comparison.')
(OUT/'structure_audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8')
v=read(HERE/'baseline_validation.json');v.update(revision='Combined animation first by default; correct local animation save paths; geometry, UV, texture and motion tracks unchanged',baseline='reference/animation_playback_fix_20261115/baseline_engine.bbmodel')
v['checks']={'geometry_uv_texture_and_motion_tracks_unchanged':True,'default_combined_clip_verified':True,'local_animation_output_paths_verified':True,'native_playback':{k:x for k,x in playback.items() if k!='samples'},'native_roundtrip':native}
v['files_sha256']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in OUT.iterdir() if p.is_file() and p.name!='validation.json'}
(OUT/'validation.json').write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'errors':[],'motion_tracks_unchanged':True,'geometry_uv_texture_unchanged':True,'default_combined_clip_verified':True,'native_samples':len(playback['samples'])}))
