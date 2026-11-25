"""Inspect the convex cap in its tilted head frame and verify edit scope and exports."""
import argparse
import base64
import hashlib
import io
import itertools
import json
from pathlib import Path

import numpy as np
from PIL import Image

from surface_coords import cubes, matrix


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def bitmap(model):
    return base64.b64decode(model["textures"][0]["source"].split(",",1)[1])


def atlas_slice(face):
    u,v,a,b=face["uv"]
    assert all(t==int(t) for t in (u,v,a,b))
    left,right=sorted((int(u),int(a)))
    top,bottom=sorted((int(v),int(b)))
    assert 0<=left<right<=256 and 0<=top<bottom<=256
    return np.s_[top:bottom,left:right]


def box(cube,rot,offset):
    a,b=np.asarray((cube["from"],cube["to"]))
    assert np.all(b>a),cube["name"]
    return rot@((a+b)/2)+offset,rot.T,(b-a)/2


def overlaps(a,b):
    center_a,axes_a,extents_a=a
    center_b,axes_b,extents_b=b
    test=np.concatenate((axes_a,axes_b,np.cross(axes_a[:,None],axes_b[None,:]).reshape(9,3)))
    keep=np.linalg.norm(test,axis=1)>1e-7
    test=test[keep]
    allowance=np.abs(test@axes_a.T)@extents_a+np.abs(test@axes_b.T)@extents_b
    return np.all(np.abs(test@(center_b-center_a))<allowance-1e-8)


def head_boxes(model,frame):
    return [(name,b@frame,a@frame,c) for name,(a,b,c) in
        [(cube["name"],box(cube,rot,offset)) for cube,group,rot,offset in cubes(model)
          if group=="hammer" and cube["name"].startswith("ram_")]]


def hits(mesh,x,y):
    """Front depth on the union of all of the head's solid cuboids."""
    points=np.stack(np.broadcast_arrays(x,y,np.zeros_like(x)),axis=-1)
    front=np.full(points.shape[:-1],-np.inf)
    for _,axis,center,extent in mesh:
        local=(points-center)@axis.T
        dz=axis[:,2]
        active=np.all((np.abs(local)<=extent)| (np.abs(dz)>1e-9),axis=-1)
        div=np.where(np.abs(dz)>1e-9,dz,1.)
        slab0=(-extent-local)/div
        slab1=(extent-local)/div
        low=np.where(np.abs(dz)>1e-9,np.minimum(slab0,slab1),-np.inf).max(axis=-1)
        high=np.where(np.abs(dz)>1e-9,np.maximum(slab0,slab1),np.inf).min(axis=-1)
        front=np.maximum(front,np.where(active&(high>=low),high,-np.inf))
    return front


DIRECTIONS = ("right", "upper_right", "upper", "upper_left", "left",
              "lower_left", "lower", "lower_right")


def radial_extent(mesh, origin, direction):
    intervals=[]
    for _,axis,center,extent in mesh:
        point=(origin-center)@axis.T
        local=direction@axis.T
        slanted=np.abs(local)>1e-10
        if np.any(np.abs(point[~slanted])>extent[~slanted]):
            continue
        t0=(-extent[slanted]-point[slanted])/local[slanted]
        t1=(extent[slanted]-point[slanted])/local[slanted]
        start,stop=np.minimum(t0,t1).max(),np.maximum(t0,t1).min()
        if stop>=max(0,start):
            intervals.append((start,stop))
    return max((stop for _,stop in intervals),default=0.)


def contour(previous,current):
    # Axes are measured in the unanimated head frame, with +Z toward the impact face.
    center=np.array([4.013,38.46])
    x,y=center
    center_before=float(hits(previous,np.array(x),np.array(y)))
    center_after=float(hits(current,np.array(x),np.array(y)))
    assert abs(center_after-center_before)<1.5,(center_before,center_after)
    stations=np.linspace(center_before-12., center_after-1.1,5)
    report={}
    for label,angle in zip(DIRECTIONS,np.arange(8)*np.pi/4,strict=True):
        ray=np.array([np.cos(angle),np.sin(angle),0])
        old_radius=np.array([radial_extent(previous,np.r_[center,z],ray) for z in stations])
        radius=np.array([radial_extent(current,np.r_[center,z],ray) for z in stations])
        assert radius[0]>0, (label,radius)
        assert radius[-1]>radius[0]*1.055 and np.min(np.diff(radius))>-.12, (label,radius)
        # The striking plate is shallow across its whole outline, including the diagonal shoulders.
        offsets=radius[-1]*ray[:2]*np.array([.25,.5,.75,.85])[:,None]
        points=offsets+center
        depths=hits(current,points[:,0],points[:,1])
        assert np.isfinite(depths).all() and np.max(np.abs(depths-center_after))<1.55,(label,depths)
        report[label]={"old_radii":old_radius.round(3).tolist(),
                       "new_radii":radius.round(3).tolist(),
                       "end_growth_percent":round(100*(radius[-1]/radius[0]-1),2),
                       "face_depths":depths.round(3).tolist()}
    angles=np.linspace(0,2*np.pi,72,endpoint=False)
    vectors=np.stack((np.cos(angles),np.sin(angles),np.zeros_like(angles)),axis=1)
    rim_center=np.r_[center,stations[-1]]
    rim=np.array([radial_extent(current,rim_center,vector) for vector in vectors])
    pos=center+rim[:,None,None]*vectors[:,None,:2]*np.linspace(.08,.94,15)[None,:,None]
    front=hits(current,pos[...,0],pos[...,1])
    assert np.isfinite(front).all() and np.max(np.abs(front-center_after))<1.55
    return {"section_depth_stations":stations.round(3).tolist(),
            "front_sample_count":int(front.size),
            "maximum_front_relief":round(float(np.max(np.abs(front-center_after))),3),
            "center_depth_before":round(center_before,3),"center_depth_after":round(center_after,3),
            "directions":report}


