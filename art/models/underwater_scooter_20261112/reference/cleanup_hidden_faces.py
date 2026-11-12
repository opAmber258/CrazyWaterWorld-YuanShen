"""Conservatively disable faces covered by one closed opaque cube.

Run after Blockbench export. --apply updates only the matching task project.
The frozen original geometry, not the successively edited model, supplies
all proofs. Every face corner and a small outward offset must lie inside
the same opaque convex cube. Partially hidden faces are retained.
"""
import argparse
import base64
import hashlib
import io
import json
import math
from pathlib import Path
from PIL import Image
from bb_client import Client
from blockbench_task import ROOT, REF, check, save

def rotate(v,angles,inverse=False):
    x,y,z=v
    axes=list(enumerate(angles))
    if inverse:axes=list(reversed(axes))
    for axis,deg in axes:
        a=math.radians(-deg if inverse else deg);s=math.sin(a);c=math.cos(a)
        if axis==0:y,z=y*c-z*s,y*s+z*c
        if axis==1:x,z=x*c+z*s,-x*s+z*c
        if axis==2:x,y=x*c-y*s,x*s+y*c
    return [x,y,z]

def world(c,v):
    p=c.get('origin',[0,0,0]);v=rotate([v[i]-p[i] for i in range(3)],c.get('rotation',[0,0,0]))
    return [v[i]+p[i] for i in range(3)]
def local(c,v):
    p=c.get('origin',[0,0,0]);v=rotate([v[i]-p[i] for i in range(3)],c.get('rotation',[0,0,0]),True)
    return [v[i]+p[i] for i in range(3)]

def points(c,f):
    axis,side={'west':(0,0),'east':(0,1),'down':(1,0),'up':(1,1),'north':(2,0),'south':(2,1)}[f]
    rest=[i for i in range(3) if i!=axis];out=[]
    for a,b in [(0,0),(0,1),(1,0),(1,1)]:
        v=[0,0,0];v[axis]=c['to' if side else 'from'][axis]
        v[rest[0]]=c['to' if a else 'from'][rest[0]]
        v[rest[1]]=c['to' if b else 'from'][rest[1]];out.append(world(c,v))
    n=[0,0,0];n[axis]=1 if side else -1
    return out,rotate(n,c.get('rotation',[0,0,0]))

def proofs(d):
    allgroups=d.get('groups',[])
    if any(any(abs(v)>1e-8 for v in g.get('rotation',[0,0,0])) for g in allgroups):
        raise ValueError('This narrowly scoped checker requires unrotated groups')
    ims=[]
    for t in d['textures']:ims.append(Image.open(io.BytesIO(base64.b64decode(t['source'].split(',',1)[1]))).convert('RGBA'))
    candidates=[]
    for c in d['elements']:
        if c.get('type')!='cube' or c.get('inflate',0):continue
        opaque=True
        for f in c['faces'].values():
            if f.get('texture') is None:opaque=False;break
            uv=f['uv'];x1,y1,x2,y2=uv
            alpha=ims[int(f['texture'])].crop((math.floor(min(x1,x2)),math.floor(min(y1,y2)),math.ceil(max(x1,x2)),math.ceil(max(y1,y2)))).getchannel('A')
            if alpha.getextrema()!=(255,255):opaque=False;break
        if opaque:candidates.append(c)
    result=[]
    for c in d['elements']:
        for face,fd in c.get('faces',{}).items():
            if fd.get('texture') is None:continue
            corners,n=points(c,face)
            samples=[[p[i]+n[i]*0.0001 for i in range(3)] for p in corners]
            for cover in candidates:
                if cover['uuid']==c['uuid']:continue
                coords=[local(cover,p) for p in samples]
                if all(cover['from'][i]+1e-7<pt[i]<cover['to'][i]-1e-7 for pt in coords for i in range(3)):
                    result.append({'cube':c['name'],'uuid':c['uuid'],'face':face,'occluder':cover['name'],'proof':'All four face corners, offset 0.0001 units outward, strictly inside one closed opaque cuboid.'});break
    return result

def main():
    p=argparse.ArgumentParser();p.add_argument('--apply',action='store_true');p.add_argument('--url',default='http://localhost:3015/bb-mcp');a=p.parse_args()
    model=ROOT/'underwater_scooter.bbmodel';d=json.loads(model.read_text(encoding='utf-8'));pr=proofs(d)
    report={'source_sha256':hashlib.sha256(model.read_bytes()).hexdigest(),'removed_faces':len(pr),'proofs':pr,'limitations':['Static model with no animations or rotated groups.','Only coverage by one fully opaque cuboid is accepted. Union coverage and partial occlusion are retained.','Rendering comparison is required separately.']}
    (REF/'work').mkdir(exist_ok=True)
    (REF/'work'/'hidden_face_candidates.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    if a.apply and pr:
        baseline=REF/'work'/'before_cleanup.bbmodel'
        if baseline.exists():raise RuntimeError('Cleanup baseline already exists')
        baseline.write_bytes(model.read_bytes())
        with Client(a.url) as c:
            check(c)
            for proof in pr:c.tool('set_cube_uv',{'id':proof['uuid'],'faces':{proof['face']:{'texture':None}}})
            save(c)
        (ROOT/'hidden_face_audit.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({'candidates':len(pr),'applied':a.apply and bool(pr)}))
if __name__=='__main__':main()
