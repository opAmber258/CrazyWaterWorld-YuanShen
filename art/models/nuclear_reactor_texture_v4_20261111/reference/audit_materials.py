"""Texture revision evidence. Records coverage and independent wear without
mistaking color count, brightness or randomness for an aesthetic pass score.
"""
from pathlib import Path
import json,hashlib
from PIL import Image,ImageStat,ImageOps

ROOT=Path(__file__).resolve().parent.parent;REF=ROOT/'reference'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
model=read(ROOT/'nuclear_reactor.bbmodel');old=read(REF/'user_baseline/nuclear_reactor_user.bbmodel')
layout=read(REF/'uv_layout.json');im=Image.open(ROOT/'nuclear_reactor_tex.png').convert('RGBA')
mask=Image.open(REF/'wear_distribution.png')
els={e['uuid']:e for e in model['elements']};names={e['name']:e for e in model['elements']}
exposed={(f['uuid'],f['face']) for f in layout['faces'] if not f['hidden']}
uv_same=all(a['faces']==b['faces'] for a,b in zip(old['elements'],model['elements']))
errors=[]
if not uv_same:errors.append('UV or face properties changed during texture-only revision')
uvs=[tuple(els[u]['faces'][f]['uv']) for u,f in exposed]
independent=len(uvs)==len(set(uvs))
if not independent:errors.append('Exposed UV islands are unexpectedly shared')
pairs=[];coverage=[];material_values={}
for s in layout['islands']:
 if s['kind']=='interior':continue
 tile=im.crop(s['uv']);m=mask.crop(s['uv']);cols=tile.getcolors(512*512)
 coverage.append({'cube':s['cube'],'face':s['face'],'pixels':tile.width*tile.height,'colors':len(cols),
  'dominant_fraction':max(n for n,c in cols)/(tile.width*tile.height),
  'wear_fraction':sum(m.tobytes())/(255*tile.width*tile.height)})
 if s['kind'] in ('sleeve','sleeve_end'):category='ivory_jackets'
 elif s['kind']=='pipe_segment':category='heat_darkened_pipes'
 elif s['kind'] in ('motor','motor_head','reactor_lower','base','terminal','service'):category='lower_iron_and_base'
 elif s['material']=='shell':category='painted_shell'
 else:continue
 mean=ImageStat.Stat(tile.convert('RGB')).mean
 material_values.setdefault(category,[]).append(.2126*mean[0]+.7152*mean[1]+.0722*mean[2])
for e in model['elements']:
 if not e['name'].startswith('left_'):continue
 other=names.get('right_'+e['name'][5:])
 if not other:continue
 for f in e['faces']:
  if (e['uuid'],f) not in exposed or (other['uuid'],f) not in exposed:continue
  a=im.crop(e['faces'][f]['uv']);b=im.crop(other['faces'][f]['uv'])
  if a.size!=b.size or min(a.size)<3 or a.width*a.height<32:continue
  ma=mask.crop(e['faces'][f]['uv']);mb=mask.crop(other['faces'][f]['uv'])
  data={'left':e['name'],'right':other['name'],'face':f,
   'same_material_pixels':a.tobytes()==b.tobytes(),'mirrored_material_pixels':a.tobytes()==ImageOps.mirror(b).tobytes(),
   'same_wear_pixels':ma.tobytes()==mb.tobytes(),'has_drawn_wear':bool(ma.getbbox() and mb.getbbox())}
  pairs.append(data)
 # Individual reports expose any naturally identical unworn faces; no filler spots.
wear_repeats=[p for p in pairs if p['same_wear_pixels'] and p['has_drawn_wear']]
if wear_repeats:errors.append('Aged bilateral surfaces have identical wear masks')
report={'errors':errors,'independent_exposed_UV_rectangles':independent,'UV_preserved_exactly':uv_same,
 'exposed_faces':len(exposed),'bilateral_pairs_checked':len(pairs),
 'bilateral_repeats':sum(p['same_material_pixels'] or p['mirrored_material_pixels'] for p in pairs),
 'bilateral_repeated_wear_masks':len(wear_repeats),
 'material_mean_sRGB_luma':{k:sum(v)/len(v) for k,v in material_values.items()},
 'aesthetic_acceptance':'pending user review; not determined by color count or numerical brightness',
 'coverage':coverage,'bilateral_pairs':pairs}
(REF/'material_audit.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k not in ('coverage','bilateral_pairs')}))
if errors:raise SystemExit(1)
