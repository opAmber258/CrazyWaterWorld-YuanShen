"""Deterministic cabinet geometry and pixel atlas. Run from this directory or any cwd."""
from pathlib import Path
from PIL import Image, ImageDraw
import json, math, base64

ROOT=Path(__file__).resolve().parents[1]
REF=ROOT/'reference'
PALETTE={
 'shell':'#777d78','shell_light':'#92988e','shell_shadow':'#626b65',
 'door':'#80867b','door_light':'#92998d','door_shadow':'#697268',
 'trim':'#555e59','trim_light':'#737e74','trim_shadow':'#454e49',
 'dark':'#303d38','dark_light':'#49574f','dark_shadow':'#29332f',
 'steel':'#9da59a','steel_light':'#b2b8aa','steel_shadow':'#77857a',
 'glass':'#243c38','glass_light':'#536c5d','glass_shadow':'#1d302d',
 'recess':'#48564c','recess_light':'#637063','recess_shadow':'#3b473f',
 'label':'#b4b4a0','label_light':'#c0c1ad','label_shadow':'#878f7f',
}
groups={}
cubes=[]
def group(name,pivot,parent='large_steel_cabinet'):
 groups[name]={'name':name,'origin':pivot,'parent':parent}
def cube(name,a,b,mat='shell',g='housing',detail=None,rotation=None,origin=None):
 cubes.append(dict(name=name,from_=a,to=b,material=mat,group=g,detail=detail,rotation=rotation or [0,0,0],origin=origin or [0,0,0]))
def box(name,x,y,z,w,h,d,**kw): cube(name,[x,y,z],[x+w,y+h,z+d],**kw)
group('large_steel_cabinet',[0,0,0],None)
for n,p in [('housing',[0,0,0]),('crown',[0,30,0]),('plinth',[0,0,0]),('rear_panels',[0,0,0])]:group(n,p)
for n,p in [('door_left_top',[-14.7,25.5,5.45]),('door_left_mid',[-14.7,19.8,5.45]),('door_left_lower',[-14.7,9.5,5.45]),('door_center',[-4.6,16,5.45]),('door_right',[14.7,16,5.45])]:group(n,p)
# Grounded recessed plinth, wrapped shell, raised crown; front +Z.
box('rear_shell',-15.1,2,-5.45,30.2,28.6,.7,detail='rear_shell')
box('left_shell',-15.6,2,-5.45,.8,28.6,10.8,detail='side')
box('right_shell',14.8,2,-5.45,.8,28.6,10.8,detail='side')
box('cabinet_floor',-14.8,2.25,-4.8,29.6,.65,10.5,mat='trim')
box('cabinet_roof',-14.8,29.5,-4.8,29.6,.65,10.5,mat='trim')
box('front_left_stile',-15.65,2.3,5.05,.95,27.5,.65,mat='trim')
box('front_right_stile',14.7,2.3,5.05,.95,27.5,.65,mat='trim')
for i,x in enumerate([-5.1,4.7]):
 box('vertical_partition_'+str(i),x,2.9,-4.7,.55,26.7,10.25,mat='trim')
 box('front_mullion_'+str(i),x-.12,2.7,5.25,.8,27,.55,mat='shell')
box('left_horizontal_sill',-14.7,16.2,4.95,9.55,.6,.85,mat='trim')
box('upper_horizontal_sill',-14.7,22.1,4.95,9.55,.5,.85,mat='trim')
box('crown_core',-15.85,29.7,-5.65,31.7,2.6,11.65,g='crown',detail='crown')
box('crown_top_cap',-15.6,32.3,-5.4,31.2,.5,11.15,g='crown',mat='shell_light')
box('crown_lower_fold',-15.6,29.45,5.45,31.2,.45,.45,g='crown',mat='trim')
box('crown_left_end',-16,30,-5.15,.35,2.15,10.8,g='crown',mat='trim')
box('crown_right_end',15.65,30,-5.15,.35,2.15,10.8,g='crown',mat='trim')
box('crown_nameplate',-4,30.6,6.03,8,1.05,.14,g='crown',mat='trim',detail='nameplate')
box('base_shadow',-15.45,.4,-5.55,30.9,2.1,11.2,g='plinth',mat='trim')
box('base_front',-15.8,.35,5.3,31.6,2.15,.7,g='plinth',detail='base')
box('base_top_fold',-15.6,2.5,-5.5,31.2,.45,11.35,g='plinth',mat='shell_light')
for i,x in enumerate([-15.3,12.7]):
 for j,z in enumerate([-5.35,3.9]):box(f'foot_{i}_{j}',x,0,z,2.6,.5,1.5,g='plinth',mat='dark')
for i,(x,w) in enumerate([(-14.85,9.5),(-4.4,8.8),(5.3,9.55)]):
 box('base_recess_'+str(i),x,.65,6.02,w,1.25,.08,g='plinth',mat='recess',detail='base_recess')
