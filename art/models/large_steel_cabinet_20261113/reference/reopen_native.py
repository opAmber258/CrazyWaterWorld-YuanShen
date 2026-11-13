from pathlib import Path
import json
from mcp_client import call
ROOT=Path(__file__).resolve().parents[1]
data=json.loads((ROOT/'large_steel_cabinet.bbmodel').read_text(encoding='utf8'))
path=(ROOT/'large_steel_cabinet.bbmodel').as_posix()
code='(()=>{Project.saved=true;const d=DATA;Codecs.project.load(d,{path:PATH,name:"large_steel_cabinet.bbmodel"});Project.box_uv=false;Project.save_path=PATH;Project.export_path=GEO;Project.saved=true;return {name:Project.name,uuid:Project.uuid,cubes:Cube.all.length,bones:Group.all.length,textures:Texture.all.length,saved:Project.saved,path:Project.save_path};})()'
code=code.replace('DATA',json.dumps(data).replace('/',r'\u002f')).replace('PATH',json.dumps(path)).replace('GEO',json.dumps((ROOT/'large_steel_cabinet.geo.json').as_posix()))
r=call('risky_eval',{'code':code})
(ROOT/'reference/reopen_native_result.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf8')
print(r)
