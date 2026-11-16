"""Detailed hard-pixel materials over the native, independent 32x UV atlas."""
import json,base64,io,hashlib,random,math
from PIL import Image,ImageDraw
from bb import ROOT,run

P={'shell':'#aaa99a','shell_hi':'#c2c0af','shell_mid':'#b5b3a3','shell_low':'#9b9e91','shell_edge':'#969b8e',
   'deck':'#bab9a8','deck_hi':'#d0cdbb','deck_low':'#aaa99a','paint_chip':'#d2cbb5',
   'seam':'#606f66','rust':'#827361','rust_light':'#9a8a70','screw':'#59665e',
   'metal':'#66756d','metal_hi':'#94a095','metal_low':'#4d5e56','metal_mid':'#738076',
   'panel':'#899083','rear':'#959c8e','gasket':'#2b3c36','rubber':'#283932','rubber_hi':'#405148','rubber_low':'#202e2a',
   'blackpod':'#333f39','blackpod_hi':'#4c5850','blackpod_low':'#24322d',
   'headband':'#303b33','earcup':'#58665b','earpad':'#26342f','foam_hi':'#3c4b42',
   'wire_faded':'#555d4e','headphone_wire':'#313b32','clamp':'#9a967b',
   'glass':'#142b26','glass_edge':'#213c32','grid':'#284c3c','green_dim':'#486f4d','green':'#8cac75','green_hi':'#b5c994',
   'amber':'#b7a77b','amber_hi':'#d5c598','amber_low':'#8d815f',
   'keys':'#899282','keyface':'#d0cbb5','keyface_alt':'#bdbca6','keyedge':'#a5aa96','keyshadow':'#586a5d',
   'center_panel':'#42544a','switch':'#a6aa91','switch_red':'#9a7664','badge':'#4e5e50'}

FONT={'0':['111','101','101','101','111'],'1':['010','110','010','010','111'],'2':['110','001','111','100','111'],'3':['110','001','110','001','110'],'4':['101','101','111','001','001'],'5':['111','100','110','001','110'],'6':['011','100','111','101','111'],'7':['111','001','010','010','010'],'8':['111','101','111','101','111'],'9':['111','101','111','001','110'],'N':['101','111','111','111','101'],'A':['010','101','111','101','101'],'V':['101','101','101','101','010'],'L':['100','100','100','100','111'],'R':['110','101','110','101','101']}

