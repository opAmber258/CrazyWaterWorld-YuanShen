from PIL import Image, ImageDraw
from revise_user_saved_v2_specs import C, ATLAS_SIZE

def clamp_box(w,h,x1,y1,x2,y2):
    return (max(0,min(w-1,x1)),max(0,min(h-1,y1)),max(0,min(w-1,x2)),max(0,min(h-1,y2)))

def rect(d,w,h,x1,y1,x2,y2,color):
    b=clamp_box(w,h,x1,y1,x2,y2)
    if b[0]<=b[2] and b[1]<=b[3]: d.rectangle(b,fill=color)

def line(d,w,h,x1,y1,x2,y2,color):
    d.line((max(0,min(w-1,x1)),max(0,min(h-1,y1)),max(0,min(w-1,x2)),max(0,min(h-1,y2))),fill=color)

def border(d,w,h,light,dark):
    line(d,w,h,0,0,w-1,0,light); line(d,w,h,0,0,0,h-1,light)
    line(d,w,h,0,h-1,w-1,h-1,dark); line(d,w,h,w-1,0,w-1,h-1,dark)

def rivet(d,w,h,x,y):
    if 1<=x<w-1 and 1<=y<h-1:
        d.point((x,y),fill=C['case_high'])
        if y+1<h-1: d.point((x,y+1),fill=C['steel_dark'])

def wear(d,w,h,x,y,n=3,rust_first=False):
    colors=(C['rust'],C['rust_dark'],C['case_dark']) if rust_first else (C['case_dark'],C['rust_dark'],C['rust'])
    for i in range(n):
        xx=x+i; yy=y if i%3!=2 else y+1
        if 0<=xx<w and 0<=yy<h: d.point((xx,yy),fill=colors[i%len(colors)])

