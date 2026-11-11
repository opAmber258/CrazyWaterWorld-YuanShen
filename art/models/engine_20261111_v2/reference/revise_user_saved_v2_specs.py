from pathlib import Path
import math

FACE_KEYS = ('north','east','south','west','up','down')
ATLAS_SIZE = 512
ATLAS_GAP = 2

C = {
 'case_dark':'#343a36','case_mid':'#60665d','case_mid2':'#565c54','case_shadow':'#474d47',
 'case_light':'#777f74','case_high':'#91988b','seam':'#252b28','steel':'#555d58',
 'steel_dark':'#343c39','steel_light':'#8a9184','vent_dark':'#232825','vent_mid':'#4d554e',
 'vent_high':'#879084','panel':'#161c1a','panel_edge':'#2e3632','key':'#a3a596',
 'key_dark':'#484e47','green':'#8cad8b','teal':'#7fa9a4','window':'#5f7d7e',
 'amber':'#ca9860','red':'#6d302c','red_light':'#93463b','red_dark':'#432624',
 'rust':'#7b4c37','rust_dark':'#56382d','rubber':'#202421','label':'#b89f64',
 'white':'#c3c2ad','black':'#101514','black_mid':'#171c1a','black_frame':'#2b322e',
 'black_bar':'#424943','soot':'#272c28','oil':'#1b211f','dust':'#68604e'
}

CUBE_SPECS=[]
def add(name,group,frm,to,rotation=(0,0,0),origin=None):
    if origin is None: origin=[round((frm[i]+to[i])/2,3) for i in range(3)]
    CUBE_SPECS.append({'name':name,'group':group,'from':[round(float(v),3) for v in frm],'to':[round(float(v),3) for v in to],'rotation':[float(v) for v in rotation],'origin':[round(float(v),3) for v in origin]})

add('bevel_housing_rear_top','housing',[-11.8,26.793,-19.793],[31.0,27.793,-18.793],[45,0,0])
add('bevel_housing_rear_left','housing',[-11.593,4.0,-19.793],[-10.593,28.0,-18.793],[0,45,0])
add('bevel_housing_rear_right','housing',[29.793,4.0,-19.793],[30.793,28.0,-18.793],[0,45,0])
add('bevel_housing_roof_left','housing',[-12.593,26.793,-18.0],[-11.593,27.793,18.0],[0,0,45])
add('bevel_housing_roof_right','housing',[29.793,26.793,-18.0],[30.793,27.793,18.0],[0,0,45])
add('bevel_black_duct_rear_top','black_duct',[-32.0,25.793,-17.793],[-12.8,26.793,-16.793],[45,0,0])
add('bevel_black_duct_rear_left','black_duct',[-31.793,1.0,-17.793],[-30.793,27.0,-16.793],[0,45,0])
add('bevel_black_duct_rear_right','black_duct',[-14.007,1.0,-17.793],[-13.007,27.0,-16.793],[0,45,0])
add('bevel_black_duct_roof_left','black_duct',[-31.793,25.793,-18.0],[-30.793,26.793,16.0],[0,0,45])
add('bevel_black_duct_roof_right','black_duct',[-14.007,25.793,-18.0],[-13.007,26.793,16.0],[0,0,45])
add('bevel_black_duct_roof_front','black_duct',[-32.0,25.793,14.793],[-12.8,26.793,15.793],[45,0,0])
add('rear_service_frame_top','rear_rotor',[0.0,25.0,-21.05],[21.2,26.4,-20.0])
add('rear_service_frame_bottom','rear_rotor',[0.0,6.2,-21.05],[21.2,7.6,-20.0])
add('rear_service_frame_left','rear_rotor',[0.0,7.6,-21.05],[1.4,25.0,-20.0])
add('rear_service_frame_right','rear_rotor',[19.8,7.6,-21.05],[21.2,25.0,-20.0])
add('rear_service_door_left','rear_rotor',[1.4,7.6,-20.65],[9.9,25.0,-19.95])
add('rear_service_door_right','rear_rotor',[10.3,7.6,-20.65],[19.8,25.0,-19.95])
add('rear_service_center_strip','rear_rotor',[9.9,7.6,-20.75],[10.3,25.0,-19.9])
add('rear_service_handle_left','rear_rotor',[9.0,14.8,-20.95],[9.8,17.0,-20.45])
add('rear_service_handle_right','rear_rotor',[10.4,14.8,-20.95],[11.2,17.0,-20.45])
add('side_right_backplate','housing',[31.0,6.0,-17.0],[31.45,26.2,-5.2])
add('side_right_frame_top','housing',[31.45,26.2,-17.0],[32.05,27.4,14.0])
add('side_right_frame_bottom','housing',[31.45,4.8,-17.0],[32.05,6.0,14.0])
add('side_right_frame_rear','housing',[31.45,6.0,-17.0],[32.05,26.2,-15.6])
add('side_right_service_door','housing',[31.45,7.5,-14.8],[31.9,15.2,-6.8])
add('side_right_handle','housing',[31.9,10.8,-8.0],[32.15,12.8,-7.2])
add('side_right_rear_vent','housing',[31.45,17.0,-15.4],[31.9,24.6,-7.2])
add('side_left_front_cover','housing',[-13.25,6.4,16.2],[-12.8,24.6,17.9])
add('side_left_front_trim','housing',[-13.4,26.0,16.0],[-12.8,27.2,18.0])
add('black_duct_rear_vent_bay','black_duct',[-29.8,17.2,-19.0],[-15.0,24.8,-18.0])
add('black_duct_rear_slat_0','black_duct',[-29.3,18.0,-19.25],[-15.5,18.55,-18.7])
add('black_duct_rear_slat_1','black_duct',[-29.3,19.6,-19.25],[-15.5,20.15,-18.7])
add('black_duct_rear_slat_2','black_duct',[-29.3,21.2,-19.25],[-15.5,21.75,-18.7])
add('black_duct_rear_slat_3','black_duct',[-29.3,22.8,-19.25],[-15.5,23.35,-18.7])
add('black_duct_rear_service_door','black_duct',[-29.8,4.2,-19.0],[-15.0,15.8,-18.0])
add('black_duct_rear_handle','black_duct',[-17.6,9.2,-19.5],[-15.8,11.2,-18.9])
add('black_duct_side_service_door','black_duct',[-32.35,5.8,-10.2],[-32.0,15.6,-0.4])
add('black_duct_side_vent','black_duct',[-32.3,17.0,-10.8],[-32.0,24.4,0.8])
add('black_duct_side_handle','black_duct',[-32.6,9.0,-2.4],[-32.35,11.0,-1.6])
add('black_duct_roof_left_panel','black_duct',[-30.7,27.0,-16.8],[-23.8,27.55,14.8])
add('black_duct_roof_right_panel','black_duct',[-21.0,27.0,-16.8],[-14.1,27.55,14.8])
add('black_duct_roof_spine','black_duct',[-23.8,27.0,-17.3],[-21.0,27.9,15.5])
add('black_duct_roof_rib_front','black_duct',[-30.7,27.55,12.8],[-14.1,28.0,14.8])
add('black_duct_roof_rib_rear','black_duct',[-30.7,27.55,-16.8],[-14.1,28.0,-15.0])
REVISION_NAMES={s['name'] for s in CUBE_SPECS}

