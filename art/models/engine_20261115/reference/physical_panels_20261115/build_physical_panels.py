"""Independent thin covers, washers and slotted heads on the frozen current edit."""
import base64,copy,hashlib,io,itertools,json,uuid
from pathlib import Path
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent;OUT=HERE.parent.parent
NS=uuid.UUID('47cdc9f2-3201-47d4-ae48-4b1c5f3e335c')
def uid(name):return str(uuid.uuid5(NS,name))
baseline=json.loads((HERE/'baseline_engine.bbmodel').read_text(encoding='utf-8'));model=copy.deepcopy(baseline)
before=Image.open(HERE/'baseline_engine_tex.png').convert('RGBA');image=before.copy()
old_paint=Image.open(OUT/'reference/texture_detail_pass_20261115/user_baseline_tex.png').convert('RGBA')
detail=json.loads((OUT/'reference/texture_detail_pass_20261115/texture_detail_report.json').read_text(encoding='utf-8'))
sx=image.width/model['resolution']['width'];sy=image.height/model['resolution']['height']
elements={e['uuid']:e for e in model['elements']};groups={g['uuid']:g for g in model['groups']};nodes={};members={}
def walk(n,parent=None):
    if isinstance(n,str):members[n]=parent;return
    nodes[n['uuid']]=n
    for ch in n['children']:walk(ch,n['uuid'])
for n in model['outliner']:walk(n)
P={'head':(161,164,141,255),'side':(139,145,129,255),'washer':(72,83,77,255),'shell_side':(96,107,97,255)}
new_names=[];panel_records=[];bolt_records=[];cleanup=set()
def face_rect(e,face):
    u1,v1,u2,v2=e['faces'][face]['uv'];return [round(u1*sx),round(v1*sy),round(u2*sx),round(v2*sy)]
def clean_hatch(e,face,rect):
    x0,y0,xend,yend=face_rect(e,face);w,h=xend-x0,yend-y0;x1,y1,x2,y2=rect
    mask=Image.new('1',(w,h),0);d=ImageDraw.Draw(mask);d.rectangle((x1,y1,x2,y2),outline=1,width=1);d.line((x1+2,y2+1,x2-2,y2+1),fill=1)
    for xx,yy in [(x1+2,y1+2),(x2-3,y1+2),(x1+2,y2-2),(x2-3,y2-2)]:d.point((xx,yy),fill=1);d.point((xx+1,yy),fill=1)
    for y in range(h):
        for x in range(w):
            if mask.getpixel((x,y)):
                point=(x0+x,y0+y);now=image.getpixel(point);old=old_paint.getpixel(point)
                if now!=old:image.putpixel(point,old);cleanup.add(point)
def solid_uv(color):
    pix=image.load()
    for y in range(image.height-1):
        for x in range(image.width-1):
            if all(pix[x+dx,y+dy]==color for dx in [0,1] for dy in [0,1]):return [x/sx,y/sy,(x+2)/sx,(y+2)/sy]
    raise RuntimeError('No constant material patch available')
material_uv={}
def material(name):
    if name not in material_uv:material_uv[name]=solid_uv(P[name])
    return material_uv[name]
def group(name,parent,origin,rotation):
    g={'uuid':uid(name),'name':name,'origin':origin[:],'rotation':rotation[:],'export':True,'visibility':True}
    model['groups'].append(g);groups[g['uuid']]=g;n={'uuid':g['uuid'],'isOpen':True,'children':[]};nodes[g['uuid']]=n;nodes[parent]['children'].append(n);return g['uuid']
def new_cube(name,parent,a,b,uv_default,face_uv=None):
    a=[round(v,4) for v in a];b=[round(v,4) for v in b]
    faces={f:{'uv':uv_default[:],'texture':0} for f in ['north','east','south','west','up','down']}
    for f,v in (face_uv or {}).items():faces[f]['uv']=v[:]
    e={'uuid':uid(name),'name':name,'type':'cube','from':a,'to':b,'origin':[round((a[i]+b[i])/2,4) for i in range(3)],'rotation':[0,0,0],'faces':faces,'box_uv':False,'autouv':0,'export':True,'color':6}
    model['elements'].append(e);nodes[parent]['children'].append(e['uuid']);new_names.append(name);return e
