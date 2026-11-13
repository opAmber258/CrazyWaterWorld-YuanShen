"""Build an original low-poly Advanced Syringe Gun. Python 3 + Pillow.
Paths resolve relative to this file; no game installation is required to rebuild.
Writes reference/authored_input.bbmodel, the PNG, palette and design manifest.
The final project and geometry are saved by Blockbench's native codecs.
"""
from pathlib import Path
import json, math, uuid, base64
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
REF = ROOT / 'reference'
IDENT = 'advanced_syringe_gun'
if (REF / 'publish_input.bbmodel').exists():
    accepted = json.loads((REF / 'publish_input.bbmodel').read_text(encoding='utf8'))
    (REF / 'authored_input.bbmodel').write_text(json.dumps(accepted,ensure_ascii=False,indent=2),encoding='utf8')
    (ROOT / (IDENT+'_tex.png')).write_bytes(base64.b64decode(accepted['textures'][0]['source'].split(',',1)[1]))
    print(json.dumps({'preserved_saved_publish_input':True,'cubes':len(accepted['elements'])}))
    raise SystemExit(0)
SCALE = .8
PALETTE = {
 'teal': ['#658b80', '#45675f', '#84a69a', '#a6bfb0'],
 'dark': ['#303d3b', '#24302f', '#4b5b54', '#69796f'],
 'steel': ['#a3ada6', '#77877f', '#c5cdc1', '#e0e1cd'],
 'rubber': ['#2e3534', '#242b2b', '#46514b', '#606e62'],
 'amber': ['#a18a4a', '#766234', '#bfaa68', '#d7c38a'],
 'socket': ['#354b44', '#273a35', '#577668', '#789486'],
 'scanner': ['#29423d', '#20322f', '#79baa5', '#b8dcc5'],
}
SPEC = []
def box(group, name, a, b, mat='teal', detail='plain', rotation=None, pivot=None):
    SPEC.append(dict(group=group,name=name,a=a,b=b,mat=mat,detail=detail,
                     rotation=rotation or [0,0,0],pivot=pivot or [0,0,0]))
def bar(group,name,p,q,width,depth,mat='dark',detail='plain',z=0):
    dx,dy=q[0]-p[0],q[1]-p[1]
    box(group,name,[p[0]-width/2,p[1],z-depth/2],
        [p[0]+width/2,p[1]+math.hypot(dx,dy),z+depth/2],mat,detail,
        [0,0,-math.degrees(math.atan2(dx,dy))],[*p,z])

# Rear receiver and medical scanner: two complete covers, not a flat silhouette.
box('receiver','breech_core',[-6.8,5.1,-1.12],[-1.25,7.7,1.12],'dark')
box('receiver','pressure_block',[-7.75,5.3,-1.0],[-6.45,8.15,1.0],'steel','bands')
box('receiver','pressure_end',[-8.2,5.65,-.78],[-7.75,7.85,.78],'dark')
box('receiver','scanner_top',[-6.45,7.25,-1.25],[-1.45,7.7,1.25],'teal','rail')
box('receiver','receiver_underside',[-6.1,4.85,-.83],[-1.25,5.3,.83],'steel','slots')
for sign,label in [(-1,'left'),(1,'right')]:
    z0,z1=sorted([sign*1.12,sign*1.36])
    box('scanner',label+'_scanner_cover',[-6.35,5.45,z0],[-2.0,7.22,z1],'teal','controls')
    z0,z1=sorted([sign*1.36,sign*1.45])
    box('scanner',label+'_health_display',[-6.0,7.29,z0],[-3.5,7.65,z1],'scanner','pulse')
    # Light-colored mechanical knobs have real thickness; marks live in the UV.
    for n,x in enumerate([-5.32,-3.75]):
        z0,z1=sorted([sign*1.37,sign*1.61])
        box('scanner',label+'_dial_'+str(n),[x,6.05,z0],[x+.52,6.57,z1],'steel','bolt')

