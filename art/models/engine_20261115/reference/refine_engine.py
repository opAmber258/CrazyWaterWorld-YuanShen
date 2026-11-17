"""Incremental refinement of the frozen user edit; never rebuild the old engine."""
import base64,copy,hashlib,io,json,math,uuid
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
OUT=Path(__file__).resolve().parent.parent
REF=OUT/'reference'
NS=uuid.UUID('55ac05ee-af35-4fdd-8765-ebf35ed93712')
def uid(s):return str(uuid.uuid5(NS,s))
model=json.loads((REF/'user_baseline.bbmodel').read_text(encoding='utf-8'))
baseline=copy.deepcopy(model)
palette=json.loads((REF/'palette.json').read_text())
im=Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',')[1]))).convert('RGBA')
original_pixels=im.copy();mask=im.getchannel('A').point(lambda a:255 if a else 0).filter(ImageFilter.MaxFilter(3))
cache={};new_islands=[]
def reserve(w,h):
    # Integral occupancy grid; never allocate over a single original texel.
    data=list(mask.tobytes());prefix=[[0]*513 for _ in range(513)]
    for y in range(512):
        total=0;prior=prefix[y];row=prefix[y+1]
        for x in range(512):total+=bool(data[y*512+x]);row[x+1]=prior[x+1]+total
    for y in list(range(462,511-h))+list(range(1,462)):
        if y+h>511:continue
        for x in range(1,511-w):
            occupied=prefix[y+h][x+w]-prefix[y][x+w]-prefix[y+h][x]+prefix[y][x]
            if not occupied:
                ImageDraw.Draw(mask).rectangle((x-1,y-1,x+w,y+h),fill=255)
                return x,y
    raise RuntimeError('New UV patches do not fit original 512 atlas')
def tile(w,h,mat,pattern):
    w,h=max(1,math.ceil(w*2)),max(1,math.ceil(h*2));key=(w,h,mat,pattern)
    if key in cache:return cache[key]
    x,y=reserve(w,h);d=ImageDraw.Draw(im);d.rectangle((x,y,x+w-1,y+h-1),fill=palette[mat])
    def line(a,b,c,e,col):d.line((x+a,y+b,x+c,y+e),fill=palette[col],width=1)
    if pattern=='blade' and w>3 and h>6:
        line(0,1,0,h-2,'steel_light');line(w-1,1,w-1,h-2,'dark_light')
        line(1,h-3,w-2,h-3,'shell_dark')
    elif pattern=='head' and w>5 and h>3:
        line(1,1,w-2,1,'steel_light');line(2,h-2,w-3,h-2,'steel')
    elif pattern=='cylinder' and w>3 and h>4:
        line(0,1,0,h-2,'steel');line(w-1,1,w-1,h-2,'dark')
        line(1,h-2,w-2,h-2,'brass')
    elif pattern=='rod' and w>2:
        line(1,0,1,h-1,'edge');line(w-1,0,w-1,h-1,'steel')
    elif pattern=='bevel' and w>10 and h>10:
        d.rectangle((x,y,x+w-1,y+h-1),fill=palette['shell_light'])
        line(2,2,w-3,2,'shell_light');line(2,h-3,w-3,h-3,'shell_dark')
    uv=[x,y,x+w,y+h];cache[key]=uv;new_islands.append({'uv':uv,'material':mat,'pattern':pattern});return uv

nodes={}
def collect(node):
    if not isinstance(node,dict):return
    nodes[node['uuid']]=node
    for child in node['children']:collect(child)
for node in model['outliner']:collect(node)
groups={g['name']:g for g in model['groups']}
def remove(name):
    e=next(e for e in model['elements'] if e['name']==name)
    model['elements'].remove(e)
    for n in nodes.values():
        if e['uuid'] in n['children']:n['children'].remove(e['uuid'])
def add_group(name,parent,origin,rotation=None):
    g={'name':name,'uuid':uid(name),'origin':origin,'rotation':rotation or [0,0,0],'export':True,'visibility':True}
    model['groups'].append(g);groups[name]=g
    node={'uuid':g['uuid'],'isOpen':True,'children':[]};nodes[g['uuid']]=node
    nodes[groups[parent]['uuid']]['children'].append(node)