# Each door and all its fittings are children of its own hinge group.
doors=[('door_left_top',-14.55,22.7,9.15,6.15,'small_01'),('door_left_mid',-14.55,16.95,9.15,5.35,'small_02'),('door_left_lower',-14.55,3.3,9.15,12.6,'lower'),('door_center',-4.35,3.3,8.75,25.55,'center'),('door_right',5.5,3.3,9.05,25.55,'right')]
for n,x,y,w,h,dt in doors:
 box(n+'_gasket',x-.12,y-.12,5.25,w+.24,h+.24,.3,g=n,mat='dark')
 box(n+'_fold',x,y,5.48,w,h,.37,g=n,mat='trim')
 box(n+'_panel',x+.25,y+.22,5.85,w-.5,h-.44,.22,g=n,mat='door',detail='door_'+dt)
 for j,hy in enumerate([y+1,y+h-1.5]):
  hx=x-.09 if n!='door_right' else x+w-.34
  box(n+'_hinge_'+str(j),hx,hy,5.7,.43,.95,.48,g=n,mat='steel',detail='hinge')
 if dt in ['lower','center','right']:
  hx=x+1.02;hy=y+h*.53
  box(n+'_lock_recess',hx-.55,hy-.65,6.08,1.25,1.45,.12,g=n,mat='dark',detail='lock')
  for k in range(8):
   ang=k*math.pi/4;lx=hx+.5*math.sin(ang);ly=hy+.5*math.cos(ang)
   box(n+'_lock_ring_'+str(k),lx-.23,ly-.075,6.21,.46,.15,.15,g=n,mat='steel',rotation=[0,0,-k*45],origin=[lx,ly,6.21])
  box(n+'_latch',hx-.1,hy-.35,6.3,.2,.8,.3,g=n,mat='steel',rotation=[0,0,-18],origin=[hx,hy,6.3])
 else:
  box(n+'_label_holder',x+w*.42,y+h-1.18,6.09,1.6,.65,.12,g=n,mat='trim',detail='small_label')
  box(n+'_pull',x+w*.4,y+.65,6.09,1.9,.26,.32,g=n,mat='steel')
# Pixel-stepped oval glass with eight steel ring segments. Surface-set closed window.
cx=.025;cy=20.6;r=2.65
box('port_inner_center',cx-2.32,cy-1.65,6.09,4.64,3.3,.12,g='door_center',mat='glass',detail='glass')
box('port_inner_upper',cx-1.67,cy+1.65,6.09,3.34,.72,.12,g='door_center',mat='glass',detail='glass')
box('port_inner_lower',cx-1.67,cy-2.37,6.09,3.34,.72,.12,g='door_center',mat='glass',detail='glass')
for i in range(8):
 a=i*math.pi/4
 x=cx+r*math.sin(a);y=cy+r*math.cos(a)
 box('port_rim_'+str(i),x-1.13,y-.2,6.18,2.26,.4,.28,g='door_center',mat='steel',rotation=[0,0,-i*45],origin=[x,y,6.18])
# Quiet back, mounting rails and shallow fabricated access panel.
box('rear_service_panel',-13.7,4.4,-5.71,27.4,23.3,.26,g='rear_panels',mat='shell_shadow',detail='rear_panel')
for i,y in enumerate([5.1,26.1]):
 box('rear_mount_rail_'+str(i),-13.3,y,-5.95,26.6,.85,.25,g='rear_panels',mat='trim',detail='mount')

FACES=['north','east','south','west','up','down']
def rect_size(c,face):
 a=c['from_'];b=c['to'];s=[b[i]-a[i] for i in range(3)]
 uv={'north':(s[0],s[1]),'south':(s[0],s[1]),'east':(s[2],s[1]),'west':(s[2],s[1]),'up':(s[0],s[2]),'down':(s[0],s[2])}[face]
 return tuple(max(1,math.ceil(x*2)) for x in uv)