# Rectangular high-capacity cassette: amber cartridges, metal perimeter and rails.
box('cassette_frame','upper_spine',[-1.8,8.24,-1.05],[7.35,8.65,1.05],'teal','rail')
box('cassette_frame','lower_spine',[-1.65,4.86,-1.09],[7.55,5.28,1.09],'teal','rail')
box('cassette_frame','rear_column',[-1.8,5.25,-1.08],[-1.2,8.24,1.08],'teal','rail')
box('cassette_frame','front_column',[6.85,5.25,-1.08],[7.55,8.24,1.08],'teal','rail')
box('cassette_frame','cassette_backbone',[-1.22,5.3,-.2],[6.88,8.23,.2],'socket')
for sign,label in [(-1,'left'),(1,'right')]:
    z0,z1=sorted([sign*.91,sign*1.12])
    for y,edge in [(5.27,'low'),(8.02,'high')]:
        box('cassette_frame',label+'_'+edge+'_bead',[-1.22,y,z0],[6.86,y+.18,z1],'steel')
    z0,z1=sorted([sign*.92,sign*1.16])
    box('cassette_frame',label+'_front_latch',[6.89,6.05,z0],[7.59,7.25,z1],'dark','latch')
for row in range(3):
    for col in range(4):
        x=-1.1+col*1.9; y=5.5+row*.82
        box('amber_magazine',f'cartridge_{row}_{col}',[x,y,-.95],[x+1.78,y+.72,.95],'amber','cartridge')
box('cassette_frame','upper_sight_line',[-1.35,8.66,-.35],[5.75,8.82,.35],'dark')
box('cassette_frame','lower_slide',[.9,4.56,-.6],[6.3,4.86,.6],'dark','slots')

# Skeletal shoulder stock, angled pistol grip and genuine empty trigger area.
bar('stock','stock_rear_leg',[-9.15,-.2],[-8.1,4.7],.82,1.35,'rubber','grip')
bar('stock','stock_top_strut',[-8.1,4.7],[-3.15,4.55],.55,1.03,'dark')
bar('stock','stock_lower_strut',[-9.05,.48],[-2.5,.65],.6,1.04,'dark','rail')
bar('stock','shoulder_heel',[-9.5,-.42],[-9.13,.5],.55,1.55,'steel')
box('stock','stock_coupling',[-7.95,4.6,-.88],[-6.6,5.45,.88],'dark')
bar('grip_trigger','main_grip',[-2.55,.35],[.05,4.95],1.45,1.72,'rubber','grip')
bar('grip_trigger','grip_heel',[-2.8,.22],[-2.44,.85],1.7,1.91,'steel','bolt')
for sign,label in [(-1,'left'),(1,'right')]:
    bar('grip_trigger',label+'_grip_inlay',[-2.13,1.2],[-.3,4.41],.62,.12,'dark','grip',sign*.91)
bar('grip_trigger','guard_bottom',[-6.13,3.6],[-3.17,3.6],.26,.52,'steel')
bar('grip_trigger','guard_rear',[-6.13,3.6],[-6.6,4.86],.28,.52,'dark')
bar('grip_trigger','guard_front',[-3.17,3.6],[-2.7,4.85],.28,.52,'dark')
bar('grip_trigger','trigger',[-4.48,3.95],[-4.25,4.85],.26,.36,'steel')
bar('grip_trigger','forward_lever_upper',[1.0,3.1],[.65,4.85],.28,.48,'steel')
bar('grip_trigger','forward_lever_middle',[2.22,1.1],[1.0,3.1],.28,.48,'steel')
bar('grip_trigger','forward_lever_tip',[3.0,.28],[2.22,1.1],.28,.48,'steel')

