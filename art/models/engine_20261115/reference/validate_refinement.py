"""Independent delivered-file checks, user-edit preservation, and motion limits."""
import base64,hashlib,io,itertools,json,math
from pathlib import Path
from PIL import Image
OUT=Path(__file__).resolve().parent.parent;REF=OUT/'reference'
bb=json.loads((OUT/'engine.bbmodel').read_text(encoding='utf-8'))
baseline=json.loads((REF/'user_baseline.bbmodel').read_text(encoding='utf-8'))
geo=json.loads((OUT/'engine.geo.json').read_text(encoding='utf-8'))
anim=json.loads((OUT/'engine.animation.json').read_text(encoding='utf-8'))
manifest=json.loads((REF/'refinement_manifest.json').read_text(encoding='utf-8'))
native=json.loads((REF/'native_roundtrip.json').read_text(encoding='utf-8'))
samples=json.loads((REF/'native_animation_samples.json').read_text(encoding='utf-8'))
errors=[];warnings=[]
def check(ok,msg):
    if not ok:errors.append(msg)
def near(a,b,tol=.00011):return len(a)==len(b) and all(abs(x-y)<tol for x,y in zip(a,b))
def geom(e):return {k:e.get(k,[0,0,0] if k=='rotation' else None) for k in ['name','from','to','origin','rotation','faces']}
old={e['uuid']:e for e in baseline['elements']};new={e['uuid']:e for e in bb['elements']}
preserved=0
for u in manifest['preserved_uuids']:
    a,b=geom(old[u]),geom(new[u]);check(a==b,'User baseline cube changed: '+a['name']);preserved+=1
check(preserved==127,'Unexpected preservation count')
check(hashlib.sha256((REF/'user_baseline.bbmodel').read_bytes()).hexdigest()==manifest['baseline_sha256'],'Baseline snapshot changed')
groups={g['uuid']:g for g in bb['groups']};names={g['name']:g for g in bb['groups']};members={};parents={}
def walk(node,parent=None):
    if isinstance(node,str):members[node]=parent;return
    parents[node['uuid']]=parent
    for ch in node['children']:walk(ch,node['uuid'])
for n in bb['outliner']:walk(n)
bones=geo['minecraft:geometry'][0]['bones'];bmap={b['name']:b for b in bones};counter={}
check(len(members)==len(bb['elements']),'Unparented cube')
for u,g in groups.items():
    bone=bmap[g['name']];check(near(bone['pivot'],[-g['origin'][0],*g['origin'][1:]]),'Bone pivot mismatch '+g['name'])
    rotation=g.get('rotation',[0,0,0]);check(near(bone.get('rotation',[0,0,0]),[-rotation[0],-rotation[1],rotation[2]]),'Bone rotation mismatch '+g['name'])
    expected=groups[parents[u]]['name'] if parents[u] else None;check(bone.get('parent')==expected,'Bone parent mismatch '+g['name'])
faces=0;rects=set();world=[]
def rotate(p,pivot,rotation):
    p=[p[i]-pivot[i] for i in range(3)]
    for axis,degrees in enumerate(rotation):
        angle=math.radians(degrees);cs,sn=math.cos(angle),math.sin(angle);i,j=[(1,2),(2,0),(0,1)][axis]
        p[i],p[j]=p[i]*cs-p[j]*sn,p[i]*sn+p[j]*cs
    return [p[i]+pivot[i] for i in range(3)]
for e in bb['elements']:
    group=groups[members[e['uuid']]]['name'];index=counter.get(group,0);counter[group]=index+1;gc=bmap[group]['cubes'][index]
    a,b=e['from'],e['to'];o=e['origin'];r=e.get('rotation',[0,0,0])
    check(near(gc['origin'],[-b[0],a[1],a[2]]),'Cube origin mismatch '+e['name'])
    check(near(gc['size'],[b[i]-a[i] for i in range(3)]),'Cube size mismatch '+e['name'])
    if any(r):check(near(gc['pivot'],[-o[0],o[1],o[2]]) and near(gc['rotation'],[-r[0],-r[1],r[2]]),'Cube rotation mismatch '+e['name'])
    for face,f in e['faces'].items():
        faces+=1;uv=f['uv'];rects.add(tuple(uv));check(f['texture']==0,'Missing texture '+e['name'])
        check(all(0<=x<=512 for x in uv),'Out-of-range UV '+e['name'])
        g=gc['uv'][face];u,v=g['uv'];w,h=g['uv_size'];actual=[u,v,u+w,v+h]
        expected=[uv[2],uv[3],uv[0],uv[1]] if face in ['up','down'] else uv
        check(near(actual,expected),'UV export mismatch '+e['name']+' '+face)
    for p in itertools.product(*zip(a,b)):
        p=rotate(p,o,r);groupid=members[e['uuid']]
        while groupid:
            g=groups[groupid];p=rotate(p,g['origin'],g.get('rotation',[0,0,0]));groupid=parents[groupid]
        world.append(p)
