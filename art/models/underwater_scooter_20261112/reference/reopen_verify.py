"""Reopen the saved self-contained model with Blockbench's native codec.

Only the project UUID in blockbench_session.json may be saved/closed.
All other open projects are preserved. Captures use native Blockbench views.
"""
import argparse
import base64
import io
import json
from PIL import Image,ImageChops
from bb_client import Client,structured
from blockbench_task import ROOT,REF,STATE,check,save,exported,js,render,write_json

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--url',default='http://localhost:3015/bb-mcp');a=parser.parse_args()
    with Client(a.url,timeout=60) as c:
        st=check(c);save(c)
        before=json.loads((ROOT/'underwater_scooter.bbmodel').read_text(encoding='utf-8'))
        geo=json.loads((ROOT/'underwater_scooter.geo.json').read_text(encoding='utf-8'))
        protected=js(c,'(()=>({projects:ModelProject.all.map(p=>({uuid:p.uuid,name:p.name,saved:p.saved}))}))()')['projects']
        payload=json.dumps(json.dumps(before,ensure_ascii=True)).replace('/',r'\u002f')
        path=json.dumps(str(ROOT/'underwater_scooter.bbmodel'))
        result=js(c,"(()=>{let original=Project.uuid;Codecs.project.load(JSON.parse("+payload+"),{path:"+path+"});Project.saved=true;return {old_project:original,new_project:Project.uuid,name:Project.name,format:Format.id,textures:Texture.all.length,cubes:Cube.all.length}})()")
        if result['new_project']==st['project_id'] or result['name']!='underwater_scooter' or result['format']!='bedrock':
            raise RuntimeError('Native reopen did not create the expected new project')
        st['project_id']=result['new_project'];write_json(STATE,st)
        after=json.loads(exported(c,'project'));after_geo=json.loads(exported(c,'bedrock'))
        tex_before=Image.open(io.BytesIO(base64.b64decode(before['textures'][0]['source'].split(',',1)[1]))).convert('RGBA')
        tex_after=Image.open(io.BytesIO(base64.b64decode(after['textures'][0]['source'].split(',',1)[1]))).convert('RGBA')
        tests={'native_reopen':True,'cube_count_preserved':len(before['elements'])==len(after['elements']),
               'geometry_export_exact':geo==after_geo,
               'embedded_texture_pixels_exact':tex_before.size==tex_after.size and not ImageChops.difference(tex_before,tex_after).getbbox(),
               'no_external_texture_paths':all(not t.get('path') and not t.get('relative_path') for t in before['textures'])}
        if not all(tests.values()):
            write_json(REF/'reopen_verification.json',{'checks':tests,'result':result});raise RuntimeError('Native reopen validation failed')
        render(c,REF/'work'/'reopen_renders',extra=True)
        rows=[]
        for f in sorted(ROOT.glob('preview_*.png')):
            im=Image.open(f).convert('RGBA');out=Image.open(REF/'work'/'reopen_renders'/f.name).convert('RGBA')
            diff=ImageChops.difference(im,out);mask=Image.new('L',im.size)
            for band in diff.split():mask=ImageChops.lighter(mask,band)
            rows.append({'name':f.name,'changed_pixels':sum(v for i,v in enumerate(mask.histogram()) if i),'pixel_identical':not any(hi for lo,hi in diff.getextrema())})
        tests['eight_native_renders_pixel_identical']=all(x['pixel_identical'] for x in rows)
        close=js(c,"(async()=>{let old=ModelProject.all.find(p=>p.uuid==="+json.dumps(result['old_project'])+");let reopened=Project;if(old){await old.close(true);reopened.select()}return {active:Project.uuid,projects:ModelProject.all.map(p=>({uuid:p.uuid,name:p.name}))}})()")
        tests['other_open_projects_preserved']=all(any(p['uuid']==q['uuid'] for q in close['projects']) for p in protected if p['uuid']!=result['old_project'])
        c.tool('set_camera_angle',{'view':'active','position':[-18,14,22],'target':[0.45,4.35,0],'projection':'perspective','fov':34,'max_size':200})
        write_json(REF/'reopen_verification.json',{'checks':tests,'result':result,'render_comparison':rows,'other_projects':close['projects']})
        print(json.dumps({'checks':tests,'changed_pixels':[x['changed_pixels'] for x in rows],'active_project':close['active']}))
if __name__=='__main__':main()
