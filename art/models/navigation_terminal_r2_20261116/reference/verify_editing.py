"""Verify atlas separation and a real, undo-restored one-face paint operation."""
import json,base64,io,hashlib
from PIL import Image
from bb import ROOT,run

def tiles(m):
    im=Image.open(io.BytesIO(base64.b64decode(m['textures'][0]['source'].split(',')[1]))).convert('RGBA');sx=im.width/m['resolution']['width'];sy=im.height/m['resolution']['height'];out={}
    for e in m['elements']:
        for f,d in e['faces'].items():
            u,v,a,b=d['uv'];box=[round(u*sx),round(v*sy),round(a*sx),round(b*sy)]
            out[e['uuid']+':'+f]=im.crop(box).tobytes()
    return out
def compile():return run('(()=>Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false}))()')
if __name__=='__main__':
    before=compile();pid=(ROOT/'reference/project_id.txt').read_text().strip()
    e=next(e for e in before['elements'] if e['name']=='earcup_L');uv=e['faces']['south']['uv'];x,y=round(uv[0]*2),round(uv[1]*2)
    code='''(()=>{if(Project.uuid!==PID)throw Error("Project changed");const t=Texture.all[0];Undo.initEdit({textures:[t],bitmap:true});t.edit(canvas=>{const c=canvas.getContext("2d");c.fillStyle="#ff00ff";c.fillRect(X,Y,1,1)},{no_undo:true});Undo.finishEdit("Verify independent headphone face painting");return true})()'''.replace('PID',json.dumps(pid)).replace('X',str(x)).replace('Y',str(y))
    run(code)
    try:
        after=compile();a,b=tiles(before),tiles(after);changed=[k for k in a if a[k]!=b[k]]
    finally:run('(()=>{Undo.undo();Canvas.updateAll();return true})()')
    restored=compile();same=tiles(before)==tiles(restored);assert same
    run('(()=>{Project.saved=true;return true})()')
    rects=[]
    for e in before['elements']:
        for f,d in e['faces'].items():rects.append((e['name']+':'+f,d['uv']))
    overlaps=[]
    for i,(name,(x,y,a,b)) in enumerate(rects):
        for name2,(x2,y2,a2,b2) in rects[:i]:
            if min(a,a2)>max(x,x2) and min(b,b2)>max(y,y2):overlaps.append([name,name2])
    report={'faces':len(rects),'atlas_overlaps':overlaps,'changed_faces':changed,'expected_face':next(e['uuid'] for e in before['elements'] if e['name']=='earcup_L')+':south','only_one_face_changed':len(changed)==1 and changed[0]==next(e['uuid'] for e in before['elements'] if e['name']=='earcup_L')+':south','undo_restored_all_face_pixels':same}
    assert report['only_one_face_changed'] and not overlaps
    (ROOT/'reference/editability_check.json').write_text(json.dumps(report,indent=2))
    print(report)
