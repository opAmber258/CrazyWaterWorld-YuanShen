"""Paint only registered quiet face regions; preserve geometry, UVs and old marks."""
import base64,copy,hashlib,io,json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFilter
HERE=Path(__file__).resolve().parent;OUT=HERE.parent.parent
baseline=json.loads((HERE/'user_baseline.bbmodel').read_text(encoding='utf-8'))
model=copy.deepcopy(baseline);before=Image.open(HERE/'user_baseline_tex.png').convert('RGBA');after=before.copy()
sx=before.width/model['resolution']['width'];sy=before.height/model['resolution']['height']
P={'shell':(121,128,115,255),'light':(139,145,129,255),'shell_dark':(96,107,97,255),'steel':(104,118,111,255),'dark_light':(72,83,77,255),'dark':(48,60,59,255),'edge':(161,164,141,255),'brass':(162,138,96,255),'rust':(128,99,78,255)}
faces=[]
def paint(element,face,kind):
    uv=element['faces'][face]['uv'];rect=[round(min(uv[0],uv[2])*sx),round(min(uv[1],uv[3])*sy),round(max(uv[0],uv[2])*sx),round(max(uv[1],uv[3])*sy)]
    original=before.crop(rect);w,h=original.size
    colors=original.getcolors(w*h);base=max(colors)[1]
    # Preserve every existing non-base-color texel plus a one-texel guard.
    raw=original.tobytes();protected=Image.new('L',(w,h),0);protected.putdata([255 if tuple(raw[i:i+4])!=base else 0 for i in range(0,len(raw),4)]);protected=protected.filter(ImageFilter.MaxFilter(3))
    proposed=original.copy();draw=ImageDraw.Draw(proposed)
    shadow=P['shell_dark'] if sum(base[:3])>350 else P['dark_light'] if sum(base[:3])>270 else P['dark']
    highlight=P['light'] if sum(base[:3])<350 else P['edge']
    def line(x1,y1,x2,y2,color=shadow):draw.line((int(x1),int(y1),int(x2),int(y2)),fill=color,width=1)
    def box(a,b,c,d,color=shadow):draw.rectangle((int(a),int(b),int(c),int(d)),outline=color,width=1)
    def bolt(x,y):
        draw.point((int(x),int(y)),fill=highlight)
        if x+1<w:draw.point((int(x+1),int(y)),fill=shadow)
    def hatch(x1,y1,x2,y2,vents=False,handle=True):
        if x2-x1<7 or y2-y1<7:return
        box(x1,y1,x2,y2);line(x1+2,y2+1,x2-2,y2+1,highlight)
        for xx,yy in [(x1+2,y1+2),(x2-3,y1+2),(x1+2,y2-2),(x2-3,y2-2)]:bolt(xx,yy)
        if handle:
            hx=x2-5;hy=(y1+y2)//2;line(hx,hy-2,hx,hy+2,shadow);line(hx+1,hy-1,hx+1,hy+1,highlight)
        if vents:
            for yy in range(y2-10,y2-3,3):line(x1+5,yy,x2-7,yy,shadow)
    if kind=='roof':
        hatch(8,7,w//2-8,h-9,vents=True);hatch(w//2+8,7,w-8,h-9,vents=False)
        line(w//2+13,12,w//2+23,12,P['shell']);line(w//2+13,14,w//2+18,14,P['shell'])
    elif kind=='end_access':
        hatch(7,5,w-8,h-17,vents=False)
        line(12,10,24,10,shadow);line(12,12,18,12,shadow)
        # Existing lower ventilation is kept as the breathing zone.
    elif kind=='inclined_access':
        hatch(3,8,w-4,h-10,vents=False,handle=False)
        if w>=18:
            for yy in [h//2-3,h//2,h//2+3]:line(6,yy,w-7,yy,shadow)
        else:line(w//2,14,w//2,h-16,shadow)
    elif kind=='motor_backplate':
        hatch(6,7,w-7,h-8,vents=False,handle=False)
        # Quiet, broad ribs around the hidden central bearing line.
        for yy in [h-17,h-13]:line(11,yy,w-12,yy,shadow)
        line(w//2,12,w//2,20,shadow)
    elif kind=='belly_access':
        hatch(10,8,w//2-7,h-8,vents=False);hatch(w//2+7,8,w-10,h-8,vents=False)
        line(w//2,4,w//2,h-5,shadow)
    elif kind=='flare_plate':
        hatch(2,3,w-3,h-4,vents=False,handle=False)
        if w>14:line(w//2,6,w//2,h-7,shadow)
    elif kind=='rim_fasteners':
        bolt(3,5);bolt(w-4,h-6)
        line(3,h//2,w-4,h//2,shadow)
    elif kind=='shroud_lower':
        hatch(3,h//2+3,w-4,h-5,vents=False,handle=False)
        if h>30:line(6,h-10,w-7,h-10,shadow)
    elif kind=='apron':
        for xx in [w//4,w//2,w*3//4]:line(xx,2,xx,h-3,shadow)
        for xx in [8,w//4+4,w//2+4,w*3//4+4,w-10]:bolt(xx,h//2)
    elif kind=='pump_backplate':
        # Two low-key service seams; the moving pistons remain the focal point.
        line(w//2,3,w//2,h-4,shadow);bolt(3,3);bolt(w-5,3)
        line(w//2-3,h-4,w//2+3,h-4,highlight)
    changed=0;out=original.copy();px=out.load();np=proposed.load();pr=protected.load();old=original.load()
    for y in range(h):
        for x in range(w):
            if not pr[x,y] and old[x,y]==base and np[x,y]!=old[x,y]:px[x,y]=np[x,y];changed+=1
    after.paste(out,rect[:2]);faces.append({'uuid':element['uuid'],'name':element['name'],'face':face,'kind':kind,'uv':uv,'pixel_rect':rect,'base_color':base,'changed_pixels':changed,'protected_existing_detail_pixels':sum(bool(v) for v in protected.tobytes())})
byname={}
for e in model['elements']:byname.setdefault(e['name'],[]).append(e)
def named(name,face,kind):
    for e in byname[name]:paint(e,face,kind)
named('crown','up','roof')
named('rounded_end','east','end_access')
named('end_continuous_bevel','up','inclined_access')
named('left_bulkhead','west','motor_backplate')
named('lower_casing','down','belly_access')
named('shroud_side_panel','south','shroud_lower')
named('front_apron','south','apron')
named('socket_bay','south','pump_backplate')
for i in range(8):
    named(f'flared_shroud_{i}','up','flare_plate')
    named(f'mouth_rim_{i}','up','rim_fasteners')
buf=io.BytesIO();after.save(buf,format='PNG');png=buf.getvalue()
model['textures'][0]['source']='data:image/png;base64,'+base64.b64encode(png).decode()
# No geometry, UV, bone or animation keys are touched.
(OUT/'engine.bbmodel').write_text(json.dumps(model,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
(OUT/'engine_tex.png').write_bytes(png)
before_data=before.tobytes();after_data=after.tobytes();changed_pixels=sum(before_data[i:i+4]!=after_data[i:i+4] for i in range(0,len(before_data),4))
report={'model_baseline_sha256':hashlib.sha256((HERE/'user_baseline.bbmodel').read_bytes()).hexdigest(),'texture_baseline_sha256':hashlib.sha256((HERE/'user_baseline_tex.png').read_bytes()).hexdigest(),'logical_uv_size':model['resolution'],'physical_texture_size':list(after.size),'cubes':len(model['elements']),'bones':len(model['groups']),'all_geometry_uv_groups_animations_unchanged':all(model[k]==baseline[k] for k in baseline if k!='textures'),'targets':faces,'modified_faces':sum(f['changed_pixels']>0 for f in faces),'changed_pixels':changed_pixels,'original_non_base_pixels_preserved':True}
(HERE/'texture_detail_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
print(json.dumps({k:v for k,v in report.items() if k!='targets'},ensure_ascii=False))
