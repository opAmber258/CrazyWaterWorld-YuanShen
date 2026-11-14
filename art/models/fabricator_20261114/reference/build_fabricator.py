"""Original static Fabricator. Python 3 + Pillow, paths relative to this file."""
import base64, io, json, math, uuid, sys
from pathlib import Path
from PIL import Image, ImageDraw
ASSET=Path(__file__).resolve().parent.parent
# Historical AI draft only. Never overwrite the human-edited delivery.
OUT=ASSET/'reference'/'regenerated_initial_draft'
OUT.mkdir(exist_ok=True)
(OUT/'reference').mkdir(exist_ok=True)
NS=uuid.UUID('dccba0d1-014d-46d0-9d27-11eb563d19ff')
def uid(s):return str(uuid.uuid5(NS,s))
P={'shell':'#68766f','light':'#819087','edge':'#a2aba0','shade':'#52615c',
   'hood':'#414d49','hoodlight':'#59645c','dark':'#293735','seam':'#364540',
   'panel':'#7d7f71','panellight':'#96988a','steel':'#8b9b91','steelshade':'#64766f',
   'yellow':'#b1a052','yellowshade':'#82773f','paper':'#b5b4a0','ink':'#525b50',
   'copper':'#8f7659','red':'#986357','screen':'#354e48','signal':'#9bad83'}
G=['base_frame','lower_cabinet','work_trough','upper_hood','chamber','vent_bank','drive_assembly','cable_harness','side_controls','rear_service']
S=[]
def box(n,g,a,b,m='shell',p=None,r=None,o=None,mass=False):
    S.append(dict(name=n,g=g,a=a,b=b,m=m,p=p or {},r=r or [0,0,0],o=o or [(a[i]+b[i])/2 for i in range(3)],mass=mass))
# Main masses: asymmetrical narrow head over a wide freestanding bench.
for x in [-19,15]:
    box('mounting_foot_'+str(x),'base_frame',[x,0,-8],[x+4,2,8],'dark',mass=True)
box('base_plinth','base_frame',[-21.5,2,-8.5],[21.5,4,8.5],'hood',{'south':'beam','north':'beam'},mass=True)
box('cabinet_body','lower_cabinet',[-21,4,-8],[21,17.5,7.5],'shade',{'east':'service','west':'service','north':'rear'},mass=True)
box('lower_front_rail','base_frame',[-22,4,7.5],[22,5.2,9],'steelshade',{'south':'beam'},mass=True)
box('waist_rail','base_frame',[-22,17.3,-8.5],[22,18.6,9.5],'steelshade',{'south':'beam','up':'top'},mass=True)
for i,x in enumerate([-21,-17.5,14,19.5]):
    box('cabinet_post_'+str(i),'base_frame',[x,5.2,7.5],[x+1.3,17.3,9],'steelshade',{'south':'fasteners'})
for i,x in enumerate([-16,0]):
    box('cabinet_door_'+str(i),'lower_cabinet',[x,5.5,8],[x+15,16.8,8.9],'panel',{'south':'door_note' if i==0 else 'door'})
    box('cabinet_latch_'+str(i),'lower_cabinet',[x+11,14.2,8.9],[x+13.4,15.1,9.5],'steel',{'south':'latch'})
    for y in [6.5,14.8]:box('door_hinge_'+str(i)+'_'+str(y),'lower_cabinet',[x-.6,y,8.8],[x+.1,y+1.1,9.5],'hoodlight')
