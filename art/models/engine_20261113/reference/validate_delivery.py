"""Independent file/UV/geometry verification. Python + Pillow, no private paths."""
import base64, hashlib, itertools, json, math
from pathlib import Path
from PIL import Image
OUT=Path(__file__).resolve().parent.parent
bb=json.loads((OUT/'engine.bbmodel').read_text(encoding='utf-8'))
geo=json.loads((OUT/'engine.geo.json').read_text(encoding='utf-8'))
errors=[];warnings=[]
def check(ok,msg):
    if not ok:errors.append(msg)
def near(a,b):return all(abs(x-y)<.00011 for x,y in zip(a,b))
elements=bb['elements'];groups={g['uuid']:g for g in bb['groups']}
members={};parents={}
def walk(node,parent=None):
    if isinstance(node,str):members[node]=groups[parent]['name'];return
    nodeid=node['uuid'];parents[nodeid]=parent
    for ch in node['children']:walk(ch,nodeid)
for root in bb['outliner']:walk(root)
check(len(members)==len(elements),'Every cube must belong to a group')
bones=geo['minecraft:geometry'][0]['bones']; bone_map={b['name']:b for b in bones}
for gu,g in groups.items():
    check(g['name'] in bone_map,'Missing bone '+g['name'])
    if parents[gu]:check(bone_map[g['name']].get('parent')==groups[parents[gu]]['name'],'Wrong bone parent')
counter={}
face_count=0;allpts=[];densities=[]
for e in elements:
    name=members[e['uuid']];i=counter.get(name,0);counter[name]=i+1
    gc=bone_map[name]['cubes'][i]
    a,b=e['from'],e['to'];o=e['origin'];rot=e.get('rotation',[0,0,0])
    check(near(gc['origin'],[-b[0],a[1],a[2]]),'Export origin mismatch '+e['name'])
    check(near(gc['size'],[b[k]-a[k] for k in range(3)]),'Export size mismatch '+e['name'])
    if any(rot):
        check(near(gc['pivot'],[-o[0],o[1],o[2]]),'Export pivot mismatch '+e['name'])
        check(near(gc['rotation'],[-rot[0],-rot[1],rot[2]]),'Export rotation mismatch '+e['name'])
    for face,f in e['faces'].items():
        face_count+=1;u1,v1,u2,v2=f['uv']
        check(all(0<=v<=512 for v in (u1,v1,u2,v2)),'UV out of range '+e['name'])
        check(f['texture']==0,'Unassigned texture '+e['name'])
        guv=gc['uv'][face];uv=guv['uv'];size=guv['uv_size'];actual=[*uv,uv[0]+size[0],uv[1]+size[1]]
        expected=[u2,v2,u1,v1] if face in ('up','down') else [u1,v1,u2,v2]
        check(near(actual,expected),'UV export mismatch '+e['name']+' '+face)
        dims=[b[k]-a[k] for k in range(3)]
        fw,fh= {'north':(dims[0],dims[1]),'south':(dims[0],dims[1]),'east':(dims[2],dims[1]),'west':(dims[2],dims[1]),'up':(dims[0],dims[2]),'down':(dims[0],dims[2])}[face]
        densities.extend([abs(u2-u1)/fw,abs(v2-v1)/fh])
    for point in itertools.product(*zip(a,b)):
        p=[point[i]-o[i] for i in range(3)]
        for axis,degrees in enumerate(rot):
            if not degrees:continue
            radians=math.radians(degrees);cs,sn=math.cos(radians),math.sin(radians)
            i,j=[(1,2),(2,0),(0,1)][axis];p[i],p[j]=p[i]*cs-p[j]*sn,p[i]*sn+p[j]*cs
        allpts.append([p[i]+o[i] for i in range(3)])
minimum=[min(p[k] for p in allpts) for k in range(3)]
maximum=[max(p[k] for p in allpts) for k in range(3)]
size=[maximum[k]-minimum[k] for k in range(3)]
check(near(minimum,[-43.3,0,-17.55]),'Unexpected minimum bounds')
check(near(maximum,[43.3,42,17.55]),'Unexpected maximum bounds')
texpath=OUT/'engine_tex.png';tex=Image.open(texpath)
check(tex.mode=='RGBA' and tex.size==(512,512),'Texture format')
check(base64.b64decode(bb['textures'][0]['source'].split(',')[1])==texpath.read_bytes(),'Embedded texture mismatch')
check(not bb['textures'][0].get('path'),'Absolute texture dependency')
colors=tex.getcolors(512*512);check(colors is not None,'Too many colors')
alpha=tex.getchannel('A');check(set(alpha.tobytes()) <= {0,255},'Partial alpha')
layout=json.loads((OUT/'reference/uv_layout.json').read_text())['islands']
for i,island in enumerate(layout):
    a=island['uv']
    for other in layout[i+1:]:
        b=other['uv'];check(not(max(a[0],b[0])<min(a[2],b[2]) and max(a[1],b[1])<min(a[3],b[3])),'UV islands overlap')
previews={}
for view in ['front','back','side','oblique']:
    im=Image.open(OUT/('preview_'+view+'.png'));bounds=im.getchannel('A').getbbox()
    check(im.size==(1200,900),'Preview dimensions '+view)
    check(bounds and bounds[0]>0 and bounds[1]>0 and bounds[2]<1200 and bounds[3]<900,'Clipped preview '+view)
    previews[view]={'size':list(im.size),'mode':im.mode,'alpha_bbox':bounds,'projection':'perspective' if view=='oblique' else 'orthographic'}
rt=json.loads((OUT/'reference/native_roundtrip.json').read_text())
check(rt['exact_native_reexport'],'Native import/reexport changed geometry')
check(geo['minecraft:geometry'][0]['description']['identifier']=='geometry.engine','Identifier')
if max(densities)>2.6:warnings.append('Sub-unit thin edges round up to whole texels; their density exceeds the nominal 2 px/BB. Main panel islands keep the nominal density.')
audit={'errors':errors,'warnings':warnings,'cubes':len(elements),'bones':len(bones),'declared_faces':face_count,'independent_bb_geo_geometry_and_uv_match':not errors,'bbox':{'min':minimum,'max':maximum,'size_bb':size,'size_blocks':[v/16 for v in size]},'texture':{'size':list(tex.size),'mode':tex.mode,'palette_colors_including_transparent':len(colors),'sha256':hashlib.sha256(texpath.read_bytes()).hexdigest(),'density_min':min(densities),'density_max':max(densities),'unique_uv_islands':len(layout)},'previews':previews,'native_import_reexport':rt['exact_native_reexport']}
(OUT/'structure_audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
print(json.dumps(audit,ensure_ascii=False,indent=2))
if errors:raise SystemExit(1)