HERO_FACES=set()
for name in ('main_shell','upper_roof','rear_backplate','black_duct_shell'):
    for face in FACE_KEYS:
        if face!='down': HERO_FACES.add((name,face))
for name in ('black_duct_side_service_door','black_duct_side_vent','rear_service_door_left','rear_service_door_right','side_right_backplate','side_right_service_door','black_duct_rear_vent_bay','black_duct_rear_service_door','black_duct_roof_left_panel','black_duct_roof_right_panel'):
    for face in ('north','east','south','west','up'): HERO_FACES.add((name,face))
SOLID_ONLY_STYLES={'hidden','rubber','black_hidden'}

def exact_style(name,face):
    if name=='main_shell': return {'north':'shell_back','east':'shell_side','west':'shell_side','south':'shell_front','up':'roof_panel','down':'hidden'}[face]
    if name=='upper_roof': return {'north':'roof_panel','south':'roof_panel','east':'rail','west':'rail','up':'roof_panel','down':'hidden'}[face]
    if name=='front_bumper': return {'south':'bumper','down':'rubber','north':'steel'}.get(face,'steel')
    if name=='rear_backplate': return 'shell_back' if face=='north' else ('steel' if face in ('east','west') else 'hidden')
    if name.startswith('vent_bay_'): return 'vent' if face=='south' else 'vent_edge'
    if name.startswith('vent_divider_'): return 'dark_metal'
    if name=='vent_sill': return {'south':'trim','north':'panel_edge','down':'dark_metal'}.get(face,'steel')
    if name=='vent_top_bezel': return 'trim' if face=='south' else 'steel'
    if name=='vent_left_bezel': return 'trim' if face=='west' else 'steel'
    if name=='vent_right_bezel': return 'trim' if face=='east' else 'steel'
    if name=='control_backplate': return {'south':'panel_edge','up':'steel','down':'dark_metal'}.get(face,'steel')
    if name=='control_face': return 'control' if face=='south' else 'panel_edge'
    if name.startswith('outlet_'): return {'south':'outlet','north':'rubber','down':'rubber'}.get(face,'steel')
    if name.startswith('control_rim_'): return 'trim' if face in ('south','east','west','up') else 'steel'
    if name=='black_duct_shell': return {'north':'black_back','east':'black_side','south':'black_side','west':'black_side','up':'black_roof','down':'black_hidden'}[face]
    if name in ('black_duct_top_frame','black_duct_mid_frame','black_duct_bottom_frame','black_duct_left_frame','black_duct_right_frame'): return 'black_frame'
    if name=='black_duct_upper_grille': return 'black_grille'
    if name.startswith('black_grille_bar_'): return 'black_bar'
    if name=='black_duct_service_door': return 'black_door'
    if name=='black_duct_door_inset': return 'black_panel'
    if name in ('black_duct_handle','black_duct_bracket'): return 'steel'
    if name.startswith('top_rail_') or name.startswith('top_crossbar_'): return 'rail'
    if name.startswith('top_support_') or name.startswith('top_end_cap_'): return 'steel'
    if name=='roof_hatch': return 'hatch'
    if name.endswith('_skirt'): return 'red' if face!='down' else 'rubber'
    if name.startswith('foot_'): return 'rubber'
    if name.startswith('corner_guard_'): return 'steel'
    if name.startswith('fan_ring_') or name.startswith('fan_support_') or name.startswith('fan_mount_'): return 'fan_metal'
    if name.startswith('fan_blade_'): return 'fan_blade'
    if name=='fan_hub': return 'hub'
    if name.startswith('rear_panel_'): return 'hatch'
    if name=='rear_control_box': return 'control' if face=='north' else 'panel_edge'
    if name.startswith('bevel_housing_'): return 'bevel_steel'
    if name.startswith('bevel_black_duct_'): return 'bevel_dark'
    if name.startswith('rear_service_frame_'): return 'service_frame'
    if name.startswith('rear_service_door_'): return ('service_door_left' if name.endswith('_left') else 'service_door_right') if face=='north' else 'steel_dark'
    if name=='rear_service_center_strip': return 'dark_metal'
    if name.startswith('rear_service_handle_'): return 'steel'
    if name=='side_right_backplate': return 'shell_side_recess'
    if name.startswith('side_right_frame_'): return 'service_frame'
    if name=='side_right_service_door': return 'service_door_right' if face=='east' else 'steel_dark'
    if name=='side_right_rear_vent': return 'vent' if face=='east' else 'steel_dark'
    if name=='side_right_handle': return 'steel'
    if name.startswith('side_left_'): return 'shell_edge_panel' if face in ('east','west') else 'steel'
    if name=='black_duct_rear_vent_bay': return 'black_vent_bay'
    if name.startswith('black_duct_rear_slat_'): return 'black_bar'
    if name=='black_duct_rear_service_door': return 'black_door'
    if name=='black_duct_rear_handle': return 'steel'
    if name=='black_duct_side_service_door': return 'black_door' if face=='west' else ('black_panel' if face in ('north','south','east') else 'steel_dark')
    if name=='black_duct_side_vent': return 'black_vent_bay' if face=='west' else 'steel_dark'
    if name=='black_duct_side_handle': return 'steel'
    if name in ('black_duct_roof_left_panel','black_duct_roof_right_panel'): return 'black_roof_panel'
    if name=='black_duct_roof_spine': return 'black_frame'
    if name.startswith('black_duct_roof_rib_'): return 'black_frame'
    return 'steel'

