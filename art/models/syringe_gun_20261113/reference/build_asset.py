"""Rebuild authored cuboids, shared per-face pixel atlas and portable Blockbench input.
Run from the asset directory: python reference/build_asset.py
No downloaded textures or geometry are used in the model.
"""
from pathlib import Path
import json, math, base64, uuid, shutil
from PIL import Image, ImageDraw
from face_cleanup import cleanup

ROOT=Path(__file__).resolve().parents[1]
REF=ROOT/'reference'
PALETTE={
 'sage':['#526962','#42564f','#70867b','#91a095'],
 'dark':['#303c3a','#242e2e','#475552','#61706a'],
 'steel':['#9ca9a4','#788882','#c3cdc4','#e0e4d6'],
 'rubber':['#2d3534','#222b2b','#414b46','#58645b'],
 'ceramic':['#d2d4bc','#a7ae99','#e5e5ce','#f2efd9'],
 'aqua':['#77ada4','#4c7a74','#a2c6b8','#c2d8c5'],
 'brass':['#9b9477','#76745f','#b9b394','#d3c9a5'],
 'recess':['#253432','#1e2b2a','#344944','#4e655e']}
S=[]
def cube(group,name,a,b,mat='sage',rot=None,origin=None,detail='plain',face_details=None):
    S.append(dict(group=group,name=name,from_=a,to=b,material=mat,rotation=rot or [0,0,0],origin=origin or [0,0,0],detail=detail,face_details=face_details or {}))
def bar(group,name,p,q,width,depth,mat):
    dx,dy=q[0]-p[0],q[1]-p[1]; length=math.hypot(dx,dy)
    cube(group,name,[p[0]-width/2,p[1],-depth/2],[p[0]+width/2,p[1]+length,depth/2],mat,[0,0,-math.degrees(math.atan2(dx,dy))],[*p,0])

# Continuous body is split only around the cartridge bay, which is genuinely open.
cube('receiver','rear_pressure_vessel',[-7.8,6.7,-1.15],[-5.9,9.05,1.15],'steel')
cube('receiver','rear_vessel_bevel',[-8.25,7.05,-.95],[-7.8,8.7,.95],'dark')
cube('receiver','rear_end_cap',[-8.5,7.45,-.65],[-8.25,8.3,.65],'steel')
cube('receiver','receiver_core',[-6.2,6.15,-1.15],[-1.05,8.8,1.15],'dark')
cube('receiver','upper_spine',[-6.6,8.8,-.85],[7.4,9.25,.85],'steel',detail='rail')
cube('receiver','lower_spine',[-5.5,5.85,-.8],[7.4,6.5,.8],'dark')
for zsign in [-1,1]:
    z0,z1=sorted([zsign*1.16,zsign*1.36])
    cube('receiver',f'sage_side_cover_{zsign}',[-5.95,6.35,z0],[-1.15,8.65,z1],'sage',detail='controls')
cube('receiver','underside_breech',[-4.7,5.35,-.7],[-1.1,5.85,.7],'steel',detail='rail')
cube('receiver','sight_rear',[-5.7,9.25,-.38],[-4.7,9.55,.38],'dark')
cube('receiver','rear_band',[-7.15,6.64,-1.23],[-6.65,9.13,1.23],'brass')

