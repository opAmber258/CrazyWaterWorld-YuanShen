"""Apply the six approved changes to a protected copy of the live baseline."""
import json,math
from pathlib import Path
from bb import ROOT,URL,run
from bb_client import Client,structured

SHIFT=[0,-.125,-1.11415]
groups={};materials={};updates={}
def vec(a):return [round(x+y,6) for x,y in zip(a,SHIFT)]
def cube(g,n,a,b,mat='shell',rot=None,pivot=None):
    e={'name':n,'from':vec(a),'to':vec(b),'origin':vec(pivot or [0,0,0])}
    if rot:e['rotation']=rot
    groups.setdefault(g,[]).append(e);materials[n]=mat
def rounded(g,n,x0,y0,x1,y1,z0,z1,r,mat):
    # Four diagonal corner prisms, one central panel and one transverse panel.
    # Sub-texel depth offsets keep overlapping cuboid faces from competing.
    cube(g,n+'_core',[x0+r,y0,z0],[x1-r,y1,z1],mat)
    cube(g,n+'_wings',[x0,y0+r,z0+.025],[x1,y1-r,z1-.025],mat+'_rim')
    q=r/math.sqrt(2)
    for side,x in [('L',x0+r),('R',x1-r)]:
        for end,y in [('B',y0+r),('T',y1-r)]:
            cube(g,n+'_corner_'+side+end,[x-q,y-q,z0+.05],[x+q,y+q,z1-.05],mat+'_rim',[0,0,45],[x,y,(z0+z1)/2])
def wire(g,n,pts,thick=.5,mat='rubber'):
    for i,(a,b) in enumerate(zip(pts,pts[1:])):
        dx,dy,dz=[b[j]-a[j] for j in range(3)];length=math.sqrt(dx*dx+dy*dy+dz*dz);c=[(a[j]+b[j])/2 for j in range(3)]
        rx=math.degrees(math.atan2(dz,math.sqrt(dx*dx+dy*dy)));rz=-math.degrees(math.atan2(dx,dy))
        cube(g,f'{n}_{i+1}',[c[0]-thick/2,c[1]-length/2-.035,c[2]-thick/2],[c[0]+thick/2,c[1]+length/2+.035,c[2]+thick/2],mat,[rx,0,rz],c)
def update(n,a,b):updates[n]={'from':vec(a),'to':vec(b)}

REMOVE={'top_header','bottom_sill','main_screen_gasket','sonar_glass','aux_screen_frame','aux_screen'}
rounded('housing','rounded_header',-20,29,20,32,-5.5,1,.75,'shell')
rounded('housing','rounded_sill',-19.5,11.5,19.5,13,-5,1,.5,'shell')
update('left_bezel',[-19.5,13,-.5],[-17.75,29,1])
update('screen_divider',[7.25,13,-.5],[8.25,29,1])
update('screen_top_lip',[-17.75,28.5,-.5],[7.25,29,.9])
update('screen_bottom_lip',[-17.75,13,-.5],[7.25,13.5,.9])
rounded('display','rounded_screen_gasket',-17.75,13.5,7.25,28.5,0,.6,.75,'gasket')
rounded('display','sonar_glass',-17.25,14,6.75,28,.6,.85,.5,'sonar')
rounded('instruments','aux_frame',8.5,22,18,29,-.1,.7,.65,'gasket')
rounded('instruments','aux_glass',9,22.5,17.5,28.5,.7,.95,.45,'aux')
next(e for e in groups['instruments'] if e['name']=='aux_frame_wings')['from'][2]-=.002

# Preserve the original keyboard group pivot and 12-degree rake.
cube('keyboard','white_control_deck',[-15,4,-.5],[15,6.3,8.5],'deck')
cube('keyboard','white_front_fascia',[-15,4.1,8.5],[15,6.1,8.8],'deck')
rounded('keyboard','left_black_pod',-19,3.8,-14.5,6.8,-.5,8.5,.6,'blackpod')
rounded('keyboard','right_black_pod',14.5,3.8,19,6.8,-.5,8.5,.6,'blackpod')
for side,x0,x1 in [('left',-13.5,-3.5),('right',3.5,13.5)]:
    cube('keyboard',side+'_keywell',[x0-.25,6.3,.4],[x1+.25,6.55,6.8],'gasket')
    cube('keyboard',side+'_keys',[x0,6.55,.65],[x1,6.95,6.55],'keys_'+side)
cube('keyboard','center_control_module',[-2.5,6.3,.55],[2.5,7,6.7],'center_panel')
cube('keyboard','center_readout',[-1.75,7,1.1],[1.75,7.25,3],'center_readout')
cube('keyboard','center_switch_L',[-1.8,7.1,4.1],[-.3,7.65,5.5],'switch')
cube('keyboard','center_switch_R',[.4,7.1,4.1],[1.9,7.65,5.5],'switch_red')
cube('keyboard','left_spacebar',[-12.5,6.95,5.75],[-5,7.2,6.4],'keycap')
cube('keyboard','tray_badge',[-3,4.8,8.8],[4.5,5.8,9],'badge')

