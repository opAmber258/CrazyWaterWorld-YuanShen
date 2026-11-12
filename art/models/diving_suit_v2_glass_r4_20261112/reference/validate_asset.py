"""Check full opaque-texel footprints against the shell, not only cube bounds."""
from pathlib import Path
import base64,collections,hashlib,io,json,math
from PIL import Image
from geometry_utils import bounds,inside
R=Path(__file__).resolve().parent;D=R.parent;N='diving_suit_v2_glass_r4'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
p=read(D/(N+'.bbmodel'));base=read(R/'baseline_live.bbmodel');spec=read(R/'cap_spec.json');seal=read(R/'neck_seal_spec.json');snap=read(R/'native_faces.json')
im=Image.open(D/(N+'_tex.png')).convert('RGBA');old=Image.open(io.BytesIO(base64.b64decode(base['textures'][0]['source'].split(',')[1]))).convert('RGBA')
es=p['elements'];ex=[e for e in es if e.get('export',True)];by={e['uuid']:e for e in es};names={e['name']:e for e in es};groups={g['name']:g for g in p['groups']};geo=read(D/(N+'.geo.json'))['minecraft:geometry'][0];checks=[]
def check(name,ok,detail=None):checks.append({'name':name,'pass':bool(ok),'detail':detail})
def sub(a,b):return [x-y for x,y in zip(a,b)]
def dot(a,b):return sum(x*y for x,y in zip(a,b))
def cross2(a,b,c):return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
def hull(points):
 pts=sorted(set(points));lo=[];hi=[]
 for a in pts:
  while len(lo)>1 and cross2(lo[-2],lo[-1],a)<=0:lo.pop()
  lo.append(a)
 for a in reversed(pts):
  while len(hi)>1 and cross2(hi[-2],hi[-1],a)<=0:hi.pop()
  hi.append(a)
 return lo[:-1]+hi[:-1]
def tile_hull(image):
 points=[];pixels=0
 for y in range(84,148):
  for x in range(58,122):
   if image.getpixel((x,y))[3]:pixels+=1;points.extend(((x,y),(x+1,y),(x,y+1),(x+1,y+1)))
 return hull(points),pixels
uvh,pixels=tile_hull(im);oldh,oldpixels=tile_hull(old)
def world_at(face,uv):
 a,b,c=face['vertices'][:3];ax,ay=a['uv'];bx,by=b['uv'];cx,cy=c['uv'];det=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax)
 s=((uv[0]-ax)*(cy-ay)-(uv[1]-ay)*(cx-ax))/det;t=((bx-ax)*(uv[1]-ay)-(by-ay)*(uv[0]-ax))/det
 return [a['position'][i]+s*(b['position'][i]-a['position'][i])+t*(c['position'][i]-a['position'][i]) for i in range(3)]
def excess(point):
 x,y,z=point[0],point[1]-28.8,point[2]
 return max(abs(x)-5.75,abs(y)-5.75,abs(z)-5.75,abs(x)+abs(y)-8.75,abs(x)+abs(z)-8.75,abs(y)+abs(z)-8.75,abs(x)+y+abs(z)-11.75)
capnames={e['name'] for e in spec['parts']};capfaces=[f for f in snap['faces'] if f['name'] in capnames]
newmax=max(excess(world_at(f,uv)) for f in capfaces for uv in uvh)
before=read(R/'before_cap_faces.json');oldmax=max(excess(world_at(f,uv)) for f in before for uv in oldh)
check('all_opaque_cap_texel_footprints_within_shell',newmax<=1e-4,{'before_max_support_plane_excess_units':oldmax,'after_max_support_plane_excess_units':newmax,'numerical_tolerance':1e-4,'opaque_texels_per_face':pixels,'coverage':'Convex hull of all four corners of every opaque texel; tested on both actual native cap faces against all shell support planes.'})
check('baseline_really_had_protrusion',oldmax>.5)
planeerrors=[]
for s in spec['planes']:
 f=next(f for f in capfaces if f['name']==s['name'] and f['face']==s['outer_local_face']);n=s['support_plane']['normal'];c=s['support_plane']['constant'];scale=math.sqrt(dot(n,n));err=max(abs(dot(n,world_at(f,uv))-c)/scale for uv in uvh);planeerrors.append({'name':s['name'],'outer_plane_error':err})
check('outward_faces_on_support_planes',max(e['outer_plane_error'] for e in planeerrors)<1e-4,planeerrors)
check('all_cap_thickness_inward',all(dot(s['support_plane']['normal'],world_at(f,uv))-s['support_plane']['constant']<=1e-4 for s in spec['planes'] for f in capfaces if f['name']==s['name'] for uv in uvh))
check('no_triangle_enlargement',all(s['seam_scale']==1 for s in spec['planes']))
check('all_caps_have_interior_and_exterior_faces',len(capfaces)==32 and len(capnames)==16)
q=sum(f.get('texture') is not None for e in ex for f in e['faces'].values())
check('counts_and_budget',len(es)==218 and len(ex)==217 and len(geo['bones'])==9 and q<=1250)
check('identifier',geo['description']['identifier']=='geometry.'+N)
changed=set(spec['removed_names'])|{e['name'] for e in seal['parts']}
check('all_other_original_elements_exact',all(by[e['uuid']]==e for e in base['elements'] if e['name'] not in changed))
check('eight_sided_frame_preserved',sum(e['name'].startswith('viewport_locking_frame_') for e in es)==8)
check('aperture_and_glass_geometry_preserved',all(by[e['uuid']]==e for e in base['elements'] if e['name']=='translucent_visor' or e['name'].startswith('helmet_wall_')))
check('bone_pivots_unchanged',all(groups[g['name']]['origin']==g['origin'] and groups[g['name']]['rotation']==g['rotation'] for g in base['groups']))
newbounds=bounds(ex);oldbounds=bounds([e for e in base['elements'] if e.get('export',True)])
check('bounds_not_expanded',all(newbounds['min'][i]>=oldbounds['min'][i]-1e-6 and newbounds['max'][i]<=oldbounds['max'][i]+1e-6 for i in range(3)),{'before':oldbounds,'after':newbounds})
check('no_negative_dimensions',all(e['to'][i]>=e['from'][i] for e in es for i in range(3)))
check('no_duplicate_geometry',len({json.dumps({k:e.get(k) for k in ('from','to','origin','rotation')},sort_keys=True) for e in ex})==len(ex))
refs=[]
def walk(nodes):
 for n in nodes:
  if isinstance(n,str):refs.append(n)
  else:walk(n.get('children',[]))
