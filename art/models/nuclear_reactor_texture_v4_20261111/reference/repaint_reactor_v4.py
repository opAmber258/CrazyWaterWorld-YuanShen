"""Readable material hierarchy with mechanically located aging. Python + Pillow.
Reuses the frozen user geometry AND exact v3 UV layout. No all-surface random fill.
Independent surface seeds only place bounded edge chips, joint grime and runoff.
"""
from pathlib import Path
from copy import deepcopy
import json,math,hashlib,random,base64
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parent.parent
REF=ROOT/'reference'
ATLAS=512
P={
 'enamel':'#A7AB9E','enamel_light':'#BCBEAD','enamel_shadow':'#929C90',
 'enamel_film':'#979E8D','enamel_dirty':'#7F8979','enamel_join':'#687769',
 'jacket':'#C1BFA6','jacket_light':'#D0CBB2','jacket_shadow':'#A5AA93',
 'jacket_stain':'#959B83','jacket_soil':'#727E6D',
 'primer':'#596A61','steel':'#7E897F','steel_light':'#A7B0A0','steel_dim':'#596D62',
 'steel_dark':'#414F4A','steel_shadow':'#2D423D',
 'frame':'#3E4C48','frame_light':'#627063','frame_shadow':'#2D3C36',
 'cast':'#2D3937','cast_face':'#3B4B45','cast_lip':'#59695E','cast_dark':'#1F302D',
 'pipe':'#484039','pipe_turn':'#625B4E','pipe_light':'#8F8B76','pipe_oxide':'#514F43',
 'pipe_hot':'#343A37','soot':'#252E2C','oil':'#263C37','oil_edge':'#3B5146',
 'oxide':'#805B4C','oxide_dark':'#5E5246','oxide_light':'#9B7A5C',
 'glass':'#28302D','glass_body':'#303833','glass_reflection':'#485044',
 'gasket':'#2B3731','warning':'#AC9C58','warning_dark':'#7A784C','ink':'#3C4C3D',
 'label':'#B9B79A','red':'#8E6151','green':'#7D9275'
}
baseline=json.loads((REF/'user_baseline/nuclear_reactor_user.bbmodel').read_text(encoding='utf-8'))
layout=json.loads((REF/'uv_layout.json').read_text(encoding='utf-8'))
meta={c['name']:c for c in json.loads((REF/'construction.json').read_text(encoding='utf-8'))['cubes']}