# Hanging headphones replace every part of the misread rotary dial.
cube('headphones','headphone_hook',[15.1,17.7,.55],[15.8,18.45,2.15],'metal')
wire('headphones','headband',[[13.2,14.5,2.1],[13.15,16.8,1.7],[14.2,18.15,1.6],[16.15,18.45,1.6],[17.55,17.15,1.85],[17.8,14.8,2.2]],.45,'headband')
for side,x,y,angle in [('L',13.4,13.6,-12),('R',17.7,13.9,12)]:
    cube('headphones','earcup_'+side,[x-1.05,y-1.4,1.5],[x+1.05,y+1.4,3.2],'earcup',[0,0,angle],[x,y,2.35])
    cube('headphones','earpad_'+side,[x-.8,y-1.1,3.2],[x+.8,y+1.1,3.65],'earpad',[0,0,angle],[x,y,2.35])
wire('headphones','headphone_cord',[[18.3,12.5,2.3],[18.6,10,2],[17.7,8.5,1.4],[15.9,8.7,1],[15.3,10.5,.5],[15.3,11.8,.2]],.3,'headphone_wire')

# Non-uniform service wiring: separate loops, crossing depth and clear endpoints.
wire('cables','loose_signal',[[3,11.3,.5],[2,9,1.4],[-1.5,8.2,2],[ -5,8.7,1.4],[-7,10,.7],[-7.5,11.4,.2]],.45,'wire_faded')
wire('cables','slack_tray_lead',[[-13,4,4],[-14,2.5,4.7],[-12.5,.9,5.2],[-8,.7,5.3],[-5.8,1.7,4.5],[-5.4,4.2,3.5]],.55,'rubber')
wire('cables','crossed_power_lead',[[-2,4,2],[-3.3,2.1,3.6],[-.5,1.5,4.5],[3,2.5,5.2],[6.5,3.2,4.6],[8,4.5,3.5]],.6,'wire_faded')
cube('cables','signal_grommet',[2.3,10.9,-.1],[3.7,12,.9],'metal')
cube('cables','left_strain_relief',[-14,3.3,3.3],[-12,4.8,4.6],'metal')
cube('cables','cable_tie',[-1.3,1.3,4.1],[-.1,2.1,4.9],'clamp')

if __name__=='__main__':
    source=ROOT/'reference/baseline_live/project.bbmodel'
    candidate=ROOT/'reference/revision_base.bbmodel';candidate.write_bytes(source.read_bytes())
    pid=run('(async()=>{await loadModelFile({path:PATH,content:require("fs").readFileSync(PATH,"utf8")});await new Promise(r=>setTimeout(r,100));return Project.uuid})()'.replace('PATH',json.dumps(str(candidate))))
    (ROOT/'reference/project_id.txt').write_text(pid)
    baseline=json.loads(source.read_text());oldmats={}
    for e in baseline['elements']:
        n=e['name'];mat='shell'
        if any(s in n for s in ['rail','tray_arm','upright','bracket','plug']):mat='metal'
        if any(s in n for s in ['lead','loop','trough']):mat='rubber'
        if n=='rear_service_cover':mat='rear'
        if n in ['connection_box','control_backplate']:mat='panel'
        if 'meter_' in n:mat='meter'
        if 'speaker' in n:mat='vents'
        oldmats[n]=mat
    materials={**oldmats,**materials}
    (ROOT/'reference/materials.json').write_text(json.dumps(materials,indent=2))
    spec={'remove_names':sorted(REMOVE),'remove_groups_contents':['dial','keyboard'],'updates':updates,'new_elements':groups,'pixel_density':32,'budget_cubes':150,'screen_visible_area_change':336/292.5-1}
    (ROOT/'reference/revision_spec.json').write_text(json.dumps(spec,indent=2))
    with Client(URL,timeout=90) as c:
        code='''(()=>{if(Project.uuid!==PID)throw Error("Project changed");const names=NAMES;const groupsToClear=Group.all.filter(g=>["dial","keyboard"].includes(g.name));const es=Cube.all.filter(e=>names.includes(e.name)||groupsToClear.includes(e.parent));Undo.initEdit({elements:es,outliner:true});for(const e of es)e.remove();const g=Group.all.find(g=>g.name==="dial");g.name="headphones";Undo.finishEdit("Replace approved structures");return true})()'''.replace('PID',json.dumps(pid)).replace('NAMES',json.dumps(sorted(REMOVE)))
        c.tool('risky_eval',{'code':code})
        for name,args in updates.items():
            e=next(e for e in baseline['elements'] if e['name']==name)
            c.tool('modify_cube',{'id':e['uuid'],**args})
        for g,es in groups.items():c.tool('place_cube',{'group':g,'elements':es,'faces':False})
        code='''(()=>{if(Project.uuid!==PID)throw Error("Project changed");if(Cube.all.length>150)throw Error("Cube budget exceeded");Undo.initEdit({elements:Cube.all,uv_mode:true});Project.box_uv=false;for(const e of Cube.all){e.box_uv=false;for(const f of Object.values(e.faces))f.texture=false;}Undo.finishEdit("Configure revised face UV");Canvas.updateAll();return {cubes:Cube.all.length,groups:Group.all.length}})()'''.replace('PID',json.dumps(pid))
        r=structured(c.tool('risky_eval',{'code':code}))
        model=structured(c.tool('risky_eval',{'code':'(()=>Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false}))()'}))
        (ROOT/'reference/geometry_after_repairs.bbmodel').write_text(json.dumps(model),encoding='utf8')
        print('revision geometry',r,'next: allocate_uv.py, paint_texture.py, save_native.py')
