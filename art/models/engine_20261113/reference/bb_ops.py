"""Blockbench MCP helper. Preserves unrelated open projects."""
import json, sys
from pathlib import Path
from bb_mcp_client import BlockbenchMCP
OUT=Path(__file__).resolve().parent.parent
c=BlockbenchMCP()
def run(code):
    result=c.eval_js(code)
    print(json.dumps(result,ensure_ascii=False) if not isinstance(result,str) else result)
    return result
cmd=sys.argv[1]
if cmd=='load':
    p=OUT/(sys.argv[2] if len(sys.argv)>2 else 'engine.bbmodel')
    run('''(async()=>{const fs=require('fs');const p=PATH;
    const old=ModelProject.all.find(pj=>pj.save_path===p);
    if(old){if(!old.saved)throw Error('Unsaved edits: refusing reload');old.select();await old.close(true);}
    await loadModelFile({path:p,content:fs.readFileSync(p,'utf8')});
    unselectAllElements();Canvas.updateAll();Preview.selected.setProjectionMode(false);
    Preview.selected.controls.target.set(0,21,0);Preview.selected.camera.position.set(-95,75,120);Preview.selected.controls.update();
    return {name:Project.name,path:Project.save_path,cubes:Cube.all.length,groups:Group.all.length};})()'''.replace('PATH',json.dumps(str(p))))
elif cmd=='export':
    run('''(()=>{const fs=require('fs');const dir=DIR;
    if(Project.save_path!==dir+'\\\\engine.bbmodel')throw Error('Wrong project');
    fs.writeFileSync(dir+'/engine.geo.json',Codecs.bedrock.compile());
    const data=Codecs.project.compile({absolute_paths:false});
    fs.writeFileSync(dir+'/engine.bbmodel',data);
    fs.writeFileSync(dir+'/engine_tex.png',Buffer.from(JSON.parse(data).textures[0].source.split(',')[1],'base64'));
    Project.saved=true;
    return {format:Format.id,cubes:Cube.all.length,groups:Group.all.length};})()'''.replace('DIR',json.dumps(str(OUT))))
elif cmd=='eval':
    run(Path(sys.argv[2]).read_text(encoding='utf-8'))
