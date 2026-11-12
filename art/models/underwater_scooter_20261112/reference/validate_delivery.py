"""Read-only package checks. Writes validation.json, never edits the model."""
import base64
import hashlib
import io
import json
import math
from pathlib import Path
from PIL import Image,ImageChops
from cleanup_hidden_faces import world

ROOT=Path(__file__).resolve().parents[1]
def load(n):return json.loads((ROOT/n).read_text(encoding='utf-8'))
def bbox(cubes):
    pts=[]
    for c in cubes:
        for a in (0,1):
            for b in (0,1):
                for d in (0,1):pts.append(world(c,[c['to' if v else 'from'][i] for i,v in enumerate((a,b,d))]))
    lo=[min(p[i] for p in pts) for i in range(3)];hi=[max(p[i] for p in pts) for i in range(3)]
    return {'min':lo,'max':hi,'size':[hi[i]-lo[i] for i in range(3)]}

def main():
    bb=load('underwater_scooter.bbmodel');geo=load('underwater_scooter.geo.json');g=geo['minecraft:geometry'][0]
    texture=Image.open(ROOT/'underwater_scooter_tex.png').convert('RGBA')
    embedded=Image.open(io.BytesIO(base64.b64decode(bb['textures'][0]['source'].split(',',1)[1]))).convert('RGBA')
    bones=g['bones'];exports=[c for bone in bones for c in bone.get('cubes',[])]
    converted=[]
    for c in exports:
        o=c['origin'];s=c['size'];p=c.get('pivot',[0,0,0]);r=c.get('rotation',[0,0,0])
        converted.append({'from':[-o[0]-s[0],o[1],o[2]],'to':[-o[0],o[1]+s[1],o[2]+s[2]],'origin':[-p[0],p[1],p[2]],'rotation':[-r[0],-r[1],r[2]]})
    bbb=bbox(bb['elements']);gb=bbox(converted)
    uv=[]
    for c in exports:
        for name,f in c['uv'].items():
            u,v=f['uv'];w,h=f['uv_size'];uv.append(0<=min(u,u+w)<=max(u,u+w)<=64 and 0<=min(v,v+h)<=max(v,v+h)<=64)
    names={b['name'] for b in bones};parents_valid=all(not b.get('parent') or b['parent'] in names for b in bones)
    alpha=texture.getchannel('A');partial=sum(alpha.histogram()[1:255]);transparent_true=all((r,g,b)==(0,0,0) for r,g,b,a in texture.getdata() if a==0)
    previews=[]
    for name in ['front','back','side','oblique','nose','top','bottom','oblique_nose']:
        file=ROOT/f'preview_{name}.png';im=Image.open(file).convert('RGBA');bound=im.getchannel('A').getbbox()
        previews.append({'file':file.name,'size':list(im.size),'alpha_bounds':bound,'projection':'perspective' if name.startswith('oblique') else 'orthographic','not_clipped':bool(bound and bound[0]>0 and bound[1]>0 and bound[2]<im.width and bound[3]<im.height)})
    required=['underwater_scooter.bbmodel','underwater_scooter.geo.json','underwater_scooter_tex.png','README.md','reference/palette.json']+[f'preview_{n}.png' for n in ['front','back','side','oblique']]
    checks={
      'required_files_present':all((ROOT/f).is_file() for f in required),
      'bedrock_model_format':bb['meta']['model_format']=='bedrock',
      'geometry_identifier_correct':g['description']['identifier']=='geometry.underwater_scooter',
      'native_export_format_version':'1.12.0'==geo['format_version'],
      'editor_export_cube_counts_equal':len(bb['elements'])==len(exports),
      'all_sizes_positive':all(all(x>0 for x in c['size']) for c in exports),
      'all_uv_within_atlas':all(uv),
      'bone_parents_valid':parents_valid,
      'editor_and_geometry_bounds_match':max(abs(bbb[key][i]-gb[key][i]) for key in ['min','max'] for i in range(3))<0.0001,
      'embedded_png_pixels_match_external':texture.size==embedded.size and not any(v for lo,hi in ImageChops.difference(texture,embedded).getextrema() for v in [hi]),
      'no_absolute_texture_dependencies':all(not t.get('path') and not t.get('relative_path') for t in bb['textures']),
      'rgba_power_of_two_texture':Image.open(ROOT/'underwater_scooter_tex.png').mode=='RGBA' and texture.size==(64,64),
      'transparent_pixels_zero_rgb':transparent_true,
      'no_partial_alpha_texture':partial==0,
      'eight_previews_1200x900_and_unclipped':all(p['size']==[1200,900] and p['not_clipped'] for p in previews),
    }
    keys=[]
    for c in bb['elements']:keys.append(json.dumps({k:c.get(k) for k in ['from','to','origin','rotation','faces']},sort_keys=True))
    checks['no_fully_duplicate_cubes']=len(keys)==len(set(keys))
    baseline_path=ROOT/'reference/work/before_cleanup.bbmodel'
    if baseline_path.exists():
        base=json.loads(baseline_path.read_text(encoding='utf-8'));old={c['uuid']:c for c in base['elements']}
        oldim=Image.open(io.BytesIO(base64.b64decode(base['textures'][0]['source'].split(',',1)[1]))).convert('RGBA')
        count=0;bad=[]
        for c in bb['elements']:
            for f,fd in c['faces'].items():
                if fd.get('texture') is None:continue
                a=oldim.crop(tuple(old[c['uuid']]['faces'][f]['uv']));b=texture.crop(tuple(fd['uv']));count+=1
                if a.size!=b.size or any(hi for lo,hi in ImageChops.difference(a,b).getextrema()):bad.append([c['name'],f])
        checks['atlas_repack_face_pixels_exact']=not bad and count==474
        (ROOT/'reference/uv_repack_verification.json').write_text(json.dumps({'faces_checked':count,'all_face_pixels_exact':not bad,'mismatches':bad,'before_atlas':[128,128],'after_atlas':[64,64]},indent=2),encoding='utf-8')
    re=load('reference/reopen_verification.json');checks.update({k:re['checks'][k] for k in ['native_reopen','cube_count_preserved','geometry_export_exact','embedded_texture_pixels_exact','other_open_projects_preserved']})
    files={f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest() for f in required}
    result={'asset_name':'水下助推器 UnderwaterScooter','identifier':'underwater_scooter','date':'2026-11-12','source':'AI / Codex (GPT-6), local Blockbench 5.2.2, MCP 1.10.1','format_version':geo['format_version'],
      'editor':{'cubes':len(bb['elements']),'bones_including_root':len(bones),'preview_draw_calls':'not_measured'},
      'export':{'cubes':len(exports),'bones':len(bones),'declared_faces':sum(len(c['uv']) for c in exports),'triangles_if_two_per_quad':sum(len(c['uv']) for c in exports)*2,'hidden_faces_disabled':24},
      'bounds_blockbench_units':bbb,'bounds_converted_bedrock_units':gb,'dimensions_blocks':{k:round(v/16,6) for k,v in zip(['length_X','height_Y','depth_Z'],bbb['size'])},
      'texture':{'dimensions':[64,64],'mode':'RGBA','textures':1,'total_pixels':4096,'opaque_pixels':4096-alpha.histogram()[0],'partial_alpha_pixels':partial,'rgba_base_bytes':16384,'animation_frames':1,'layers':'single opaque material; unused atlas area transparent'},
      'animation':{'exported':False,'geometry_copies':1,'rotor_bone':'rotor'},
      'checks':checks,'all_required_offline_checks_passed':all(checks.values()),'previews':previews,'native_reopen_render_measurements':re['render_comparison'],
      'limits':['Minecraft client, hand poses, gameplay propulsion and lighting were not tested or integrated.','Generic structural audit is not a complete Bedrock/GeckoLib schema certification.','Reopen preserved geometry JSON and texture pixels exactly. Orthographic images were pixel-identical; two perspective views differed at 168 and 8 pixels. No strict full-image identity is claimed.','Blockbench offscreen-view deletion reported the known controls.dispose warning after deletion. Absence was verified in both view registries; listener disposal was not certified.'],
      'sha256':files}
    (ROOT/'validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'all_required_offline_checks_passed':all(checks.values()),'failed':[k for k,v in checks.items() if not v],'editor':result['editor'],'export':result['export'],'bounds':bbb,'texture':result['texture']},ensure_ascii=True))
    if not all(checks.values()):raise SystemExit(1)
if __name__=='__main__':main()
