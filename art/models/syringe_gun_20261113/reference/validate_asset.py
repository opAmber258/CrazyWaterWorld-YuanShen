"""Offline delivery checks, complementary to native Blockbench reopen/export evidence.
Usage: python reference/validate_asset.py
"""
from pathlib import Path
import json, math, itertools, base64, hashlib
from PIL import Image
from face_cleanup import rotated

ROOT=Path(__file__).resolve().parents[1]
def read(p):return json.loads((ROOT/p).read_text(encoding='utf-8-sig'))
def bounds(elements,bedrock=False):
    pts=[]
    for e in elements:
        if bedrock:
            a=e['origin'];b=[v+s for v,s in zip(a,e['size'])];origin=e.get('pivot',[0,0,0]);rotation=e.get('rotation',[0,0,0])
        else:a=e['from'];b=e['to'];origin=e.get('origin',[0,0,0]);rotation=e.get('rotation',[0,0,0])
        assert rotation[:2]==[0,0]
        # Bedrock is left-handed: negate Z angle when evaluating its mirrored
        # coordinates with this right-handed rotation helper.
        for bits in itertools.product([0,1],repeat=3):pts.append(rotated([(b if bit else a)[i] for i,bit in enumerate(bits)],origin,-rotation[2] if bedrock else rotation[2]))
    mn=[min(p[j] for p in pts) for j in range(3)];mx=[max(p[j] for p in pts) for j in range(3)]
    return {'min':mn,'max':mx,'size':[b-a for a,b in zip(mn,mx)]}
def sha(p):return hashlib.sha256((ROOT/p).read_bytes()).hexdigest()
m=read('syringe_gun.bbmodel');g=read('syringe_gun.geo.json');geom=g['minecraft:geometry'][0]
es=m['elements'];bones=geom['bones'];cs=[c for b in bones for c in b.get('cubes',[])]
bb=bounds(es);gb=bounds(cs,True)
tex=Image.open(ROOT/'syringe_gun_tex.png');png=(ROOT/'syringe_gun_tex.png').read_bytes()
assert base64.b64decode(m['textures'][0]['source'].split(',')[1])==png
uv_alpha=[]
for e in es:
    for f in e['faces'].values():
        if f.get('texture') is None:continue
        u,v,u1,v1=f['uv'];assert 0<=min(u,u1)<max(u,u1)<=64 and 0<=min(v,v1)<max(v,v1)<=64
        uv_alpha.extend(tex.crop((min(u,u1),min(v,v1),max(u,u1),max(v,v1))).getchannel('A').getdata())
assert set(uv_alpha)=={255}
checks={
 'required_files_present':all((ROOT/p).exists() for p in ['syringe_gun.bbmodel','syringe_gun.geo.json','syringe_gun_tex.png','README.md','preview_front.png','preview_back.png','preview_side.png','preview_oblique.png']),
 'bedrock_geometry_identifier':geom['description']['identifier']=='geometry.syringe_gun',
 'format_version':'1.12.0'==g['format_version'],
 'cube_and_bone_counts_match':len(es)==len(cs)==62 and len(bones)==len(m['groups'])==7,
 'no_empty_bones':all(b.get('cubes') for b in bones),
 'no_duplicate_geometry':len({json.dumps({k:e.get(k,[0,0,0]) for k in ['from','to','origin','rotation']},sort_keys=True) for e in es})==len(es),
 'positive_volumes':all(all(b>a for a,b in zip(e['from'],e['to'])) for e in es),
 'export_bbox_matches_mirrored_editor_bbox':all(abs(a-b)<2e-5 for a,b in zip(gb['size'],bb['size'])),
 'png_rgba_power_of_two':tex.mode=='RGBA' and tex.size==(64,64),
 'no_partial_alpha':all(p[3] in (0,255) for p in tex.getdata()),
 'transparent_pixels_zero_rgb':all(p==(0,0,0,0) for p in tex.getdata() if p[3]==0),
 'embedded_texture_matches_png':True,
 'all_sampled_face_texels_opaque':True,
 'portable_embedded_texture':all(not t.get('path') and not t.get('relative_path') and t.get('source','').startswith('data:image/png;base64,') for t in m['textures']),
 'preview_size_and_nonempty':all(Image.open(p).size==(1200,900) and len(Image.open(p).getcolors(1080001))>2 for p in ROOT.glob('preview_*.png')),
}
native=read('reference/native_reopen_validation.json')
checks['native_reopen_and_export']=all(native[k] for k in ['geometry_exact_roundtrip','elements_exact_roundtrip','groups_exact_roundtrip','texture_source_exact','texture_embedded']) and native['texture_errors']==[0]
evidence={
 'asset':'注射器枪 SyringeGun','identifier':'syringe_gun','date':'2026-11-13','source':'AI / Codex (GPT-6) + Blockbench 5.2.2 native APIs + authored Python pixel atlas',
 'format':'bedrock','format_version':g['format_version'],'editor_bbox':bb,'export_bbox':gb,'dimensions_blocks':[s/16 for s in bb['size']],
 'cost':{'editor_cubes':len(es),'editor_groups':7,'export_cubes':len(cs),'export_bones':len(bones),'declared_export_faces':sum(len(c['uv']) for c in cs),'materials':1,'texture_pixels':4096,'texture_frame':[64,64],'frames':1,'base_rgba_bytes':16384,'draw_calls_measured':False,'fps_measured':False},
 'checks':checks,'all_delivery_checks_pass':all(checks.values()),
 'visual_review':{'views':['front','back','side','top','oblique','rear_oblique'],'reviewed_in_blockbench':True,'silhouette':'open skeletal stock, slanted grip, two-row syrette bay, short muzzle and vertical forward cylinder','hidden_face_candidates_retained':17,'cleanup_removed_faces':0,'note':'Geometric candidates retained after native strict pixel comparisons differed. No geometry duplicates or sliced surfaces. Historical comparison records are evidence, not a claimed strict optimization pass.'},
 'client_test':{'performed':False,'reason':'Art-only request. No attachable/item renderer, holding transforms, animation, firing logic, client FPS or Minecraft test performed.'},
 'sha256':{p:sha(p) for p in ['syringe_gun.bbmodel','syringe_gun.geo.json','syringe_gun_tex.png','preview_front.png','preview_back.png','preview_side.png','preview_oblique.png']},
 'limitations':['Generic skill audit is not a full Bedrock schema validator; native export roundtrip checked separately.','Single-view source gives no depth or rear-side information; those details are authored inference.','No game runtime, lighting, held-item pose or performance certification.']}
(ROOT/'validation.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'passed':evidence['all_delivery_checks_pass'],'checks':checks,'bbox':bb,'dimensions_blocks':evidence['dimensions_blocks'],'cost':evidence['cost']},ensure_ascii=True,indent=2))
assert all(checks.values())
