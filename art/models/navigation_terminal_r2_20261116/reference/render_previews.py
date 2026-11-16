import base64
import json
import sys
from pathlib import Path
from bb import run, ROOT

OUT = ROOT
OUTPUT=Path(sys.argv[1]) if len(sys.argv)>1 else OUT
OUTPUT.mkdir(parents=True,exist_ok=True)
class LocalClient:
    eval_js=staticmethod(run)
client=LocalClient()


def render(view, axis):
    if view == 'oblique':
        camera_code = """
const aspect = 4 / 3;
const radius = box.getBoundingSphere(new THREE.Sphere()).radius;
const camera = new THREE.PerspectiveCamera(35, aspect, 0.1, 500);
const direction = new THREE.Vector3(-1.0, 0.62, 1.25).normalize();
camera.position.copy(center).addScaledVector(direction, radius * 2.85);
camera.lookAt(center);
"""
    else:
        camera_code = """
const aspect = 4 / 3;
const viewWidth = FRAME_WIDTH;
const viewHeight = FRAME_HEIGHT;
let frameWidth = Math.max(viewWidth * 1.18, viewHeight * 1.18 * aspect);
let frameHeight = frameWidth / aspect;
const camera = new THREE.OrthographicCamera(-frameWidth / 2, frameWidth / 2, frameHeight / 2, -frameHeight / 2, 0.1, 500);
camera.position.copy(center).add(new THREE.Vector3(CAM_X, CAM_Y, CAM_Z));
camera.up.set(0, 1, 0);
camera.lookAt(center);
""".replace('FRAME_WIDTH', 'boxSize.x' if view != 'side' else 'boxSize.z').replace('FRAME_HEIGHT', 'boxSize.y').replace('CAM_X', '-120' if view == 'side' else '0').replace('CAM_Y', '0').replace('CAM_Z', '120' if view == 'front' else ('0' if view == 'side' else '-120'))
    code = """
(async () => {
const p = Preview.selected;
const scene = Canvas.scene;
const grid = scene.getObjectByName('grid_group');
const outline = scene.getObjectByName('outline_group');
const backdrop = scene.children.find(o => o.isMesh && o.geometry && o.geometry.type === 'PlaneGeometry');
const modelRoots = new Set();
for (const cube of Cube.all) { let root = cube.mesh; while (root && root.parent && root.parent !== scene) root = root.parent; if (root) modelRoots.add(root); }
const auxTop = scene.children.filter(o => o.name !== 'lights' && !modelRoots.has(o));
const oldAux = auxTop.map(o => o.visible);
const axisNames = new Set(['X','Y','Z','NX','NY','NZ','XY','YZ','XZ','XYZE','E']);
const axisObjects = [];
scene.traverse(o => { if (axisNames.has(o.name) || o.name.startsWith('axis_line') || o.type === 'GridHelper') axisObjects.push(o); });
const oldAxis = axisObjects.map(o => o.visible);
const oldBg = scene.background;
const oldOverride=scene.overrideMaterial;
const gray=GRAY;
if(gray)scene.overrideMaterial=new THREE.MeshLambertMaterial({color:0xb6b6ac});
const oldGrid = grid ? grid.visible : null;
const oldOutline = outline ? outline.visible : null;
const oldBackdrop = backdrop ? backdrop.visible : null;
const oldClear = p.renderer.getClearColor(new THREE.Color()).getHex();
const oldAlpha = p.renderer.getClearAlpha();
const oldRatio = p.renderer.getPixelRatio();
const oldSize = [p.renderer.domElement.width, p.renderer.domElement.height];
const box = new THREE.Box3();
for (const cube of Cube.all) {
  if (!cube.mesh) continue;
  cube.mesh.updateMatrixWorld(true);
  const pos=cube.mesh.geometry.attributes.position; for(let i=0;i<pos.count;i++){const q=new THREE.Vector3().fromBufferAttribute(pos,i).applyMatrix4(cube.mesh.matrixWorld);box.expandByPoint(q);}
}
const center = box.getCenter(new THREE.Vector3());
const boxSize = box.getSize(new THREE.Vector3());
""" + camera_code + """
camera.updateProjectionMatrix();
scene.background = null;
for (const o of auxTop) o.visible = false;
for (const o of axisObjects) o.visible = false;
if (grid) grid.visible = false;
if (outline) outline.visible = false;
if (backdrop) backdrop.visible = false;
p.renderer.setClearColor(0x000000, 0);
p.renderer.setPixelRatio(1);
p.renderer.setSize(1200, 900, false);
p.renderer.clear(true, true, true);
Canvas.withoutGizmos(() => { p.renderer.render(scene, camera); });
const url = p.renderer.domElement.toDataURL('image/png');
p.renderer.setPixelRatio(oldRatio);
p.renderer.setSize(oldSize[0] / oldRatio, oldSize[1] / oldRatio, false);
scene.background = oldBg;
if(gray){scene.overrideMaterial.dispose();scene.overrideMaterial=oldOverride;}
for (let i = 0; i < auxTop.length; i++) auxTop[i].visible = oldAux[i];
for (let i = 0; i < axisObjects.length; i++) axisObjects[i].visible = oldAxis[i];
if (grid) grid.visible = oldGrid;
if (outline) outline.visible = oldOutline;
if (backdrop) backdrop.visible = oldBackdrop;
p.renderer.setClearColor(oldClear, oldAlpha);
p.render();
return {image:url,box:{min:box.min.toArray(),max:box.max.toArray()},center:center.toArray(),size:boxSize.toArray()};
})()
"""
    pid=(OUT/'reference/project_id.txt').read_text().strip()
    code=code.replace('const p = Preview.selected;', 'if(Project.uuid!=='+json.dumps(pid)+')throw Error("Project changed");const p = Preview.selected;')
    code=code.replace('GRAY','true' if '--gray' in sys.argv else 'false')
    result = client.eval_js(code)
    image = result['image']
    if not image.startswith('data:image/png;base64,'):
        raise RuntimeError('Blockbench returned no PNG data')
    path = OUTPUT / f'preview_{view}.png'
    path.write_bytes(base64.b64decode(image.split(',', 1)[1]))
    print(view, path, result['size'])


for view in ('front', 'back', 'side', 'oblique'):
    render(view, '')
