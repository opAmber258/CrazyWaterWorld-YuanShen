"""Reopen a .bbmodel in a dedicated Blockbench CDP instance, export and render."""
import argparse
import base64
import json
import urllib.request
from pathlib import Path

from websocket import create_connection

class Blockbench:
    def __init__(self, port):
        http = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with http.open(f"http://127.0.0.1:{port}/json/list", timeout=5) as stream:
            pages = json.load(stream)
        target = next(p for p in pages if p["type"] == "page" and "app.asar/index.html" in p["url"])
        self.ws = create_connection(target["webSocketDebuggerUrl"], timeout=60, suppress_origin=True)
        self.sequence = 0

    def evaluate(self, script):
        self.sequence += 1
        self.ws.send(json.dumps({"id":self.sequence,"method":"Runtime.evaluate","params":{
            "expression":script,"awaitPromise":True,"returnByValue":True,
            "allowUnsafeEvalBlockedByCSP":True}}))
        while True:
            reply = json.loads(self.ws.recv())
            if reply.get("id") != self.sequence:
                continue
            result = reply["result"]
            if "exceptionDetails" in result:
                raise RuntimeError(result["exceptionDetails"])
            return result["result"].get("value")

OPEN = """
(async () => {
    const path = INPUT;
    const data = MODEL;

    const prior = Project;
    if (prior && !['hammerhead','baseline'].includes(prior.name)) throw Error('Unexpected active project');
    Codecs.project.load(data, {path, no_file: true});
    for (let i = 0; i < 100; i++) {
        if (Texture.all.length === 1 && Texture.all[0].img.naturalWidth === 256) break;
        await new Promise(resolve => setTimeout(resolve, 80));
    }
    if (Project === prior || Project.name !== 'hammerhead' || Format.id !== 'bedrock' ||
        Texture.all.length !== 1 || Texture.all[0].img.naturalWidth !== 256) {
        throw Error('Hammerhead failed to reopen with its embedded atlas');
    }
    Canvas.updateAll();
    const geo = Codecs.bedrock.compile();
    return {geo, version: Blockbench.version, uuid: Project.uuid, rotationOrder: Cube.all[0].mesh.rotation.order,
            groups: Group.all.length, cubes: Cube.all.length, texture: Texture.all[0].img.naturalWidth,
            animationCount: Project.animations.length};
})()
"""

IMPORT_GEO = """
(async () => {
    const geo = GEOMETRY;
    setupProject(Formats.bedrock);
    await new Promise(resolve => setTimeout(resolve, 100));
    const imported = Project;
    Codecs.bedrock.parse(geo, 'models/entity/hammerhead.geo.json', {switch_to_existing_tab:false});
    await new Promise(resolve => setTimeout(resolve, 150));
    if (Project !== imported || Cube.all.length === 0) throw Error('Geometry opened in an unexpected tab');

    Project.name = 'hammerhead';
    const texture = new Texture({name:'hammerhead_tex.png',uv_width:256,uv_height:256})
        .fromDataURL(ATLAS).add(false);
    for (const cube of Cube.all) cube.applyTexture(texture, true);
    for (let i=0; i<100 && texture.img.naturalWidth!==256; i++) {
        await new Promise(resolve => setTimeout(resolve, 80));
    }
    if (texture.img.naturalWidth!==256) throw Error('Missing atlas on geometry reimport');
    Canvas.updateAll();
    return {cubes:Cube.all.length,bones:Group.all.length};
})()
"""

