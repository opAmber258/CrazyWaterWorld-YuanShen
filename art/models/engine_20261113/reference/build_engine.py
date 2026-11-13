"""Original Engine reconstruction. Run with Python + Pillow; outputs beside reference/."""
import base64, io, json, math, uuid, sys
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent
NS = uuid.UUID('58bef8a0-72da-499e-aefe-1962eec7e213')
def uid(s): return str(uuid.uuid5(NS, s))
P = {'shell':'#798073','shell_light':'#8b9181','shell_dark':'#606b61',
     'edge':'#a1a48d','seam':'#4d5852','dark':'#303c3b','dark_light':'#48534d',
     'steel':'#68766f','steel_light':'#93a398','vent':'#82958a','vent_edge':'#a1b0a0',
     'red':'#8e5046','red_light':'#aa6a54','rust':'#80634e','brass':'#a28a60',
     'screen':'#273d3e','screen_line':'#557a73','signal':'#a9bca1','amber':'#c4a465'}
groups = ['housing','vent_doors','instrument_bank','control_panel','nozzle','rotor','cable_harness','rear_service','mounting']
spec = []
def box(name,g,a,b,mat='shell',patterns=None,rot=None,origin=None,mass=False):
    spec.append(dict(name=name,g=g,a=a,b=b,mat=mat,patterns=patterns or {},rot=rot or [0,0,0],origin=origin or [(a[i]+b[i])/2 for i in range(3)],mass=mass))

# The broad housing and chamfered shoulders establish the original silhouette.
box('main_pressure_casing','housing',[-24,7,-12],[37,35,12],patterns={'south':'body','north':'rear_body','up':'top'},mass=True)
box('crown','housing',[-21,35,-10],[34,39,10],'shell_light',{'up':'top'},mass=True)
box('lower_casing','housing',[-23,4,-10],[37,7,10],'shell_dark',mass=True)
box('rounded_end','housing',[37,7,-10],[42,33,10],'shell',{'east':'end_panel','south':'end_panel'},mass=True)
box('end_shoulder_upper','housing',[34,35,-10],[37,38,10],'shell_light',mass=True)
box('end_shoulder_lower','housing',[37,33,-10],[40,35.5,10],'shell',mass=True)
for z,angle in [(10.9,-45),(-10.9,45)]:
    box('crown_bevel_'+str(z),'housing',[-21,33.3,z-1.8],[34,37.8,z+1.8],'shell_light',rot=[angle,0,0],mass=True)
box('left_bulkhead','housing',[-27,6,-11],[-24,36,11],'dark_light',mass=True)
box('front_apron','housing',[-23,4,11.6],[35,8,13],'shell_dark',{'south':'apron'},mass=True)

# Three shallow slatted access doors with physically separated blades.
for j,x in enumerate([-20.5,-9.2,2.1]):
    box(f'door_{j+1}_recess','vent_doors',[x,9,12],[x+10.3,30.2,12.6],'dark',{'south':'door_back'})
    for k,xx in enumerate([x,x+9.65]):
        box(f'door_{j+1}_stile_{k}','vent_doors',[xx,8.8,12.6],[xx+.65,30.8,13.6],'shell_light')
    for k,yy in enumerate([9,29.9]):
        box(f'door_{j+1}_rail_{k}','vent_doors',[x,yy,12.7],[x+10.3,yy+.7,13.65],'steel')
    for k in range(9):
        y=10.1+k*2.12
        box(f'door_{j+1}_louver_{k+1:02}','vent_doors',[x+.75,y,12.5],[x+9.5,y+1.42,14],'vent',{'south':'slat'},rot=[-12,0,0])
    box(f'door_{j+1}_latch','vent_doors',[x+8.8,16,13.8],[x+9.4,18.6,14.5],'brass')
    box(f'door_{j+1}_instrument_bezel','instrument_bank',[x,31,12],[x+10.3,37.2,13.4],'rust',{'south':'bezel'})
    box(f'door_{j+1}_instrument_face','instrument_bank',[x+.65,31.65,13.4],[x+9.65,36.5,13.65],'screen',{'south':f'instrument{j}'})

