"""Check the openable project and capture its four views in a dedicated Blockbench window.
Run against an empty Blockbench profile started with --remote-debugging-port=PORT.
Dependencies: Pillow, websocket-client. Default port 9238, override with --port.
"""
import argparse
import base64
import json
from pathlib import Path
import urllib.request

import websocket

OUT = Path(__file__).resolve().parents[1]
MODEL = OUT / 'medical_fabricator.bbmodel'
parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=9238)
args = parser.parse_args()
tabs = json.load(urllib.request.urlopen(f'http://127.0.0.1:{args.port}/json/list'))
target = next(t for t in tabs if t['type']=='page' and 'app.asar' in t['url'])
socket = websocket.create_connection(target['webSocketDebuggerUrl'],suppress_origin=True,timeout=45)


def run(source):
    socket.send(json.dumps(dict(id=1,method='Runtime.evaluate',params=dict(
        expression=source,returnByValue=True,awaitPromise=True))))
    while True:
        reply=json.loads(socket.recv())
        if reply.get('id')==1: break
    result=reply['result']
    if 'exceptionDetails' in result: raise RuntimeError(result['exceptionDetails'])
    return result['result'].get('value')


def open_project():
    raw=MODEL.read_text(encoding='utf8')
    return run('(async()=>{await loadModelFile('+json.dumps(dict(path=str(MODEL),content=raw))+');'+
               'await new Promise(r=>setTimeout(r,650));Canvas.updateAll();return [Project.name,Project.save_path,Texture.all[0].error];})()')


state = run('ModelProject.all.map(p=>[p.save_path,p.saved])')
if state:
    if len(state)!=1 or Path(state[0][0])!=MODEL or not state[0][1]:
        raise RuntimeError('Use an empty dedicated Blockbench window')
    run('(async()=>{await Project.close(true)})()')
print('open',open_project())
first=run('Codecs.bedrock.compile()')
# Round-trip through the native editable-project codec, embedding the PNG.
native=run('Codecs.project.compile({bitmaps:true,raw:true,absolute_paths:false})')
if isinstance(native,str): native=json.loads(native)
for tex in native['textures']:
    tex.pop('path',None)
    tex['relative_path']=tex['name']
    if not tex['source'].startswith('data:image/png;base64,'): raise ValueError('Bitmap missing')
MODEL.write_text(json.dumps(native,ensure_ascii=False,indent=2),encoding='utf8')
run('(async()=>{await Project.close(true)})()')
print('reopen',open_project())
second=run('Codecs.bedrock.compile()')
if json.loads(first)!=json.loads(second): raise ValueError('Geometry changed across round trip')
(OUT/'medical_fabricator.geo.json').write_text(second,encoding='utf8')
(OUT/'medical_fabricator_tex.png').write_bytes(base64.b64decode(native['textures'][0]['source'].split(',')[1]))
run('unselectAllElements();Preview.selected.setProjectionMode(false);Preview.selected.controls.target.set(0,18,0);Preview.selected.camera.position.set(42,43,88);Preview.selected.controls.update();Preview.selected.render();true')
capture = """
(view=>{
const p=Preview.selected, scene=Canvas.scene, r=p.renderer;
const roots=new Set();
const box=new THREE.Box3();
for(const c of Cube.all){
  const mesh=c.mesh;
  mesh.updateWorldMatrix(true,false);
  mesh.geometry.computeBoundingBox();
  box.union(mesh.geometry.boundingBox.clone().applyMatrix4(mesh.matrixWorld));
  let top=mesh;
  while(top.parent && top.parent!==scene) top=top.parent;
  roots.add(top);
}
const hidden=scene.children.filter(o=>!roots.has(o)&&o.name!=='lights');
const visibility=hidden.map(o=>o.visible), bg=scene.background;
const clear=r.getClearColor(new THREE.Color()), alpha=r.getClearAlpha();
const size=r.getSize(new THREE.Vector2()), ratio=r.getPixelRatio();
const center=box.getCenter(new THREE.Vector3());
const cam=view==='oblique'?new THREE.PerspectiveCamera(27,4/3,.1,600):new THREE.OrthographicCamera(-29,29,21.75,-21.75,.1,600);
const direction={front:[0,0,1],back:[0,0,-1],side:[1,0,0],oblique:[.48,.38,1]}[view];
cam.position.copy(center).addScaledVector(new THREE.Vector3(...direction).normalize(),view==='oblique'?103:120);
cam.lookAt(center);cam.updateProjectionMatrix();
try {
  hidden.forEach(o=>o.visible=false);scene.background=null;
  r.setPixelRatio(1);r.setSize(1200,900,false);r.setClearColor(0,0);
  let url;
  Canvas.withoutGizmos(()=>{r.render(scene,cam);url=r.domElement.toDataURL('image/png')});
  return {image:url,min:box.min.toArray(),max:box.max.toArray()};
} finally {
  hidden.forEach((o,i)=>o.visible=visibility[i]);scene.background=bg;
  r.setClearColor(clear,alpha);r.setPixelRatio(ratio);r.setSize(size.x,size.y,false);p.render();
}
})
"""
for name in ('front','side','back','oblique'):
    data=run(capture+'('+json.dumps(name)+')')
    (OUT/f'preview_{name}.png').write_bytes(base64.b64decode(data['image'].split(',')[1]))
info=run('({version:Blockbench.version,format:Format.id,cubes:Cube.all.length,bones:Group.all.length,textures:Texture.all.length, texture_error:Texture.all[0].error})')
info.update(min=data['min'],max=data['max'],reopened_export_equal=True)
(OUT/'reference'/'native_check.json').write_text(json.dumps(info,indent=2),encoding='utf8')
print(info)