for x in [-20.6,17]:box('outer_inset_'+str(x),'lower_cabinet',[x,6,8],[x+2.6,16.4,8.3],'dark',{'south':'inset'})
# Long blue-grey working trough with chamfered ends; right auxiliary motor pod.
box('trough_lower_body','work_trough',[-20.5,18.6,-8],[14.5,20.6,8.5],'shade',mass=True)
box('trough_main_cover','work_trough',[-17,20.6,-7.5],[13,27,8.5],'shell',{'south':'trough','up':'top','north':'rear'},mass=True)
box('trough_top_strip','work_trough',[-16.5,27,-7],[12.5,27.8,7.7],'light',{'up':'top'},mass=True)
box('trough_left_end','work_trough',[-19.4,20.6,-7.5],[-17,27,8],'steelshade',{'south':'inset'},mass=True)
box('trough_front_upper_bevel','work_trough',[-16.8,26.4,7.3],[12.8,27.5,8.4],'light',r=[-25,0,0],mass=True)
box('trough_front_lower_bevel','work_trough',[-16.8,20.1,7.6],[12.8,21.2,8.5],'hoodlight',r=[25,0,0],mass=True)
box('trough_left_guard','work_trough',[-21,18.6,7.4],[-19.7,28,9.3],'steel',{'south':'fasteners'},mass=True)
box('trough_right_guard','work_trough',[13.7,18.6,7.4],[15,28,9.3],'steel',{'south':'fasteners'},mass=True)
box('right_motor_pod','side_controls',[15.2,19,-6],[21.4,27.2,7.6],'shell',{'south':'pod','east':'service','up':'top'},mass=True)
box('right_motor_cap','side_controls',[21.4,20,-5],[22,26,6.6],'hoodlight',{'east':'inset'})
# Upper head: open left machining bay and stacked vent panel on the right.
box('head_backbone','upper_hood',[-21,29,-7.5],[10,45,-4],'hood',{'north':'rear'},mass=True)
box('head_bottom_rail','upper_hood',[-21.5,33,-7.5],[11,34.5,7],'hoodlight',{'south':'beam'},mass=True)
box('head_top_lintel','upper_hood',[-21.5,45.5,-7.5],[11,47,7],'hoodlight',{'south':'beam'},mass=True)
for x in [-21.5,-5,10]:box('head_upright_'+str(x),'upper_hood',[x,34.5,-7.5],[x+1,45.5,7],'hoodlight',{'south':'fasteners','west':'service' if x==-21.5 else 'inset'},mass=True)
box('hood_roof','upper_hood',[-15,50.5,-6.5],[4.5,52.5,6.3],'hoodlight',{'south':'hood','up':'top'},mass=True)
box('hood_crown_body','upper_hood',[-18,47,-7],[7.5,49.6,6.8],'hood',{'south':'hood','up':'top'},mass=True)
box('hood_crown_mid','upper_hood',[-16.8,49,-6.8],[6.3,51,6.6],'hoodlight',{'south':'hood','up':'top'},mass=True)
box('hood_left_chamfer','upper_hood',[-21.85,48.4,-6.5],[-14.15,50.4,6.4],'hoodlight',r=[0,0,35],mass=True)
box('hood_right_chamfer','upper_hood',[3.65,48.4,-6.5],[11.35,50.4,6.4],'hoodlight',r=[0,0,-35],mass=True)
for k in range(5):
    x=-21+k*1.5; top=48.1792+math.tan(math.radians(35))*(x+1.5+18)+.1
    box('hood_left_infill_'+str(k),'upper_hood',[x,47,-6.4],[x+1.5,top,6.3],'hoodlight',mass=True)
    box('hood_right_infill_'+str(k),'upper_hood',[-10.5-x-1.5,47,-6.4],[-10.5-x,top,6.3],'hoodlight',mass=True)