def verify_winding(snapshot, edited):
    count=0
    for cube in snapshot:
        vertices=np.asarray(cube["position"])
        if cube["name"] in edited:
            assert cube["parents"]==["root","thorax","hammer"]
        triangles=vertices[np.asarray(cube["indices"]).reshape(-1,3)]
        edges=triangles[:,1:]-triangles[:,[0]]
        normal=np.cross(edges[:,0],edges[:,1])
        outward=triangles.mean(axis=1)-vertices.mean(axis=0)
        assert (np.einsum("ij,ij->i",normal,outward)>0).all(),cube["name"]
        count+=len(triangles)
    return count


def validate(source,output):
    before,after=read(source),read(output/"hammerhead.bbmodel")
    protected=("elements","outliner","textures")
    assert {k:v for k,v in before.items() if k not in protected}=={k:v for k,v in after.items() if k not in protected}
    original={c["uuid"]:(c,g,box(c,r,o)) for c,g,r,o in cubes(before)}
    revised={c["uuid"]:(c,g,box(c,r,o)) for c,g,r,o in cubes(after)}
    moved=[c["name"] for uid,(c,g,_) in revised.items() if uid not in original or original[uid][0]!=c]
    retired=[c["name"] for uid,(c,g,_) in original.items() if uid not in revised]
    assert moved
    assert all(c["name"].startswith("ram_") and g=="hammer" for uid,(c,g,_) in revised.items()
               if c["name"] in moved)
    assert all(n.startswith("ram_") for n in retired)
    assert all(g==revised[uid][1] for uid,(c,g,v) in original.items() if uid in revised)
    mesh={c["name"]:b for c,g,b in revised.values() if c["name"].startswith("ram_")}
    adjacent={}
    for name in moved:
        adjacent[name]=[neighbor for neighbor,shape in mesh.items() if neighbor!=name and overlaps(mesh[name],shape)]
        assert adjacent[name], name

    connected=set(mesh)-set(moved)
    pending=set(moved)
    while pending:
        reached={name for name in pending if connected.intersection(adjacent[name])}
        assert reached,pending
        pending-=reached
        connected.update(reached)

    ref=next(c for c in before["elements"] if c["name"]=="ram_impact")
    frame,_=matrix(ref["rotation"],np.asarray(ref["origin"]))
    heads=[head_boxes(model,frame) for model in (before,after)]
    flare=contour(*heads)

    old_png,new_png=bitmap(before),bitmap(after)
    assert new_png==(output/"hammerhead_tex.png").read_bytes()
    first=np.asarray(Image.open(io.BytesIO(old_png)))
    last=np.asarray(Image.open(io.BytesIO(new_png)))
    assert first.shape==last.shape==(256,256,4)
    used=np.zeros((256,256),dtype=np.uint8)
    texture_change=np.any(first!=last,axis=2)
    edited_mask=np.zeros((256,256),dtype=bool)
    for item,_,_ in revised.values():
        for uv in item["faces"].values():
            coord=atlas_slice(uv)
            used[coord]+=1
            if item["name"] in moved:
                edited_mask[coord]=True
            else:
                assert np.array_equal(first[coord],last[coord]),item["name"]
    assert used.max()==1
    assert np.all(last[edited_mask,3]==255)
    assert not np.any(last[last[:,:,3]==0])
    # A legitimately repacked island may also clear the freed space from a removed target box.
    for name in retired:
        item=next(c for c,_,_ in original.values() if c["name"]==name)
        for uv in item["faces"].values():
            edited_mask[atlas_slice(uv)]=True
    assert not np.any(texture_change&~edited_mask)
    native=read(output/"editor_evidence.json")
    native_winding=verify_winding(read(output/"native_mesh.json"),set(moved))
    assert native["animationCount"]==2 and native["cubes"]==len(revised)
    assert native["groups"]==len(after["groups"])==19
    assert native["geometry_reimport"]["cubes"]==len(revised)
    for view,r in native["geometry_reimport"]["preview_difference"].items():
        assert r["different_pixels"]<300,(view,r)
        assert Image.open(output/f"preview_{view}.png").size==(1200,900)
    proof={"baseline_sha256":hashlib.sha256(source.read_bytes()).hexdigest(),
           "geometry":{"identifier":"geometry.hammerhead","cubes":len(revised),"bones":native["groups"],
                       "edited_or_new":moved,"replaced":retired,"world_bounds":native["bounds"],
                       "size_blocks":[v/16 for v in native["bounds"]["size"]],"solid_neighbors":adjacent,
                       "cap_cross_sections":flare},
           "texture":{"resolution":[256,256],"overlaps":0,"preserved_other_face_texels":True,
                      "changed_texels":int(texture_change.sum())},
           "native":{"version":native["version"],"outward_triangles":native_winding,
                     "preview_reimport":native["geometry_reimport"]},
           "minecraft_geckolib":"not tested"}
    (output/"validation.json").write_text(json.dumps(proof,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(json.dumps({"sections":flare,"cubes":len(revised),"edited":moved},ensure_ascii=False))


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("source",type=Path)
    p.add_argument("output",type=Path)
    a=p.parse_args()
    validate(a.source,a.output)