def cube(name,group,a,b,mat='steel',patterns=None,rotation=None,origin=None):
    a=[round(v,4) for v in a];b=[round(v,4) for v in b]
    dx,dy,dz=[b[i]-a[i] for i in range(3)];patterns=patterns or {}
    faces={f:{'uv':tile(w,h,mat,patterns.get(f)),'texture':0} for f,(w,h) in {'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}.items()}
    e={'name':name,'uuid':uid(name),'type':'cube','from':a,'to':b,'origin':[round(v,4) for v in (origin or [(a[i]+b[i])/2 for i in range(3)])],'rotation':rotation or [0,0,0],'box_uv':False,'autouv':0,'export':True,'faces':faces,'color':2}
    model['elements'].append(e);nodes[groups[group]['uuid']]['children'].append(e['uuid']);return e

# A continuous, broad 45-degree face from the crown to the end wall.
for name in ['end_shoulder_upper','end_shoulder_lower']:remove(name)
end=next(e for e in model['elements'] if e['name']=='rounded_end');end['to'][1]=31
length=math.sqrt(8*8+8*8);thickness=4
cx,cy=39.3-thickness/(2*math.sqrt(2)),35-thickness/(2*math.sqrt(2))
cube('end_continuous_bevel','housing',[cx-length/2,cy-thickness/2,-12.02],[cx+length/2,cy+thickness/2,12.02],'shell',{'up':'bevel'},rotation=[0,0,-45],origin=[cx,cy,0])
# Re-fit the two existing roof bevels to the user's thickened shell. Their outer
# edges meet crown (Y39,Z12) and shell (Y35,Z14.4) without the old overhang.
edge_length=math.hypot(4,2.4);angle=math.degrees(math.atan2(2.4,4));depth=3
for sign in [1,-1]:
    e=next(e for e in model['elements'] if e['name']==f'crown_bevel_{10.9*sign}')
    yy=37-(2.4/edge_length)*(depth/2);zz=sign*(13.2-(4/edge_length)*(depth/2))
    e['from']=[-19.7,round(yy-edge_length/2,4),round(zz-depth/2,4)]
    e['to']=[35.3,round(yy+edge_length/2,4),round(zz+depth/2,4)]
    e['origin']=[7.8,round(yy,4),round(zz,4)];e['rotation']=[round(-sign*angle,4),0,0]

# Keep the user-sized shaft and open shroud, rebuild only the moving rotor head.
for name in ['rotor_hub']+[f'rotor_blade_{i}' for i in range(4)]:remove(name)
C=[-39.6,21,0];groups['rotor']['origin']=C
cube('rotor_hub_core','rotor',[-41.9,18.5,-2.5],[-39.0,23.5,2.5],'dark_light')
for i in range(8):
    r=3.1;t=1.2;half=r*math.tan(math.pi/8)+.12
    cube('rotor_hub_chamfer_'+str(i),'rotor',[-42.0,21+r-t/2,-half],[-39.0,21+r+t/2,half],'steel',rotation=[i*45,0,0],origin=C)
cube('rotor_center_cap','rotor',[-42.25,19.4,-1.6],[-41.95,22.6,1.6],'shell_dark',{'west':'head'})
for i,(y,z) in enumerate([(23.3,0),(21,2.3),(18.7,0),(21,-2.3)]):
    cube('rotor_hub_fastener_'+str(i),'rotor',[-42.15,y-.35,z-.35],[-41.9,y+.35,z+.35],'brass')
for i in range(4):
    g=f'rotor_blade_{i+1}';add_group(g,'rotor',C,[22.5+i*90,0,0])
    cube(g+'_root',g,[-40.9,23.5,-1.8],[-38.7,26,1.8],'dark_light')
    cube(g+'_cuff',g,[-41.0,24.5,-2.05],[-38.6,25.25,2.05],'brass')
    cube(g+'_pitched_face',g,[-40.6,25.2,-2.2],[-39.2,31.1,2.2],'steel',{'west':'blade'},rotation=[0,-22,0],origin=[-39.9,26,0])
    cube(g+'_swept_tip',g,[-40.6,30.7,-1.5],[-39.2,33.7,1.5],'steel_light',{'west':'blade'},rotation=[6,-22,0],origin=[-39.9,30.7,0])

# Fixed cylinders and guides. Only rod/head bones reciprocate.
for i,x in enumerate([19.8,28.3]):
    for old in [f'socket_{i}',f'socket_hood_{i}',f'socket_neck_{i}']:remove(old)
    name='left' if i==0 else 'right'
    add_group('piston_'+name,'control_panel',[x,8.5,16.5])
    add_group('piston_'+name+'_moving','piston_'+name,[x,10.5,16.5])
    fixed='piston_'+name;moving=fixed+'_moving'
    cube(name+'_pump_mount',fixed,[x-1.9,5.8,15.1],[x+1.9,7.2,18.2],'shell_dark')
    cube(name+'_pump_cylinder',fixed,[x-1.4,6.8,15.3],[x+1.4,10.5,18.0],'dark_light',{'south':'cylinder'})
    cube(name+'_pump_gland',fixed,[x-1.65,10.1,15.2],[x+1.65,10.9,18.1],'steel')
    for k,xx in enumerate([x-1.5,x+1.0]):
        cube(f'{name}_guide_{k}',fixed,[xx,7.5,15.6],[xx+.5,12.1,16.1],'dark_light')
    cube(name+'_pump_return_line',fixed,[x+1.4,6.3,15.4],[x+1.9,9.3,15.9],'brass')
    cube(name+'_piston_rod',moving,[x-.7,7.3,16.1],[x+.7,14.3,17.3],'steel_light',{'south':'rod'})
    cube(name+'_piston_head',moving,[x-2.35,13.9,15.5],[x+2.35,15.1,18.6],'dark_light',{'up':'head','south':'head'})
    cube(name+'_piston_head_plate',moving,[x-1.8,15.1,15.9],[x+1.8,15.4,18.2],'steel')

# Editable native animation tracks; cosine gives an exact seamless pump cycle.
def keyframe(channel,time,values):
    return {'channel':channel,'time':time,'data_points':[dict(zip(('x','y','z'),map(str,values)))],'interpolation':'linear','color':-1,'uuid':uid(channel+str(time)+repr(values))}
def clip(name,length,spin,pump):
    anim={'uuid':uid(name),'name':name,'loop':'loop','length':length,'override':False,'animators':{}}
    if spin:
        kfs=[keyframe('rotation',round(i*.2,4),[i*90,0,0]) for i in range(round(length/.2)+1)]
        anim['animators'][groups['rotor']['uuid']]={'name':'rotor','type':'bone','keyframes':kfs}
    if pump:
        for side,sign in [('left',1),('right',-1)]:
            nameg='piston_'+side+'_moving'
            anim['animators'][groups[nameg]['uuid']]={'name':nameg,'type':'bone','keyframes':[keyframe('position',0,[0,f'{sign*1.5} * math.cos(query.anim_time * 300)',0])]}
    for bone_id,animator in anim['animators'].items():
        for i,kf in enumerate(animator['keyframes']):kf['uuid']=uid(name+bone_id+str(i))
    return anim
model['animations']=[clip('animation.engine.rotor_spin',.8,True,False),clip('animation.engine.pistons',1.2,False,True),clip('animation.engine.running',2.4,True,True)]
model['name']='engine';model['model_identifier']='engine';model['bedrock_animation_mode']='entity'
model['ai_used']=True;model['ai_agents']='Human-edited 2026-11-15 baseline + Codex refinement / native Blockbench animation'
buf=io.BytesIO();im.save(buf,format='PNG');png=buf.getvalue();model['textures'][0]['source']='data:image/png;base64,'+base64.b64encode(png).decode()
model['textures'][0]['name']='engine_tex.png';model['textures'][0]['relative_path']='engine_tex.png';model['textures'][0].pop('path',None)
(OUT/'engine.bbmodel').write_text(json.dumps(model,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
(OUT/'engine_tex.png').write_bytes(png)
after={e['uuid']:e for e in model['elements']};before={e['uuid']:e for e in baseline['elements']}
unchanged=[u for u,e in before.items() if u in after and e==after[u]]
changed=[before[u]['name'] for u in before if u in after and before[u]!=after[u]]
old_bytes=original_pixels.tobytes();new_bytes=im.tobytes()
for offset in range(0,len(old_bytes),4):
    if old_bytes[offset+3]:assert old_bytes[offset:offset+4]==new_bytes[offset:offset+4],'Overwrote original painted pixel'
report={'baseline_sha256':hashlib.sha256((REF/'user_baseline.bbmodel').read_bytes()).hexdigest(),'baseline_cubes':len(before),'final_cubes':len(after),'final_groups':len(model['groups']),'preserved_exact_cubes':len(unchanged),'preserved_uuids':unchanged,'modified_existing_cubes':changed,'removed_existing_cubes':[before[u]['name'] for u in before if u not in after],'added_cubes':[e['name'] for u,e in after.items() if u not in before],'original_opaque_texels_preserved':True,'new_uv_islands':new_islands,'animations':[{'name':a['name'],'length':a['length'],'loop':a['loop']} for a in model['animations']]}
(REF/'refinement_manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
print(json.dumps({k:v for k,v in report.items() if k not in ['preserved_uuids','added_cubes','new_uv_islands']},ensure_ascii=False))