axes={'up':([1,0,0],[0,0,1],[0,1,0]),'down':([1,0,0],[0,0,-1],[0,-1,0]),'south':([1,0,0],[0,-1,0],[0,0,1]),'north':([-1,0,0],[0,-1,0],[0,0,-1]),'east':([0,0,-1],[0,-1,0],[1,0,0]),'west':([0,0,1],[0,-1,0],[-1,0,0])}
def surface_point(e,face,px,py):
    assert not e['faces'][face].get('rotation',0),'Rotated UV face requires explicit mapping'
    u1,v1,u2,v2=e['faces'][face]['uv'];tu=px/(abs(u2-u1)*sx);tv=py/(abs(v2-v1)*sy);a,b=e['from'],e['to'];dx,dy,dz=[b[i]-a[i] for i in range(3)]
    return {'up':[a[0]+tu*dx,b[1],a[2]+tv*dz],'down':[a[0]+tu*dx,a[1],b[2]-tv*dz],'south':[a[0]+tu*dx,b[1]-tv*dy,b[2]],'north':[b[0]-tu*dx,b[1]-tv*dy,a[2]],'east':[b[0],b[1]-tv*dy,b[2]-tu*dz],'west':[a[0],b[1]-tv*dy,a[2]+tu*dz]}[face]
def local_box(point,u,v,n,urange,vrange,nrange):
    pts=[[point[i]+u[i]*a+v[i]*b+n[i]*c for i in range(3)] for a,b,c in itertools.product(urange,vrange,nrange)]
    return [min(p[i] for p in pts) for i in range(3)],[max(p[i] for p in pts) for i in range(3)]
def bolt(name,parent,e,face,px,py,depth):
    p=surface_point(e,face,px,py);u,v,n=axes[face]
    washer=new_cube(name+'_washer',parent,*local_box(p,u,v,n,[-.55,.55],[-.55,.55],[depth,depth+.12]),material('washer'))
    for suffix,urange in [('head_a',[-.425,-.06]),('head_b',[.06,.425])]:
        new_cube(name+'_'+suffix,parent,*local_box(p,u,v,n,urange,[-.425,.425],[depth+.12,depth+.40]),material('head'))
    bolt_records.append({'name':name,'parent_group':parent,'source_uuid':e['uuid'],'face':face,'pixel_position':[px,py],'part_cubes':3,'head_size_bb':.85,'actual_slot_gap_bb':.12,'washer_depth_bb':.12,'head_depth_bb':.28})
def panel(name,e,face,rect,existing=False):
    parent=group(name,members[e['uuid']],e['origin'],e.get('rotation',[0,0,0]));x1,y1,x2,y2=rect
    if not existing:
        clean_hatch(e,face,rect)
        p1=surface_point(e,face,x1+.5,y1+.5);p2=surface_point(e,face,x2+.5,y2+.5);n=axes[face][2]
        pts=[[p[i]+n[i]*d for i in range(3)] for p,d in itertools.product([p1,p2],[.035,.535])]
        a=[min(p[i] for p in pts) for i in range(3)];b=[max(p[i] for p in pts) for i in range(3)]
        u1,v1,_,_=e['faces'][face]['uv'];front_uv=[u1+(x1+1)/sx,v1+(y1+1)/sy,u1+x2/sx,v1+y2/sy]
        if name=='motor_bearing_service_cover':
            # The original 3.6-square shaft rotates through this face. A 5.6
            # square aperture clears its entire 2.546-BB radial envelope.
            assert face=='west'
            pieces=[('upper',[a[0],23.8,a[2]],b[:]),('lower',a[:],[b[0],18.2,b[2]]),('left',[a[0],18.2,a[2]],[b[0],23.8,-2.8]),('right',[a[0],18.2,2.8],[b[0],23.8,b[2]])]
            for suffix,pa,pb in pieces:
                fu,fv,eu,ev=front_uv
                uv=[fu+(pa[2]-a[2])/(b[2]-a[2])*(eu-fu),fv+(b[1]-pb[1])/(b[1]-a[1])*(ev-fv),fu+(pb[2]-a[2])/(b[2]-a[2])*(eu-fu),fv+(b[1]-pa[1])/(b[1]-a[1])*(ev-fv)]
                new_cube(name+'_plate_'+suffix,parent,pa,pb,material('shell_side'),{face:uv})
        else:new_cube(name+'_plate',parent,a,b,material('shell_side'),{face:front_uv})
        positions=[(x1+3,y1+2.5),(x2-2,y1+2.5),(x1+3,y2-1.5),(x2-2,y2-1.5)];depth=.55
    else:
        # These two rear covers already have physical thickness: retain them.
        x0,y0,xend,yend=face_rect(e,face);w,h=xend-x0,yend-y0
        positions=[(3.5,3.5),(w-3.5,3.5),(3.5,h-3.5),(w-3.5,h-3.5)];depth=.035
        base=max(before.crop((x0,y0,xend,yend)).getcolors(w*h))[1]
        for xx,yy in [(3,3),(w-4,3),(3,h-4),(w-4,h-4)]:
            for dx in [0,1]:
                point=(x0+xx+dx,y0+yy)
                if image.getpixel(point)==P['head']:image.putpixel(point,base);cleanup.add(point)
    for i,(x,y) in enumerate(positions):bolt(name+f'_bolt_{i+1:02}',parent,e,face,x,y,depth)
    panel_records.append({'name':name,'source_uuid':e['uuid'],'source_name':e['name'],'face':face,'pixel_region':rect,'existing_plate_reused':existing,'new_plate_cubes':0 if existing else 4 if name=='motor_bearing_service_cover' else 1,'bolts':4,'parent_bone':groups[members[e['uuid']]]['name'],'source_rotation':e.get('rotation',[0,0,0])})

