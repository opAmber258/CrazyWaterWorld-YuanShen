"""Replace only the undersized rear hose by a broad faceted black circular housing.
Requires Pillow. Preserves all non-tail elements and their existing UV/pixels.
"""
from pathlib import Path
import json,math,uuid,base64,io
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1];REF=ROOT/'reference'
if (REF/'manual_publish_portable.bbmodel').exists():
    accepted=json.loads((REF/'manual_publish_portable.bbmodel').read_text(encoding='utf8'))
    (REF/'authored_input.bbmodel').write_text(json.dumps(accepted,ensure_ascii=False,indent=2),encoding='utf8')
    (ROOT/'advanced_syringe_gun_tex.png').write_bytes(base64.b64decode(accepted['textures'][0]['source'].split(',',1)[1]))
    print(json.dumps({'preserved_final_manual_input':True,'cubes':len(accepted['elements'])}))
    raise SystemExit(0)
if (REF/'round_input.bbmodel').exists():
    accepted=json.loads((REF/'round_input.bbmodel').read_text(encoding='utf8'))
    (REF/'authored_input.bbmodel').write_text(json.dumps(accepted,ensure_ascii=False,indent=2),encoding='utf8')
    (ROOT/'advanced_syringe_gun_tex.png').write_bytes(base64.b64decode(accepted['textures'][0]['source'].split(',',1)[1]))
    print(json.dumps({'restored_equal_radius_input':True,'cubes':len(accepted['elements'])}))
    raise SystemExit(0)
m=json.loads((REF/'baseline_advanced_syringe_gun.bbmodel').read_text(encoding='utf8'))
old=json.loads(json.dumps(m));im=Image.open(REF/'baseline_advanced_syringe_gun_tex.png').convert('RGBA')
removed=[e for e in m['elements'] if e['name'].startswith('hose_segment_')]
assert len(removed)==5
old_ids={e['uuid'] for e in removed};m['elements']=[e for e in m['elements'] if e['uuid'] not in old_ids]
def purge(nodes):
    for n in nodes:
        if isinstance(n,dict):n['children']=[c for c in n.get('children',[]) if not (isinstance(c,str) and c in old_ids)];purge(n.get('children',[]))
purge(m['outliner'])
group=next(g for g in m['groups'] if g['name']=='rear_hose');group['name']='rear_ring'
def rename(nodes):
    for n in nodes:
        if isinstance(n,dict):
            if n.get('uuid')==group['uuid']:n['name']='rear_ring'
            rename(n.get('children',[]))
rename(m['outliner'])
outgroup=next(n for n in m['outliner'] if isinstance(n,dict) and n['uuid']==group['uuid'])

# Dedicated patches occupy only unused transparent atlas area; old texels stay fixed.
patches={};palette={'rim':['#2e3632','#222a27','#414b43'],
 'recess':['#252e2a','#1d2523','#354239'],'spoke':['#3d4740','#2b342e','#5b675a']}
def patch(mat,w,h):
    key=(mat,w,h)
    if key in patches:return patches[key]
    pos=None
    for y in range(1,64-h):
        for x in range(1,64-w):
            if im.getchannel('A').crop((x-1,y-1,x+w+1,y+h+1)).getextrema()==(0,0):pos=(x,y);break
        if pos:break
    assert pos,'No free atlas patch'
    x,y=pos;base,dark,light=palette[mat]
    tile=Image.new('RGBA',(w,h),base);d=ImageDraw.Draw(tile)
    if w>1:d.line((0,0,0,h-1),fill=light);d.line((w-1,0,w-1,h-1),fill=dark)
    if h>2 and mat not in ['rim','recess']:d.line((0,h-1,w-1,h-1),fill=dark)
    if mat=='recess':d.rectangle((0,0,w-1,h-1),fill=base)
    for yy in range(-1,h+1):
        for xx in range(-1,w+1):im.putpixel((x+xx,y+yy),tile.getpixel((min(w-1,max(0,xx)),min(h-1,max(0,yy)))))
    patches[key]=[x,y,x+w,y+h];return patches[key]
