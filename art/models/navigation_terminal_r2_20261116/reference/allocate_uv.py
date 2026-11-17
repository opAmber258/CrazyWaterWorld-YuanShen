"""Pack independent faces at the pixel sizes used by native 32x templates."""
import json,math
from bb import ROOT,run

def size(v):return max(1,math.floor(v*2+.5))
def pack(rectangles,side=512):
    free=[(0,0,side,side)];out={}
    for key,w,h in sorted(rectangles,key=lambda r:(-max(r[1:]),-r[1]*r[2],r[0])):
        pw,ph=w+2,h+2
        fits=[(min(fw-pw,fh-ph),max(fw-pw,fh-ph),y,x,i) for i,(x,y,fw,fh) in enumerate(free) if pw<=fw and ph<=fh]
        if not fits:raise ValueError('Atlas exceeds 512 at native density')
        _,_,y,x,_=min(fits);used=(x,y,x+pw,y+ph);new=[]
        for fx,fy,fw,fh in free:
            right,bottom=fx+fw,fy+fh
            if x>=right or x+pw<=fx or y>=bottom or y+ph<=fy:new.append((fx,fy,fw,fh));continue
            if x>fx:new.append((fx,fy,x-fx,fh))
            if x+pw<right:new.append((x+pw,fy,right-x-pw,fh))
            if y>fy:new.append((fx,fy,fw,y-fy))
            if y+ph<bottom:new.append((fx,y+ph,fw,bottom-y-ph))
        free=[a for i,a in enumerate(new) if a[2]>0 and a[3]>0 and not any(i!=j and a[0]>=b[0] and a[1]>=b[1] and a[0]+a[2]<=b[0]+b[2] and a[1]+a[3]<=b[1]+b[3] and (a!=b or i>j) for j,b in enumerate(new))]
        out[key]=[x+1,y+1,x+1+w,y+1+h]
    return out

if __name__=='__main__':
    model=run('(()=>Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false}))()')
    rs=[]
    for e in model['elements']:
        x,y,z=[b-a for a,b in zip(e['from'],e['to'])]
        for f in e['faces']:
            w,h=(x,y) if f in ['north','south'] else ((z,y) if f in ['east','west'] else (x,z))
            rs.append((e['uuid']+':'+f,size(w),size(h)))
    layout=pack(rs)
    pid=(ROOT/'reference/project_id.txt').read_text()
    code='''(()=>{if(Project.uuid!==PID)throw Error("Project changed");const layout=LAYOUT;Undo.initEdit({elements:Cube.all,uv_only:true,uv_mode:true});Project.texture_width=256;Project.texture_height=256;Project.box_uv=false;for(const e of Cube.all){e.box_uv=false;for(const [f,d] of Object.entries(e.faces)){d.uv=layout[e.uuid+":"+f].map(v=>v/2);d.rotation=0;d.texture=Texture.all[0].uuid;}}Undo.finishEdit("Pack independent native-size faces");Canvas.updateAll();return Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false})})()'''.replace('PID',json.dumps(pid)).replace('LAYOUT',json.dumps(layout))
    model=run(code)
    (ROOT/'reference/native_uv_template.bbmodel').write_text(json.dumps(model),encoding='utf8')
    (ROOT/'reference/uv_pack.json').write_text(json.dumps({'density':32,'pixels_per_bb':2,'png':[512,512],'logical_uv':[256,256],'layout_origin':'custom packing, pixel dimensions cross-checked with native 32x sample','faces':len(rs),'texel_area':sum(w*h for k,w,h in rs),'gutter_pixels':1,'layout':layout},indent=2))
    print('faces',len(rs),'texel_area',sum(w*h for k,w,h in rs))
