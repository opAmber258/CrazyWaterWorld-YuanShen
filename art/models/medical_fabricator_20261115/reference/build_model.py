"""Rebuild the editable model and its embedded atlas with Pillow."""
import base64
import json
import math
import uuid
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[1]
ID = 'medical_fabricator'
SIZE = 512
parts = []
group = None


def uid(label):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, ID + '/' + label))


def box(name, low, high, material, **extra):
    parts.append(dict(name=name, group=group, low=low, high=high, mat=material, **extra))


def bar(name, start, end, thickness, depth, material):
    # Segments lie in a vertical plane and pivot about their centre.
    x, y, z = [(a+b)/2 for a, b in zip(start, end)]
    dx, dy = end[0]-start[0], end[1]-start[1]
    length = math.hypot(dx, dy) + thickness * 0.26
    box(name, [x-thickness/2,y-length/2,z-depth/2], [x+thickness/2,y+length/2,z+depth/2],
        material, rotation=[0,0,-math.degrees(math.atan2(dx,dy))], pivot=[x,y,z])


group = 'wall_mount'
for x in [-17,12]:
    box(f'wall_strap_{x}', [x,11,-8.5], [x+2,35,-7], 'strap')
    arm_x = x if x < 0 else 20
    box(f'shelf_bracket_{x}', [arm_x+.25,11,-7.25], [arm_x+1.75,12.5,4], 'steel')
    box(f'bracket_web_{x}', [arm_x+.5,9,-7.5], [arm_x+1.5,11.25,-5.75], 'strap')
box('shelf_rear_tie',[12,11,-7.75],[21.75,12.5,-6.75],'steel')
box('rear_service_bridge',[-20,32,-8],[16,35,-6.5],'steel')

group = 'upper_cabinet'
box('upper_shell',[-22,24,-7.5],[17,36.5,3],'enamel')
box('folded_crown',[-21.5,36.5,-7.25],[16.5,37,2.75],'pale')
box('lower_fold',[-21.5,23.5,-7.25],[16.5,24,2.75],'rim')
box('lower_valance',[-21,23,-7],[16,23.5,2.5],'steel')
box('crown_service_lid',[-10.5,36.6,-5.5],[15.5,37.15,2.25],'lid')
box('outer_service_cheek',[17,25,-6.5],[17.5,35.5,2],'cheek')

group = 'chamber_doors'
for label,x0,x1 in [('process',-20.5,.5),('medical',3.5,15.5)]:
    box(label+'_seal',[x0-.25,25.25,2.9],[x1+.25,34.75,3.45],'seal')
    box(label+'_steel_sash',[x0,25.5,3.35],[x1,34.5,3.85],'rim')
    box(label+'_glazing',[x0+.5,26,3.8],[x1-.5,34,4],label)
    for y in [26.5,31.5]:
        box(f'{label}_hinge_{y}',[x0-.5,y,3.5],[x0+.25,y+2,4.2],'hinge')
box('chamber_release',[-1,27,3.95],[0,29,4.45],'steel')

group = 'dosing_head'
box('feed_casting',[-19.5,19,-3],[-11.5,23.2,2.5],'cast')
box('plunger_mantle',[-18.5,17.5,-1.25],[-15,19,1.75],'metal')
box('plunger_tip',[-18,16,-.75],[-15.5,17.5,1.25],'steel')
box('guide_rail',[-10.75,15,-3.25],[-9.25,23,0],'metal')
box('dosing_carriage',[-11.25,16,-3.75],[-8.75,19.5,.5],'cast')
box('small_pipette_body',[-5.75,17,-2.75],[-4.25,23.1,-1.25],'steel')
box('small_pipette_collar',[-6,16.5,-3],[-4,17.5,-1],'brass')
box('small_pipette_tip',[-5.5,15.5,-2.5],[-4.5,16.5,-1.5],'metal')
box('slide_stage',[-20,15,-2.5],[-12,15.5,2.75],'cast')
box('sample_clamp',[-19.5,15.5,-1.75],[-13,16,.25],'brass')
for i,(x,y) in enumerate([(-5,21.4),(0,20.8),(4.6,21.3),(9.3,20.7)]):
    for j,(a,b) in enumerate([((x,23.3),(x+.9,y+.4)),((x+.9,y+.4),(x+2.2,y)),((x+2.2,y),(x+3.6,23.3))]):
        bar(f'loom_{i}_{j}',(*a,-4+i%2*.45),(*b,-4+i%2*.45),.48,.5,'cord')

