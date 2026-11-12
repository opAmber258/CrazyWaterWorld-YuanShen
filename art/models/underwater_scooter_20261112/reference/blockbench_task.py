"""Native Blockbench MCP build, texture, export and screenshot workflow.

Requires an open Blockbench 5.2.2 with MCP 1.10.1 and an unauthenticated
loopback endpoint. The endpoint can be selected with --url. Never touches
other projects after creating this task's new project.
"""
import argparse
import base64
import json
import uuid
from pathlib import Path
from PIL import Image
from bb_client import Client, structured

ROOT=Path(__file__).resolve().parents[1]
REF=ROOT/'reference'
PLAN=json.loads((REF/'model_plan.json').read_text(encoding='utf-8'))
STATE=REF/'blockbench_session.json'
FACES=['north','south','east','west','up','down']

def write_json(p,d):p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')
def check(c):
    st=json.loads(STATE.read_text(encoding='utf-8'))
    current=structured(c.tool('get_project_info',{}))
    if current.get('project',{}).get('uuid')!=st['project_id']:
        raise RuntimeError('Active project changed; no batch mutation was performed')
    return st
def js(c,code):return structured(c.tool('risky_eval',{'code':code}))
def exported(c,codec):
    r=structured(c.tool('export_model',{'codec_id':codec,'options':{'bitmaps':True},'max_content_length':2000000}))
    content=r.get('content')
    if content is None:content=r.get('metadata',{}).get('content')
    if content is None:raise ValueError('Export response omitted content: '+str(r)[:1000])
    return content if isinstance(content,str) else json.dumps(content,ensure_ascii=False,indent=2)
def save(c,gray=False):
    check(c)
    dest=REF/'work' if gray else ROOT
    dest.mkdir(exist_ok=True)
    project=json.loads(exported(c,'project'))
    for tex in project.get('textures',[]):
        for key in ('path','relative_path'):tex.pop(key,None)
    project.pop('export_path',None)
    write_json(dest/('gray_baseline.bbmodel' if gray else 'underwater_scooter.bbmodel'),project)
    if not gray:
        (ROOT/'underwater_scooter.geo.json').write_text(exported(c,'bedrock'),encoding='utf-8')
    return {'saved_to':str(dest),'cubes':len(project.get('elements',[])),'textures':len(project.get('textures',[]))}

def build(c):
    if STATE.exists():raise RuntimeError('This model already has a live session; use a fresh project folder to rebuild')
    before=structured(c.tool('get_project_info',{}))
    c.tool('create_project',{'name':'underwater_scooter','format':'bedrock'})
    info=structured(c.tool('get_project_info',{}))
    write_json(STATE,{'project_id':info['project']['uuid'],'original_project_id':before['project']['uuid']})
    js(c,"(()=>{Undo.initEdit({uv_mode:true});Project.texture_width=128;Project.texture_height=128;Project.box_uv=false;Project.model_identifier='underwater_scooter';Project.visible_box=[2.0,1.7,0.4];Undo.finishEdit('Set scooter project UV and visibility');return {project:Project.uuid,width:Project.texture_width,height:Project.texture_height}})()")
    c.tool('create_texture',{'name':'scooter_gray_working','width':128,'height':128,'fill_color':'#919a9a'})
    c.tool('add_group',{'name':'scooter','origin':[0,0,0]})
    for group in PLAN['groups']:
        c.tool('add_group',{'name':group,'parent':'scooter','origin':[0,0,0]})
        elements=[{k:x[k] for k in ('name','from','to','origin','rotation')} for x in PLAN['cubes'] if x['group']==group]
        c.tool('place_cube',{'group':group,'texture':'scooter_gray_working','elements':elements,
                           'faces':[{'face':face,'uv':[1,1,3,3]} for face in FACES]})
    return save(c,True)