for target in detail['targets']:
    e=elements[target['uuid']];face=target['face'];x0,y0,xend,yend=target['pixel_rect'];w,h=xend-x0,yend-y0;kind=target['kind']
    if kind=='roof':
        panel('roof_cover_front',e,face,[8,7,w//2-8,h-9]);panel('roof_cover_rear',e,face,[w//2+8,7,w-8,h-9])
    elif kind=='end_access':panel('right_end_service_cover',e,face,[7,5,w-8,h-17])
    elif kind=='inclined_access':panel('right_bevel_cover' if e['uuid'].startswith('877d') else 'left_bevel_cover',e,face,[3,8,w-4,h-10])
    elif kind=='motor_backplate':panel('motor_bearing_service_cover',e,face,[6,7,w-7,h-8])
    elif kind=='belly_access':
        panel('underside_cover_left',e,face,[10,8,w//2-7,h-8]);panel('underside_cover_right',e,face,[w//2+7,8,w-10,h-8])
    elif kind=='shroud_lower':panel('shroud_lower_service_cover',e,face,[3,h//2+3,w-4,h-5])
    elif kind=='flare_plate':panel('nozzle_segment_cover_'+e['name'].rsplit('_',1)[1],e,face,[2,3,w-3,h-4])
for e in baseline['elements']:
    if e['name'] in ['rear_access_0','rear_access_1']:panel(e['name']+'_physical_fasteners',e,'north',[1,1,42,42],existing=True)
assert len(panel_records)==19 and len(bolt_records)==76
png=io.BytesIO();image.save(png,format='PNG');data=png.getvalue();model['textures'][0]['source']='data:image/png;base64,'+base64.b64encode(data).decode()
(OUT/'engine.bbmodel').write_text(json.dumps(model,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n');(OUT/'engine_tex.png').write_bytes(data)
manifest={'baseline_sha256':hashlib.sha256((HERE/'baseline_engine.bbmodel').read_bytes()).hexdigest(),'baseline_cubes':len(baseline['elements']),'original_cubes_preserved':all(elements[e['uuid']]==e for e in baseline['elements']),'original_groups_preserved':all(groups[g['uuid']]==g for g in baseline['groups']),'original_animations_preserved':model['animations']==baseline['animations'],'new_plate_bodies':17,'existing_plate_bodies_reused':2,'independent_fasteners':len(bolt_records),'slot_is_geometry':True,'added_cubes':len(new_names),'final_cubes':len(model['elements']),'final_bones':len(model['groups']),'panels':panel_records,'fasteners':bolt_records,'new_element_names':new_names,'texture_cleanup_pixels':[list(p) for p in sorted(cleanup)],'material_uv':material_uv,'notes':['Original face UVs are unchanged. New plate skins intentionally reuse the corresponding original panel UV subregions.','Washer and screw-head faces intentionally share constant-color material patches.','Only corresponding painted panel borders and large fastener marks were removed; internal vent and handle pixels remain.']}
(HERE/'physical_panel_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
print(json.dumps({k:v for k,v in manifest.items() if k not in ['panels','fasteners','new_element_names','texture_cleanup_pixels','material_uv','notes']},ensure_ascii=False))
