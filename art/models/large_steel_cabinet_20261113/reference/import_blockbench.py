from pathlib import Path
import json
from mcp_client import call
ROOT=Path(__file__).resolve().parents[1]
spec=json.loads((ROOT/'reference/model_spec.json').read_text(encoding='utf8'))
code='''(()=>{
const s=SPEC;
if(Project && Project.name===s.name){Undo.initEdit({outliner:true,elements:Cube.all.slice(),textures:Texture.all.slice()}); for(const e of Outliner.root.slice())e.remove();for(const t of Texture.all.slice())t.remove();Undo.finishEdit('Replace cabinet revision');}else{newProject(Formats.bedrock);}
Project.name=s.name; Project.geometry_name=s.name; Project.texture_width=s.texture_size;Project.texture_height=s.texture_size;
Project.visible_box=[4.5,3.5,1.25];
const gs={};
Undo.initEdit({outliner:true,elements:[],textures:[]});
for (const g of s.groups) { gs[g.name]=new Group({name:g.name,origin:g.origin}).init(); if(g.parent) gs[g.name].addTo(gs[g.parent]); }
const tex=new Texture({name:s.name+'_tex.png',id:'0',width:s.texture_size,height:s.texture_size,uv_width:s.texture_size,uv_height:s.texture_size}).fromDataURL('data:image/png;base64,'+s.texture).add(false);
for (const c of s.cubes) {
 const el=new Cube({name:c.name,from:c.from_,to:c.to,origin:c.origin,rotation:c.rotation,box_uv:false,autouv:0}).addTo(gs[c.group]).init();
 for(const [f,d] of Object.entries(c.faces)){el.faces[f].uv=d.uv;el.faces[f].texture=tex.uuid;}
}
Undo.finishEdit('Build large steel cabinet');
Canvas.updateAll();unselectAll();
return {uuid:Project.uuid,cubes:Cube.all.length,groups:Group.all.length,textures:Texture.all.length};
})()'''.replace('SPEC',json.dumps(spec).replace('/',r'\u002f'))
r=call('risky_eval',{'code':code})
(ROOT/'reference/native_build_result.json').write_text(json.dumps(r,indent=2),encoding='utf8')
print(r)