# Compact circuit/switch panel: geometry for the housing, pixels for circuitry.
box('control_box','control_panel',[15,22,12],[30,37.3,14],'dark_light',{'south':'control_border'})
box('circuit_board','control_panel',[15.65,22.7,14],[29.3,36.65,14.25],'dark',{'south':'circuit'})
box('control_rain_lip','control_panel',[14.6,37.2,11.8],[30.4,38.1,14.8],'shell_dark')
box('socket_bay','control_panel',[15.5,5,12],[30.3,18,12.65],'shell_dark',{'south':'socket_bay'})
for j,x in enumerate([17.5,26]):
    box('socket_'+str(j),'control_panel',[x,6,12.65],[x+2,10.8,14],'dark',{'south':'socket'})
    box('socket_hood_'+str(j),'control_panel',[x-1.5,14.6,12.6],[x+3.5,15.3,15],'dark')
    box('socket_neck_'+str(j),'control_panel',[x+.3,10.8,12.7],[x+1.7,13.8,13.7],'steel')
box('end_front_panel','housing',[31.5,7,12],[38,22,12.5],'shell',{'south':'end_panel'})

# Open octagonal nozzle along X. Three nested rings provide flare and depth.
def ring(label,x0,x1,r,thick,mat):
    # tangent bars; the union is an open octagon, never a solid painted disk.
    half=r*math.tan(math.pi/8)+.35
    for i in range(8):
        angle=i*45
        box(f'{label}_{i}','nozzle',[x0,21+r-thick/2,-half],[x1,21+r+thick/2,half],mat,
            {'east':'duct','west':'duct','south':'duct','north':'duct','up':'duct','down':'duct'},rot=[angle,0,0],origin=[(x0+x1)/2,21,0],mass=True)
ring('neck_ring',-31,-25,11.9,2.4,'dark_light')
ring('flared_shroud',-39,-30,14.25,2.7,'steel')
ring('mouth_rim',-44,-38.2,16.25,2.6,'shell_dark')
ring('mouth_lip',-44.6,-43.4,16.3,1.1,'steel_light')
box('shaft','rotor',[-42,19.2,-1.8],[-25,22.8,1.8],'steel')
box('rotor_hub','rotor',[-42.4,17.5,-3.5],[-39.8,24.5,3.5],'dark_light',{'west':'hub'})
for j in range(4):
    box(f'rotor_blade_{j}','rotor',[-41.1,23,-2.2],[-39.5,33.7,1.3],'steel',{'west':'blade'},rot=[j*90+22.5,0,0],origin=[-40.3,21,0])
# Broad forward facing shroud strip and inset vent echo the 2D reference.
box('shroud_side_panel','nozzle',[-41.5,10,13.5],[-31,31,15],'shell_dark',{'south':'shroud_service'})
box('shroud_grille','nozzle',[-37.5,14,15],[-32,26.2,15.4],'dark',{'south':'grille'})

# Ribbed top cables follow the roof; elbows have real connections.
box('roof_cable_tray','cable_harness',[-23,38,-4],[31,40.2,1],'dark_light',{'up':'tray'})
for i,z in enumerate([-2.8,-.8]):
    box('roof_cable_'+str(i),'cable_harness',[-18,40.2,z],[30,41.6,z+1.25],'dark',{'up':'cable','south':'cable'})
    box('left_cable_slope_'+str(i),'cable_harness',[-25,36.8,z],[-18,38.15,z+1.25],'dark',rot=[0,0,30])
    box('left_cable_drop_'+str(i),'cable_harness',[-26.3,30,z],[-25,35.8,z+1.25],'dark')
    box('right_cable_elbow_'+str(i),'cable_harness',[29,36.5,z],[35.5,37.85,z+1.25],'dark',rot=[0,0,-35])
for j,x in enumerate([-16,-1,15,28]):
    box('cable_clamp_'+str(j),'cable_harness',[x,39.9,-3.4],[x+1.3,42,1],'brass' if j==0 else 'steel')

# Rear maintenance uses quiet panels and a low conduit, avoiding a second front.
for j,x in enumerate([-20,5]):
    box('rear_access_'+str(j),'rear_service',[x,10,-12.8],[x+22,32,-12],'shell_dark',{'north':'rear_access'})
    box('rear_handle_'+str(j),'rear_service',[x+17,19,-13.4],[x+18,23,-12.8],'steel')
box('rear_connection_bank','rear_service',[29,12,-12.8],[36,29,-12],'dark_light',{'north':'rear_connect'})
box('rear_lower_conduit','rear_service',[-21,6.7,-13.5],[31,8,-12],'dark',{'north':'cable'})
for j,x in enumerate([-18,0,21]):
    box('rear_conduit_clip_'+str(j),'rear_service',[x,6.5,-14],[x+1.2,8.3,-12],'steel')
box('roof_service_hatch','rear_service',[-12,39,-9],[17,39.4,-5],'shell_dark',{'up':'roof_hatch'})