box('hood_lifting_strip','upper_hood',[-12,49.8,6.9],[2,50.5,7.5],'steelshade',{'south':'latch'})
# Bay has real depth and exposed tooling; no black decal pretending to be a hole.
box('chamber_rear_plate','chamber',[-20.4,34.5,-4.1],[-5.1,45.4,-3.7],'dark',{'south':'chamber'})
box('chamber_lower_bed','chamber',[-20.2,34.5,-3.8],[-5.2,35.3,6.4],'steelshade',{'up':'bed'})
box('chamber_gantry','chamber',[-19.7,43.4,-2.8],[-5.9,44.3,.1],'steelshade',{'south':'beam'})
box('tool_carriage','chamber',[-15.5,41,-2.2],[-11.6,43.3,1.8],'hoodlight',{'south':'carriage'})
box('tool_spindle','chamber',[-14.4,38.9,-.9],[-12.9,41.2,.8],'steel')
box('tool_tip','chamber',[-14,37.8,-.6],[-13.3,39,.4],'copper')
for x in [-18,-8]:box('bed_guide_'+str(x),'chamber',[x,35.3,-3],[x+.7,35.8,5.8],'steel',{'up':'bed'})
box('machining_fixture','chamber',[-16,35.8,0],[-10.7,36.5,3.6],'hoodlight',{'up':'fixture'})
box('bay_warning_tab','chamber',[-8.3,34.8,6.6],[-5.5,36.6,7.1],'yellow',{'south':'warning'})
# Deliberate grouped louver geometry, visible from oblique and side.
box('vent_backing','vent_bank',[-3.8,34.6,4.4],[9.8,45.4,5.5],'dark',{'south':'ventback'})
for bank,(y,count,step) in enumerate([(40.3,5,.93),(35.3,4,1.0)]):
    for k in range(count):
        box('head_louver_'+str(bank)+'_'+str(k),'vent_bank',[-3.1,y+k*step,5.4],[9.1,y+k*step+.5,7.2],'steelshade' if bank else 'steel',{'south':'slat'},r=[-12,0,0])
    for x in [-2.2,3.2,8.3]:box('louver_stay_'+str(bank)+'_'+str(x),'vent_bank',[x,y-.2,7.1],[x+.5,y+count*step-.1,7.45],'hoodlight')
box('head_vent_divider','vent_bank',[-4,39.3,5.4],[10,40,7.3],'hood')
# Exposed mechanism and grouped cable loops in the narrow gap under the head.
box('drive_mount','drive_assembly',[-20,27.8,-3.6],[-10,32.9,1.4],'hood',{'south':'drive'})
for i,(x,y,s) in enumerate([(-17.5,30.6,2),(-13.5,29.7,1.4)]):
    for a in [0,45]:box('drive_wheel_'+str(i)+'_'+str(a),'drive_assembly',[x-s,y-s,1.3],[x+s,y+s,2.5],'steelshade',{'south':'wheel'},r=[0,0,a],o=[x,y,1.9])
    box('drive_hub_'+str(i),'drive_assembly',[x-.65,y-.65,2.5],[x+.65,y+.65,3.1],'copper')
for i,x in enumerate([-8,-3,3,8]):
    box('cable_drop_'+str(i),'cable_harness',[x,30.1,-2],[x+.6,33.1,-1.2],'dark')
    box('cable_elbow_'+str(i),'cable_harness',[x+.3,28.9,-2],[x+2.4,30.4,-1.2],'dark',r=[0,0,28])
    box('cable_return_'+str(i),'cable_harness',[x+2.2,29.4,-2],[x+2.8,32,-1.2],'dark')
box('cable_clamp','cable_harness',[-7.5,31.4,-1.1],[10.5,32,-.5],'steelshade')
# Compact control box at the upper right of the bench, mostly subdued.
box('control_neck','side_controls',[15.3,26,-4.5],[18.5,30,-.8],'shade',mass=True)
box('control_console','side_controls',[14.8,27.6,-3.8],[20.5,32,2.5],'hoodlight',{'south':'controls','east':'service','up':'top'},mass=True)
box('control_rain_lip','side_controls',[14.5,31.7,-4],[20.8,32.5,3],'steelshade')
# Rear: large service panels, single cable tray and plausible support to the head.
box('rear_left_riser','rear_service',[-20,18.5,-8.4],[-17.8,34,-6.7],'shade',{'north':'fasteners'},mass=True)
box('rear_right_riser','rear_service',[7.5,18.5,-8.4],[9.7,34,-6.7],'shade',{'north':'fasteners'},mass=True)
box('rear_lower_cover','rear_service',[-17.5,6,-8.5],[17.5,16,-8.1],'panel',{'north':'rear_access'})
box('rear_head_cover','rear_service',[-19.3,35,-8],[8.6,44.5,-7.6],'hoodlight',{'north':'rear_access'})
box('rear_cable_tray','rear_service',[11.2,5,-8.8],[13,27,-8],'hood',{'north':'cable'})
box('rear_power_socket','rear_service',[15,7,-9.2],[18.3,11,-8.3],'dark',{'north':'socket'})

