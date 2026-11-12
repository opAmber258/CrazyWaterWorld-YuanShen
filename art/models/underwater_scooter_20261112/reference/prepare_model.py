"""Create the scooter's original pixel atlas and compact, reproducible cube plan.

Run: python <asset directory>/reference/prepare_model.py
No reference model geometry or textures are copied. Pillow is required.
"""
import base64
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
REF = ROOT / 'reference'
PALETTE = {
    'ochre': '#bd8528', 'yellow': '#d59b33', 'yellow_light': '#e8b650',
    'yellow_shadow': '#9f6c24', 'yellow_dark': '#785426',
    'steel': '#859493', 'steel_light': '#b4c0b6', 'steel_shadow': '#607475',
    'steel_dark': '#43585b', 'graphite': '#303e46', 'graphite_light': '#45545a',
    'rubber': '#26323a', 'rubber_light': '#3b4850', 'recess': '#1b282f',
    'ivory': '#d9ddbd', 'lens': '#bdd9ac', 'lens_light': '#e3edbe',
    'lens_shadow': '#81a99a', 'red': '#b45b41', 'red_light': '#d78959',
    'green': '#8fac78', 'green_dark': '#527a69', 'label': '#e2cf99',
}
def col(name):
    return PALETTE.get(name, name)

ATLAS_SIZE=64
atlas = Image.new('RGBA', (ATLAS_SIZE, ATLAS_SIZE), (0, 0, 0, 0))
tiles = {}
tile_images={}
def tile(name, w, h, material, detail=None):
    im=Image.new('RGBA',(w,h),col(material));d=ImageDraw.Draw(im)
    if detail: detail(d,w,h)
    tile_images[name]=im
    return name

def panel(d,w,h):
    d.line([(1,h-2),(w-2,h-2)],fill=col('yellow_shadow'))
    d.line([(1,1),(w-3,1)],fill=col('yellow_light'))
    d.line([(1,2),(1,h-3)],fill=col('ochre'))
    for x,y in [(2,3),(w-3,3),(2,h-4),(w-3,h-4)]:
        d.point((x,y),fill=col('yellow_dark'));d.point((x,y-1),fill=col('label'))
    d.rectangle([5,h-5,13,h-3],fill=col('yellow_shadow'))
    d.line([(6,h-4),(11,h-4)],fill=col('label'))
    d.line([(w-6,5),(w-3,5)],fill=col('ochre'))
    d.line([(w-7,h-2),(w-5,h-2)],fill=col('steel_shadow'))
    d.point((w-7,h-3),fill=col('yellow_light'))

tile('yellow_panel',24,16,'yellow',panel)
tile('yellow_top',24,12,'yellow',lambda d,w,h:(d.line((1,1,w-3,1),fill=col('yellow_light')),d.line((2,h-2,w-2,h-2),fill=col('ochre')),d.rectangle((w-5,3,w-3,4),fill=col('yellow_shadow'))))
tile('yellow_bevel',24,4,'ochre',lambda d,w,h:d.line((1,0,w-2,0),fill=col('yellow_light')))
tile('yellow_end',8,8,'ochre')
tile('steel',12,8,'steel',lambda d,w,h:(d.line((0,0,w-1,0),fill=col('steel_light')),d.line((0,h-1,w-1,h-1),fill=col('steel_shadow'))))
tile('steel_flat',4,4,'steel')
tile('steel_light',4,4,'steel_light')
tile('darksteel',12,8,'steel_dark',lambda d,w,h:d.line((0,0,w-1,0),fill=col('steel_shadow')))
tile('graphite',12,8,'graphite',lambda d,w,h:(d.line((1,0,w-2,0),fill=col('graphite_light')),d.line((0,h-1,w-1,h-1),fill=col('rubber'))))
tile('graphite_flat',4,4,'graphite')
tile('rubber',16,6,'rubber',lambda d,w,h:[d.line((x,0,x,h-1),fill=col('rubber_light')) for x in range(1,w-1,3)])
tile('rubber_flat',4,4,'rubber')
tile('recess',4,4,'recess')
tile('red',4,4,'red',lambda d,w,h:d.line((0,0,3,0),fill=col('red_light')))
tile('brass',4,4,'yellow_shadow',lambda d,w,h:d.line((0,0,3,0),fill=col('yellow_light')))

