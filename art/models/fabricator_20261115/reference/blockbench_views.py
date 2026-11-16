"""Open the project in a dedicated Blockbench window, export and render four views.

Start Blockbench with --remote-debugging-port=19479 and its own --user-data-dir.
Requires Python, websocket-client and a running desktop Blockbench.
"""
import base64
import argparse
import json
import urllib.request
from pathlib import Path
import websocket

ROOT=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser()
parser.add_argument('--port',type=int,default=19479)
args=parser.parse_args()
pages=json.load(urllib.request.urlopen(f'http://127.0.0.1:{args.port}/json/list', timeout=10))
page=next(x for x in pages if x['type']=='page' and 'blockbench' in x['url'].lower())
ws=websocket.create_connection(page['webSocketDebuggerUrl'],suppress_origin=True,timeout=35)
script=r'''(async()=>{
const dir=ROOT, images={};
const path=dir+'/fabricator.bbmodel',geo=dir+'/fabricator.geo.json';
const old=ModelProject.all.find(p=>p.save_path?.replaceAll('\\','/')===path);
if(old){if(!old.saved)throw Error('Save edits before rebuilding previews');await old.close(true);}
await loadModelFile({path,content:PROJECT_DATA});
await new Promise(r=>setTimeout(r,450));
if(Project.save_path?.replaceAll('\\','/')!==path)throw Error('Wrong project loaded');
const atlas=Texture.all[0];
if (!atlas) throw Error('Missing texture');
await atlas.img.decode();
if (atlas.img.naturalWidth!==512 || atlas.img.naturalHeight!==512) throw Error('Texture dimensions differ');
const editable=Project;
Canvas.updateAll();
const compiled=Codecs.bedrock.compile();
const native=JSON.parse(compiled);

const box=new THREE.Box3();
for(const c of Cube.all){
  const mesh=c.mesh;mesh.updateWorldMatrix(true,false);
  mesh.geometry.computeBoundingBox();
  box.union(mesh.geometry.boundingBox.clone().applyMatrix4(mesh.matrixWorld));
}
const center=box.getCenter(new THREE.Vector3()),size=box.getSize(new THREE.Vector3());
const pane=Preview.selected,scene=Canvas.scene;
const modelRoots=new Set();
for(const c of Cube.all){let o=c.mesh;while(o.parent && o.parent!==scene)o=o.parent;modelRoots.add(o);}
const extras=scene.children.filter(o=>o.name!=='lights' && !modelRoots.has(o));
const previous=extras.map(o=>o.visible),bg=scene.background;
// Each view uses the active Blockbench meshes, their materials and editor lighting.
const rr=pane.renderer,ratio=rr.getPixelRatio(),rrSize=rr.getSize(new THREE.Vector2());
const clear=rr.getClearColor(new THREE.Color()).clone(),alpha=rr.getClearAlpha();
const direction=new THREE.Vector3(.68,.43,1.65).normalize();
try{
  scene.background=null;extras.forEach(o=>o.visible=false);
  rr.setPixelRatio(1);rr.setSize(1200,900,false);rr.setClearColor(0,0);
  for(const view of ['front','back','side','oblique']){
    let camera;
    if(view==='oblique'){
      camera=new THREE.PerspectiveCamera(30,4/3,0.1,500);
      camera.position.copy(center).addScaledVector(direction,122);
    }else{
      const hh=size.y*1.15/2,hw=hh*4/3;
      camera=new THREE.OrthographicCamera(-hw,hw,hh,-hh,.1,500);
      camera.position.copy(center).add(view==='side'?new THREE.Vector3(150,0,0):new THREE.Vector3(0,0,view==='front'?150:-150));
    }
    camera.lookAt(center);camera.updateProjectionMatrix();
    Canvas.withoutGizmos(()=>{rr.clear();rr.render(scene,camera);});
    images[view]=rr.domElement.toDataURL('image/png').split(',')[1];
  }
}finally{
  extras.forEach((o,i)=>o.visible=previous[i]);scene.background=bg;rr.setPixelRatio(ratio);
  rr.setSize(rrSize.x,rrSize.y,false);rr.setClearColor(clear,alpha);pane.render();
}
const report={version:Blockbench.version,format:Format.id,cubes:Cube.all.length,bones:Group.all.length,
  texture:Texture.all.map(t=>[t.name,t.width,t.height]),bounds:[box.min.toArray(),box.max.toArray()]};
{
  await loadModelFile({path:geo,content:compiled});
  const imported=Project;
  report.geometry_roundtrip=JSON.stringify(native)===JSON.stringify(JSON.parse(Codecs.bedrock.compile()));
  imported.saved=true;await imported.close(true);editable.select();

}
if(pane.isOrtho)pane.setProjectionMode(false);
pane.controls.target.copy(center);pane.camera.position.copy(center).addScaledVector(direction,91);
pane.camera.lookAt(center);pane.controls.update();pane.render();
return {report,images,compiled};
})()'''.replace('ROOT',json.dumps(ROOT.as_posix())).replace('PROJECT_DATA',json.dumps((ROOT/'fabricator.bbmodel').read_text(encoding='utf-8')))
ws.send(json.dumps(dict(id=1,method='Runtime.evaluate',params=dict(expression=script,awaitPromise=True,returnByValue=True))))
while True:
    reply=json.loads(ws.recv())
    if reply.get('id')==1: break
ws.close()
result=reply['result']
if 'exceptionDetails' in result:
    raise RuntimeError(result['exceptionDetails'].get('exception',result['exceptionDetails']))
data=result['result']['value']
if not data['report']['geometry_roundtrip']:
    raise RuntimeError('Bedrock round trip differs')
for view in ('front','back','side','oblique'):
    (ROOT/f'preview_{view}.png').write_bytes(base64.b64decode(data['images'][view]))
(ROOT/'fabricator.geo.json').write_text(data['compiled'],encoding='utf-8')
(ROOT/'reference'/'native_check.json').write_text(json.dumps(data['report'],indent=2)+'\n',encoding='utf-8')
print(json.dumps(data['report'],ensure_ascii=False))