atlas=Image.new('RGBA',(512,512),(0,0,0,0));cache={};islands=[];cursor=[2,2,0]
def tile(w,h,m,p):
    key=(w,h,m,p)
    if key in cache:return cache[key]
    x,y,row=cursor
    if x+w+2>512:x,y,row=2,y+row+2,0
    if y+h+2>512:raise ValueError('Atlas full')
    cursor[:]=[x+w+2,y,max(h,row)]
    d=ImageDraw.Draw(atlas);d.rectangle((x,y,x+w-1,y+h-1),fill=P[m])
    def rect(a,b,c,e,col):
        a,b,c,e=map(int,(a,b,c,e))
        if c>=a and e>=b:d.rectangle((x+max(0,a),y+max(0,b),x+min(w-1,c),y+min(h-1,e)),fill=P[col])
    if p:
        if p in ['door','door_note','service','rear','rear_access','top','pod','trough','hood']:
            if min(w,h)>6:
                rect(1,1,w-2,1,'shade');rect(1,h-2,w-2,h-2,'shade')
                for xx,yy in [(2,2),(w-3,2),(2,h-3),(w-3,h-3)]:rect(xx,yy,xx,yy,'edge')
                rect(4,h-2,8,h-2,'steelshade')
            if p in ['door','door_note']:
                for yy in range(h//2,h-4,3):
                    rect(w//4,yy,w//2-2,yy,'seam');rect(w//2+2,yy,3*w//4,yy,'seam')
                rect(w//2-4,3,w//2+4,5,'panellight');rect(w//2-3,4,w//2+2,4,'steelshade')
            if p=='door_note':
                rect(3,4,12,12,'paper');rect(5,6,5,9,'ink');rect(4,9,8,9,'ink');rect(7,7,8,10,'ink')
                rect(13,3,20,11,'paper');rect(13,3,20,4,'yellowshade')
                for yy in [6,8,10]:rect(14,yy,19-(yy%3),yy,'ink')
            if p in ['service','rear_access']:
                for yy in range(max(4,h-11),h-3,3):rect(w//2+2,yy,w-5,yy,'seam')
                rect(5,4,10,5,'steelshade')
            if p=='trough':
                rect(3,h-4,w-4,h-4,'shade');rect(7,h-3,14,h-3,'hoodlight')
                rect(w-14,h-10,w-3,h-4,'yellowshade');rect(w-13,h-9,w-4,h-5,'yellow')
                for xx in [w-12,w-8]:rect(xx,h-8,xx+1,h-7,'ink')
            if p=='hood':rect(w//3,h-4,2*w//3,h-4,'hoodlight')
            if p=='top' and min(w,h)>9:rect(w//2,3,w//2,h-4,'shade')
        elif p in ['beam','fasteners']:
            if h>2:rect(0,h-1,w-1,h-1,'shade')
            for xx in range(2,w-1,12):rect(xx,1,xx,1,'edge')
        elif p=='slat':rect(0,0,w-1,0,'light');rect(0,h-1,w-1,h-1,'steelshade')
        elif p in ['bed','fixture']:
            for xx in range(2,w-1,5):rect(xx,1,xx,h-2,'shade')
        elif p in ['chamber','drive']:
            if min(w,h)>8:rect(2,2,w-3,2,'hoodlight');rect(2,h-3,w-3,h-3,'hoodlight')
        elif p=='wheel':rect(w//2-1,0,w//2,h-1,'hoodlight');rect(0,h//2-1,w-1,h//2,'hoodlight')
        elif p=='warning':rect(0,0,w-1,0,'yellowshade');rect(w//2,1,w//2,h-2,'ink')
        elif p=='controls':
            rect(1,2,w-2,h-4,'dark');rect(2,3,w-3,5,'screen');rect(3,3,w-4,3,'signal')
            rect(2,h-3,3,h-2,'red');rect(w-4,h-3,w-3,h-2,'yellow')
        elif p=='cable':
            for yy in range(2,h-1,7):rect(0,yy,w-1,yy,'steelshade')
        elif p=='socket':rect(2,2,w-3,h-3,'steelshade');rect(3,3,w-4,h-4,'dark')
        elif p in ['carriage','latch','inset']:rect(1,1,w-2,1,'steelshade')
    uv=[x,y,x+w,y+h];cache[key]=uv;islands.append({'uv':uv,'material':m,'pattern':p});return uv
gray='--gray' in sys.argv
els=[];children={g:[] for g in G}
for s in S:
    if gray and not s['mass']:continue
    a,b,o=[[round(v-(.15 if i==2 else 0),4) for i,v in enumerate(q)] for q in [s['a'],s['b'],s['o']]]
    dx,dy,dz=[b[i]-a[i] for i in range(3)];faces={}
    for f,(w,h) in {'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}.items():
        faces[f]={'uv':tile(max(1,math.ceil(w*2)),max(1,math.ceil(h*2)),'shell' if gray else s['m'],None if gray else s['p'].get(f)),'texture':0}
    e={'name':s['name'],'type':'cube','uuid':uid(s['name']),'from':a,'to':b,'origin':o,'rotation':s['r'],'faces':faces,'box_uv':False,'autouv':0,'export':True,'color':G.index(s['g'])%8}
    els.append(e);children[s['g']].append(e['uuid'])
buf=io.BytesIO();atlas.save(buf,format='PNG');png=buf.getvalue();name='fabricator_gray' if gray else 'fabricator'
model={'meta':{'format_version':'5.0','model_format':'bedrock','box_uv':False},'name':name,'model_identifier':'fabricator','resolution':{'width':512,'height':512},'visible_box':[4,4,1.6],
 'elements':els,'groups':[{'name':'fabricator','uuid':uid('root'),'origin':[0,0,0],'rotation':[0,0,0],'export':True}]+[{'name':g,'uuid':uid(g),'origin':[0,0,0],'rotation':[0,0,0],'export':True} for g in G if children[g]],
 'outliner':[{'uuid':uid('root'),'isOpen':True,'children':[{'uuid':uid(g),'isOpen':True,'children':children[g]} for g in G if children[g]]}],
 'textures':[{'name':'fabricator_tex.png','relative_path':'fabricator_tex.png','id':'0','uuid':uid('texture'),'width':512,'height':512,'uv_width':512,'uv_height':512,'internal':True,'source':'data:image/png;base64,'+base64.b64encode(png).decode()}],
 'ai_used':True,'ai_agents':'Codex + Blockbench + deterministic Python geometry and pixel painting'}
((OUT/'reference' if gray else OUT)/(name+'.bbmodel')).write_text(json.dumps(model,ensure_ascii=False,indent=2),encoding='utf8')
if not gray:
 (OUT/'fabricator_tex.png').write_bytes(png)
 (OUT/'reference/palette.json').write_text(json.dumps(P,indent=2))
 (OUT/'reference/uv_layout.json').write_text(json.dumps({'pixels_per_bb_unit':2,'islands':islands,'used_height':cursor[1]+cursor[2]},indent=2))
print(json.dumps({'name':name,'cubes':len(els),'groups':len(model['groups']),'atlas_height':cursor[1]+cursor[2]}))