group = 'work_surface'
box('worktop_apron',[-22,11.3,6.5],[22,15,8.5],'apron')
box('back_splash',[-22,11.5,-8],[22,15,-6.5],'rim')
box('preparation_deck',[-22,11.5,-6.5],[.5,15,6.5],'deck')
box('sink_right_rim',[20,11.5,-6.5],[22,15,6.5],'deck')
box('tray_underside',[-21.5,10.75,-7.5],[.5,11.5,8],'steel')
box('outer_drip_edge',[-21.5,10.85,7.2],[21.5,11.35,8.25],'rim')

group = 'wash_basin'
# The bowl is open from the top. Offset each smaller tier inwards.
for i,(x0,x1,z0,z1,lo,hi) in enumerate([
    (0,20.5,-7,7,10.5,14.5),(.75,19.75,-6.25,6.25,9,10.52),
    (1.5,19,-5.5,5.5,7.75,9.02)]):
    box(f'bowl_left_{i}',[x0,lo,z0],[x0+1,hi,z1],'porcelain')
    box(f'bowl_right_{i}',[x1-1,lo,z0],[x1,hi,z1],'porcelain')
    box(f'bowl_near_{i}',[x0+1,lo,z1-1],[x1-1,hi,z1],'porcelain')
    box(f'bowl_far_{i}',[x0+1,lo,z0],[x1-1,hi,z0+1],'porcelain')
box('bowl_floor',[2.25,7,-4.75],[18.25,7.77,4.75],'bowl_floor')

group = 'waste_plumbing'
box('bowl_tailpiece',[9.5,5.7,-.75],[12,7,.75],'metal')
box('counter_service_union',[-9,9.9,-1.25],[-6.5,11,1.25],'brass')
box('line_strain_relief',[-8.5,8.5,-.75],[-7,10,.75],'metal')
def pipe_chain(name,points,width,mat):
    for i,(a,b) in enumerate(zip(points,points[1:])):
        bar(f'{name}_{i}',(*a,0),(*b,0),width,width,mat)
pipe_chain('supply_return',[(-7.75,8.75),(-7.75,6),(-6.7,5.3),(1.8,3.9),(4,3.1)],1,'hose')
pipe_chain('basin_elbow',[(10.75,6),(14.5,5.25),(15.2,4.4),(13.9,3.9),(6,3.6),(5,3)],1.15,'hose')
pipe_chain('bottle_trap',[(5,3.35),(5,.95),(4.45,.53),(3,.53),(2.3,1.35),(2.3,2.6)],.95,'metal')
box('trap_tee',[3,2.55,-1],[5.75,4,1],'steel')
box('cleanout_nut',[1.5,2,-.75],[3,3.2,.75],'brass')

# Each face gets a separately painted patch at 2 pixels per Blockbench unit.
PALETTE = {
    'enamel':('#a7b4ad','#7c908b','#c6cec1'), 'pale':('#c3c9bb','#a2afa6','#d8dac9'),
    'lid':('#adbbb1','#82968e','#cad0be'), 'cheek':('#97aaa4','#728680','#becabf'),
    'seal':('#33413f','#263231','#4a5653'), 'rim':('#899990','#5c706c','#c0c9b7'),
    'steel':('#596962','#40524f','#81918a'), 'strap':('#626f68','#495a55','#8a9990'),
    'hinge':('#777c68','#545e54','#a7a589'), 'cast':('#485852','#344540','#708075'),
    'metal':('#96a8a1','#667c77','#bcc6b7'), 'brass':('#a99462','#78674c','#c2b283'),
    'cord':('#737e7c','#4c5c5a','#8c9990'), 'apron':('#b7b8a7','#959c90','#d3d3be'),
    'deck':('#acb7ac','#83968e','#c9cfc0'), 'porcelain':('#b6b9a8','#969e91','#c9ccba'),
    'bowl_floor':('#a7b1a5','#788b85','#c5cab8'), 'hose':('#526361','#374946','#82908a'),
    'process':('#293c40','#202e32','#54696b'), 'medical':('#394e58','#293d48','#6b8187')}
faces = ['north','south','east','west','up','down']


