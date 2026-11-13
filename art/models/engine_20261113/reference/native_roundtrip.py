"""Verify native Bedrock import and re-export, then restore the editable project."""
import json
from pathlib import Path
from bb_mcp_client import BlockbenchMCP
OUT=Path(__file__).resolve().parent.parent
c=BlockbenchMCP()
code=r'''(async()=>{
const fs=require('fs'),dir=DIR;
const editable=ModelProject.all.find(p=>p.save_path?.split(String.fromCharCode(92)).join('/')===dir+'/engine.bbmodel');
if(!editable)throw Error('Editable project not open');
editable.select();if(!editable.saved)throw Error('Unsaved edits');
const before=JSON.parse(Codecs.bedrock.compile());
const file=dir+'/engine.geo.json';
await loadModelFile({path:file,content:fs.readFileSync(file,'utf8')});
await new Promise(r=>setTimeout(r,250));
const imported=Project;
const report={name:Project.name,cubes:Cube.all.length,bones:Group.all.length,format:Format.id,reexport:JSON.parse(Codecs.bedrock.compile())};
report.exact_native_reexport=JSON.stringify(before)===JSON.stringify(report.reexport);
const tex=fs.readFileSync(dir+'/engine_tex.png').toString('base64');
if(!Texture.all.length)new Texture({name:'engine_tex.png'}).fromDataURL('data:image/png;base64,'+tex).add(false);
Cube.all.forEach(c=>Object.values(c.faces).forEach(f=>f.texture=Texture.all[0].uuid));Canvas.updateAll();
report.textures=Texture.all.length;
fs.writeFileSync(dir+'/reference/native_roundtrip.json',JSON.stringify(report,null,2));
imported.saved=true;await imported.close(true);editable.select();
return {cubes:report.cubes,bones:report.bones,exact_native_reexport:report.exact_native_reexport,restored:Project.name};
})()'''.replace('DIR',json.dumps(OUT.as_posix()))
print(c.eval_js(code))
