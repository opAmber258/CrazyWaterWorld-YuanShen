"""Propose only faces wholly inside one opaque, unrotated cuboid.
Does not alter the project; candidate application requires native render comparison.
"""
from pathlib import Path
import json
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
m=json.loads((ROOT/'advanced_syringe_gun.bbmodel').read_text(encoding='utf8'))
im=Image.open(ROOT/'advanced_syringe_gun_tex.png').convert('RGBA')
def opaque(e):
    for f in e['faces'].values():
        if f.get('texture') is None:return False
        x0,y0,x1,y1=f['uv']
        if im.crop((min(x0,x1),min(y0,y1),max(x0,x1),max(y0,y1))).getchannel('A').getextrema()!=(255,255):return False
    return True
eligible=[e for e in m['elements'] if not any(e.get('rotation',[0,0,0])) and opaque(e)]
dirs={'north':(2,-1),'south':(2,1),'west':(0,-1),'east':(0,1),'up':(1,1),'down':(1,-1)}
result=[]
for e in eligible:
 for f,(axis,sign) in dirs.items():
    plane=e['to' if sign>0 else 'from'][axis]
    for b in eligible:
        if b['uuid']==e['uuid']:continue
        if sign>0: inside=b['from'][axis]<=plane+1e-8 and b['to'][axis]>plane+1e-8
        else: inside=b['from'][axis]<plane-1e-8 and b['to'][axis]>=plane-1e-8
        cover=all(b['from'][a]<=e['from'][a]+1e-8 and b['to'][a]>=e['to'][a]-1e-8 for a in range(3) if a!=axis)
        if inside and cover:
            result.append({'uuid':e['uuid'],'name':e['name'],'face':f,'occluder':b['name']});break
(ROOT/'reference/face_cleanup_candidates.json').write_text(json.dumps(result,indent=2),encoding='utf8')
print(json.dumps({'eligible_axis_aligned_opaque_cubes':len(eligible),'candidate_faces':len(result)}))