def base_color_for_style(style):
    m={'hidden':C['case_dark'],'rubber':C['rubber'],'black_hidden':C['black'],'shell_front':C['case_mid'],'shell_back':C['case_shadow'],'shell_side':C['case_shadow'],'shell_side_recess':C['case_dark'],'shell_edge_panel':C['case_shadow'],'roof_panel':C['case_mid2'],'service_frame':C['steel'],'service_door_left':C['case_shadow'],'service_door_right':C['case_shadow'],'dark_metal':C['steel_dark'],'black_back':C['black'],'black_side':C['black'],'black_roof':C['black_mid'],'black_vent_bay':C['black'],'black_roof_panel':C['black_mid'],'black_grille':C['black'],'black_frame':C['black_frame'],'black_bar':C['black_bar'],'black_door':C['black'],'black_panel':C['black_mid'],'red':C['red'],'vent':C['vent_dark'],'vent_edge':C['case_dark'],'control':C['panel'],'panel_edge':C['panel_edge'],'outlet':C['rubber'],'steel':C['steel'],'steel_dark':C['steel_dark'],'trim':C['steel_light'],'rail':C['steel'],'fan_metal':C['steel_dark'],'fan_blade':C['steel_dark'],'hub':C['steel_light'],'hatch':C['case_shadow'],'bumper':C['steel'],'bevel_steel':C['steel'],'bevel_dark':C['black_frame']}
    return m.get(style,C['steel'])