def render_tile(spec):
 w,h,kind,mat,face=spec['w'],spec['h'],spec['kind'],spec['material'],spec['face']
 name=spec['cube'];seed=int(hashlib.sha256(('reactor-v4:'+spec['key']).encode()).hexdigest()[:16],16)
 rng=random.Random(seed)
 enamel=mat in ('shell','shell_dark','shell_edge')
 if mat=='shell_light':base='jacket'
 elif mat=='shell_dark':base='enamel_shadow'
 elif mat=='shell_edge':base='steel'
 elif mat=='shell':base='enamel'
 elif mat=='metal':base='steel'
 elif mat in ('metal_dark','deep'):base='steel_dark' if mat=='metal_dark' else 'soot'
 elif mat=='window':base='glass'
 elif mat=='warning':base='warning'
 else:base='pipe'
 if kind in ('base','rail','handle','small_pipe','cable_tray'):base='frame'
 if kind in ('motor','motor_head','reactor_lower','terminal','service'):base='cast'
 if kind=='fin':base='steel'
 if kind=='interior':base='cast_dark'
 im=Image.new('RGBA',(w,h),P[base]);d=ImageDraw.Draw(im)
 mask=Image.new('L',(w,h),0);md=ImageDraw.Draw(mask)
 def rect(x0,y0,x1,y1,c,wear=False):
  x0,y0,x1,y1=max(0,int(x0)),max(0,int(y0)),min(w-1,int(x1)),min(h-1,int(y1))
  if x1>=x0 and y1>=y0:
   d.rectangle((x0,y0,x1,y1),fill=P[c])
   if wear:md.rectangle((x0,y0,x1,y1),fill=255)
 def line(pts,c,width=1,wear=False):
  pts=[(int(x),int(y)) for x,y in pts];d.line(pts,fill=P[c],width=width)
  if wear:md.line(pts,fill=255,width=width)
 def rivet(x,y,light='steel_light',dark='steel_dark'):
  rect(x,y,x+1,y+1,dark);rect(x,y,x,y,light)
 def chip_run(x,y,length,horizontal=True,oxide=True):
  # One broken exposure line on a contact edge. No framed rust blob or decal.
  for i in range(length):
   if rng.random()<.22:continue
   xx,yy=(x+i,y) if horizontal else (x,y+i)
   rect(xx,yy,xx,yy,'primer',True)
   if i%3==rng.randrange(3):rect(xx,yy,xx,yy,'steel',True)
   if oxide and rng.random()<.32:
    rect(xx,yy+1,xx,yy+1,'oxide_dark',True)
    if rng.random()<.4:rect(xx,yy+1,xx,yy+1,'oxide',True)
 def drip(x,y,length,c='enamel_dirty',warm=False):
  if length<2:return
  # Gravity-directed runoff from an actual screw, collar or seam.
  end=min(h-1,y+length)
  rect(x,y,x,end,c,True)
  if length>5:rect(x+1,y+1,x+1,y+length//2,'enamel_film' if c=='enamel_dirty' else 'jacket_stain',True)
  if warm:rect(x,y,x,min(end,y+2),'oxide_dark',True)
 def film_at_edge(maxdepth,c,lower='enamel_dirty'):
  # Continuous buildup at a lower flange, with low-contrast broken edge only.
  if h<3 or w<3:return
  depth=max(1,min(maxdepth,h//3))
  for x in range(w):
   if rng.random()<.22:depth=max(1,min(maxdepth,depth+rng.choice([-1,1])))
   rect(x,h-depth,x,h-1,c,True)
   if depth>2:rect(x,h-1,x,h-1,lower,True)
 def short_rub(c='steel_light'):
  if min(w,h)<3:return
  # Handling marks: two related strokes in one contact region.
  x=rng.randrange(max(1,w-3));y=rng.choice([1,max(1,h-3)])
  length=min(w-x,rng.randint(2,5))
  rect(x,y,x+length-1,y,c,True)
  if length>3:rect(x+1,y+1,x+length-2,y+1,'primer',True)

 if kind in ('case','panel_edge','rear_door'):
  if min(w,h)>3:
   # Pressed sheet return. Quiet center is intentional and reads as solid paint.
   rect(0,0,w-1,0,'enamel_light')
   rect(0,1,0,h-1,'enamel_shadow')
   rect(0,h-1,w-1,h-1,'enamel_join')
   if w>24 and h>12:
    sx=w//2
    rect(sx,1,sx,h-2,'enamel_shadow')
    rect(sx+1,2,sx+1,min(h-3,6),'enamel_light')
   if min(w,h)>7:
    screws=[(2,2),(w-4,2),(2,h-4),(w-4,h-4)]
    for x,y in screws:rivet(x,y)
    anchor=rng.choice(screws[:2]);drip(anchor[0],anchor[1]+2,rng.randint(3,max(4,h//3)),warm=rng.random()<.5)
   if h>6:film_at_edge(min(4,max(1,h//8)),'enamel_film')
   if w>12:
    x=rng.randrange(1,max(2,w-6));chip_run(x,h-2,rng.randint(3,6))
   if h>18 and w>6:
    y=rng.randrange(4,h-7);chip_run(rng.choice([0,w-1]),y,rng.randint(3,7),False,False)
   if face=='south' and name=='upper_front_plate':
    # Port runoff and paint damage immediately above the inspection opening.
    drip(w//2+1,max(0,h-6),6,'enamel_dirty',True)
    chip_run(w*2//3,h-2,min(6,max(1,w//8)))
   if face=='south' and name in ('hatch_left_jamb','hatch_right_jamb'):
    xx=w-2 if name=='hatch_left_jamb' else 1
    start=h//3+rng.randint(0,3)
    drip(xx,start,max(3,h//3),'enamel_dirty',True)
   if face=='south' and name=='window_sill' and w>6:
    rect(w//3,h-2,w*2//3,h-1,'enamel_dirty',True)
    rect(w//3+1,h-1,w//2,h-1,'oxide_dark',True)
   if kind=='rear_door' and w>16 and h>16:
    rect(2,2,w-3,2,'steel_dark')
    rect(2,3,2,h-4,'enamel_shadow')
    rect(2,h-3,w-3,h-3,'steel_dark')
    for yy in range(h-12,h-4,3):
     rect(5,yy,w-6,yy,'steel_shadow');rect(5,yy+1,w-6,yy+1,'enamel_shadow')
    rect(w//2-4,5,w//2+4,7,'steel_dim');rect(w//2-3,6,w//2+2,6,'label')
    # Smudging near the handle and lower ventilation, rather than random center specks.
    drip(w*3//4,h//2,min(7,h//4),'enamel_dirty')
    short_rub('enamel_shadow')
   elif face in ('up','down') and w>20 and h>12:
    # Top cover is one coherent pressed panel, with a recessed service seam.
    rect(3,h//3,w-4,h//3,'enamel_shadow')
    rect(3,h//3+1,w-4,h//3+1,'enamel_light')
    if w>40:
     rect(w//3,2,w//3,h//3,'enamel_shadow')
     rect(w*2//3,h//3,w*2//3,h-3,'enamel_shadow')
    # A single handling scuff is localized to the opening lip.
    chip_run(rng.randrange(max(1,w-9)),h//3,rng.randint(4,8),oxide=False)

 elif kind in ('sleeve','sleeve_end'):
  if h>5 and min(w,h)>1 and kind=='sleeve':
   # Painted/insulated jacket. Long, quiet vertical plane, dark collar dirt.
   rect(0,1,0,h-2,'jacket_shadow')
   if w>4:rect(1,2,1,h-4,'jacket_light')
   rect(0,0,w-1,1,'jacket_shadow')
   film_at_edge(min(4,max(2,h//7)),'jacket_stain','jacket_soil')
   x=rng.randrange(w);start=rng.choice([1,2,3])
   drip(x,start,rng.randint(3,max(4,h//2)),'jacket_stain',warm=rng.random()<.4)
   if w>3:
    chip_run(rng.randrange(max(1,w-2)),h-3,rng.randint(2,min(4,w)),oxide=True)
   # Hairline at a stressed collar, not a field of coating-shaped patches.
   if h>16 and w>4 and rng.random()<.6:
    yy=rng.randrange(h//2,h-5);xx=rng.randrange(1,w-1)
    line([(xx,yy),(xx,yy+2),(min(w-1,xx+1),yy+3)],'jacket_shadow',wear=True)
  elif min(w,h)>1:
   rect(0,0,w-1,h-1,'steel_dim');rect(0,0,w-1,0,'steel')

 elif kind=='pipe_segment' or (mat.startswith('pipe') and kind=='plain'):
  # The dark pipe is warm, but the heat-darkened ends and exposed steel are cool.
  if min(w,h)>2:
   edge=max(1,w//4)
   rect(0,0,edge-1,h-1,'pipe_hot')
   rect(w-1,0,w-1,h-1,'pipe_hot')
   if w>4:rect(edge,1,min(w-2,edge+1),h-2,'pipe_turn')
   rect(0,0,w-1,min(h-1,1),'pipe_oxide')
   rect(0,h-2,w-1,h-1,'soot')
   # Linear abrasion follows a bent-pipe tangent; small separate run each face.
   if w>4 and h>4:
    xx=rng.randrange(1,w-2);yy=rng.randrange(1,max(2,h//2))
    length=min(w-1-xx,rng.randint(2,4));chip_run(xx,yy,length,oxide=False)
    rect(xx,yy,xx+length-1,yy,'pipe_light',True)

 elif kind=='fin':
  # Machined front lip / dark side. Fin-to-fin wear remains independent.
  rect(0,0,0,h-1,'steel')
  if w>1:rect(w-1,0,w-1,h-1,'steel_dark')
  if h>8:
   # Dust rises by different amounts on each fin; the body above stays calm.
   depth=rng.randint(2,max(3,h//5))
   rect(0,h-depth,w-1,h-1,'steel_dim')
   rect(0,h-2,w-1,h-1,'oxide_dark')
   y=rng.randrange(3,max(4,h-4))
   rect(0,y,0,min(h-1,y+rng.randint(1,3)),'steel_light',True)
   if w>1 and rng.random()<.5:rect(w-1,h-depth,w-1,h-depth+1,'oxide',True)

 elif kind in ('side_grille','radiator_back'):
  rect(0,0,w-1,h-1,'steel_shadow' if kind=='side_grille' else 'soot')
  if kind=='side_grille' and w>5 and h>10:
   # Clean readable negative slots with a dull steel lower lip.
   for yy in range(4,h-4,4):
    rect(2,yy,w-3,yy+1,'soot');rect(2,yy+2,w-3,yy+2,'steel_dim')
    if rng.random()<.35:
     xx=rng.randrange(2,max(3,w-4));rect(xx,yy+2,min(w-3,xx+rng.randint(1,4)),yy+2,'steel',True)
   rect(0,0,w-1,0,'steel');rect(0,h-1,w-1,h-1,'steel_dark')
   for x,y in ((1,1),(w-3,1),(1,h-3),(w-3,h-3)):rivet(x,y)
   # One isolated lower drain trace, not material variation across every slot.
   xx=rng.randrange(2,max(3,w-2));rect(xx,h-6,xx,h-2,'oxide_dark',True)
  if h>4:rect(0,h-2,w-1,h-1,'oil')

 elif kind in ('clamp','rail','handle','small_pipe','cable_tray'):
  base='steel_dim' if kind=='clamp' else 'frame'
  rect(0,0,w-1,h-1,base)
  if h>1:
   rect(0,0,w-1,0,'steel' if kind=='clamp' else 'frame_light')
   rect(0,h-1,w-1,h-1,'steel_dark' if kind=='clamp' else 'frame_shadow')
  if w>5 and h>2:
   rivet(1,1);rivet(w-3,1)
   if w>10:rect(w//2,0,w//2,h-1,'steel_dark')
   x=rng.randrange(max(1,w-3));chip_run(x,h-1,rng.randint(2,min(5,w)))
  if kind=='cable_tray':
   rect(0,h//2,w-1,h-1,'oil')
   for xx in range(5,w-3,13):rect(xx,0,xx,h-1,'frame_shadow')
  if kind=='handle' and min(w,h)>2:short_rub('steel')

 elif kind in ('motor','motor_head','reactor_lower','base'):
  if min(w,h)>2:
   # Deliberate plane separation, casting ribs and end-plate detail.
   rect(1,1,w-2,h-2,'frame' if kind=='base' else 'cast_face')
   rect(0,h-2,w-1,h-1,'cast_dark')
   if kind=='motor':
    for xx in range(3,w-2,4):
     rect(xx,2,xx,h-3,'cast_dark');rect(xx+1,2,xx+1,h-3,'cast_face')
     if xx%8==3:rect(xx+1,2,xx+1,max(2,h//2),'cast_lip')
    rect(0,1,w-1,1,'cast_lip')
   elif kind=='reactor_lower':
    for xx in range(4,w-3,7):
     rect(xx,3,xx,h-3,'cast_dark');rect(xx+1,3,xx+1,h-3,'cast')
    rect(2,1,w-3,1,'steel_dim')
   elif kind=='motor_head' and min(w,h)>6:
    cx,cy=(w-1)/2,(h-1)/2;rr=min(w,h)*.4
    for yy in range(h):
     for xx in range(w):
      r=math.hypot(xx-cx,yy-cy)
      if rr-1<r<rr:d.point((xx,yy),fill=P['steel_dim'])
      elif r<rr-1:d.point((xx,yy),fill=P['cast_dark'] if (xx==round(cx) or yy==round(cy)) else P['cast'])
    for xx,yy in ((1,1),(w-3,1),(1,h-3),(w-3,h-3)):rivet(xx,yy)
   elif kind=='base':
    rect(0,0,w-1,0,'frame_light')
    for xx in range(6,w-4,21):rivet(xx,max(0,h//2-1),dark='frame_shadow')
    if face in ('up','down') and min(w,h)>8:
     # Repeated fixed weld seam is structure; only the rubbed contact zones vary.
     rect(1,h-3,w-2,h-3,'frame_shadow');rect(1,h-2,w-2,h-2,'frame_light')
     # Grease lies beneath mounts, with short abrasion beside a foot contact.
     xx=rng.randrange(max(1,w//4),max(2,w*3//4));yy=max(1,h//2)
     rect(xx,yy,min(w-1,xx+5),min(h-1,yy+1),'oil',True)
     rect(xx+1,yy+2,min(w-1,xx+3),min(h-1,yy+2),'oil_edge',True)
     chip_run(max(0,xx-5),max(0,yy-1),4,oxide=False)
   # Buildup on lower seal, plus an actual lubricant run from the casing joint.
   film_at_edge(min(3,max(1,h//6)),'oil','oil')
   if w>8 and h>5:
    x=rng.randrange(2,w-2);start=max(1,h//3)
    rect(x,start,x,min(h-1,start+rng.randint(2,max(3,h//2))),'oil',True)
    rect(x+1,start+1,x+1,min(h-1,start+3),'oil_edge',True)
    rect(max(0,x-1),h-2,min(w-1,x+3),h-1,'oil',True)
    chip_run(rng.randrange(max(1,w-5)),0,rng.randint(2,min(5,w)),oxide=False)

 elif kind in ('window','window_seal'):
  if kind=='window':
   rect(0,0,w-1,h-1,'glass')
   # Quiet, smoky glass. Low-contrast reflection is a single coherent shape.
   if min(w,h)>5:
    rect(2,2,w-3,h-4,'glass_body')
    rect(2,2,w//2,2,'glass_reflection')
    rect(2,3,2,h//2,'glass_reflection')
    rect(0,h-2,w-1,h-1,'soot')
  else:
   rect(0,0,w-1,h-1,'steel_dark')
   if min(w,h)>3:rect(1,1,w-2,h-2,'gasket')
   if w>8 and h>4:
    rect(0,h-1,w-1,h-1,'oxide_dark')
    xx=rng.randrange(1,w-4);chip_run(xx,h-1,rng.randint(2,4))

 elif kind=='round_port':
  rect(0,0,w-1,h-1,'enamel')
  cx,cy=(w-1)/2,(h-1)/2;rr=min(w,h)/2
  for yy in range(h):
   for xx in range(w):
    dx,dy=xx-cx,yy-cy;r=math.hypot(dx,dy)
    if r<rr-1:
     col='steel_dark' if r>rr-2 else 'steel' if r>rr-3 else 'cast'
     if r<rr-3 and (xx==round(cx) or yy==round(cy) or abs(abs(dx)-abs(dy))<.4):col='steel_dim'
     if r<1.5:col='soot'
     d.point((xx,yy),fill=P[col])
  if h>5:drip(w//2+rng.choice([-1,1]),h-3,3,'enamel_dirty',True)

 elif kind=='controls':
  rect(0,0,w-1,h-1,'cast_dark')
  for i,col in enumerate(['label','green','red','steel_dim','cast_lip']):
   xx=1+i*max(2,(w-2)//5);rect(xx,0,xx+1,min(1,h-1),col)
  if h>2:rect(0,h-1,w-1,h-1,'oil')

 elif kind=='warning':
  rect(0,0,w-1,h-1,'warning')
  if h>5:
   for y in (2,4,7):rect(1,y,max(1,w-2),y,'ink')
   rect(0,h-2,w-1,h-1,'warning_dark')

 elif kind in ('terminal','service'):
  if min(w,h)>4:
   rect(1,1,w-2,h-2,'steel_dim')
   rect(2,2,w-3,h-3,'cast_dark')
   for yy in range(3,h-2,3):rect(3,yy,max(3,w-4),yy,'cast_face')
   rivet(1,1);rivet(w-3,h-3)
   if w>7:rect(w-4,2,w-3,3,'warning_dark')
   chip_run(rng.randrange(max(1,w-3)),h-2,rng.randint(2,4),oxide=False)

 elif kind=='fuel_sockets':
  rect(0,0,w-1,h-1,'steel_dim')
  for i in range(4):
   xx=1+i*max(2,(w-2)//4);rect(xx,1,xx+1,h-1,'cast_dark');rect(xx,0,xx,0,'steel_light')
  if w>5:chip_run(rng.randrange(w-3),h-1,3,oxide=False)
 elif kind=='interior':pass
 else:
  # Material return faces use a clear fold or turned edge, not blanket grain.
  if min(w,h)>2:
   edge='enamel_shadow' if enamel else 'steel_dark' if mat.startswith('metal') else 'pipe_hot'
   rect(0,h-1,w-1,h-1,edge)
   if w>8 and h>4:short_rub('enamel_shadow' if enamel else 'steel_dim')
 # Thin metal strips retain their readable continuous lip. One local abrasion only.
 if min(w,h)==1 and max(w,h)>7 and kind not in ('window','warning'):
  ll=max(w,h);start=rng.randrange(1,ll-3);end=min(ll-2,start+rng.randint(1,3))
  if w==1:rect(0,start,0,end,'steel_dim',True)
  else:rect(start,0,end,0,'steel_dim',True)
 if kind=='motor_head' and min(w,h)>4:
  # The terminal-side motor has a lubricant trace below its fastener; the
  # opposite end cap has a rubbed upper rim. These are distinct contact causes.
  if name.startswith('left_'):
   rect(2,3,2,min(h-2,7),'oil',True)
   rect(3,4,3,min(h-2,5),'oil_edge',True)
  else:
   chip_run(max(1,w-5),1,3,oxide=False)
   rect(max(1,w-4),1,max(1,w-3),1,'steel',True)
 return im,mask

atlas=Image.new('RGBA',(ATLAS,ATLAS),(0,0,0,0))
wear=Image.new('L',(ATLAS,ATLAS),0)
coverage=[]
for s in layout['islands']:
 tile,mask=render_tile(s);x,y,x1,y1=s['uv'];atlas.paste(tile,(x,y));wear.paste(mask,(x,y))
 atlas.paste(tile.crop((0,0,tile.width,1)),(x,y-1));atlas.paste(tile.crop((0,tile.height-1,tile.width,tile.height)),(x,y1))
 atlas.paste(tile.crop((0,0,1,tile.height)),(x-1,y));atlas.paste(tile.crop((tile.width-1,0,tile.width,tile.height)),(x1,y))
 for px,py,tx,ty in ((x-1,y-1,0,0),(x1,y-1,tile.width-1,0),(x-1,y1,0,tile.height-1),(x1,y1,tile.width-1,tile.height-1)):
  atlas.putpixel((px,py),tile.getpixel((tx,ty)))
 colors=tile.getcolors(ATLAS*ATLAS)
 coverage.append({'cube':s['cube'],'face':s['face'],'kind':s['kind'],'uv':s['uv'],'pixels':s['w']*s['h'],
                  'colors':len(colors),'wear_fraction':sum(mask.tobytes())/(255*s['w']*s['h']),
                  'dominant_fraction':max(n for n,c in colors)/(s['w']*s['h'])})

atlas.save(ROOT/'nuclear_reactor_tex.png');wear.save(REF/'wear_distribution.png')
model=deepcopy(baseline)
model['textures'][0].update({'name':'nuclear_reactor_tex.png','path':'','saved':True,
 'source':'data:image/png;base64,'+base64.b64encode((ROOT/'nuclear_reactor_tex.png').read_bytes()).decode()})
(ROOT/'nuclear_reactor.bbmodel').write_text(json.dumps(model,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
assert model['elements']==baseline['elements'] and model['groups']==baseline['groups'] and model['outliner']==baseline['outliner']
(REF/'palette.json').write_text(json.dumps(P,indent=2),encoding='utf-8')
(REF/'material_coverage.json').write_text(json.dumps(coverage,indent=2),encoding='utf-8')
(REF/'repaint_build.json').write_text(json.dumps({'geometry_and_UV_preserved_exactly':True,'cubes':len(model['elements']),
 'atlas':[ATLAS,ATLAS],'islands':len(layout['islands']),'material_direction':'warm ivory jackets, gray enamel, dark iron/teal base, heat-darkened warm pipes; restrained location-specific aging',
 'blanket_random_texture':False,'colors':len(atlas.getcolors(ATLAS*ATLAS))},indent=2),encoding='utf-8')
print(json.dumps({'geometry_and_UV_preserved_exactly':True,'islands':len(layout['islands']),'colors':len(atlas.getcolors(ATLAS*ATLAS))}))
