"""Cuboid projection and shell layout from the preceding scale paint."""
import numpy as np

CORNERS = {
    "east":  ((1, 1, 1), (1, 1, 0), (1, 0, 1), (1, 0, 0)),
    "west":  ((0, 1, 0), (0, 1, 1), (0, 0, 0), (-1, 0, 0)),
    "up":    ((0, 1, 0), (1, 1, 0), (0, 1, 1), (0, 1, 0)),
    "down":  ((0, 0, 1), (1, 0, 1), (0, 0, 0), (0, -1, 0)),
    "south": ((0, 1, 1), (1, 1, 1), (0, 0, 1), (0, 0, 1)),
    "north": ((1, 1, 0), (0, 1, 0), (1, 0, 0), (0, 0, -1)),
}
EYES = ("cheek_socket_", "cheek_lens_", "cheek_photophore_")
def matrix(angles, pivot):
    x,y,z = np.deg2rad(angles)
    sx,sy,sz = np.sin((x,y,z))
    cx,cy,cz = np.cos((x,y,z))
    turn = np.array(((cz*cy, cz*sy*sx-sz*cx, cz*sy*cx+sz*sx),
                     (sz*cy, sz*sy*sx+cz*cx, sz*sy*cx-cz*sx),
                     (-sy,cy*sx,cy*cx)))
    return turn, pivot - turn @ pivot


def cubes(model):
    groups = {bone["uuid"]: bone for bone in model["groups"]}
    by_id = {cube["uuid"]: cube for cube in model["elements"]}

    def visit(node, basis, offset, group=None):
        if isinstance(node,str):
            item=by_id[node]
        else:
            item=groups[node["uuid"]]
        rotation,shift=matrix(item.get("rotation",(0,0,0)),np.asarray(item["origin"]))
        shift=basis@shift+offset
        rotation=basis@rotation
        if isinstance(node,str):
            yield item,group,rotation,shift
        else:
            for child in node["children"]:
                yield from visit(child,rotation,shift,item["name"])

    for node in model["outliner"]:
        yield from visit(node,np.eye(3),np.zeros(3))


def project(cube, side, uv, basis, offset):
    x0,y0,x1,y1=map(int,uv)
    h,w=abs(y1-y0),abs(x1-x0)
    u=(np.arange(w)+0.5)/w
    v=(np.arange(h)+0.5)/h
    if x1<x0:
        u=1-u
    if y1<y0:
        v=1-v
    low,high=np.array((cube["from"],cube["to"]))
    left,right,lower,normal=CORNERS[side]
    points=low+(high-low)*np.asarray([left,right,lower])
    samples=points[0]+u[None,:,None]*(points[1]-points[0])+v[:,None,None]*(points[2]-points[0])
    extent=np.linalg.norm(points[1]-points[0])/w,np.linalg.norm(points[2]-points[0])/h
    return samples@basis.T+offset,basis@normal,max(extent)


def polygon_distance(x,z,vertices):
    border=np.full(x.shape,np.inf)
    for (x0,z0),(x1,z1) in zip(vertices,vertices[1:]+vertices[:1]):
        edge=(x1-x0)*(z-z0)-(z1-z0)*(x-x0)
        border=np.minimum(border,edge/np.hypot(x1-x0,z1-z0))
    return border


# Center x, leading edge z, half-width, length, wear index.
DORSAL_PLATES = (
    (-0.7, 65, 9.0,15.0,1), (0.1, 53, 10.1,19.0,0),
    (-0.7, 37, 11.9,20.5,2), (0.2, 20, 12.7,22.0,1),
    (0.0,  2, 13.0,21.0,2), (-0.4,-17,11.4,21.0,1),
    (0.5,-35, 10.2,19.5,0), (-0.6,-51,8.9,18.4,2),
    (0.0,-66, 6.9,16.8,1), (0.7,-79,5.4,12.5,0),
)


def stroke(a,b,points):
    dist=np.full(a.shape,np.inf)
    for (x0,y0),(x1,y1) in zip(points,points[1:]):
        vx,vy=x1-x0,y1-y0
        t=np.clip(((a-x0)*vx+(b-y0)*vy)/(vx*vx+vy*vy),0,1)
        dist=np.minimum(dist,np.hypot(a-x0-t*vx,b-y0-t*vy))
    return dist