def vent(d,w,h):
    d.line((0,0,w-1,0),fill=col('steel_light'))
    d.line((0,h-1,w-1,h-1),fill=col('steel_shadow'))
    for y in range(3,h-2,4):
        for x in range(2,w-2,4):
            d.rectangle((x,y,x+1,y+1),fill=col('recess'))
            d.line((x,y+2,x+1,y+2),fill=col('steel_shadow'))
tile('vent',16,12,'steel',vent)

def lens(d,w,h):
    d.rectangle((0,0,w-1,h-1),fill=col('steel_dark'))
    d.rectangle((1,1,w-2,h-2),fill=col('steel_light'))
    d.rectangle((2,2,w-3,h-3),fill=col('lens_shadow'))
    d.rectangle((3,3,w-4,h-4),fill=col('lens'))
    d.rectangle((3,3,w-4,5),fill=col('lens_light'))
    d.rectangle((4,5,5,h-5),fill=col('lens_light'))
    d.rectangle((w-5,h-6,w-4,h-4),fill=col('green'))
    d.line((3,h-3,w-4,h-3),fill=col('green_dark'))
tile('lamp',12,12,'lens',lens)

def service(d,w,h):
    d.rectangle((1,1,w-2,h-2),fill=col('steel_shadow'))
    d.rectangle((2,2,w-3,h-3),fill=col('graphite_light'))
    d.line((3,3,w-4,3),fill=col('steel'))
    d.rectangle((4,4,w-5,h-5),fill=col('graphite'))
    d.line((4,h-5,w-5,h-5),fill=col('steel_dark'))
    d.line((w//2-1,5,w//2+1,5),fill=col('steel_shadow'))
tile('service',12,12,'steel',service)

def controls(d,w,h):
    d.rectangle((1,1,w-2,h-2),fill=col('recess'))
    for x in range(3,w-3,3):d.rectangle((x,3,x+1,5),fill=col('green'))
    d.line((3,h-3,w-4,h-3),fill=col('steel_shadow'))
tile('controls',14,8,'graphite_light',controls)

def battery(d,w,h):
    d.line((1,0,w-2,0),fill=col('yellow_light'))
    d.line((0,h-1,w-1,h-1),fill=col('yellow_dark'))
    d.rectangle((3,2,w-4,h-3),outline=col('yellow_shadow'))
    d.rectangle((5,4,12,7),fill=col('graphite'))
    d.line((6,5,11,5),fill=col('label'))
    for x in (2,w-3):d.point((x,h-3),fill=col('steel_light'))
tile('battery',20,12,'yellow',battery)
tile('rotor',8,12,'steel_shadow',lambda d,w,h:(d.line((0,0,0,h-1),fill=col('steel_light')),d.rectangle((w-2,2,w-1,h-3),fill=col('steel_dark'))))
tile('cable',12,4,'yellow_dark',lambda d,w,h:[d.line((x,0,x,3),fill=col('graphite')) for x in range(2,w,3)])

def pack_tiles():
    orders=[lambda kv:(-kv[1].width*kv[1].height,-kv[1].height,kv[0]),
            lambda kv:(-max(kv[1].size),-min(kv[1].size),kv[0]),
            lambda kv:(-kv[1].height,-kv[1].width,kv[0])]
    for order in orders:
        for heuristic in ('short','area'):
            free=[(0,0,ATLAS_SIZE,ATLAS_SIZE)];placed={}
            for name,im in sorted(tile_images.items(),key=order):
                w,h=im.width+2,im.height+2
                fits=[r for r in free if r[2]>=w and r[3]>=h]
                if not fits:break
                score=(lambda r:(min(r[2]-w,r[3]-h),r[2]*r[3]-w*h,r[1],r[0])) if heuristic=='short' else (lambda r:(r[2]*r[3]-w*h,min(r[2]-w,r[3]-h),r[1],r[0]))
                x,y,_,_=min(fits,key=score);placed[name]=(x+1,y+1,x+1+im.width,y+1+im.height)
                remaining=[]
                for rx,ry,rw,rh in free:
                    if x>=rx+rw or x+w<=rx or y>=ry+rh or y+h<=ry:remaining.append((rx,ry,rw,rh));continue
                    if x>rx:remaining.append((rx,ry,x-rx,rh))
                    if x+w<rx+rw:remaining.append((x+w,ry,rx+rw-x-w,rh))
                    if y>ry:remaining.append((rx,ry,rw,y-ry))
                    if y+h<ry+rh:remaining.append((rx,y+h,rw,ry+rh-y-h))
                free=[]
                for i,r in enumerate(remaining):
                    if any(i!=j and r[0]>=o[0] and r[1]>=o[1] and r[0]+r[2]<=o[0]+o[2] and r[1]+r[3]<=o[1]+o[3] and (r!=o or i>j) for j,o in enumerate(remaining)):continue
                    free.append(r)
            if len(placed)==len(tile_images):return placed
    raise ValueError('64px atlas overflow')
for name,uv in pack_tiles().items():
    atlas.paste(tile_images[name],uv[:2]);tiles[name]=list(uv)
atlas.save(ROOT/'underwater_scooter_tex.png')
(REF/'palette.json').write_text(json.dumps(PALETTE,ensure_ascii=False,indent=2),encoding='utf-8')
(REF/'uv_tiles.json').write_text(json.dumps(tiles,indent=2),encoding='utf-8')

cubes=[]
def box(name, group, a, b, mat='steel', rot=None, pivot=None, faces=None, disabled=()):
    item={'name':name,'group':group,'from':list(a),'to':list(b),'rotation':rot or [0,0,0],
          'origin':list(pivot or [(a[i]+b[i])/2 for i in range(3)]),'material':mat,
          'face_materials':faces or {},'disabled_faces':list(disabled)}
    cubes.append(item);return item

def octagonal(name,group,x0,x1,cy,cz,r,c,mat='yellow_panel',axis='x',plain='yellow_end'):
    def cv(v): return v if axis=='x' else [v[1],v[2],v[0]]
    def add(s,a,b,rot=None,p=None,face_mats=None):
        rt=rot or [0,0,0]
        if axis!='x': rt=[rt[1],rt[2],rt[0]]
        fm=face_mats or {}
        if axis!='x':
            remap={'east':'south','west':'north','up':'east','down':'west','south':'up','north':'down'}
            fm={remap[k]:v for k,v in fm.items()}
        material='yellow_bevel' if '_bevel_' in s and mat=='yellow_panel' else mat
        if mat=='yellow_panel' and '_bevel_' not in s:fm.update({'up':'yellow_top','down':'yellow_top'})
        return box(name+s,group,cv(a),cv(b),material,rt,cv(p) if p else None,fm)
    fm={'east':plain,'west':plain}
    add('_vertical',[x0,cy-r,cz-r+c],[x1,cy+r,cz+r-c],face_mats=fm)
    add('_horizontal',[x0,cy-r+c,cz-r],[x1,cy+r-c,cz+r],face_mats=fm)
    thick=c*math.sqrt(2)
    for sy in (-1,1):
        for sz in (-1,1):
            ctr=[(x0+x1)/2,cy+sy*(r-c),cz+sz*(r-c)]
            angle=-45*sy*sz
            add(f'_bevel_{sy}_{sz}',[x0,ctr[1]-c/math.sqrt(2),ctr[2]-thick/2],
                [x1,ctr[1]+c/math.sqrt(2),ctr[2]+thick/2],[angle,0,0],ctr,fm)

CY=6.2
octagonal('yellow_shell','housing',-0.8,7.3,CY,0,3.6,1.0)
octagonal('rear_collar','housing',-1.18,-0.66,CY,0,3.7,1.05,'steel','x','steel_flat')
octagonal('front_band','nose',6.96,7.48,CY,0,3.7,1.05,'steel','x','steel_flat')
octagonal('head_cowl','nose',7.48,9.55,CY,0,3.22,0.94,'graphite','x','graphite_flat')
box('spotlight_lens','nose',[9.56,CY-1.75,-1.75],[9.82,CY+1.75,1.75],'steel',faces={'east':'lamp'})
box('battery_hatch','housing',[0.1,9.82,-1.65],[5.9,10.08,1.65],'yellow_top',faces={'up':'battery'})
box('battery_latch','housing',[5.34,10.09,-0.55],[6.18,10.35,0.55],'darksteel',faces={'up':'steel'})

octagonal('motor_casing','motor',-5.4,-1.1,CY,0,2.55,0.72,'darksteel','x','graphite_flat')
octagonal('service_cover','motor',2.46,2.89,-2.7,CY,1.68,0.5,'steel','z','steel_flat')
box('service_recess','motor',[-3.88,CY-1.18,2.90],[-1.52,CY+1.18,3.10],'graphite',faces={'south':'service'})
box('left_motor_cover','motor',[-4.0,CY-1.25,-2.85],[-1.6,CY+1.25,-2.56],'graphite',faces={'north':'controls'})
box('drive_shaft','motor',[-7.9,CY-0.60,-0.60],[-5.2,CY+0.60,0.60],'graphite')

def ring(name,group,x0,x1,r,thickness):
    length=2*(r+thickness/2)*math.tan(math.pi/8)
    for i in range(8):
        a=i*math.pi/4;y=CY+r*math.cos(a);z=r*math.sin(a)
        box(f'{name}_{i+1:02}',group,[x0,y-thickness/2,z-length/2],[x1,y+thickness/2,z+length/2],
            'steel',[i*45,0,0],[(x0+x1)/2,y,z])
ring('propeller_guard','guard',-8.7,-7.28,3.68,0.48)
for y,z,angle in [(CY+2.1,0,0),(CY-2.1,0,180),(CY,2.1,90),(CY,-2.1,270)]:
    box(f'guard_stay_{angle}','guard',[-7.45,y-1.4,z-0.17],[-7.14,y+1.4,z+0.17],'darksteel',[angle,0,0],[-7.30,y,z])
for i in range(4):
    a=i*90+28
    box(f'propeller_blade_{i+1}','rotor',[-8.12,CY+0.48,-0.70],[-7.70,CY+3.14,0.70],
        'rotor',[a,0,0],[-7.91,CY,0])
box('propeller_hub','rotor',[-8.38,CY-0.8,-0.8],[-7.42,CY+0.8,0.8],'steel',faces={'west':'service'})

for sign,label in [(1,'right'),(-1,'left')]:
    g='handle_'+label;z=sign*3.45
    box(label+'_front_socket',g,[-1.76,7.45,z-0.54],[-0.92,8.72,z+0.54],'brass')
    box(label+'_upper_arm',g,[-5.72,8.20,z-0.40],[-1.33,8.90,z+0.40],'steel', [0,0,-34],[-1.5,8.5,z])
    box(label+'_rubber_grip',g,[-7.43,10.2,z-0.60],[-4.65,11.28,z+0.60],'rubber',[0,0,-34],[-6.05,10.74,z])
    box(label+'_grip_heel',g,[-7.71,10.05,z-0.68],[-7.15,11.23,z+0.68],'steel',[0,0,-34],[-6.05,10.74,z])
    for suffix,p1,p2,material in [
        ('lower_return',(-7.5,5.2),(-7.05,2.2),'steel'),
        ('lower_rail',(-7.05,2.2),(-3.55,1.75),'steel'),
        ('lower_mount',(-3.55,1.75),(-2.55,4.05),'brass')]:
        cx=(p1[0]+p2[0])/2;cy=(p1[1]+p2[1])/2
        length=math.hypot(p2[0]-p1[0],p2[1]-p1[1])+.25
        angle=math.degrees(math.atan2(p2[1]-p1[1],p2[0]-p1[0]))
        box(label+'_'+suffix,g,[cx-length/2,cy-.32,z-.34],[cx+length/2,cy+.32,z+.34],material,[0,0,angle],[cx,cy,z])
    box(label+'_perforated_plate',g,[-7.83,5.1,z-0.50],[-5.15,7.4,z+0.50],'steel',faces={('south' if sign==1 else 'north'):'vent'})

box('control_console','controls',[-2.42,8.62,-1.24],[-0.82,9.20,1.24],'graphite',faces={'up':'controls'})
box('safety_trigger','controls',[-3.32,9.33,2.91],[-2.58,9.67,3.98],'red',[0,0,-34],[-2.95,9.5,3.45])

points=[(-5.8,3.8),(-5.3,2.3),(-4.0,1.4),(-2.5,1.75),(-1.5,3.35)]
for i in range(len(points)-1):
    x1,y1=points[i];x2,y2=points[i+1];cx=(x1+x2)/2;cy=(y1+y2)/2
    length=math.hypot(x2-x1,y2-y1)
    angle=math.degrees(math.atan2(y2-y1,x2-x1))
    box(f'power_cable_{i+1}','wiring',[cx-length/2,cy-.21,-2.37],[cx+length/2,cy+.21,-1.95],'cable',[0,0,angle],[cx,cy,-2.16])
for i,(x,y) in enumerate([points[0],points[-1]]):
    box(f'cable_ferrule_{i+1}','wiring',[x-.34,y-.34,-2.52],[x+.34,y+.34,-1.80],'brass')

SCALE=0.8
for c in cubes:
    for key in ('from','to','origin'):c[key]=[round(v*SCALE,5) for v in c[key]]

def rotated_corners(c):
    ax,ay,az=[math.radians(v) for v in c['rotation']]
    for ix in (0,1):
        for iy in (0,1):
            for iz in (0,1):
                x,y,z=[c['to' if j else 'from'][k]-c['origin'][k] for k,j in enumerate((ix,iy,iz))]
                y,z=y*math.cos(ax)-z*math.sin(ax),y*math.sin(ax)+z*math.cos(ax)
                x,z=x*math.cos(ay)+z*math.sin(ay),-x*math.sin(ay)+z*math.cos(ay)
                x,y=x*math.cos(az)-y*math.sin(az),x*math.sin(az)+y*math.cos(az)
                yield [x+c['origin'][0],y+c['origin'][1],z+c['origin'][2]]

bottom=min(v[1] for c in cubes for v in rotated_corners(c))
for c in cubes:
    for key in ('from','to','origin'):c[key][1]=round(c[key][1]-bottom,5)
corners=[v for c in cubes for v in rotated_corners(c)]
bmin=[min(v[i] for v in corners) for i in range(3)]
bmax=[max(v[i] for v in corners) for i in range(3)]
groups=list(dict.fromkeys(c['group'] for c in cubes))
plan={'identifier':'underwater_scooter','name':'水下助推器 UnderwaterScooter','scale':SCALE,
      'origin_convention':'+X nose/spotlight, -X propeller/operator, +Y up',
      'bounding_box':{'min':bmin,'max':bmax,'size':[bmax[i]-bmin[i] for i in range(3)]},
      'groups':groups,'cubes':cubes,'tiles':tiles}
(REF/'model_plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8')
(REF/'cost_plan.json').write_text(json.dumps({'budgets':{'components':100,'declared_faces':600,'texture_pixels':4096,'textures':1,'inverted_components':0,'partial_alpha_textures':0},'lanes':[]},indent=2),encoding='utf-8')
print(json.dumps({'cubes':len(cubes),'groups_with_root':len(groups)+1,'texture':[ATLAS_SIZE,ATLAS_SIZE],'bbox':plan['bounding_box']},ensure_ascii=False))
