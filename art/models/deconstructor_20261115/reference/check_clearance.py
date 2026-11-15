"""Sample cutter/hopper oriented-box intersections at one-degree increments."""
import itertools,json
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1]

def rotation(angles):
    (a,b,c)=np.radians(angles)
    cx,cy,cz=np.cos([a,b,c]);sx,sy,sz=np.sin([a,b,c])
    return np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],
                     [sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])

def obb(c):
    lo=np.array(c['from']);hi=np.array(c['to']);pivot=np.array(c['origin'])
    R=rotation(c.get('rotation',[0,0,0]))
    return pivot+R@((lo+hi)*.5-pivot),R.T,(hi-lo)*.5

def apply_group_rotation(center,axes,half,gpivot,grot):
    """Apply a parent group's rotation to an element's oriented box."""
    gR=rotation(grot);gc=np.array(gpivot,float)
    return gc+gR@(center-gc), axes@gR.T, half

def sweep(m):
    groups={g['uuid']:g for g in m['groups']};els={c['uuid']:c for c in m['elements']};parts={}
    def walk(n):
        g=groups[n['uuid']]
        parts[g['name']]=(np.array(g['origin']),[els[k] for k in n['children'] if isinstance(k,str)],
                          g.get('rotation',[0,0,0]))
        for k in n['children']:
            if isinstance(k,dict):walk(k)
    for n in m['outliner']:walk(n)
    hull=[]
    for c in parts['feed_hopper'][1]:
        center,axes,half=obb(c)
        center,axes,half=apply_group_rotation(center,axes,half,
                                              parts['feed_hopper'][0],parts['feed_hopper'][2])
        hull.append((c['name'],center,axes,half))
    results={};radii=[]
    hopper_pivot,hopper_rot=parts['feed_hopper'][0],parts['feed_hopper'][2]
    for key,sign in [('left_cutters',-1),('right_cutters',1)]:
        pivot,pieces,_grot=parts[key]
        pivot_t=hopper_pivot+rotation(hopper_rot)@(pivot-np.array(hopper_pivot,float))
        rz=np.array([rotation([0,0,deg*sign]) for deg in range(360)])
        contacts={};collected=[]
        for c in pieces:
            center,axes,half=obb(c)
            # The cradle group is tilted, and the cutter groups are its children.
            center,axes,half=apply_group_rotation(center,axes,half,hopper_pivot,hopper_rot)
            corners=np.array(list(itertools.product(*zip(-half,half))))@axes+center
            collected.append(np.linalg.norm((corners-pivot_t)[:,:2],axis=1).max())
            moving_centers=np.einsum('tij,j->ti',rz,center-pivot_t)+pivot_t
            moving_axes=np.einsum('tij,kj->tki',rz,axes)
            for name,fc,fa,fh in hull:
                fixed=np.broadcast_to(fa,(360,3,3))
                cross=np.cross(moving_axes[:,:,None,:],fixed[:,None,:,:]).reshape(360,9,3)
                tests=np.concatenate((moving_axes,fixed,cross),axis=1)
                norm=np.linalg.norm(tests,axis=2)
                tests/=np.where(norm>1e-8,norm,1)[:,:,None]
                ar=np.abs(np.einsum('tij,tkj->tik',tests,moving_axes))@half
                br=np.abs(np.einsum('tij,kj->tik',tests,fa))@fh
                dist=np.abs(np.einsum('tij,tj->ti',tests,moving_centers-fc))
                sep=np.where(norm>1e-8,dist-ar-br,-np.inf).max(axis=1)
                depth=float(-sep.min())
                if depth>1.e-4:
                    row=contacts.setdefault(name,{'max_penetration_BB':0,'parts':set()})
                    row['parts'].add(c['name'].split('_')[0])
                    if depth>row['max_penetration_BB']:row.update(max_penetration_BB=round(depth,5),angle_degrees=int(np.argmin(sep)))
        results[key]={name:{**v,'parts':sorted(v['parts'])} for name,v in contacts.items()}
        radii.append(max(collected))
    dist=np.linalg.norm((parts['left_cutters'][0]-parts['right_cutters'][0])[:2])
    return {'sampling_step_degrees':1,'scope':'each rotor versus all feed_hopper cubes; pairs use oriented-box separating axes',
            'intersections':results,'rotor_to_rotor_envelope_gap_BB':round(float(dist-sum(radii)),5)}
if __name__=='__main__':
    report=sweep(json.loads((ROOT/'deconstructor.bbmodel').read_text(encoding='utf8')))
    print(json.dumps(report,indent=2))