# Frame surrounding four syrettes: two on either side of a central spine.
cube('cartridge_rack','bay_central_backbone',[-1.0,6.5,-.28],[6.6,9.0,.28],'recess')
cube('cartridge_rack','bay_rear_post',[-1.3,6.5,-1.15],[-.8,9.0,1.15],'dark')
cube('cartridge_rack','bay_front_post',[6.4,6.5,-1.15],[6.95,9.0,1.15],'dark')
for zsign in [-1,1]:
    a,b=sorted([zsign*.82,zsign*1.2])
    for y,name in [(6.42,'bottom'),(8.97,'top')]:
        cube('cartridge_rack',f'bay_{name}_rail_{zsign}',[-.9,y,a],[6.5,y+.27,b],'sage',detail='rail')
    for row,y in enumerate([6.95,8.1]):
        a,b=sorted([zsign*.45,zsign*.98])
        cube('syrettes',f'syrette_{zsign}_{row}_barrel',[.05,y,a],[5.6,y+.8,b],'ceramic',detail='dose')
        a,b=sorted([zsign*.38,zsign*1.07])
        cube('syrettes',f'syrette_{zsign}_{row}_rear_cap',[-.35,y-.07,a],[.2,y+.87,b],'steel')
        cube('syrettes',f'syrette_{zsign}_{row}_front_cap',[5.6,y-.05,a],[6.02,y+.85,b],'steel')
        a,b=sorted([zsign*.52,zsign*.92])
        cube('syrettes',f'syrette_{zsign}_{row}_nozzle',[6.02,y+.15,a],[6.4,y+.43,b],'brass')

# Main grip and open skeletal shoulder stock; angled bars replace slices.
cube('grip','grip_tang',[-.7,5.8,-.85],[.85,6.65,.85],'dark')
cube('grip','pistol_grip',[-.85,.7,-.92],[.85,6.1,.92],'rubber',[0,0,-20],[0,6.1,0],detail='grip')
cube('grip','grip_heel',[-1.05,.55,-1.03],[1.0,1.18,1.03],'steel',[0,0,-20],[0,6.1,0])
for zsign in [-1,1]:
    a,b=sorted([zsign*.92,zsign*1.01])
    cube('grip',f'grip_sage_inlay_{zsign}',[-.57,1.45,a],[.54,4.9,b],'sage',[0,0,-20],[0,6.1,0],detail='grip')
bar('stock','stock_upper_strut',[-8.2,5.8],[-3.1,5.25],.55,1.25,'dark')
bar('stock','stock_shoulder_strut',[-9.75,.65],[-8.2,5.8],.8,1.45,'rubber')
bar('stock','stock_lower_strut',[-9.7,.65],[-2.35,1.05],.55,1.15,'sage')
bar('stock','stock_butt_pad',[-10.0,.28],[-9.55,1.3],.48,1.65,'steel')
cube('stock','stock_mount',[-5.05,5.1,-1.0],[-3.75,6.0,1.0],'dark')

# Open trigger guard and curved forward lever: four straight structural segments.
bar('trigger','guard_front',[2.05,3.55],[2.45,5.95],.3,.65,'steel')
bar('trigger','guard_lower',[-.55,3.5],[2.05,3.55],.3,.65,'steel')
bar('trigger','trigger_blade',[.4,4.4],[.75,5.7],.3,.45,'dark')
bar('trigger','lever_upper',[4.95,3.5],[4.25,6.1],.34,.48,'steel')
bar('trigger','lever_middle',[5.8,2.15],[4.95,3.5],.34,.48,'steel')
bar('trigger','lever_tip',[6.4,1.65],[5.8,2.15],.27,.48,'steel')

# Short forward muzzle with a square open bore and vertical ribbed fore cylinder.
cube('front_assembly','front_bridge',[6.95,7.8,-.65],[9.3,8.55,.65],'steel')
cube('front_assembly','front_upright',[7.2,6.2,-1.17],[7.8,9.55,1.17],'sage',detail='rail')
cube('front_assembly','front_upper_latch',[7.0,9.1,-1.3],[8.2,9.48,1.3],'steel')
cube('front_assembly','forward_cylinder',[8.3,4.1,-.7],[9.65,8.75,.7],'sage',detail='ribs')
cube('front_assembly','cylinder_front_flat',[9.65,4.5,-.48],[9.88,8.4,.48],'dark',detail='ribs')
cube('front_assembly','cylinder_top_cap',[8.1,8.65,-.82],[9.85,9.08,.82],'steel')
cube('front_assembly','cylinder_bottom_cap',[8.2,3.93,-.82],[9.75,4.4,.82],'steel')
cube('front_assembly','muzzle_root',[9.7,8.05,-.38],[10.4,8.6,.38],'dark')
cube('front_assembly','muzzle_lower',[10.4,8.06,-.4],[10.95,8.2,.4],'steel')
cube('front_assembly','muzzle_upper',[10.4,8.48,-.4],[10.95,8.62,.4],'steel')
cube('front_assembly','muzzle_left',[10.4,8.2,-.4],[10.95,8.48,-.24],'steel')
cube('front_assembly','muzzle_right',[10.4,8.2,.24],[10.95,8.48,.4],'steel')

