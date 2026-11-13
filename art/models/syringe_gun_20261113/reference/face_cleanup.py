"""Conservative convex-box proof for fully buried opaque faces; no partial deletion.
All authored elements here have only Z rotation and solid rectangular UV islands.
"""
import math, itertools

def rotated(v,origin,degrees):
    a=math.radians(degrees);c,s=math.cos(a),math.sin(a)
    x,y=v[0]-origin[0],v[1]-origin[1]
    return [origin[0]+x*c-y*s,origin[1]+x*s+y*c,v[2]]

def cleanup(elements):
    for e in elements:
        if e['rotation'][:2]!=[0,0]:raise ValueError('Only authored Z rotations supported')
    removed=[]
    axes={'west':(0,-1),'east':(0,1),'down':(1,-1),'up':(1,1),'north':(2,-1),'south':(2,1)}
    for e in elements:
        a,b=e['from'],e['to'];o=e['origin'];rot=e['rotation'][2]
        for face,(axis,sign) in axes.items():
            other=[j for j in range(3) if j!=axis];corners=[]
            for bits in itertools.product([0,1],repeat=2):
                v=[0.,0.,0.];v[axis]=(b if sign==1 else a)[axis]+sign*1e-6
                for j,bit in zip(other,bits):v[j]=(b if bit else a)[j]
                corners.append(rotated(v,o,rot))
            for target in elements:
                if target is e:continue
                tc=[rotated(v,target['origin'],-target['rotation'][2]) for v in corners]
                if all(all(target['from'][j]+1e-5<v[j]<target['to'][j]-1e-5 for j in range(3)) for v in tc):
                    e['faces'][face]['texture']=None
                    removed.append({'element':e['name'],'face':face,'fully_covered_by':target['name']})
                    break
    return {'method':'All four corners of the face, offset 1e-6 outward, lie strictly inside one opaque oriented cuboid with a 1e-5 margin. Convexity proves coverage of the entire rectangular face. Boundary-touching faces retained to preserve antialiased seams. Static geometry only. No partial faces or components removed.','removed_face_count':len(removed),'removed_faces':removed}