# The original rear loop becomes a five-segment low-poly pressure hose.
for n,(p,q) in enumerate(zip([[-8.1,6.0],[-9.55,6.75],[-9.2,8.25],[-7.65,9.0],[-5.9,8.6]],
                              [[-9.55,6.75],[-9.2,8.25],[-7.65,9.0],[-5.9,8.6],[-4.65,7.7]])):
    bar('rear_hose',f'hose_segment_{n}',p,q,.48,.55,'rubber')
box('rear_hose','hose_connector',[-5.02,7.4,-.48],[-4.3,7.95,.48],'steel','bands')

# Vertical foregrip and short barrel with a real square bore.
box('front_assembly','barrel_bridge',[7.54,7.2,-.57],[9.35,7.83,.57],'steel','bands')
box('front_assembly','foregrip_body',[7.85,2.55,-.77],[8.95,7.25,.77],'dark','ribs')
box('front_assembly','foregrip_heel',[7.7,2.4,-.89],[9.1,2.9,.89],'teal')
box('front_assembly','foregrip_top',[7.65,7.77,-.84],[9.18,8.26,.84],'dark')
box('front_assembly','barrel_root',[9.1,7.3,-.47],[9.65,7.79,.47],'dark')
box('front_assembly','muzzle_upper',[9.65,7.7,-.44],[10.7,7.88,.44],'steel')
box('front_assembly','muzzle_lower',[9.65,7.19,-.44],[10.7,7.37,.44],'steel')
box('front_assembly','muzzle_left',[9.65,7.37,-.44],[10.7,7.7,-.25],'steel')
box('front_assembly','muzzle_right',[9.65,7.37,.25],[10.7,7.7,.44],'steel')

assert len(SPEC)<=90, 'Review budget before adding more components'