walk(p['outliner']);check('single_ownership',collections.Counter(refs)==collections.Counter(e['uuid'] for e in es))
check('all_uv_inbounds',all(all(0<=v<=256 for v in f['uv']) for e in es for f in e['faces'].values() if f.get('texture') is not None))
newseal=[names[e['name']] for e in seal['parts']];covered=[]
for xi in range(33):
 for zi in range(33):covered.append(any(inside([-4+xi*.25,24,-4+zi*.25],e) for e in newseal))
check('existing_neck_seal_covers_player_lower_skin',all(covered),{'lower_head_samples':len(covered),'covered':sum(covered),'new_cubes':0})
check('two_sided_shell_metadata',p['textures'][0]['render_sides']=='double')
check('reference_head_excluded',not any('preview' in b['name'] for b in geo['bones']) and not names['_preview_generic_player_head']['visibility'])
embedded=Image.open(io.BytesIO(base64.b64decode(p['textures'][0]['source'].split(',')[1]))).convert('RGBA')
check('portable_texture',len(p['textures'])==1 and im.size==(256,256) and im.tobytes()==embedded.tobytes() and all(not t.get('path') and not t.get('relative_path') for t in p['textures']))
diff=[(x,y) for y in range(256) for x in range(256) if im.getpixel((x,y))!=old.getpixel((x,y))]
check('only_cap_mask_pixels_changed',len(diff)==96 and all(58<=x<122 and 84<=y<148 for x,y in diff),{'changed_pixels':len(diff)})
check('glass_pixels_exact',im.crop((1,212,33,244)).tobytes()==old.crop((1,212,33,244)).tobytes())
check('transparent_rgb_zero',all(im.getpixel((x,y))[:3]==(0,0,0) for y in range(256) for x in range(256) if im.getpixel((x,y))[3]==0))
check('native_reopen_matches',all(read(R/'reopen_validation.json').values()))
check('poses_and_materials_restored',all(snap['restored'].values()) and read(R/'capture.json')['restored'])
check('four_root_previews',all(Image.open(D/('preview_'+v+'.png')).size==(1200,900) for v in ('front','back','side','oblique')))
check('feedback_angle_previews',all((R/v).exists() for v in ('empty/preview_rear_oblique.png','empty/preview_upper_back.png','worn/preview_rear_oblique.png','empty/preview_lower_back.png')))
wear=Image.open(R/'worn'/'preview_oblique.png').convert('RGBA')
skin_count=sum((lambda a:a[3]>200 and a[0]>90 and a[0]>a[1]*1.3 and a[1]>a[2]*1.2)(wear.getpixel((x,y))) for y in range(590,690) for x in range(430,660))
check('prior_front_seam_no_skin_colored_pixels',skin_count==0,{'region':[430,590,660,690],'skin_colored_pixels':skin_count,'scope':'Fixed provided example skin and this fixed camera only; not a generic skin detector.'})
preservation=read(R/'source_preservation.json')
check('baseline_and_source_preservation_record',preservation['status']=='verified_before_publish' and hashlib.sha256((R/'baseline_live.bbmodel').read_bytes()).hexdigest()==preservation['baseline_sha256'])
cap_and_seal=spec['parts']+seal['parts']
check('native_snapshot_geometry_matches_current_spec',all(all(names[e['name']].get(k,[0,0,0])==e.get(k,[0,0,0]) for k in ('from','to','origin','rotation')) for e in cap_and_seal))
check('native_export_complete',sum(len(b.get('cubes',[])) for b in geo['bones'])==len(ex) and geo['description']['texture_width']==256 and geo['description']['texture_height']==256)
check('delivery_files_complete',all((D/f).is_file() for f in (N+'.bbmodel',N+'.geo.json',N+'_tex.png','README.md','material_manifest.json','structure_audit.json')))

report={'status':'pass' if all(c['pass'] for c in checks) else 'fail','checks':checks,'stats':{'editor_cubes':len(es),'export_cubes':len(ex),'export_bones':len(geo['bones']),'quads':q,'triangle_upper_bound':q*2,'texture_size':[256,256],'bbox':bounds(ex)},'cleanup':{'old_caps_removed':16,'new_caps':16,'new_neck_cubes':0,'cap_visible_faces':32,'duplicate_geometry':0,'note':'Inner and outer cap faces retained for hollow shell lining; no stacked full-model copies or enlarged cap triangles.'},'limitations':['No Minecraft runtime test.','Thin cap plates use binary-alpha triangular masks; they are not arbitrary triangular meshes.','Two-sided cutout shell and separately blended glass must be configured by the integrator.','Texel-footprint proof covers current cap surfaces, not all animation collisions.']}
report.update(read(R/'asset_metadata.json'))
(D/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
print(report['status'],sum(c['pass'] for c in checks),'/',len(checks),'max excess',oldmax,'->',newmax,'quads',q)
for c in checks:
 if not c['pass']:print('FAILED',c)
assert report['status']=='pass'