def face_dimensions(frm,to,face):
    d=[abs(to[i]-frm[i]) for i in range(3)]
    return {'north':(d[0],d[1]),'south':(d[0],d[1]),'east':(d[2],d[1]),'west':(d[2],d[1]),'up':(d[0],d[2]),'down':(d[0],d[2])}[face]

def is_pattern_face(name,face,style,wb,hb):
    if style in SOLID_ONLY_STYLES or face=='down': return False
    if name.endswith('_skirt') and face=='up': return False
    area=wb*hb
    if wb<0.95 or hb<0.95 or area<1.2: return False
    if min(wb,hb)<1.5: return False
    return area>=30.0

def scale_for_face(name,face,style,wb,hb):
    if (name,face) in HERO_FACES: return 2.5
    if style in ('bevel_steel','bevel_dark','steel','steel_dark','trim','rail','black_bar','black_frame'):
        return 2.0 if min(wb,hb)<2.0 else 1.5
    if min(wb,hb)<1.4: return 1.5
    return 2.0

def pixel_dimensions(wb,hb,scale):
    tw=max(1,round(wb*scale)); th=max(1,round(hb*scale)); ga=wb/hb; best=None; candidates=set()
    for dw in range(-3,4):
        for dh in range(-3,4): candidates.add((max(1,tw+dw),max(1,th+dh)))
    for h in range(max(1,th-8),th+9):
        wc=max(1,round(h*ga))
        for dw in (-1,0,1): candidates.add((max(1,wc+dw),h))
    for w in range(max(1,tw-12),tw+13):
        hc=max(1,round(w/ga))
        for dh in (-1,0,1): candidates.add((w,max(1,hc+dh)))
    for w,h in candidates:
        ua=w/h; ae=abs(ua/ga-1.0) if ga else 0.0
        de=abs(w/wb-scale)+abs(h/hb-scale); score=(round(max(0.0,ae-0.03),8),round(de,8),round(ae,8),abs(w-tw)+abs(h-th))
        if best is None or score<best[0]: best=(score,w,h)
    return best[1],best[2],best[0][0]*100.0

def build_layout(elements):
    pattern_items=[]; shared={}; info={}
    for e in elements:
        for face in FACE_KEYS:
            wb,hb=face_dimensions(e['from'],e['to'],face); style=exact_style(e['name'],face); key=f"{e['name']}|{face}"
            if is_pattern_face(e['name'],face,style,wb,hb):
                scale=scale_for_face(e['name'],face,style,wb,hb); w,h,err=pixel_dimensions(wb,hb,scale)
                pattern_items.append((key,e['name'],face,style,wb,hb,scale,w,h,err))
                info[key]={'rect':None,'shared':False,'style':style,'width_bb':round(wb,3),'height_bb':round(hb,3),'width_px':w,'height_px':h,'px_per_bb':round(scale,3),'aspect_error_percent':round(err,4)}
            else:
                color=base_color_for_style(style); shared[(style,color)]=color
                info[key]={'rect':None,'shared':True,'style':style,'width_bb':round(wb,3),'height_bb':round(hb,3),'width_px':2,'height_px':2,'px_per_bb':None,'aspect_error_percent':None,'solid_color':color}
    pattern_items.sort(key=lambda i:(-i[8],-i[7],i[1],i[2]))
    x=y=row=0; regions={}
    for key,name,face,style,wb,hb,scale,w,h,err in pattern_items:
        if x+w>ATLAS_SIZE: x=0; y+=row+ATLAS_GAP; row=0
        if y+h>ATLAS_SIZE: raise RuntimeError(f'UV atlas overflow at {key}: y={y}, h={h}, atlas={ATLAS_SIZE}')
        r=[x,y,x+w,y+h]; regions[key]=r; info[key]['rect']=r; x+=w+ATLAS_GAP; row=max(row,h)
    y+=row+ATLAS_GAP if row else 0; x=0; solids={}
    for (style,color), _color in sorted(shared.items()):
        cr=f'{style}|{color}'
        if x+2>ATLAS_SIZE: x=0; y+=4
        if y+2>ATLAS_SIZE: raise RuntimeError('solid UV atlas overflow')
        r=[x,y,x+2,y+2]; solids[cr]=r; x+=4
    for key,v in info.items():
        if v['shared']: v['rect']=solids[f"{v['style']}|{v['solid_color']}"]
    return {'texture_size':ATLAS_SIZE,'gap_px':ATLAS_GAP,'pattern_island_count':len(regions),'shared_solid_island_count':len(solids),'face_count':len(info),'atlas_occupied_height':y+2,'pattern_regions':regions,'shared_solid_regions':solids,'faces':info}
