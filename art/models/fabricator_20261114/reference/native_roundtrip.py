"""Reopen delivered project, compare native export, import/re-export geometry."""
import json
from pathlib import Path
from bb_api import BlockbenchMCP
OUT=Path(__file__).resolve().parent.parent
c=BlockbenchMCP()
bbpath=OUT/'fabricator.bbmodel';geopath=OUT/'fabricator.geo.json'
bbtext=bbpath.read_text(encoding='utf8');geotext=geopath.read_text(encoding='utf8')
code='''(async()=>{const path=PATH;const old=ModelProject.all.find(p=>p.save_path===path);
 if(old){if(!old.saved)throw Error('Unsaved edits');old.select();await old.close(true);}
 await loadModelFile({path,content:BB});await new Promise(r=>setTimeout(r,200));
 const editable=Project;const reopened={blockbench_version:Blockbench.version,name:Project.name,format:Format.id,cubes:Cube.all.length,bones:Group.all.length,textures:Texture.all.length,geo:JSON.parse(Codecs.bedrock.compile())};
 await loadModelFile({path:GEOPATH,content:GEO});await new Promise(r=>setTimeout(r,200));
 const imported=Project;const roundtrip={format:Format.id,cubes:Cube.all.length,bones:Group.all.length,reexport:JSON.parse(Codecs.bedrock.compile())};
 imported.saved=true;await imported.close(true);editable.select();
 for(const p of [...ModelProject.all])if(p!==editable&&p.name==='fabricator_gray'&&p.saved)await p.close(true);
 editable.select();unselectAllElements();Canvas.updateAll();Preview.selected.setProjectionMode(false);
 Preview.selected.controls.target.set(0,26,0);Preview.selected.camera.position.set(-75,57,105);Preview.selected.controls.update();
 return {reopened,roundtrip};})()'''.replace('GEOPATH',json.dumps(str(geopath))).replace('PATH',json.dumps(str(bbpath))).replace('BB',json.dumps(bbtext)).replace('GEO',json.dumps(geotext))
report=c.eval_js(code);disk=json.loads(geotext)
reopened=report['reopened'];reopened['exact_reopened_export_match']=reopened['geo']==disk
(OUT/'reference/reopened_export.geo.json').write_text(json.dumps(reopened.pop('geo'),indent=2))
(OUT/'reference/reopened_project_check.json').write_text(json.dumps(reopened,indent=2))
rt=report['roundtrip'];rt['exact_native_reexport']=rt['reexport']==disk
(OUT/'reference/native_roundtrip.json').write_text(json.dumps(rt,indent=2))
assert reopened['exact_reopened_export_match'] and rt['exact_native_reexport']
print(json.dumps({'reopened':reopened,'native_geo_roundtrip_exact':rt['exact_native_reexport']}))