def face_image(w,h,mat,name,face):
    rim=mat.endswith('_rim');mat=mat.removesuffix('_rim')
    isglass=mat in ['sonar','aux','center_readout']
    col=P['glass'] if isglass else P.get(mat,P['shell'])
    if mat.startswith('keys_'):col=P['keys']
    im=Image.new('RGBA',(w,h),col);d=ImageDraw.Draw(im)
    rng=random.Random(int.from_bytes(hashlib.sha256((name+':'+face).encode()).digest()[:8],'big'))
    def rect(box,c):
        x,y,a,b=map(int,box)
        if a>=x and b>=y:d.rectangle((x,y,a,b),fill=P.get(c,c))
    def line(pts,c):d.line(pts,fill=P.get(c,c),width=1)
    def text(x,y,s,c):
        for ch in s:
            for yy,row in enumerate(FONT.get(ch,[])):
                for xx,p in enumerate(row):
                    if p=='1':rect((x+xx,y+yy,x+xx,y+yy),c)
            x+=4
    def bolt(x,y):
        rect((x,y,x,y),'screw')
        if w>8 and h>8:rect((x+1,y,x+1,y),'shell_hi')
    if mat in ['shell','deck','panel','rear']:
        hi='deck_hi' if mat=='deck' else 'shell_mid';lo='deck_low' if mat=='deck' else 'shell_low'
        # Fine paint changes collect beside folded edges and service seams.
        # Quiet areas have a few low-contrast brush runs, not a uniform noise field.
        if min(w,h)>=3:
            for _ in range(min(55,max(3,w*h//36))):
                x=rng.randrange(w);y=rng.randrange(h);edge=min(x,y,w-1-x,h-1-y)
                if edge>3 and rng.random()<.78:continue
                ln=rng.choice([1,2,2,3,4]);rect((x,y,min(w-1,x+ln),y),rng.choice([hi,lo]))
                if edge<2 and rng.random()<.28:rect((x,min(h-1,y+1),min(w-1,x+1),min(h-1,y+1)),'shell_edge')
            # Short folded lip accents, never one bright perimeter frame.
            line([(1,0),(max(1,w//3),0)],hi)
            line([(max(0,w*2//3),h-1),(w-2,h-1)],lo)
        if min(w,h)>8:
            for x,y in [(2,2),(w-3,2),(2,h-3),(w-3,h-3)]:bolt(x,y)
        if name=='main_chassis' and face in ['east','west']:
            for y in range(6,h-6,3):
                rect((2,y,w-3,y),'seam');rect((2,y+1,w-4,y+1),'shell_low')
            for y in [3,h-5]:bolt(w//2,y)
            rect((1,h-4,2,h-3),'rust');rect((2,h-3,3,h-3),'rust_light')
        if name.startswith('rounded_header') and name.endswith('_core') and face=='south':
            for x in range(2,min(w-4,25),2):rect((x,2,x,h-3),'seam')
            line([(34,0),(34,h-2),(w-5,h-2)],'shell_low')
            for x,y in [(29,2),(53,h-2),(w-5,1)]:rect((x,y,x+1,y),'paint_chip')
            rect((28,3,29,3),'rust')
        if name in ['left_bezel','right_bezel','screen_divider'] and face=='south':
            for y in [2,h//2,h-4]:
                rect((0,y,max(0,w-2),y),'shell_low')
            if name=='left_bezel':rect((0,h-5,1,h-5),'rust');rect((0,h-4,0,h-3),'rust_light')
        if name=='control_backplate' and face=='south':
            line([(0,9),(w-1,9)],'shell_low');rect((2,h-4,5,h-3),'seam')
            for x in range(2,8,2):rect((x,h-3,x,h-3),'shell_hi')
        if mat=='rear' and face=='north':
            # Closely valued recessed lids, fine fasteners and local drip traces.
            for x0,x1 in [(1,w//2-2),(w//2+1,w-2)]:
                d.rectangle((x0,1,x1,h-2),outline=P['seam'])
                line([(x0+1,2),(x1-1,2)],'shell_mid')
                for x in [x0+2,x1-2]:
                    bolt(x,4);bolt(x,h-5);rect((x,5,x,7),'shell_edge')
            for x in range(w-17,w-4,2):line([(x,6),(x,14)],'seam')
            rect((w//2-12,h-8,w//2-3,h-5),'metal_low')
            for x in range(w//2-11,w//2-3,2):rect((x,h-7,x,h-7),'deck_hi')
        if mat=='deck' and face=='up':
            for x in [2,w//2-6,w//2+6,w-3]:bolt(x,2)
            for x in [7,10,18,w-12,w-8]:
                rect((x,h-3,x+2,h-3),'paint_chip');rect((x+1,h-2,x+2,h-2),'deck_low')
        if name=='white_front_fascia' and face=='south':
            for x in [3,18,w-6]:bolt(x,1)
            rect((2,h-2,5,h-2),'rust_light')
    elif mat in ['metal','rubber','gasket','blackpod','headband','earcup','earpad','wire_faded','headphone_wire','clamp']:
        if mat in ['metal','earcup']:
            if min(w,h)>2:
                line([(0,1),(max(0,w//2),1)],'metal_mid');line([(max(0,w//2),h-2),(w-1,h-2)],'metal_low')
            for _ in range(min(6,max(1,w*h//35))):
                x=rng.randrange(w);y=rng.randrange(h);rect((x,y,min(w-1,x+2),y),'metal_mid')
            if 'wall_rail' in name and face=='north':
                for y in [3,h-4]:bolt(w//2,y)
            if mat=='earcup' and face=='south' and min(w,h)>3:
                rect((1,1,w-2,1),'metal_hi');rect((1,h-2,w-2,h-2),'metal_low')
        elif mat=='blackpod':
            if face=='up':
                for y in range(2,h-2,3):rect((1,y,w-2,y),'blackpod_low');rect((1,y+1,w-2,y+1),'blackpod_hi')
            elif min(w,h)>3:
                line([(1,1),(w-2,1)],'blackpod_hi');bolt(1,h-2)
            if face=='south' and name.endswith('_core'):
                rect((1,1,w-2,1),'metal_low');rect((2,h-2,min(w-2,3),h-2),'rubber_hi')
        elif mat=='earpad':
            if min(w,h)>2:
                line([(1,0),(w-2,0)],'foam_hi');line([(1,h-1),(w-2,h-1)],'rubber_low')
                for y in range(2,h-1,2):rect((0,y,0,y),'foam_hi')
        else:
            if w>2:line([(0,0),(0,h-1)],'rubber_hi')
            elif h>3:rect((0,1,w-1,1),'rubber_hi')
            if 'loose_signal' in name and min(w,h)>1:rect((0,h//2,w-1,h//2),'clamp')
    elif isglass:
        if (face=='south' and not rim) or (mat=='center_readout' and face=='up'):
            if mat=='sonar':
                cx,cy=16,h//2+1;r=min(cy-2,h-cy-2,11)
                for rad,c in [(r,'green_dim'),(r*2//3,'grid'),(r//3,'grid')]:d.ellipse((cx-rad,cy-rad,cx+rad,cy+rad),outline=P[c])
                line([(cx-r-1,cy),(cx+r+1,cy)],'grid');line([(cx,cy-r-1),(cx,cy+r+1)],'grid')
                for yy in range(cy-r+2,cy+r,3):rect((cx-r,yy,cx-r,yy),'green_dim')
                line([(cx,cy),(cx+7,cy-7)],'green_dim')
                rect((cx-3,cy,cx+3,cy+1),'green');rect((cx-1,cy-1,cx+1,cy-1),'green')
                for x,y in [(cx-5,cy-7),(cx+7,cy+4),(cx+3,cy-9)]:rect((x,y,x+1,y),'green_hi')
                x=w-13;text(x,2,'NAV','green_dim');text(x,10,'350','green_dim')
                line([(x,18),(w-3,18)],'grid')
                for i in range(6):rect((x+i*2,21,x+i*2,21+(i%3)),'green_dim')
                rect((2,2,7,2),'green_dim')
            elif mat=='aux':
                for y,l in [(1,w-4),(3,w-7)]:rect((1,y,l,y),'green_dim')
                line([(1,h-3),(3,h-3),(5,h-5),(7,h-4),(9,h-7),(w-2,h-7)],'green')
            else:
                rect((1,1,w-2,1),'green');rect((2,h-1,w-3,h-1),'green_dim')
    elif mat in ['meter','vents']:
        rect((0,0,w-1,h-1),'gasket' if mat=='meter' else 'panel')
        if face=='south':
            if mat=='meter':
                rect((1,1,w-2,h-2),'amber');rect((1,1,w-2,1),'amber_hi')
                for x in range(2,w-1,2):rect((x,h-2,x,h-2),'amber_low')
                line([(w//2,h-2),(w//2+1,1)],'seam')
            else:
                for x in range(1,w-1,2):rect((x,1,x,h-2),'seam')
    elif mat.startswith('keys_'):
        if face=='up':
            for y in range(0,h-1,3):
                for x in range(0,w-1,3):
                    rect((x,y,min(w-1,x+1),y+1),'keyface_alt' if (x+y)%5==0 else 'keyface')
                    rect((x,y+2,min(w-1,x+1),y+2),'keyedge')
            if mat=='keys_right':
                rect((w-3,h-5,w-1,h-2),'keyface_alt');rect((0,h-2,4,h-1),'keyface')
    elif mat in ['center_panel','switch','switch_red','keycap','badge']:
        if mat=='center_panel' and face=='up':
            rect((0,0,w-1,0),'metal_mid');rect((1,h-3,w-2,h-3),'metal_low')
            for x in [1,w-2]:bolt(x,1)
            rect((1,4,1,4),'green');rect((3,4,3,4),'amber')
        if mat in ['switch','switch_red','keycap']:
            rect((0,h-1,w-1,h-1),'keyshadow')
            if face=='up' and mat=='keycap':rect((1,0,w-2,0),'deck_hi')
        if mat=='badge' and face=='south':
            rect((1,0,w-2,0),'amber_low')
            for x in range(2,w-2,2):rect((x,1,x,1),'deck_hi')
    return im

def main():
    model=json.loads((ROOT/'reference/native_uv_template.bbmodel').read_text(encoding='utf8'))
    materials=json.loads((ROOT/'reference/materials.json').read_text())
    t=model['textures'][0];raw=base64.b64decode(t['source'].split(',')[1]);size=Image.open(io.BytesIO(raw)).size
    atlas=Image.new('RGBA',size,(0,0,0,0));sx=size[0]/model['resolution']['width'];sy=size[1]/model['resolution']['height'];layout=[]
    for e in model['elements']:
        for f,fd in e['faces'].items():
            u,v,u2,v2=fd['uv'];a,b,c,d=[round(u*sx),round(v*sy),round(u2*sx),round(v2*sy)];w,h=abs(c-a),abs(d-b)
            if not w or not h:continue
            tile=face_image(w,h,materials[e['name']],e['name'],f)
            if c<a:tile=tile.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            if d<b:tile=tile.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
            atlas.paste(tile,(min(a,c),min(b,d)));layout.append([e['name'],f,[a,b,c,d]])
    atlas.save(ROOT/'navigation_terminal_tex.png')
    (ROOT/'reference/palette.json').write_text(json.dumps(P,indent=2));(ROOT/'reference/uv_pixel_layout.json').write_text(json.dumps(layout,indent=2))
    data=json.dumps('data:image/png;base64,'+base64.b64encode((ROOT/'navigation_terminal_tex.png').read_bytes()).decode()).replace(chr(47),chr(92)+'u002f')
    pid=(ROOT/'reference/project_id.txt').read_text()
    code='''(async()=>{if(Project.uuid!==PID)throw Error("Project changed");const t=Texture.all[0];Undo.initEdit({textures:[t],bitmap:true});t.fromDataURL(DATA);await new Promise(r=>setTimeout(r,150));Undo.finishEdit("Paint detailed terminal revision");unselectAllElements();Canvas.updateAll();return {width:t.width,height:t.height,cubes:Cube.all.length}})()'''.replace('PID',json.dumps(pid)).replace('DATA',data)
    print('paint',run(code),'faces',len(layout))
if __name__=='__main__':main()
