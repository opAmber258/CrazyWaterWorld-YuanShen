"""Operate only this batch through the existing Blockbench MCP server."""
import json,sys
from pathlib import Path
from bb_mcp_client import BlockbenchMCP
OUT=Path(__file__).resolve().parent.parent
c=BlockbenchMCP()
if sys.argv[1]=='load':
    path=OUT/'engine.bbmodel'
    code='''(async()=>{
const fs=require('fs'),path=PATH;
const old=ModelProject.all.find(p=>p.save_path===path);
if(old){if(!old.saved)throw Error('Unsaved edits; refusing reload');old.select();await old.close(true);}
await loadModelFile({path,content:fs.readFileSync(path,'utf8')});
unselectAllElements();Canvas.updateAll();Preview.selected.setProjectionMode(false);
Preview.selected.controls.target.set(0,21,0);Preview.selected.camera.position.set(-100,70,120);Preview.selected.controls.update();
return {name:Project.name,cubes:Cube.all.length,bones:Group.all.length,animations:Animation.all.map(a=>({name:a.name,length:a.length,keyframes:Object.values(a.animators).reduce((n,b)=>n+b.keyframes.length,0)})),bevel:Cube.all.find(c=>c.name==='end_continuous_bevel').mesh.rotation.toArray()};
})()'''.replace('PATH',json.dumps(str(path)))
elif sys.argv[1]=='export':
    code='''(()=>{const fs=require('fs'),dir=DIR;
if(Project.save_path.split(String.fromCharCode(92)).join('/')!==dir+'/engine.bbmodel')throw Error('Wrong project');
const project=Codecs.project.compile({absolute_paths:false});fs.writeFileSync(dir+'/engine.bbmodel',project);
fs.writeFileSync(dir+'/engine.geo.json',Codecs.bedrock.compile());
fs.writeFileSync(dir+'/engine_tex.png',Buffer.from(JSON.parse(project).textures[0].source.split(',')[1],'base64'));
const animations={};for(const a of Animation.all)animations[a.name]=a.compileBedrockAnimation();
fs.writeFileSync(dir+'/engine.animation.json',JSON.stringify({format_version:'1.8.0',animations},null,2));Project.saved=true;
return {cubes:Cube.all.length,bones:Group.all.length,clips:Object.keys(animations)};
})()'''.replace('DIR',json.dumps(OUT.as_posix()))
else:code=Path(sys.argv[2]).read_text(encoding='utf-8')
result=c.eval_js(code)
print(result if isinstance(result,str) else json.dumps(result,ensure_ascii=False))
