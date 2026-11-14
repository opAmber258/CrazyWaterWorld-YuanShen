"""Blockbench project load/save and native Bedrock export through local API."""
import json,sys,base64
from pathlib import Path
from bb_api import BlockbenchMCP
OUT=Path(__file__).resolve().parent.parent
c=BlockbenchMCP()
cmd=sys.argv[1]
if cmd=='load':
 p=OUT/(sys.argv[2] if len(sys.argv)>2 else 'fabricator.bbmodel')
 code='''(async()=>{const p=PATH;const old=ModelProject.all.find(pj=>pj.save_path===p);
 if(old){if(!old.saved)throw Error('Unsaved edits: refusing reload');old.select();await old.close(true);}
 await loadModelFile({path:p,content:CONTENT});unselectAllElements();Canvas.updateAll();Preview.selected.setProjectionMode(false);
 Preview.selected.controls.target.set(0,26,0);Preview.selected.camera.position.set(-78,61,115);Preview.selected.controls.update();
 return {name:Project.name,path:Project.save_path,cubes:Cube.all.length,groups:Group.all.length};})()'''.replace('PATH',json.dumps(str(p))).replace('CONTENT',json.dumps(p.read_text(encoding='utf8')))
 print(json.dumps(c.eval_js(code)))
elif cmd=='export':
 data=c.eval_js('''(()=>{if(Project.name!=='fabricator')throw Error('Wrong project');
 return {geo:Codecs.bedrock.compile(),project:Codecs.project.compile({absolute_paths:false})};})()''')
 (OUT/'fabricator.geo.json').write_text(data['geo'],encoding='utf8')
 (OUT/'fabricator.bbmodel').write_text(data['project'],encoding='utf8')
 tex=json.loads(data['project'])['textures'][0]['source']
 (OUT/'fabricator_tex.png').write_bytes(base64.b64decode(tex.split(',')[1]))
 print(c.eval_js('Project.saved=true;({format:Format.id,cubes:Cube.all.length,groups:Group.all.length})'))
elif cmd=='eval':print(json.dumps(c.eval_js(Path(sys.argv[2]).read_text(encoding='utf8')),ensure_ascii=False))