def texture(c):
    check(c)
    image='data:image/png;base64,'+base64.b64encode((ROOT/'underwater_scooter_tex.png').read_bytes()).decode()
    existing=js(c,"(()=>({names:Texture.all.map(t=>t.name)}))()")['names']
    size=Image.open(ROOT/'underwater_scooter_tex.png').size
    if 'underwater_scooter_tex.png' not in existing:
        c.tool('create_texture',{'name':'underwater_scooter_tex.png','width':size[0],'height':size[1],'data':image,'render_sides':'front'})
    else:
        temporary='scooter_atlas_'+uuid.uuid4().hex[:8]
        c.tool('create_texture',{'name':temporary,'width':size[0],'height':size[1],'data':image,'render_sides':'front'})
        c.tool('delete_texture',{'texture':'underwater_scooter_tex.png'})
        js(c,"(()=>{let t=Texture.all.find(t=>t.name==="+json.dumps(temporary)+");Undo.initEdit({textures:[t],uv_mode:true});t.name='underwater_scooter_tex.png';t.uv_width="+str(size[0])+";t.uv_height="+str(size[1])+";Project.texture_width="+str(size[0])+";Project.texture_height="+str(size[1])+";Undo.finishEdit('Set packed scooter atlas resolution');return {width:Project.texture_width,height:Project.texture_height}})()")
    js(c,"(()=>{let t=Texture.all.find(t=>t.name==='underwater_scooter_tex.png');Undo.initEdit({textures:[t],uv_mode:true});t.uv_width="+str(size[0])+";t.uv_height="+str(size[1])+";Project.texture_width="+str(size[0])+";Project.texture_height="+str(size[1])+";Undo.finishEdit('Match scooter UV and bitmap dimensions');return {resolution:[Project.texture_width,Project.texture_height]}})()")
    for item in PLAN['cubes']:
        check(c)
        faces={f:{'uv':PLAN['tiles'][item['face_materials'].get(f,item['material'])]} for f in FACES}
        for f in item['disabled_faces']:faces[f]={'texture':None}
        c.tool('set_cube_uv',{'id':item['name'],'box_uv':False,'faces':faces})
    if 'scooter_gray_working' in existing:c.tool('delete_texture',{'texture':'scooter_gray_working'})
    return save(c)

def render(c,output,prefix='preview',extra=False):
    st=check(c);output.mkdir(parents=True,exist_ok=True)
    target=[0.45,5.0,0]
    views=[
      {'name':'front','position':[0.45,4.35,48],'target':[0.45,4.35,0],'projection':'orthographic','zoom':1.30},
      {'name':'back','position':[0.45,4.35,-48],'target':[0.45,4.35,0],'projection':'orthographic','zoom':1.30},
      {'name':'side','position':[-48,4.35,0],'target':[0.45,4.35,0],'projection':'orthographic','zoom':1.50},
      {'name':'oblique','position':[-18,14,22],'target':[0.45,4.35,0],'projection':'perspective','fov':34},
    ]
    if extra:views.extend([
      {'name':'nose','position':[48,4.35,0],'target':[0.45,4.35,0],'projection':'orthographic','zoom':1.50},
      {'name':'top','position':[0.45,48,0.001],'target':target,'projection':'orthographic','zoom':1.30},
      {'name':'bottom','position':[0.45,-48,0.001],'target':target,'projection':'orthographic','zoom':1.30},
      {'name':'oblique_nose','position':[21,14,-22],'target':[0.45,4.35,0],'projection':'perspective','fov':34},
    ])
    viewid='scooter_'+uuid.uuid4().hex[:8];cleanup=None;reports=[]
    c.tool('create_offscreen_view',{'id':viewid,'width':1200,'height':900,'antialias':True,'copy_view':'none'})
    try:
        for v in views:
            check(c)
            result=c.tool('set_camera_angle',dict(view=viewid,format='png',**{k:val for k,val in v.items() if k!='name'}))
            images=[x for x in result.get('content',[]) if x['type']=='image']
            if len(images)!=1:raise ValueError('Expected one rendered image')
            (output/(prefix+'_'+v['name']+'.png')).write_bytes(base64.b64decode(images[0]['data']))
            reports.append({'name':v['name'],'requested':v,'applied':structured(result).get('view')})
    finally:
        try:c.tool('delete_offscreen_view',{'view':viewid})
        except RuntimeError as error:
            if 'onContextMenu is not defined' not in str(error):raise
            remains=structured(c.tool('list_views',{})).get('views',[])
            verify=js(c,'(()=>({remaining:Preview.all.some(p=>p.id==='+json.dumps('mcp_offscreen_'+viewid)+')}))()')
            if any(v.get('id')==viewid for v in remains) or verify.get('remaining') is not False:raise
            cleanup='Blockbench 5.2.2 controls.dispose reported onContextMenu is not defined after deletion; absence verified in both registries; listener disposal not certified.'
    write_json(output/(prefix+'_capture.json'),{'project':st['project_id'],'size':[1200,900],'renderer':'Blockbench native WebGL offscreen preview','views':reports,'cleanup_warning':cleanup})
    return {'images':len(views),'output':str(output),'cleanup_warning':cleanup}

def main():
    p=argparse.ArgumentParser();p.add_argument('action',choices=['gray','texture','export','render','gray-render','inspect']);p.add_argument('--url',default='http://localhost:3015/bb-mcp');a=p.parse_args()
    with Client(a.url,timeout=60) as c:
        if a.action=='gray':r=build(c)
        elif a.action=='texture':r=texture(c)
        elif a.action=='export':r=save(c)
        elif a.action=='render':r=render(c,ROOT,extra=True)
        elif a.action=='gray-render':r=render(c,REF/'work'/'gray',prefix='gray')
        else:r=structured(c.tool('get_project_info',{}))
        print(json.dumps(r,ensure_ascii=False))
if __name__=='__main__':main()