# Shared per-face islands; surface marks do not create geometry.
AXES={'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}
islands={}; face_keys={}; density=2
for i,s in enumerate(SPEC):
    dims=[b-a for a,b in zip(s['a'],s['b'])]
    for f,ax in AXES.items():
        w,h=[max(1,round(dims[a]*density)) for a in ax]
        detail=s['detail'] if f in ['north','south'] else ('ribs' if s['detail']=='ribs' else 'plain')
        if detail in ['cartridge','pulse','bolt']:
            w=max(2,w);h=max(2,h)
        if min(w,h)<2: detail='plain'
        k=(s['mat'],w,h,detail)
        islands.setdefault(k,None); face_keys[i,f]=k
for atlas in [64,128]:
    x=y=1; row=0; fit=True
    for k in sorted(islands,key=lambda k:(-k[2],-k[1])):
        w,h=k[1:3]
        if x+w+1>atlas:x=1;y+=row+2;row=0
        if y+h+1>atlas:fit=False;break
        islands[k]=(x,y);x+=w+2;row=max(row,h)
    if fit:break
else:raise RuntimeError('Atlas over budget')
im=Image.new('RGBA',(atlas,atlas),(0,0,0,0))
for (mat,w,h,detail),(x,y) in islands.items():
    base,shade,light,glint=PALETTE[mat]
    tile=Image.new('RGBA',(w,h),base); d=ImageDraw.Draw(tile)
    if w>=3 and h>=3:
        d.line((0,0,w-1,0),fill=light);d.line((0,h-1,w-1,h-1),fill=shade)
    if detail=='plain' and w>=6 and h>=3:
        d.line((1,1, max(1,w//3),1),fill=light)
    elif detail in ['ribs','grip','bands']:
        for yy in range(2,h-1,3):d.line((0,yy,w-1,yy),fill=shade)
        if w>=3:d.line((w-1,0,w-1,h-1),fill=light)
    elif detail=='rail' and h>=2:
        d.line((0,h-1,w-1,h-1),fill=shade)
        if w>9:d.point((w-3,0),fill=glint)
    elif detail=='controls':
        d.rectangle((1,1,max(1,w-2),1),fill=shade)
        if w>=6 and h>=3:d.rectangle((w-3,h-2,w-2,h-2),fill=glint)
    elif detail=='bolt':
        d.point((w//2,h//2),fill=shade)
    elif detail=='cartridge':
        if w>=3 and h>=2:
            d.line((0,0,0,h-1),fill=shade)
            d.line((1,0,w-1,0),fill=light)
            d.point((w-1,0),fill=glint)
    elif detail=='latch':
        d.rectangle((0,h//2,w-1,h//2),fill=glint)
    elif detail=='pulse':
        d.line((0,h//2,w-1,h//2),fill=light)
        d.point((w//2,0),fill=glint)
    elif detail=='slots':
        for xx in range(2,w-1,4):d.line((xx,1,xx,max(1,h-2)),fill=shade)
    im.paste(tile,(x,y))
    # One-pixel edge extrusion; uncovered atlas stays truly transparent.
    im.paste(tile.crop((0,0,w,1)),(x,y-1));im.paste(tile.crop((0,h-1,w,h)),(x,y+h))
    im.paste(tile.crop((0,0,1,h)),(x-1,y));im.paste(tile.crop((w-1,0,w,h)),(x+w,y))
im.save(ROOT/(IDENT+'_tex.png'))
def uid(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'advanced-syringe-20261113/'+s))
elements=[]; groups={}
for i,s in enumerate(SPEC):
    e=dict(name=s['name'],type='cube',uuid=uid(s['name']),box_uv=False,
           **{'from':[round(v*SCALE,6) for v in s['a']],'to':[round(v*SCALE,6) for v in s['b']]},
           origin=[round(v*SCALE,6) for v in s['pivot']],rotation=s['rotation'],autouv=0,
           color=list(PALETTE).index(s['mat']),export=True,faces={})
    for f in AXES:
        k=face_keys[i,f];x,y=islands[k];w,h=k[1:3]
        e['faces'][f]={'uv':[x,y,x+w,y+h],'texture':0}
    elements.append(e);groups.setdefault(s['group'],[]).append(e['uuid'])
outliner=[dict(name=g,uuid=uid('group/'+g),origin=[0,0,0],rotation=[0,0,0],export=True,
               children=children) for g,children in groups.items()]
model={'meta':{'format_version':'4.10','model_format':'bedrock','box_uv':False},
       'name':'高级注射器枪 AdvancedSyringeGun','model_identifier':IDENT,'visible_box':[2,2,.5],
       'resolution':{'width':atlas,'height':atlas},'elements':elements,'outliner':outliner,
       'textures':[{'name':IDENT+'_tex.png','uuid':uid('texture'),'id':'0','width':atlas,'height':atlas,
                    'uv_width':atlas,'uv_height':atlas,'mode':'bitmap','source':'data:image/png;base64,'+
                    base64.b64encode((ROOT/(IDENT+'_tex.png')).read_bytes()).decode()}]}
(REF/'authored_input.bbmodel').write_text(json.dumps(model,ensure_ascii=False,indent=2),encoding='utf8')
(REF/'palette.json').write_text(json.dumps(PALETTE,indent=2),encoding='utf8')
(REF/'geometry_spec.json').write_text(json.dumps(SPEC,indent=2),encoding='utf8')
(REF/'design_budget.json').write_text(json.dumps({'planned_max_cubes':90,'planned_max_faces':540,
 'planned_max_bones':10,'materials':1,'max_atlas_side':128,'actual_atlas_side':atlas,
 'actual_cubes':len(elements),'actual_bones':len(groups),'density_source_px_per_unit':density,
 'density_final_px_per_unit':density/SCALE,'scale':SCALE,'animation':False,
 'method':'Continuous cuboids and rotated bars; texture-based small markings; one shared atlas'},indent=2),encoding='utf8')
print(json.dumps({'cubes':len(elements),'bones':len(groups),'atlas':atlas,'islands':len(islands)}))