def paint(part, face, w, h):
    mat, name = part['mat'], part['name']
    base, shadow, light = PALETTE[mat]
    tile = Image.new('RGBA',(w,h),base)
    d = ImageDraw.Draw(tile)
    def line(points, color):
        d.line(points, fill=color)
    def rect(coords, color):
        d.rectangle(coords, fill=color)
    def bolt(x,y):
        d.point((x,y),shadow)
        if h>6: d.point((x,y+1),light)
    if w>=6 and h>=4:
        line([(0,h-1),(w-1,h-1)],shadow)
        if mat not in ('cord','seal','cast'):
            line([(1,0),(max(1,w*2//3),0)],light)
    if mat in ('process','medical') and face=='south':
        # Smoked glass: overhead reflection, chamber shapes, lower retaining rail.
        rect([0,0,w-1,2], '#74868a' if mat=='medical' else '#687877')
        line([(2,3),(w//2,3)],'#536a6c')
        rect([0,h-5,w-1,h-2], '#293b45' if mat=='medical' else '#243235')
        line([(2,h-2),(w-3,h-2)],'#455859')
        if mat=='process':
            d.polygon([(0,2),(w//2+3,2),(w//2,4),(4,4),(1,5),(0,5)],fill='#586a69')
            line([(w-15,2),(w-6,2)],'#86918a')
            line([(w-16,7),(w-16,11),(w-14,12)],'#35474a')
            # Recessed carousel seen through the glass on the right.
            for xx,yy in [(w-13,5),(w-8,4)]:
                rect([xx,yy,xx+3,yy+6],'#35474a')
                line([(xx+1,yy+1),(xx+2,yy+1)],'#475a59')
            # Crooked batch card clipped to the glass.
            d.polygon([(2,3),(13,5),(12,11),(1,9)],fill='#ad9d69')
            d.polygon([(3,4),(12,6),(11,9),(2,8)],fill='#647570')
            line([(4,5),(9,6)],'#d4c290')
            for xx in [4,6,8,10]:
                d.point((xx,7+(xx>7)),'#364b49')
                d.point((xx,6+(xx>7)),'#8da49a')
            rect([2,2,5,3],'#dbc27c')
        else:
            # Winged staff and two alternating snakes, 19 x 15 native pixel motif.
            glyph=[
                '.......xxx.......',
                'xxxx....x....xxxx',
                '.xxxxxx.x.xxxxxx.',
                '..xxxx.xxx.xxxx..',
                '....xx..x..xx....',
                '.....xx.x.xx.....',
                '.....x..x..x.....',
                '......xx.xx......',
                '.......xxx.......',
                '......xx.xx......',
                '......x.x.x......',
                '.......xxx.......',
                '.......xxx.......',
                '........x........',
            ]
            for yy,row in enumerate(glyph):
                for xx,ch in enumerate(row):
                    if ch=='x':
                        d.point(((w-len(row))//2+xx,(h-len(glyph))//2+yy),
                                '#e0e2ce' if yy<5 else '#c5d4c9')
    if mat=='enamel' and face=='south':
        for x in (2,w-4):
            bolt(x,2);bolt(x,h-3)
        line([(w//2-9,1),(w-6,1)],shadow)
        rect([3,1,9,2],'#d8c174')
        for x in [4,6,8]: d.point((x,2),'#716446')
    if mat in ('enamel','cheek') and face in ('east','west','north') and w>9 and h>10:
        if face=='north':
            d.rectangle((3,3,w-4,h-4),outline=shadow)
            line([(w//2,4),(w//2,h-5)],shadow)
            for x in (5,w-7,w//2-2): bolt(x,5);bolt(x,h-6)
        for row in range(4):
            line([(w//5,5+row*2),(w//5+min(w//2,14),5+row*2)],shadow)
        if face!='north':
            line([(3,h-5),(w-4,h-5)],shadow)
            rect([3,h-4,6,h-4],light)
    if mat=='apron' and face=='south':
        # Printed hazard label on warm unpainted front skirt.
        rect([2,1,14,5],'#d7cfac')
        rect([3,1,13,1],'#9e5647')
        d.polygon([(3,5),(5,2),(7,5)],fill='#716343')
        d.point((5,4),'#ebcf76')
        line([(8,3),(12,3)],'#736e5e')
        line([(8,4),(10,4)],'#827c69')
        for x in [w//2+7,w-4]: bolt(x,2)
        line([(w-20,h-2),(w-12,h-2)],shadow)
    if mat=='lid' and face=='up' and w>20:
        for x in [2,w-3]:
            for y in [2,h-3]: bolt(x,y)
        for x in [w-14,w-11,w-8]:
            line([(x,4),(x,7)],shadow)
    if mat=='porcelain' and face in ('west','east') and w>10 and h>=5:
        line([(w-6,h-2),(w-3,h-2)],'#a2ac9d')
    if mat=='deck' and face=='up' and w>14:
        # Steel instrument rest and recessed drainage flutes.
        for x in range(w-13,w-3,3):
            line([(x,4),(x, min(h-4,17))],shadow)
            line([(x+1,5),(x+1,min(h-4,16))],light)
        for x,y in [(3,3),(w-3,h-3)]: bolt(x,y)
    if mat=='bowl_floor' and face=='up':
        x,y=w//2,h//2
        d.rounded_rectangle((x-4,y-3,x+4,y+3),radius=2,fill=shadow,outline=light)
        for dx in [-2,0,2]: line([(x+dx,y-1),(x+dx,y+1)],'#495b57')
    if mat=='hose' and h>=5:
        for y in range(2,h-1,3): line([(0,y),(w-1,y)],light)
    if mat in ('metal','brass','cast','strap','hinge') and w>=3 and h>=4:
        line([(1,1),(1,h-2)],light if mat!='cast' else shadow)
        if mat=='cast' and w>=10:
            for x in range(3,w-2,3): line([(x,h//2),(x,h-3)],shadow)
    return tile


atlas = Image.new('RGBA',(SIZE,SIZE))
elements = []
patches = []
for part in parts:
    low,high=part['low'],part['high']
    dx,dy,dz=[math.floor((b-a)*2+0.5+1e-9) for a,b in zip(low,high)]
    elem=dict(name=part['name'],uuid=uid(part['name']),type='cube',box_uv=False,
              autouv=0,from_=low,to=high,origin=part.get('pivot',[0,0,0]),faces={})
    elem['from']=elem.pop('from_')
    if 'rotation' in part: elem['rotation']=part['rotation']
    elements.append(elem)
    for face,(w,h) in zip(faces,[(dx,dy),(dx,dy),(dz,dy),(dz,dy),(dx,dz),(dx,dz)]):
        w,h=max(1,w),max(1,h)
        patches.append((part,elem,face,paint(part,face,w,h)))

x=y=row=0
for part,elem,face,tile in sorted(patches,key=lambda item:-item[-1].height):
    w,h=tile.size
    if x+w+2>SIZE: x,y,row=0,y+row,0
    if y+h+2>SIZE: raise RuntimeError('Atlas overflow')
    u,v=x+1,y+1
    # Extend outer colors by one texel, including corners.
    for dest,src in [((x,v),(0,0,1,h)),((u+w,v),(w-1,0,w,h)),
                     ((u,y),(0,0,w,1)),((u,v+h),(0,h-1,w,h))]:
        atlas.paste(tile.crop(src),dest)
    for ax,tx in [(x,0),(u+w,w-1)]:
        for ay,ty in [(y,0),(v+h,h-1)]:
            atlas.putpixel((ax,ay),tile.getpixel((tx,ty)))
    atlas.paste(tile,(u,v))
    elem['faces'][face]=dict(uv=[u,v,u+w,v+h],texture=0)
    x+=w+2
    row=max(row,h+2)
texture_name=ID+'_tex.png'
atlas.save(OUT/texture_name)
groups=[]
tree=[]
for name in dict.fromkeys(p['group'] for p in parts):
    key=uid('bone/'+name)
    groups.append(dict(name=name,uuid=key,origin=[0,0,0],rotation=[0,0,0]))
    tree.append(dict(uuid=key,isOpen=False,children=[uid(p['name']) for p in parts if p['group']==name]))
texture=dict(name=texture_name,id='0',uuid=uid('texture'),width=SIZE,height=SIZE,
             uv_width=SIZE,uv_height=SIZE,internal=True,source='data:image/png;base64,'+
             base64.b64encode((OUT/texture_name).read_bytes()).decode())
model=dict(meta=dict(format_version='5.0',model_format='bedrock',box_uv=False),name=ID,
           model_identifier=ID,visible_box=[4,3,1.2],resolution=dict(width=SIZE,height=SIZE),
           elements=elements,groups=groups,outliner=tree,textures=[texture])
(OUT/(ID+'.bbmodel')).write_text(json.dumps(model,separators=(',',':')),encoding='utf8')
print(f'{len(parts)} cubes, {len(groups)} groups, {len(patches)} faces, occupied rows {y+row}/{SIZE}')