def grime(d,w,h,x,top,n,width=1):
    for i in range(n):
        yy=top+i
        if yy>=h: break
        xx=x+(1 if i>n//2 and width>1 else 0)
        if 0<=xx<w: d.point((xx,yy),fill=C['soot'] if i%5 else C['oil'])

def shell_panel(d,w,h,name,face,variant='mid'):
    base=C['case_mid'] if variant=='mid' else (C['case_shadow'] if variant=='side' else C['case_mid2'])
    d.rectangle((0,0,w-1,h-1),fill=base)
    if w<4 or h<4: border(d,w,h,C['case_light'],C['seam']); return
    border(d,w,h,C['case_light'],C['seam'])
    cx=2 if w<36 else 3; cy=2 if h<24 else 3
    cols=(base,C['case_mid2'],C['case_shadow'],base)
    for iy in range(cy):
        for ix in range(cx):
            x1=2+ix*(w-4)//cx; x2=2+(ix+1)*(w-4)//cx-2
            y1=2+iy*(h-4)//cy; y2=2+(iy+1)*(h-4)//cy-2
            if x2>=x1 and y2>=y1: rect(d,w,h,x1,y1,x2,y2,cols[(ix+iy*2)%len(cols)])
    for ix in range(1,cx):
        x=1+ix*(w-2)//cx; line(d,w,h,x,2,x,h-3,C['seam']); line(d,w,h,x+1,2,x+1,h-3,C['case_light'])
    for iy in range(1,cy):
        y=1+iy*(h-2)//cy; line(d,w,h,2,y,w-3,y,C['seam']); line(d,w,h,2,y+1,w-3,y+1,C['case_light'])
    rect(d,w,h,2,2,w-3,min(5,h-4),C['case_shadow']); line(d,w,h,2,2,w-3,2,C['case_high'])
    line(d,w,h,2,min(6,h-3),w-3,min(6,h-3),C['seam'])
    rect(d,w,h,2,max(2,h-7),w-3,h-3,C['case_dark']); line(d,w,h,2,max(2,h-8),w-3,max(2,h-8),C['case_shadow'])
    for x,y in ((4,4),(w-6,4),(4,h-6),(w-6,h-6)): rivet(d,w,h,x,y)
    for x in (w//3,(w*2)//3,max(4,w//2)): grime(d,w,h,x,6,max(3,h//3),1+(x%2))
    for x in (3,max(4,w//2-2),w-8): wear(d,w,h,x,h-4,min(5,max(2,w-x)),True)
    for x in range(8,max(9,w-8),max(16,w//4)): line(d,w,h,x,h-10,min(w-3,x+max(4,w//8)),h-10,C['steel_dark'])

def roof_panel(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['case_mid2'])
    if w<4 or h<4: border(d,w,h,C['case_light'],C['seam']); return
    border(d,w,h,C['case_light'],C['seam']); rect(d,w,h,3,3,w-4,h-4,C['case_shadow'])
    mx=w//2; my=h//2
    line(d,w,h,mx,2,mx,h-3,C['seam']); line(d,w,h,mx+1,2,mx+1,h-3,C['case_light'])
    line(d,w,h,2,my,w-3,my,C['seam']); line(d,w,h,2,my+1,w-3,my+1,C['case_light'])
    rect(d,w,h,4,4,max(5,mx-3),max(5,my-3),C['case_mid']); rect(d,w,h,mx+3,4,w-5,max(5,my-3),C['case_mid'])
    for x,y in ((4,4),(w-6,4),(4,h-6),(w-6,h-6)): rivet(d,w,h,x,y)
    for x in (mx-5,mx+5): grime(d,w,h,x,5,max(3,h//4),1)
    wear(d,w,h,3,h-4,min(5,w-3),True); wear(d,w,h,max(3,w-8),h-4,min(5,8),True)

def service_frame(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['steel']); border(d,w,h,C['steel_light'],C['seam'])
    if w>=4 and h>=4:
        rect(d,w,h,2,2,w-3,h-3,C['steel_dark']); line(d,w,h,2,2,w-3,2,C['steel_light'])
        for x in range(3,max(4,w-3),12): line(d,w,h,x,2,x,h-3,C['steel'])
        for x,y in ((2,2),(w-4,2),(2,h-4),(w-4,h-4)): rivet(d,w,h,x,y)
        wear(d,w,h,2,h-3,min(4,w-2),True)

def service_door(d,w,h,name,face,right=False):
    d.rectangle((0,0,w-1,h-1),fill=C['case_shadow']); border(d,w,h,C['steel_light'],C['seam'])
    if w<5 or h<5: return
    rect(d,w,h,2,2,w-3,h-3,C['case_dark']); rect(d,w,h,4,4,w-5,h-5,C['case_mid2'])
    line(d,w,h,4,2,4,h-3,C['steel']); line(d,w,h,w-3,2,w-3,h-3,C['seam'])
    line(d,w,h,4,3,w-5,3,C['steel_light']); line(d,w,h,4,h-4,w-5,h-4,C['seam'])
    for y in range(6,max(7,h-8),max(10,h//5)): line(d,w,h,6,y,w-7,y,C['case_shadow']); line(d,w,h,6,y+1,w-7,y+1,C['case_light'])
    hx=w-9 if right else 7; rect(d,w,h,hx,h//2-3,hx+2,h//2+3,C['steel_dark']); line(d,w,h,hx,h//2-3,hx+2,h//2-3,C['steel_light'])
    for x,y in ((3,3),(w-5,3),(3,h-5),(w-5,h-5)): rivet(d,w,h,x,y)
    grime(d,w,h,max(5,w//3),h//3,max(4,h//3),2); wear(d,w,h,4,h-5,min(6,w-4),True); wear(d,w,h,max(3,w-9),h-5,min(5,9),True)

def black_panel(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['black_mid']); border(d,w,h,C['black_frame'],C['seam'])
    if w<4 or h<4: return
    rect(d,w,h,2,2,w-3,h-3,C['black']); line(d,w,h,2,2,w-3,2,C['black_frame']); line(d,w,h,2,h-3,w-3,h-3,C['seam'])
    for x in range(5,max(6,w-3),max(12,w//4)): line(d,w,h,x,3,x,h-4,C['black_frame'])
    for y in range(6,max(7,h-4),max(10,h//4)): line(d,w,h,3,y,w-4,y,C['steel_dark'])
    for x in (5,w-6): wear(d,w,h,x,h-5,4,True)
    grime(d,w,h,w//2,max(3,h//4),max(4,h//3),2)

def black_roof_panel(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['black_mid']); border(d,w,h,C['black_bar'],C['seam'])
    if w<4 or h<4: return
    rect(d,w,h,3,3,w-4,h-4,C['black'])
    for x in range(8,max(9,w-4),max(16,w//3)): line(d,w,h,x,3,x,h-4,C['black_frame']); line(d,w,h,x+1,3,x+1,h-4,C['black_bar'])
    y=h//2; line(d,w,h,3,y,w-4,y,C['black_frame']); line(d,w,h,3,y+1,w-4,y+1,C['black_bar'])
    for x,y in ((4,4),(w-6,4),(4,h-6),(w-6,h-6)): rivet(d,w,h,x,y)
    for x in (w//3,(w*2)//3): grime(d,w,h,x,4,max(3,h//4),1)
    wear(d,w,h,4,h-5,min(5,w-4),True)

def black_vent_bay(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['black']); border(d,w,h,C['black_frame'],C['seam'])
    if w<5 or h<5: return
    rect(d,w,h,3,3,w-4,h-4,C['black_mid']); line(d,w,h,3,3,w-4,3,C['black_bar']); line(d,w,h,3,h-4,w-4,h-4,C['seam'])
    line(d,w,h,w//2,3,w//2,h-4,C['black_frame'])
    for x in (4,w-5): grime(d,w,h,x,4,max(4,h//3),1)
    for x in (5,max(6,w//2+4),w-7): wear(d,w,h,x,h-5,4,True)

def black_grille(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['black']); border(d,w,h,C['black_frame'],C['black_frame'])
    if w<4 or h<4: return
    step=max(3,min(6,h//4))
    for y in range(2,h-2,step): line(d,w,h,2,y,w-3,y,C['black_bar']); line(d,w,h,2,min(h-2,y+1),w-3,min(h-2,y+1),C['seam'])
    line(d,w,h,w//2,2,w//2,h-3,C['black_frame'])
    rect(d,w,h,2,max(2,h-4),w-3,h-3,C['dust']); line(d,w,h,2,max(2,h-5),w-3,max(2,h-5),C['soot'])

def black_door(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['black']); border(d,w,h,C['black_bar'],C['black_frame'])
    if w<5 or h<5: return
    rect(d,w,h,2,2,w-3,h-3,C['black_mid']); rect(d,w,h,4,4,w-5,h-5,C['black'])
    line(d,w,h,4,3,w-5,3,C['black_bar']); line(d,w,h,4,h-4,w-5,h-4,C['black_frame'])
    for y in range(max(6,h//5),max(7,h-6),max(10,h//4)): line(d,w,h,6,y,w-7,y,C['black_frame']); line(d,w,h,6,y+1,w-7,y+1,C['steel_dark'])
    hx=w-9; rect(d,w,h,hx,h//2-3,hx+2,h//2+3,C['steel_dark']); line(d,w,h,hx,h//2-3,hx+2,h//2-3,C['steel_light'])
    for x,y in ((3,3),(w-5,3),(3,h-5),(w-5,h-5)): rivet(d,w,h,x,y)
    grime(d,w,h,w//3,h//3,max(4,h//3),2); wear(d,w,h,4,h-5,min(5,w-4),True)

def red_panel(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['red'])
    if w<3 or h<3: border(d,w,h,C['red_light'],C['red_dark']); return
    border(d,w,h,C['red_light'],C['red_dark']); rect(d,w,h,1,2,w-2,min(4,h-2),C['red_light']); rect(d,w,h,2,max(2,h-5),w-3,h-3,C['red_dark'])
    for x in range(max(5,w//5),max(6,w-3),max(10,w//5)): line(d,w,h,x,2,x,h-2,C['red_dark']); line(d,w,h,x+1,2,x+1,h-2,C['red_light'] if x%2 else C['red'])
    for y in (h//3,(h*2)//3): line(d,w,h,2,y,w-3,y,C['rust_dark'])
    for x in (2,max(3,w//2),w-7): wear(d,w,h,x,h-4,min(5,max(2,w-x)),True)
    for x in (5,max(6,w//2+5),w-9): line(d,w,h,x,h-7,min(w-3,x+4),h-7,C['rust'])

def vent(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['vent_dark']); border(d,w,h,C['panel_edge'],C['panel_edge'])
    if w<6 or h<6: return
    if h>=24:
        rect(d,w,h,3,3,w-4,min(8,h-4),C['panel']); mid=w//2; rect(d,w,h,4,4,max(4,mid-2),7,C['window']); rect(d,w,h,mid+1,4,w-5,7,C['window']); line(d,w,h,mid,4,mid,7,C['seam']); top=11
    else: top=5
    rect(d,w,h,2,top,w-3,h-3,C['vent_mid'])
    for y in range(top+2,max(top+3,h-3),max(4,h//8)): line(d,w,h,3,y,w-4,y,C['vent_high']); line(d,w,h,3,min(h-3,y+1),w-4,min(h-3,y+1),C['seam'])
    line(d,w,h,w//2,top+1,w//2,h-4,C['seam'])
    for x in (max(3,w//4),max(4,(w*3)//4)): grime(d,w,h,x,top+2,max(3,(h-top)//3),1)
    wear(d,w,h,max(3,w-7),h-4,4,True)

def vent_edge(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['case_dark']); border(d,w,h,C['steel_dark'],C['seam'])
    if h>8: line(d,w,h,1,3,w-2,3,C['seam']); line(d,w,h,1,4,w-2,4,C['steel_dark'])

def control(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['panel']); border(d,w,h,C['steel_light'],C['seam'])
    if w<8 or h<8: return
    colors=(C['green'],C['teal'],C['green']); bw=max(2,(w-8)//3-1)
    for i,color in enumerate(colors):
        x=3+i*(bw+2); rect(d,w,h,x,4,min(w-4,x+bw),6,color)
    size=3; gap=4
    for row in range(3):
        for col in range(3):
            x=4+col*(size+gap); y=10+row*(size+gap)
            if x+size<w-3 and y+size<h-9:
                rect(d,w,h,x,y,x+size,y+size,C['key']); line(d,w,h,x,y,x,y+size,C['white']); line(d,w,h,x+size,y,x+size,y+size,C['key_dark'])
    line(d,w,h,2,max(8,h//2),w-3,max(8,h//2),C['panel_edge']); rect(d,w,h,3,h-8,w-4,h-6,C['panel_edge'])
    for i,color in enumerate((C['rust'],C['amber'],C['steel_light'])):
        x=3+i*3
        if x+1<w-2: rect(d,w,h,x,h-6,x+1,h-5,color)
    line(d,w,h,4,h-11,w-5,h-11,C['soot']); grime(d,w,h,max(4,w//3),h//2,max(3,h//4),1)

def steel_panel(d,w,h,name,face,light=False,dark=False):
    fill=C['steel_light'] if light else (C['steel_dark'] if dark else C['steel'])
    d.rectangle((0,0,w-1,h-1),fill=fill); border(d,w,h,C['steel_light'],C['seam'])
    if w>3 and h>3:
        if w>=h:
            line(d,w,h,2,1,w-3,1,C['case_high']); line(d,w,h,2,h-2,w-3,h-2,C['steel_dark'])
        else:
            line(d,w,h,1,2,1,h-3,C['case_high']); line(d,w,h,w-2,2,w-2,h-3,C['steel_dark'])
        wear(d,w,h,max(3,w//3),max(3,h-4),min(4,max(2,w-3)),True)

def rail_panel(d,w,h,name,face):
    steel_panel(d,w,h,name,face)
    if w>=h and w>10:
        line(d,w,h,2,max(2,h//2),w-3,max(2,h//2),C['steel_dark']); line(d,w,h,2,min(h-3,h//2+1),w-3,min(h-3,h//2+1),C['steel_light'])
    elif h>10: line(d,w,h,max(2,w//2),2,max(2,w//2),h-3,C['steel_dark'])

def fan_metal(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['steel_dark']); border(d,w,h,C['steel'],C['seam'])
    if w>=4 and h>=4:
        line(d,w,h,2,2,w-3,2,C['steel_light'])
        for x in range(4,max(5,w-3),max(8,w//4)): line(d,w,h,x,3,x,h-4,C['steel'])
        wear(d,w,h,max(3,w-7),4,4,True)

def blade(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['steel_dark'])
    if w>=h:
        line(d,w,h,2,max(1,h//3),w-3,max(1,h//3),C['steel_light']); line(d,w,h,2,min(h-2,(h*2)//3),w-3,min(h-2,(h*2)//3),C['steel'])
    else:
        line(d,w,h,max(1,w//3),2,max(1,w//3),h-3,C['steel_light']); line(d,w,h,min(w-2,(w*2)//3),2,min(w-2,(w*2)//3),h-3,C['steel'])
    wear(d,w,h,max(2,w-5),max(2,h-4),3,True)

def hub(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['steel_light']); border(d,w,h,C['white'],C['steel_dark'])
    if w>=4 and h>=4:
        rect(d,w,h,w//3,h//3,(w*2)//3,(h*2)//3,C['case_dark']); rect(d,w,h,max(2,w//2-1),max(2,h//2-1),min(w-3,w//2),min(h-3,h//2),C['steel_light'])
        line(d,w,h,2,2,w-3,2,C['white']); line(d,w,h,2,h-3,w-3,h-3,C['steel_dark'])

def hatch(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['case_shadow'])
    if w>3 and h>3:
        border(d,w,h,C['steel_light'],C['seam']); rect(d,w,h,2,2,w-3,h-3,C['case_dark'])
        line(d,w,h,2,2,w-3,2,C['steel']); line(d,w,h,2,h-3,w-3,h-3,C['seam'])
        for x,y in ((3,3),(w-5,3),(3,h-5),(w-5,h-5)): rivet(d,w,h,x,y)
        rect(d,w,h,w-7,h//2-2,w-5,h//2+2,C['rust']); line(d,w,h,w-7,h//2-2,w-5,h//2-2,C['rust_dark'])
        wear(d,w,h,3,h-4,min(4,w-3),True)

def bumper(d,w,h,name,face):
    steel_panel(d,w,h,name,face)
    if w>=20:
        line(d,w,h,2,h//2,w-3,h//2,C['seam'])
        for x in (w//5,(w*4)//5): rect(d,w,h,x-4,max(2,h//2-1),x+4,min(h-3,h//2+1),C['case_dark'])

def outlet(d,w,h,name,face):
    d.rectangle((0,0,w-1,h-1),fill=C['rubber']); border(d,w,h,C['panel_edge'],C['seam'])
    if w>=6 and h>=6:
        rect(d,w,h,3,3,w-4,max(3,h//3),C['case_dark']); rect(d,w,h,3,max(4,(h*2)//3),w-4,h-4,C['case_dark'])

def draw_style(d,w,h,name,face,style):
    if style=='shell_front': shell_panel(d,w,h,name,face,'mid')
    elif style=='shell_back':
        shell_panel(d,w,h,name,face,'mid')
        if w>10 and h>10: grime(d,w,h,max(4,w//2-3),7,max(5,h//2),2)
    elif style=='shell_side': shell_panel(d,w,h,name,face,'side')
    elif style=='shell_side_recess':
        shell_panel(d,w,h,name,face,'side')
        if w>8 and h>8: rect(d,w,h,3,3,w-4,h-4,C['case_dark']); line(d,w,h,3,3,w-4,3,C['case_shadow'])
    elif style=='shell_edge_panel': shell_panel(d,w,h,name,face,'side')
    elif style=='roof_panel': roof_panel(d,w,h,name,face)
    elif style=='service_frame': service_frame(d,w,h,name,face)
    elif style in ('service_door_left','service_door_right'): service_door(d,w,h,name,face,right=style.endswith('right'))
    elif style in ('black_back','black_side'): black_panel(d,w,h,name,face)
    elif style=='black_roof': black_roof_panel(d,w,h,name,face)
    elif style=='black_vent_bay': black_vent_bay(d,w,h,name,face)
    elif style=='black_roof_panel': black_roof_panel(d,w,h,name,face)
    elif style=='black_grille': black_grille(d,w,h,name,face)
    elif style=='black_door': black_door(d,w,h,name,face)
    elif style=='black_panel': black_panel(d,w,h,name,face)
    elif style=='red': red_panel(d,w,h,name,face)
    elif style=='vent': vent(d,w,h,name,face)
    elif style=='vent_edge': vent_edge(d,w,h,name,face)
    elif style=='control': control(d,w,h,name,face)
    elif style=='hatch': hatch(d,w,h,name,face)
    elif style=='bumper': bumper(d,w,h,name,face)
    elif style=='rail': rail_panel(d,w,h,name,face)
    elif style in ('steel','trim','bevel_steel'): steel_panel(d,w,h,name,face,light=(style=='trim'))
    elif style in ('steel_dark','dark_metal','panel_edge','black_frame','black_bar','bevel_dark'): steel_panel(d,w,h,name,face,dark=(style!='black_bar'))
    elif style=='fan_metal': fan_metal(d,w,h,name,face)
    elif style=='fan_blade': blade(d,w,h,name,face)
    elif style=='hub': hub(d,w,h,name,face)
    elif style=='outlet': outlet(d,w,h,name,face)
    else: steel_panel(d,w,h,name,face)

def render_atlas(layout):
    image=Image.new('RGBA',(ATLAS_SIZE,ATLAS_SIZE),(0,0,0,0))
    for key,coords in layout['pattern_regions'].items():
        info=layout['faces'][key]; w=coords[2]-coords[0]; h=coords[3]-coords[1]
        tile=Image.new('RGBA',(w,h),(0,0,0,0)); draw=ImageDraw.Draw(tile)
        name,face=key.split('|',1); draw_style(draw,w,h,name,face,info['style'])
        image.alpha_composite(tile,(coords[0],coords[1]))
    draw=ImageDraw.Draw(image)
    for key,coords in layout['shared_solid_regions'].items():
        color=key.split('|',1)[1]
        draw.rectangle((coords[0],coords[1],coords[2]-1,coords[3]-1),fill=color)
    return image
