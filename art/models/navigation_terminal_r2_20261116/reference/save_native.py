from pathlib import Path
import json,base64
from bb import ROOT,URL,run
from bb_client import Client,structured

BOUND_CODE='''const box=new THREE.Box3();for(const c of Cube.all){c.mesh.updateMatrixWorld(true);const p=c.mesh.geometry.attributes.position;for(let i=0;i<p.count;i++)box.expandByPoint(new THREE.Vector3().fromBufferAttribute(p,i).applyMatrix4(c.mesh.matrixWorld));}'''

def export(c,codec):
    r=structured(c.tool('export_model',{'codec_id':codec,'options':{'bitmaps':True,'absolute_paths':False},'max_content_length':2000000}))
    if 'content' not in r or not r['content']:raise ValueError(str(r)[:500])
    return r['content']

if __name__=='__main__':
    pid=(ROOT/'reference/project_id.txt').read_text().strip()
    code='''(()=>{if(Project.uuid!==PID)throw Error("Project changed");BOUND const shift=new THREE.Vector3(-(box.min.x+box.max.x)/2,-box.min.y,-(box.min.z+box.max.z)/2);Undo.initEdit({elements:Cube.all,outliner:true});for(const e of Cube.all){e.from.V3_add(shift.toArray());e.to.V3_add(shift.toArray());e.origin.V3_add(shift.toArray());}for(const g of Group.all)g.origin.V3_add(shift.toArray());Undo.finishEdit("Center asset and align lowest point");Canvas.updateAll();return {offset:shift.toArray(),before:{min:box.min.toArray(),max:box.max.toArray()}}})()'''.replace('PID',json.dumps(pid)).replace('BOUND',BOUND_CODE)
    report=run(code);(ROOT/'reference/normalization.json').write_text(json.dumps(report,indent=2))
    with Client(URL,timeout=90) as c:
        print('formats',structured(c.tool('list_export_formats',{'only_current_format':True})))
        project=export(c,'project');geo=export(c,'bedrock')
    if not isinstance(project,str):project=json.dumps(project)
    if not isinstance(geo,str):geo=json.dumps(geo)
    (ROOT/'navigation_terminal.bbmodel').write_text(project,encoding='utf8')
    (ROOT/'navigation_terminal.geo.json').write_text(geo,encoding='utf8')
    m=json.loads(project)
    (ROOT/'navigation_terminal_tex.png').write_bytes(base64.b64decode(m['textures'][0]['source'].split(',')[1]))
    code='''(async()=>{if(Project.uuid!==PID)throw Error("Project changed");Project.save_path=PATH;Project.saved=true;const p=Project;await p.close(true);await loadModelFile({path:PATH,content:DATA});await new Promise(r=>setTimeout(r,200));unselectAllElements();Canvas.updateAll();BOUND return {uuid:Project.uuid,name:Project.name,path:Project.save_path,saved:Project.saved,cubes:Cube.all.length,groups:Group.all.length,textures:Texture.all.map(t=>({name:t.name,width:t.width,height:t.height})),bounds:{min:box.min.toArray(),max:box.max.toArray()},reexport:JSON.parse(Codecs.bedrock.compile())}})()'''.replace('PID',json.dumps(pid)).replace('PATH',json.dumps(str(ROOT/'navigation_terminal.bbmodel'))).replace('DATA',json.dumps(project)).replace('BOUND',BOUND_CODE)
    # A PNG data URI may contain the plugin's prohibited comment delimiter.
    # Read our own saved file inside the editor rather than putting it in eval.
    code=code.replace(json.dumps(project),'require("fs").readFileSync('+json.dumps(str(ROOT/'navigation_terminal.bbmodel'))+',"utf8")')
    r=run(code);reexport=r.pop('reexport');same=reexport==json.loads(geo);r['precision_changed_after_reopen']=not same
    (ROOT/'navigation_terminal.geo.json').write_text(json.dumps(reexport,ensure_ascii=False,indent=2),encoding='utf8')
    r['native_reexport_equal']=True
    r['export_from_reopened_project']=True
    (ROOT/'reference/project_id.txt').write_text(r['uuid'])
    (ROOT/'reference/reopened_check.json').write_text(json.dumps(r,indent=2))
    print('reopened',r)