def draw_tile(img,c,face,uv):
 x,y,w,h=uv;d=ImageDraw.Draw(img);m=c['material'];base=PALETTE[m]
 light=PALETTE.get(m+'_light',PALETTE['shell_light']);shadow=PALETTE.get(m+'_shadow',PALETTE['shell_shadow'])
 d.rectangle([x,y,x+w-1,y+h-1],fill=base)
 if w>5 and h>5:
  d.line([x+1,y,x+w-2,y],fill=light)
  d.line([x+w-1,y+1,x+w-1,y+h-2],fill=shadow)
  d.line([x+1,y+h-1,x+w-2,y+h-1],fill=shadow)
 dt=c['detail'];front=face=='south';back=face=='north';side=face in ['east','west']
 def line(xx,yy,ww,col=shadow):
  if 0<=yy<h:d.line([x+max(0,xx),y+yy,x+min(w-1,xx+ww-1),y+yy],fill=col)
 def bolt(xx,yy):
  if 0<=xx<w-1 and 0<=yy<h-1:
   d.point((x+xx,y+yy),fill=light);d.point((x+xx+1,y+yy+1),fill=shadow)
 if dt and dt.startswith('door_') and front:
  # Folded steel, restrained wear and functional ventilation; same pixel density throughout.
  line(2,2,w-4,light);line(2,h-3,w-4,shadow)
  if dt in ['door_center','door_right','door_lower']:
   for vy in [5,8,11]:
    line(w//2-4,vy,9,PALETTE['dark']);line(w//2-3,vy+1,7,light)
   if dt=='door_center':
    for vy in [h-9,h-6]:line(w//2-4,vy,8,shadow)
  else:
   for vx in [4,w-7]:
    for vy in range(max(4,h-7),h-2,2):line(vx,vy,4,shadow)
  line(2,3,3,light);line(w-5,h-4,2,light)
  bolt(2,h-5);bolt(w-4,4)
 if dt=='glass' and front and w>4:
  # A single flat highlight, no simulated emission or gradient.
  line(2,2,max(1,w-4),PALETTE['glass_light'])
  if h>5:line(2,3,2,PALETTE['glass_light'])
 if dt=='side' and side and w>10:
  for yy in [4,h-6]:line(3,yy,w-6,shadow)
  for yy in [6,h-8]:bolt(4,yy);bolt(w-6,yy)
  d.line([x+3,y+8,x+3,y+h-10],fill=shadow)
 if dt=='rear_panel' and back:
  for xx in [3,w//2,w-5]:
   for yy in [3,h-5]:bolt(xx,yy)
  d.line([x+w//2,y+6,x+w//2,y+h-7],fill=shadow)
  line(w//2+4,8,7,shadow);line(w//2+4,10,7,light)
 if dt=='crown' and front:
  for xx in [4,w-19]:
   line(xx,2,14,light);line(xx,3,14,shadow)
 if dt=='nameplate' and front:
  line(2,0,w-4,PALETTE['label_shadow'])
  for j in [3,6,8,11]:line(j,1,1,PALETTE['label'])
 if dt=='base_recess' and front:
  bolt(1,0);bolt(w-3,0)
 if dt=='mount' and back:
  for xx in [2,w-4]:bolt(xx,0)

def build():
 # User requested approximately 3 x 2.5 blocks. Maintain circular port proportions.
 sx,sy,sz=1.5,40/32.8,1.1
 zcenter=(-5.95+6.6)/2
 for g in groups.values():g['origin']=[g['origin'][0]*sx,g['origin'][1]*sy,(g['origin'][2]-zcenter)*sz]
 for c in cubes:
  port=c['name'].startswith('port_')
  for key in ['from_','to','origin']:
   a=c[key];a[0]=(cx*sx+(a[0]-cx)*sy) if port else a[0]*sx;a[1]*=sy;a[2]=(a[2]-zcenter)*sz
 # Shared tiles are deliberate for identical plain steel faces; detailed faces stay unique by type.
 tiles=[]
 unique={}
 for ci,c in enumerate(cubes):
  c['faces']={}
  for f in FACES:
   w,h=rect_size(c,f);dt=c['detail']
   special=dt if (f=='south' and dt in ['glass','crown','nameplate','base_recess'] or f=='south' and dt and dt.startswith('door_') or f=='north' and dt in ['rear_panel','mount'] or f in ['east','west'] and dt=='side') else None
   key=(c['material'],w,h,special)
   c['faces'][f]={'tile_key':key}
   if key not in unique:unique[key]=None;tiles.append((w,h,ci,f,key))
 tiles.sort(key=lambda t:(-t[1],-t[0]))
 x=y=1;row=0;size=512;atlas=Image.new('RGBA',(size,size),(0,0,0,0))
 for w,h,ci,f,key in tiles:
  if x+w+1>size:x=1;y+=row+2;row=0
  if y+h+1>size:raise RuntimeError('atlas overflow '+str(y+h))
  unique[key]=[x,y,x+w,y+h]
  draw_tile(atlas,cubes[ci],f,(x,y,w,h));x+=w+2;row=max(row,h)
 for c in cubes:
  for f in FACES:c['faces'][f]={'uv':unique[c['faces'][f]['tile_key']],'texture':0}
 atlas.save(ROOT/'large_steel_cabinet_tex.png')
 (REF/'palette.json').write_text(json.dumps(PALETTE,indent=2),encoding='utf8')
 spec={'name':'large_steel_cabinet','texture_size':size,'groups':list(groups.values()),'cubes':cubes,'texture':base64.b64encode((ROOT/'large_steel_cabinet_tex.png').read_bytes()).decode(),'atlas_used_height':y+row}
 (REF/'model_spec.json').write_text(json.dumps(spec,indent=2),encoding='utf8')
 print(json.dumps({'cubes':len(cubes),'bones':len(groups),'atlas_height':y+row}))
if __name__=='__main__':build()
