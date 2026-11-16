"""Check the custom packing face sizes against an actual native 32x sample."""
import json,math
from bb import ROOT,URL
from bb_client import Client,structured

if __name__=='__main__':
    original=(ROOT/'reference/project_id.txt').read_text().strip()
    samples=[('screen',(23,14,.25)),('rounded_corner',(.707106,.707106,.15)),('header_corner',(1.06066,1.06066,6.4)),('thin_wire',(.3,2.86,.3)),('keys',(10,.4,5.9)),('aux_rim',(9.5,5.7,.752))]
    with Client(URL,timeout=180) as c:
        c.tool('create_project',{'name':'navigation_terminal_uv_sample','format':'bedrock'})
        sample=structured(c.tool('get_project_info',{}))['project']['uuid'];assert sample!=original
        c.tool('add_group',{'name':'samples'})
        c.tool('place_cube',{'group':'samples','elements':[{'name':name,'from':[i*30,0,0],'to':[i*30+s[0],s[1],s[2]]} for i,(name,s) in enumerate(samples)],'faces':False})
        code='''(async()=>{Undo.initEdit({elements:Cube.all,uv_mode:true});Project.box_uv=false;for(const e of Cube.all){e.box_uv=false;for(const f of Object.values(e.faces))f.texture=false;}Undo.finishEdit("Configure sample face UV");await TextureGenerator.generateTemplate({resolution:32,rearrange_uv:true,box_uv:false,double_use:false,padding:true,power:true,color:false},data=>new Texture({name:"sample.png"}).fromDataURL(data).add(false));await new Promise(r=>setTimeout(r,120));return Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false})})()'''
        model=structured(c.tool('risky_eval',{'code':code}))
        (ROOT/'reference/native_density_sample.bbmodel').write_text(json.dumps(model),encoding='utf8')
        t=model['textures'][0];sx=t['width']/model['resolution']['width'];sy=t['height']/model['resolution']['height'];checks=[]
        for e in model['elements']:
            x,y,z=[b-a for a,b in zip(e['from'],e['to'])]
            for face,d in e['faces'].items():
                w,h=(x,y) if face in ['north','south'] else ((z,y) if face in ['east','west'] else (x,z))
                actual=[abs(d['uv'][2]-d['uv'][0])*sx,abs(d['uv'][3]-d['uv'][1])*sy];expected=[max(1,math.floor(v*2+.5)) for v in [w,h]]
                checks.append({'name':e['name'],'face':face,'native':actual,'programmatic':expected,'equal':actual==expected})
        c.tool('risky_eval',{'code':'(async()=>{const p=ModelProject.all.find(p=>p.uuid===SAMPLE);if(!p)throw Error("Missing sample");p.select();await p.close(true);const original=ModelProject.all.find(p=>p.uuid===ORIGINAL);if(!original)throw Error("Missing delivery project");original.select();return true})()'.replace('SAMPLE',json.dumps(sample)).replace('ORIGINAL',json.dumps(original))})
    report={'editor':'Blockbench 5.2.2','density':32,'faces_checked':len(checks),'pass':all(v['equal'] for v in checks),'checks':checks,'full_native_repack':'not completed; full template operation cancelled after background timer slowdown','production_layout':'independent custom atlas packing with native face pixel dimensions, 1 pixel gutters'}
    (ROOT/'reference/native_density_sample.json').write_text(json.dumps(report,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='checks'}))