RENDER = """
(() => {
    const preset = PRESET;
    const scene = Canvas.scene;
    const preview = Preview.selected;
    const renderer = preview.renderer;
    const roots = new Set();
    scene.updateMatrixWorld(true);
    const points = [];
    const box = new THREE.Box3();
    for (const cube of Cube.all) {
        const mesh = cube.mesh;
        let root = mesh;
        while (root.parent && root.parent !== scene) root = root.parent;
        roots.add(root);
        const pos = mesh.geometry.attributes.position;
        for (let i = 0; i < pos.count; i++) {
            const p = new THREE.Vector3().fromBufferAttribute(pos, i).applyMatrix4(mesh.matrixWorld);
            points.push(p);
            box.expandByPoint(p);
        }
    }
    const direction = new THREE.Vector3(...preset.direction).normalize();
    const viewUp = preset.top ? new THREE.Vector3(0, 0, 1) : new THREE.Vector3(0, 1, 0);
    const right = viewUp.clone().cross(direction).normalize();
    const up = direction.clone().cross(right).normalize();
    const along = vector => {
        const values = points.map(point => point.dot(vector));
        return [Math.min(...values), Math.max(...values)];
    };
    const projected = [right, up, direction].map(along);
    const center = new THREE.Vector3();
    [right, up, direction].forEach((vector,i) =>
        center.addScaledVector(vector, (projected[i][0]+projected[i][1])/2));
    if (preset.target) center.set(...preset.target);
    const aspect = 4/3;
    const apparentWidth = Math.max(projected[0][1]-projected[0][0], (projected[1][1]-projected[1][0])*aspect);
    const frameWidth = preset.width || apparentWidth*(preset.margin || 1.19);
    const frameHeight = frameWidth/aspect;
    let camera, distance;
    if (preset.perspective) {
        camera = new THREE.PerspectiveCamera(31,aspect,0.1,1000);
        distance = (frameHeight/2)/Math.tan(THREE.MathUtils.degToRad(31/2)) + 0.17*(projected[2][1]-projected[2][0]);
    } else {
        camera = new THREE.OrthographicCamera(-frameWidth/2,frameWidth/2,frameHeight/2,-frameHeight/2,0.1,1000);
        distance = 240;
    }
    camera.up.copy(viewUp);
    camera.position.copy(center).addScaledVector(direction,distance);
    camera.lookAt(center);
    camera.updateProjectionMatrix();
    camera.updateMatrixWorld(true);
    const hidden = scene.children.filter(o => !roots.has(o) && o.name !== 'lights' && !o.isLight);
    const visibility = hidden.map(o => o.visible);
    const ratio = renderer.getPixelRatio();
    const size = renderer.getSize(new THREE.Vector2());
    const bg = scene.background;
    const oldColor = renderer.getClearColor(new THREE.Color()).clone();
    const oldAlpha = renderer.getClearAlpha();
    try {
        hidden.forEach(o => o.visible = false);
        scene.background = null;
        renderer.setClearColor(0x1a252c, 1);
        renderer.setPixelRatio(1);
        renderer.setSize(1200,900,false);
        renderer.clear(true,true,true);
        Canvas.withoutGizmos(() => renderer.render(scene,camera));
        return {image:renderer.domElement.toDataURL('image/png'),
                bounds:{min:box.min.toArray(),max:box.max.toArray(),size:box.getSize(new THREE.Vector3()).toArray()}};
    } finally {
        scene.background = bg;
        renderer.setClearColor(oldColor,oldAlpha);
        renderer.setPixelRatio(ratio);
        renderer.setSize(size.x,size.y,false);
        hidden.forEach((o,i) => o.visible = visibility[i]);
        preview.render();
    }
})()
"""

VIEWS = {
    "front":{"direction":[0,0,1]},
    "back":{"direction":[0,0,-1]},
    "side":{"direction":[-1,0,0]},
    "oblique":{"direction":[-1.90,0.74,1.11],"perspective":True},
    "right":{"direction":[1,0,0]},
    "top":{"direction":[0,1,0],"top":True},
    "underside":{"direction":[-1,-0.85,0.8],"perspective":True},
    "head_left":{"direction":[-1,0.14,0.12],"target":[0,26,47],"width":54},
    "head_right":{"direction":[1,0.14,0.12],"target":[0,26,47],"width":54},
    "tail":{"direction":[-1,0.12,-0.03],"target":[0,31,-109],"width":188},
    "tail_top":{"direction":[0,1,-0.001],"target":[0,26,-112],"width":244,"top":True},
    "aft":{"direction":[-1.2,0.65,-1],"perspective":True,"margin":1.29},
    "dorsal":{"direction":[1,0.5,-0.64],"target":[0,43,-54],"width":112},
    "skin":{"direction":[-1,0.54,0.30],"target":[0,34,-14],"width":117},
    "armor":{"direction":[0.64,1.2,0.37],"target":[0,42,3],"width":127},
    "ram":{"direction":[-0.89,0.33,0.57],"target":[0,35,59],"width":84},
    "fork":{"direction":[-1.0,0.04,-0.035],"target":[3,30,-146],"width":125},
    "fork_aft":{"direction":[1.65,0.57,-.78],"target":[4,31,-146],"width":125},
    "fork_top":{"direction":[0,1,-.001],"target":[0,29,-141],"width":148,"top":True},
    "head_top":{"direction":[0,1,0],"top":True,"target":[0,34,59],"width":96},
    "head_front":{"direction":[0,0,1],"target":[0,33,55],"width":83},
    "head_turn":{"direction":[-0.75,.53,1.38],"target":[0,35,55],"width":87},
    "head_turn_right":{"direction":[.75,.53,1.38],"target":[0,35,55],"width":87},
    "head_profile":{"direction":[-1,0,0],"target":[0,34,63],"width":66},
    "head_below":{"direction":[.54,-.58,1.34],"target":[0,34,60],"width":88},
    "fins":{"direction":[1,-1.45,0.35],"target":[1,14,-24],"width":105},
}

