import base64
import json
from pathlib import Path
from bb_mcp_client import BlockbenchMCP

OUT = Path(__file__).resolve().parent.parent
REF = OUT / 'reference'
PROJECT = OUT / 'engine.bbmodel'
client = BlockbenchMCP()

load_code = "(async()=>{const fs=require('fs');const p=" + json.dumps(str(PROJECT)) + ";if(Project&&Project.name==='engine'&&Project.save_path===p){Project.saved=true;await Project.close(true);await new Promise(r=>setTimeout(r,300));}const txt=fs.readFileSync(p,'utf8');await loadModelFile({path:p,content:txt});await new Promise(r=>setTimeout(r,700));return {project:Project?.name,elements:Cube.all.length};})()"
print('load', client.eval_js(load_code))

base_code = """
(async () => {
const p = Preview.selected;
const scene = Canvas.scene;
const backdrop = scene.children.find(o => o.isMesh && o.geometry && o.geometry.type === 'PlaneGeometry');
const oldBg = scene.background;
const oldBackdrop = backdrop ? backdrop.visible : null;
const oldClear = p.renderer.getClearColor(new THREE.Color()).getHex();
const oldAlpha = p.renderer.getClearAlpha();
const oldRatio = p.renderer.getPixelRatio();
const oldSize = [p.renderer.domElement.width, p.renderer.domElement.height];
const box = new THREE.Box3();
for (const cube of Cube.all) { if (!cube.mesh) continue; cube.mesh.updateMatrixWorld(true); box.expandByObject(cube.mesh); }
const center = box.getCenter(new THREE.Vector3());
const size = box.getSize(new THREE.Vector3());
const aspect = 4 / 3;
CAMERA
scene.background = null;
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
if (backdrop) backdrop.visible = oldBackdrop;
p.renderer.setClearColor(oldClear, oldAlpha);
p.render();
return {image:url,size:size.toArray()};
})()
"""

right_camera = """
const camera = new THREE.OrthographicCamera(-size.z * 0.72, size.z * 0.72, size.z * 0.72 / aspect, -size.z * 0.72 / aspect, 0.1, 500);
camera.position.set(center.x + size.x * 3, center.y, center.z);
camera.up.set(0, 1, 0);
camera.lookAt(center);
camera.updateProjectionMatrix();
"""
aux_camera = """
const camera = new THREE.PerspectiveCamera(35, aspect, 0.1, 500);
camera.position.set(-80, 48, 64);
camera.up.set(0, 1, 0);
camera.lookAt(new THREE.Vector3(-30, 15, -5));
camera.updateProjectionMatrix();
"""
corner_camera = """
const camera = new THREE.PerspectiveCamera(32, aspect, 0.1, 500);
camera.position.set(-62, 45, 52);
camera.up.set(0, 1, 0);
camera.lookAt(new THREE.Vector3(-22, 20, 2));
camera.updateProjectionMatrix();
"""

for camera_code, filename in ((right_camera, 'right_side_check.png'), (aux_camera, 'aux_structure_check.png'), (corner_camera, 'bevel_corner_check.png')):
    result = client.eval_js(base_code.replace('CAMERA', camera_code))
    path = REF / filename
    path.write_bytes(base64.b64decode(result['image'].split(',', 1)[1]))
    print(path, result['size'])