# Red longitudinal skid and four grounded mounting blocks.
box('red_front_skid','mounting',[-23,1.8,9.5],[38,4.4,13],'red',{'south':'red_beam'},mass=True)
box('red_back_skid','mounting',[-23,1.8,-13],[38,4.4,-9.5],'red',{'north':'red_beam'},mass=True)
for j,x in enumerate([-19,28]):
    box('crossmember_'+str(j),'mounting',[x,1.4,-11],[x+4,3.1,11],'dark_light',mass=True)
    for k,z in enumerate([-12,8]):
        box(f'foot_{j}_{k}','mounting',[x-1,0,z],[x+5,2.5,z+4],'dark_light',{'south':'foot','north':'foot'},mass=True)

# Shelf packed per-face pixels. Shared islands only when dimensions/material match.
atlas=Image.new('RGBA',(512,512),(0,0,0,0)); draw=ImageDraw.Draw(atlas)
cache={}; islands=[]; cursor=[2,2,0]
def tile(w,h,mat,pattern):
    key=(w,h,mat,pattern)
    if key in cache:return cache[key]
    x,y,row=cursor
    if x+w+2>512:x,y,row=2,y+row+2,0
    if y+h+2>512:raise RuntimeError('Atlas overflow')
    cursor[:]=[x+w+2,y,max(row,h)]
    d=ImageDraw.Draw(atlas);d.rectangle((x,y,x+w-1,y+h-1),fill=P[mat])
    def rect(a,b,c,e,col):
        a,b,c,e=int(a),int(b),int(c),int(e)
        if c>=a and e>=b:d.rectangle((x+max(0,a),y+max(0,b),x+min(w-1,c),y+min(h-1,e)),fill=P[col])
    def line(a,b,c,e,col):d.line((x+a,y+b,x+c,y+e),fill=P[col],width=1)
    if pattern:
        if pattern=='slat':
            rect(0,0,w-1,0,'vent_edge');rect(0,h-1,w-1,h-1,'steel');rect(2,h-2,4,h-2,'shell_dark')
        elif pattern.startswith('instrument'):
            rect(1,1,w-2,h-2,'screen');rect(2,2,w//2-1,h-3,'dark');rect(w//2+1,2,w-3,h-3,'dark')
            for xx in [3,w//2+2]:
                rect(xx,3,xx+4,3,'screen_line');rect(xx,5,xx+3,5,'steel')
            rect(3,h-2,5,h-2,'amber');rect(w-5,h-2,w-3,h-2,'screen_line')
        elif pattern=='circuit':
            rect(1,1,w-2,5,'steel');
            for xx in range(2,w-2,4):rect(xx,1,xx+1,2,'edge');rect(xx,4,xx+1,4,'dark')
            for xx in [3,8,13,18]:
                rect(xx,9,xx+2,h-5,'dark_light');rect(xx,10,xx+1,10,'steel');rect(xx,h-7,xx+2,h-7,'screen_line')
            for yy in range(8,h-2,4):rect(w-6,yy,w-3,yy+1,'steel');rect(w-5,yy,w-4,yy,'edge')
            for yy in [9,15,21]:rect(1,yy,2,yy+1,'brass')
            line(5,12,5,h-6,'screen_line');line(10,h-6,16,h-6,'steel')
        elif pattern in ('body','rear_body','end_panel','rear_access','roof_hatch','top','shroud_service'):
            # Large flat plates, low contrast seams, and sparse located wear.
            if w>15 and h>12:
                rect(1,1,w-2,1,'shell_dark');rect(1,h-2,w-2,h-2,'shell_dark')
                for xx,yy in [(3,3),(w-4,3),(3,h-4),(w-4,h-4)]:rect(xx,yy,xx+1,yy,'edge')
                if pattern=='body':
                    rect(w-18,1,w-18,h-2,'shell_dark');rect(1,h-7,w-2,h-7,'seam')
                if pattern=='rear_body':rect(w//2,2,w//2,h-3,'shell_dark')
                if pattern in ('rear_access','end_panel'):
                    for yy in range(h-10,h-3,3):rect(4,yy,w-5,yy,'seam')
                    rect(5,5,min(13,w-5),6,'edge')
                if pattern=='top':rect(w//2,2,w//2,h-3,'shell_dark')
                rect(4,h-3,9,h-3,'rust');rect(w-12,2,w-8,2,'shell_light')
        elif pattern in ('bezel','control_border','apron','socket_bay','door_back'):
            rect(0,0,w-1,0,'steel');rect(0,h-1,w-1,h-1,'seam')
            if pattern=='bezel':rect(1,h-2,w-2,h-2,'brass')
            if pattern=='socket_bay':rect(2,h-3,w-3,h-3,'seam')
        elif pattern=='grille':
            for yy in range(2,h-1,3):rect(1,yy,w-2,yy,'steel');rect(1,yy+1,w-2,yy+1,'dark_light')
        elif pattern in ('duct','blade','hub'):
            if min(w,h)>3:rect(1,1,w-2,1,'steel');rect(1,h-2,w-2,h-2,'dark_light')
            if pattern=='duct' and w>10 and h>6:rect(w//2,2,w//2,h-3,'seam');rect(3,h-2,5,h-2,'rust')
        elif pattern in ('cable','tray'):
            for xx in range(2,w-1,7):rect(xx,0,xx,h-1,'dark_light')
        elif pattern=='red_beam':
            rect(0,0,w-1,0,'red_light');rect(1,h-1,w-2,h-1,'rust')
            for xx in [8,29,64,99]:
                if xx<w-4:rect(xx,h-2,xx+4,h-2,'shell_dark')
        elif pattern=='rear_connect':
            for yy in range(4,h-3,7):rect(3,yy,w-4,yy+2,'steel');rect(4,yy+1,w-5,yy+1,'dark')
        elif pattern=='socket':rect(1,1,w-2,h-2,'dark');rect(1,h-2,w-2,h-2,'steel')
        elif pattern=='foot':rect(2,1,w-3,1,'steel')
    uv=[x,y,x+w,y+h];cache[key]=uv;islands.append(dict(uv=uv,material=mat,pattern=pattern));return uv

gray='--gray' in sys.argv
elements=[]; children={g:[] for g in groups}
for s in spec:
    if gray and not s['mass']:continue
    a,b=s['a'][:],s['b'][:]; a[0]+=1.3; b[0]+=1.3
    pivot=s['origin'][:];pivot[0]+=1.3
    # Match native project precision before export; avoid independently rounded
    # irrational ring endpoints changing derived sizes on project reopen.
    a,b,pivot=([round(v,4) for v in values] for values in (a,b,pivot))
    dx,dy,dz=[b[i]-a[i] for i in range(3)]
    faces={}
    for face,(fw,fh) in {'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}.items():
        mat='shell' if gray else s['mat']; pat=None if gray else s['patterns'].get(face)
        uv=tile(max(1,math.ceil(fw*2)),max(1,math.ceil(fh*2)),mat,pat)
        faces[face]={'uv':uv,'texture':0}
    e={'name':s['name'],'type':'cube','uuid':uid(s['name']),'from':a,'to':b,'origin':pivot,'rotation':s['rot'],'faces':faces,'box_uv':False,'autouv':0,'color':groups.index(s['g'])%8,'export':True}
    elements.append(e);children[s['g']].append(e['uuid'])
buf=io.BytesIO();atlas.save(buf,format='PNG');tex=buf.getvalue()
name='engine_gray' if gray else 'engine'
model={'meta':{'format_version':'5.0','model_format':'bedrock','box_uv':False},'name':name,'model_identifier':'engine','resolution':{'width':512,'height':512},'visible_box':[7,4,1.5],'elements':elements,
       'groups':[{'name':'engine','uuid':uid('root'),'origin':[0,0,0],'rotation':[0,0,0],'export':True}]+[{'name':g,'uuid':uid(g),'origin':[0,0,0],'rotation':[0,0,0],'export':True} for g in groups if children[g]],
       'outliner':[{'uuid':uid('root'),'isOpen':True,'children':[{'uuid':uid(g),'isOpen':True,'children':children[g]} for g in groups if children[g]]}],
       'textures':[{'name':'engine_tex.png','relative_path':'engine_tex.png','id':'0','uuid':uid('texture'),'width':512,'height':512,'uv_width':512,'uv_height':512,'internal':True,'source':'data:image/png;base64,'+base64.b64encode(tex).decode()}],
       'ai_used':True,'ai_agents':'Codex / Blockbench / deterministic Python geometry and pixels'}
(OUT/(name+'.bbmodel')).write_text(json.dumps(model,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
if not gray:
    (OUT/'engine_tex.png').write_bytes(tex)
    (OUT/'reference'/'palette.json').write_text(json.dumps(P,indent=2),encoding='utf-8',newline='\n')
    (OUT/'reference'/'uv_layout.json').write_text(json.dumps({'pixels_per_bb_unit':2,'islands':islands,'atlas_used_height':cursor[1]+cursor[2]},indent=2),encoding='utf-8',newline='\n')
print(json.dumps({'file':name+'.bbmodel','cubes':len(elements),'groups':len(model['groups']),'atlas_height':cursor[1]+cursor[2]}))
