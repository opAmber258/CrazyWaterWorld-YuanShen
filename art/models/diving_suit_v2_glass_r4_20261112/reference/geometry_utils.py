"""Cuboid transforms matching Blockbench's observed ZYX Euler order."""
import math,itertools
def rotate_axis(p,axis,ang):
    x,y,z=p;t=math.radians(ang);c,s=math.cos(t),math.sin(t)
    if axis==0:y,z=y*c-z*s,y*s+z*c
    elif axis==1:x,z=x*c+z*s,-x*s+z*c
    else:x,y=x*c-y*s,x*s+y*c
    return [x,y,z]
def transform(p,e,inverse=False):
    o=e.get('origin',[0,0,0]);q=[p[i]-o[i] for i in range(3)];r=e.get('rotation',[0,0,0])
    for axis in ([2,1,0] if inverse else [0,1,2]):q=rotate_axis(q,axis,(-1 if inverse else 1)*r[axis])
    return [q[i]+o[i] for i in range(3)]
def vertices(e):return [transform(p,e) for p in itertools.product(*zip(e['from'],e['to']))]
def face_vertices(e,face):
    axis,which={'north':(2,0),'south':(2,1),'east':(0,1),'west':(0,0),'up':(1,1),'down':(1,0)}[face]
    ranges=[(e['from'][i],e['to'][i]) for i in range(3)];ranges[axis]=(ranges[axis][which],)
    return [transform(p,e) for p in itertools.product(*ranges)]
def inside(p,e,strict=False):
    q=transform(p,e,True)
    return all(e['from'][i]+(1e-5 if strict else -1e-5)<q[i]<e['to'][i]+(-1e-5 if strict else 1e-5) for i in range(3))
def bounds(elements):
    vs=[p for e in elements for p in vertices(e)];lo=[min(p[i] for p in vs) for i in range(3)];hi=[max(p[i] for p in vs) for i in range(3)]
    return {'min':lo,'max':hi,'size':[hi[i]-lo[i] for i in range(3)]}
def native_vertices(snapshot):
    m=snapshot['matrix'];return [[sum(m[j*4+i]*p[j] for j in range(3))+m[12+i] for i in range(3)] for p in itertools.product(*zip(snapshot['local_min'],snapshot['local_max']))]