# Face islands are shared only for matching material, dimensions, detail, orientation.
DENSITY=2
islands={}; faces={}
for idx,s in enumerate(S):
    size=[b-a for a,b in zip(s['from_'],s['to'])]
    for face,axes in {'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}.items():
        w,h=[max(1,round(size[a]*DENSITY)) for a in axes]
        detail=s['face_details'].get(face,s['detail'])
        # Surface motifs are only applied where the shape has room for them.
        if face in ['up','down'] or (min(w,h)<3 and detail not in ['dose','grip','ribs']):detail='plain'
        key=(s['material'],w,h,detail,face in ['up','down'])
        islands.setdefault(key,None);faces[idx,face]=key
cursorx=1;cursory=1;rowh=0;ATLAS=64
for key in sorted(islands,key=lambda k:-k[2]):
    mat,w,h,detail,top=key
    if cursorx+w+1>ATLAS:cursorx=1;cursory+=rowh+2;rowh=0
    if cursory+h+1>ATLAS:raise RuntimeError('Atlas budget exceeded')
    islands[key]=(cursorx,cursory,w,h);cursorx+=w+2;rowh=max(rowh,h)
atlas=Image.new('RGBA',(ATLAS,ATLAS),(0,0,0,0))
for key,(x,y,w,h) in islands.items():
    mat,_,_,detail,top=key;c=PALETTE[mat];tile=Image.new('RGBA',(w,h),c[0]);d=ImageDraw.Draw(tile)
    if w>=3 and h>=3:
        d.line((0,0,w-1,0),fill=c[2]);d.line((0,h-1,w-1,h-1),fill=c[1])
        if w>=7:
            d.line((0,1,0,h-2),fill=c[1])
            d.line((w-1,1,w-1,min(2,h-2)),fill=c[3])
    if detail=='controls' and w>=8 and h>=4:
        for px in [2,w-3]:
            d.point((px,1),fill=PALETTE['steel'][2]);d.point((px,2),fill=PALETTE['dark'][0])
        d.line((3,h-2,5,h-2),fill=PALETTE['ceramic'][1])
    elif detail=='dose' and w>=7:
        d.rectangle((int(w*.48),0,int(w*.68),h-1),fill=PALETTE['aqua'][0])
        for px in range(2,w-2,3):d.point((px,h-1),fill=PALETTE['dark'][2])
        d.line((0,0,1,0),fill=PALETTE['brass'][1])
        d.point((w-2,0),fill=c[2])
    elif detail in ['grip','ribs']:
        for py in range(2,h-1,3):
            d.line((1,py,w-2,py),fill=c[1])
            if py+1<h-1:d.line((1,py+1,w-2,py+1),fill=c[2])
    elif detail=='rail' and h>=3:d.line((1,1,w-2,1),fill=c[1])
    # A few authored edge chips, no random noise.
    if w>=12 and h>=4 and mat in ['sage','steel','dark']:
        d.line((2,0,3,0),fill=c[3]);d.line((w-4,h-1,w-3,h-1),fill=c[2])
    atlas.paste(tile,(x,y))
    # One-pixel extruded borders; UV excludes padding.
    atlas.paste(tile.crop((0,0,w,1)),(x,y-1));atlas.paste(tile.crop((0,h-1,w,h)),(x,y+h))
    atlas.paste(tile.crop((0,0,1,h)),(x-1,y));atlas.paste(tile.crop((w-1,0,w,h)),(x+w,y))
atlas.save(ROOT/'syringe_gun_tex.png')
texture_id=str(uuid.uuid5(uuid.NAMESPACE_URL,'syringe_gun_texture'))
groups={g:str(uuid.uuid5(uuid.NAMESPACE_URL,'syringe_gun_'+g)) for g in dict.fromkeys(s['group'] for s in S)}
elements=[]
for idx,s in enumerate(S):
    f={}
    for face in ['north','east','south','west','up','down']:
        x,y,w,h=islands[faces[idx,face]]
        f[face]={'uv':[x,y,x+w,y+h],'texture':0}
    elements.append({'name':s['name'],'type':'cube','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'syringe_gun_'+s['name'])),'from':[round(v*.75,6) for v in s['from_']],'to':[round(v*.75,6) for v in s['to']],'origin':[round(v*.75,6) for v in s['origin']],'rotation':s['rotation'],'box_uv':False,'autouv':0,'color':list(groups).index(s['group'])%8,'faces':f})
model={'meta':{'format_version':'5.0','model_format':'bedrock','box_uv':False},'name':'注射器枪 SyringeGun','model_identifier':'syringe_gun','geometry_name':'syringe_gun','visible_box':[2.5,2.0,0.3],'resolution':{'width':ATLAS,'height':ATLAS},'elements':elements,'outliner':[{'name':g,'origin':[0,0,0],'uuid':gid,'children':[e['uuid'] for e,s in zip(elements,S) if s['group']==g]} for g,gid in groups.items()],'textures':[{'name':'syringe_gun_tex.png','id':'0','uuid':texture_id,'mode':'bitmap','width':ATLAS,'height':ATLAS,'uv_width':ATLAS,'uv_height':ATLAS,'source':'data:image/png;base64,'+base64.b64encode((ROOT/'syringe_gun_tex.png').read_bytes()).decode(),'render_mode':'default','visible':True,'internal':True,'saved':True}]}
cleanup_report=cleanup(elements)
selection_path=REF/'verified_face_selection.json'
selection=json.loads(selection_path.read_text(encoding='utf-8')) if selection_path.exists() else {'accepted':[]}
accepted={(r['element'],r['face']) for r in selection['accepted']}
cleanup_report['geometric_candidates']=cleanup_report['removed_faces']
for row in cleanup_report['geometric_candidates']:
    if (row['element'],row['face']) not in accepted:
        next(e for e in elements if e['name']==row['element'])['faces'][row['face']]['texture']=0
cleanup_report['removed_faces']=[r for r in cleanup_report['geometric_candidates'] if (r['element'],r['face']) in accepted]
cleanup_report['removed_face_count']=len(cleanup_report['removed_faces'])
cleanup_report['retained_reason']='Only native raster-verified candidates may be deleted; see verified_face_selection.json'
(REF/'face_cleanup_audit.json').write_text(json.dumps(cleanup_report,indent=2),encoding='utf-8')
(REF/'authored_input.bbmodel').write_text(json.dumps(model,ensure_ascii=False,indent=2),encoding='utf-8')
(REF/'palette.json').write_text(json.dumps(PALETTE,ensure_ascii=False,indent=2),encoding='utf-8')
(REF/'geometry_spec.json').write_text(json.dumps(S,ensure_ascii=False,indent=2),encoding='utf-8')
(REF/'design_budget.json').write_text(json.dumps({'cube_budget':85,'bone_budget':8,'material_budget':1,'texture':[64,64],'texture_density_px_per_unit':2/.75,'authoring_to_final_scale':.75,'animation':False,'actual_cubes':len(S),'actual_groups':len(groups),'islands':len(islands),'source':'Reference-driven new asset; inferred depth and reverse side'},indent=2),encoding='utf-8')
print(json.dumps({'cubes':len(S),'groups':len(groups),'islands':len(islands),'atlas_used_height':cursory+rowh+1,'size':ATLAS}))