bbox={'min':[min(p[i] for p in world) for i in range(3)],'max':[max(p[i] for p in world) for i in range(3)]}
bbox['size']=[bbox['max'][i]-bbox['min'][i] for i in range(3)]
check(near(bbox['min'],native['bbox']['min']) and near(bbox['max'],native['bbox']['max']),'Independent and native transformed bounds differ')
rects=list(rects)
for i,a in enumerate(rects):
    for b in rects[i+1:]:check(not(max(a[0],b[0])<min(a[2],b[2]) and max(a[1],b[1])<min(a[3],b[3])),'Distinct UV islands overlap')
tex=Image.open(OUT/'engine_tex.png');embedded=base64.b64decode(bb['textures'][0]['source'].split(',')[1])
check(embedded==(OUT/'engine_tex.png').read_bytes(),'Embedded/external texture bytes differ')
check(tex.mode=='RGBA' and tex.size==(512,512),'Texture format')
check(set(tex.getchannel('A').tobytes())<={0,255},'Partial-alpha texels')
before=Image.open(REF/'user_baseline_tex.png').convert('RGBA').tobytes();after=tex.tobytes()
check(all(before[i:i+4]==after[i:i+4] for i in range(0,len(before),4) if before[i+3]),'Original opaque texture pixels modified')
for key in ['reopened_project_geometry_match','reopened_project_animation_match','geo_import_reexport_match','animation_import_reexport_match']:check(native[key],key)
for name,frames in samples['samples'].items():
    for s in frames:
        spin=name!='animation.engine.pistons';pump=name!='animation.engine.rotor_spin'
        check(abs(s['rotor_degrees']-(450*s['time'] if spin else 0))<1e-7,'Rotor speed changed')
        displacement=1.5*math.cos(math.radians(s['time']*300)) if pump else 0
        check(abs(s['left_y']-(2+displacement))<1e-7 and abs(s['right_y']-(2-displacement))<1e-7,'Pump phase/amplitude mismatch')
    first,last=frames[0],frames[-1]
    check(abs((last['rotor_degrees']-first['rotor_degrees'])%360)<1e-6,'Rotor loop seam')
    check(abs(first['left_y']-last['left_y'])<1e-7 and abs(first['right_y']-last['right_y'])<1e-7,'Piston loop seam')
radius=max(s['blade_radius'] for f in samples['samples'].values() for s in f);clearance=14.95-radius
check(clearance>2,'Insufficient blade-to-mouth clearance')
check(all(s['blade_x'][1]<-37.7 for frames in samples['samples'].values() for s in frames),'Blades enter narrower flare')
elements={e['name']:e for e in bb['elements']}
guide_gap=min(elements[side+'_piston_head']['from'][1]-1.5-elements[side+'_guide_0']['to'][1] for side in ['left','right'])
check(guide_gap>.29,'Guide posts penetrate head at minimum stroke')
clip_specs={'animation.engine.rotor_spin':.8,'animation.engine.pistons':1.2,'animation.engine.running':2.4}
for name,length in clip_specs.items():check(anim['animations'][name]['loop'] is True and anim['animations'][name]['animation_length']==length,'Clip loop/length '+name)
previews={}
for v in ['front','back','side','oblique']:
    image=Image.open(OUT/f'preview_{v}.png');bounds=image.getchannel('A').getbbox()
    check(image.size==(1200,900),'Static preview size '+v);check(bounds[0]>0 and bounds[1]>0 and bounds[2]<1200 and bounds[3]<900,'Clipped static preview '+v)
    previews[v]={'size':list(image.size),'alpha_bbox':bounds,'projection':'perspective' if v=='oblique' else 'orthographic'}
for name,frames,dimensions in [('running',60,(960,720)),('rotor',20,(600,600)),('pistons',30,(600,600))]:
    gif=Image.open(OUT/f'preview_{name}.gif');check(gif.size==dimensions and gif.n_frames==frames,'Motion preview format '+name)
    check(gif.info.get('loop')==0,'GIF loop '+name);previews[name]={'size':list(gif.size),'frames':gif.n_frames,'frame_duration_ms':40,'loop':True}
warnings.append('User baseline geometry/UV scaling and narrow whole-texel patches have different pixel densities; no global UV repack was performed.')
report={'errors':errors,'warnings':warnings,'cubes':len(bb['elements']),'bones':len(bones),'declared_faces':faces,'unique_uv_islands':len(rects),'transformed_bbox':bbox,'size_blocks':[v/16 for v in bbox['size']],'preserved_user_cubes':preserved,'original_opaque_texels_preserved':True,'native_roundtrip':native,'motion':{'spin_seconds_per_turn':.8,'piston_period_seconds':1.2,'piston_total_stroke_bb':3,'phase_offset_degrees':180,'blade_max_radius_bb':radius,'mouth_inradius_bb':14.95,'conservative_radial_clearance_bb':clearance,'guide_head_min_gap_bb':guide_gap},'previews':previews}
(OUT/'structure_audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
print(json.dumps({k:v for k,v in report.items() if k not in ['previews','native_roundtrip']},ensure_ascii=False,indent=2))
if errors:raise SystemExit(1)