new=[]
def cube(name,a,b,mat,rot=0,pivot=None):
    uid=str(uuid.uuid5(uuid.NAMESPACE_URL,'advanced-rear-ring-20261114/'+name))
    e={'name':name,'type':'cube','uuid':uid,'box_uv':False,'from':a,'to':b,'origin':pivot or [0,0,0],
       'rotation':[0,0,rot],'autouv':0,'color':3,'export':True,'faces':{}}
    dims=[y-x for x,y in zip(a,b)]
    for face,axes in {'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}.items():
        w,h=[max(1,round(dims[i]*2.5)) for i in axes]
        e['faces'][face]={'uv':patch(mat,w,h),'texture':0}
    new.append(e);outgroup['children'].append(uid)
cx,cy=-5.7,6.3;rx,ry=2.55,2.55
def bar(name,p,q,width,depth,mat):
    dx,dy=q[0]-p[0],q[1]-p[1];length=math.hypot(dx,dy)
    cube(name,[p[0]-width/2,p[1]-.025,-depth/2],
         [p[0]+width/2,p[1]+length+.025,depth/2],mat,-math.degrees(math.atan2(dx,dy)),[*p,0])
points=[(cx+rx*math.cos(2*math.pi*i/12),cy+ry*math.sin(2*math.pi*i/12)) for i in range(12)]
for i in range(12):bar('rear_ring_rim_'+str(i),points[i],points[(i+1)%12],.55,1.12,'rim')
for angle in [0,45]:cube('rear_ring_recess_'+str(angle),[cx-1.65,cy-1.65,-.32],[cx+1.65,cy+1.65,.32],'recess',angle,[cx,cy,0])
for slope in [-1,1]:bar('rear_ring_brace_'+str(slope),(cx-1.75,cy-slope*1.65),(cx+1.75,cy+slope*1.65),.19,.86,'spoke')
m['elements']+=new
m['name']='高级注射器枪 AdvancedSyringeGun — 尾环修订'
m['textures'][0]['source']='data:image/png;base64,'
buf=io.BytesIO();im.save(buf,format='PNG');png=buf.getvalue()
m['textures'][0]['source']+=base64.b64encode(png).decode()
m['textures'][0].pop('path',None);m['textures'][0].pop('relative_path',None)
(ROOT/'advanced_syringe_gun_tex.png').write_bytes(png)
(REF/'authored_input.bbmodel').write_text(json.dumps(m,ensure_ascii=False,indent=2),encoding='utf8')
assert len(m['elements'])<=90
old_by_id={e['uuid']:e for e in old['elements']}
preserved=[e for e in m['elements'] if e['uuid'] in old_by_id]
assert all(e==old_by_id[e['uuid']] for e in preserved)
oldim=Image.open(REF/'baseline_advanced_syringe_gun_tex.png').convert('RGBA')
assert all(im.getpixel((x,y))==oldim.getpixel((x,y)) for y in range(64) for x in range(64) if oldim.getpixel((x,y))[3])
manifest={'baseline_cubes':len(old['elements']),'final_cubes':len(m['elements']),'removed':len(removed),'added':len(new),
 'preserved_elements_exact':len(preserved),'preserved_prior_opaque_texels':True,'retained_breech_to_y':next(e for e in preserved if e['name']=='breech_core')['to'][1],
 'ring_center':[cx,cy,0],'radius_to_rim_centerline':[rx,ry],'rim_width':.55,'rim_depth':1.12,
 'max_cubes':90,'max_faces':540,'texture_size':[64,64],'patches':len(patches),
 'interpretation':'Broad black circular silhouette with recessed interior, inferred depth; no claim about original function.'}
(REF/'revision_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
(REF/'palette.json').write_text(json.dumps(palette,indent=2),encoding='utf8')
print(json.dumps(manifest))
