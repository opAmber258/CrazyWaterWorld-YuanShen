from pathlib import Path
import json
from bb_client import Client,structured
R=Path(__file__).resolve().parent
with Client('http://127.0.0.1:3015/bb-mcp',timeout=55) as c:
 assert structured(c.tool('get_project_info',{}))['project']['uuid']==json.loads((R/'live_state.json').read_text())['uuid']
 out=structured(c.tool('risky_eval',{'code':'(()=>({faces:Cube.all.filter(e=>{let g=e.parent;while(g&&g!=="root"){if(g.name==="armorHead")return e.name!=="translucent_visor";g=g.parent;}return false;}).flatMap(e=>{e.mesh.updateMatrixWorld(true);const g=e.mesh.geometry,p=g.attributes.position,uv=g.attributes.uv;return Canvas.face_order.filter(f=>e.faces[f].texture!==null).map(f=>{const k=Canvas.face_order.indexOf(f);return {name:e.name,export:e.export,face:f,vertices:[0,1,2,3].map(j=>{const i=k*4+j;return {position:new THREE.Vector3(p.getX(i),p.getY(i),p.getZ(i)).applyMatrix4(e.mesh.matrixWorld).toArray(),uv:[uv.getX(i)*256,(1-uv.getY(i))*256]};})};});}),restored:{neutral:Group.all.every(g=>g.mesh.rotation.x===0&&g.mesh.rotation.y===0&&g.mesh.rotation.z===0),materials:typeof viewportR4Capture==="undefined"}}))()'}))
 (R/'native_faces.json').write_text(json.dumps(out,indent=2),encoding='utf-8');print('native faces',len(out['faces']))