SNAPSHOT = r'''
(() => {
    Canvas.scene.updateMatrixWorld(true);
    return Cube.all.map(cube => {
        const mesh = cube.mesh;
        const geometry = mesh.geometry;
        const positions = geometry.attributes.position;
        const uvs = geometry.attributes.uv;
        const position = Array.from({length: positions.count}, (_, i) =>
            new THREE.Vector3().fromBufferAttribute(positions, i).applyMatrix4(mesh.matrixWorld).toArray());
        const uv = Array.from({length: uvs.count}, (_, i) => [uvs.getX(i),uvs.getY(i)]);
        const parents = [];
        for (let p=cube.parent; p && p !== 'root'; p=p.parent) parents.unshift(p.name);
        return {name:cube.name, uuid:cube.uuid, parents, position, uv,
                indices:Array.from(geometry.index.array),
                faces:geometry.groups.map(group => [group.start,group.count])};
    });
})()
'''

def run(project, out, port, views, native_save=False, check_reimport=False, mesh_snapshot=False):
    out.mkdir(parents=True,exist_ok=True)
    client = Blockbench(port)
    try:
        results = client.evaluate(OPEN.replace("INPUT",json.dumps(str(project.resolve()))).replace("MODEL",project.read_text(encoding="utf-8-sig")))
        geo = json.loads(results.pop("geo"))
        client.evaluate("Modes.options.edit.select(); Canvas.scene.updateMatrixWorld(true)")
        if native_save:
            compiled = client.evaluate("Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false})")
            # Save a usable wide view for a creature much larger than the default preview.
            compiled["editor_state"] = {
                "mode":"edit",
                "previews":{"main":{"position":[-184,92,106],"target":[0,24,-25],
                                    "orthographic":False,"zoom":1,"angle":None}},
                "selected_elements":[],"selected_groups":[],
            }
            texture = compiled["textures"][0]
            texture["saved"] = True
            (out/"hammerhead.bbmodel").write_text(
                json.dumps(compiled,ensure_ascii=False,separators=(",",":")),encoding="utf-8")
            (out/"hammerhead_tex.png").write_bytes(
                base64.b64decode(texture["source"].split(",",1)[1]))
        (out/"hammerhead.geo.json").write_text(json.dumps(geo,indent=2)+"\n",encoding="utf-8")
        for view in views:
            data = client.evaluate(RENDER.replace("PRESET",json.dumps(VIEWS[view])))
            results["bounds"] = data["bounds"]
            (out/f"preview_{view}.png").write_bytes(base64.b64decode(data["image"].split(",",1)[1]))
        results["views"] = list(views)
        if mesh_snapshot:
            payload = client.evaluate(SNAPSHOT)
            (out / "native_mesh.json").write_text(json.dumps(payload, separators=(",", ":")), encoding="utf-8")
        if check_reimport:
            from PIL import Image, ImageChops
            import io
            atlas = json.loads(project.read_text(encoding="utf-8-sig"))["textures"][0]["source"]
            info = client.evaluate(IMPORT_GEO.replace("GEOMETRY",json.dumps(geo)).replace("ATLAS",json.dumps(atlas)))
            differences = {}
            for view in views:
                data = client.evaluate(RENDER.replace("PRESET",json.dumps(VIEWS[view])))
                png = base64.b64decode(data["image"].split(",",1)[1])
                with Image.open(io.BytesIO(png)) as new, Image.open(out/f"preview_{view}.png") as reference:
                    diff = ImageChops.difference(reference.convert("RGB"),new.convert("RGB"))
                    differences[view] = {"different_pixels":sum(any(rgb) for rgb in zip(*(channel.tobytes() for channel in diff.split()))),
                                         "per_channel_extrema":diff.getextrema()}
            results["geometry_reimport"] = {**info,"preview_difference":differences}
        (out/"editor_evidence.json").write_text(json.dumps(results,indent=2)+"\n",encoding="utf-8")
        print(json.dumps(results))
    finally:
        client.ws.close()

if __name__ == "__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("project",type=Path)
    parser.add_argument("--output",required=True,type=Path)
    parser.add_argument("--port",required=True,type=int)
    parser.add_argument("--views",nargs="+",choices=VIEWS,default=list(VIEWS)[:4])
    parser.add_argument("--native-save",action="store_true")
    parser.add_argument("--check-reimport",action="store_true")
    parser.add_argument("--mesh-snapshot",action="store_true")
    args=parser.parse_args()
    run(args.project,args.output,args.port,args.views,args.native_save,args.check_reimport,args.mesh_snapshot)















