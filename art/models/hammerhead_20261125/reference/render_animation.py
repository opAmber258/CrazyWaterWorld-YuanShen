"""Render whole-body loop previews for every cruise clip and encode WebP + GIF."""
import argparse
import io
import json
from base64 import b64decode
from pathlib import Path

from PIL import Image

from make_gif import convert as convert_gif
from render_blockbench import OPEN, RENDER, Blockbench

CLIPS = {"swim_cruise": 3.0, "swim_fast": 1.5}
FPS = 20
DIRECTION = [-1.90, 0.74, 1.11]
MARGIN = 1.20

SELECT = """
function selectClip(name) {
    const clip=Animation.all.find(a=>a.name===name);
    if (!clip || clip.loop!=='loop') throw new Error('Missing loop '+name);
    Modes.options.animate.select();
    clip.select();
    for (const a of Animation.all) a.playing=a===clip;
    return clip;
}
function seek(t) {
    Timeline.setTime(t);
    Animator.preview();
    Canvas.scene.updateMatrixWorld(true);
}
"""

FRAME = """
(() => {
    const preset = PRESET;
    const scene = Canvas.scene;
    const preview = Preview.selected;
    const renderer = preview.renderer;
    const roots = new Set();
    scene.updateMatrixWorld(true);
    for (const cube of Cube.all) {
        let root = cube.mesh;
        while (root.parent && root.parent !== scene) root = root.parent;
        roots.add(root);
    }
    const direction = new THREE.Vector3(...preset.direction).normalize();
    const viewUp = new THREE.Vector3(0, 1, 0);
    const right = viewUp.clone().cross(direction).normalize();
    const up = direction.clone().cross(right).normalize();
    const aspect = 720/540;
    const frameWidth = preset.width;
    const frameHeight = frameWidth/aspect;
    const center = new THREE.Vector3(...preset.target);
    const camera = new THREE.PerspectiveCamera(31,aspect,0.1,1000);
    const distance = (frameHeight/2)/Math.tan(THREE.MathUtils.degToRad(31/2)) + 0.17*preset.depth;
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
        renderer.setSize(720,540,false);
        renderer.clear(true,true,true);
        Canvas.withoutGizmos(() => renderer.render(scene,camera));
        return renderer.domElement.toDataURL('image/png');
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

FRAMING = """
(() => {
    SELECT
    const direction = new THREE.Vector3(...DIRECTION).normalize();
    const viewUp = new THREE.Vector3(0, 1, 0);
    const right = viewUp.clone().cross(direction).normalize();
    const up = direction.clone().cross(right).normalize();
    const span = {right:[Infinity,-Infinity],up:[Infinity,-Infinity],depth:[Infinity,-Infinity]};
    const push = (axis,value) => {
        if (value < span[axis][0]) span[axis][0] = value;
        if (value > span[axis][1]) span[axis][1] = value;
    };
    let sampled = 0;
    for (const [key,duration] of Object.entries(CLIPS)) {
        selectClip('animation.hammerhead.'+key);
        for (let n=0;n<=Math.round(duration*4);n++) {
            seek(n/4);
            sampled++;
            for (const cube of Cube.all) {
                const mesh = cube.mesh;
                const pos = mesh.geometry.attributes.position;
                for (let i=0;i<pos.count;i++) {
                    const p = new THREE.Vector3().fromBufferAttribute(pos,i).applyMatrix4(mesh.matrixWorld);
                    push('right',p.dot(right));
                    push('up',p.dot(up));
                    push('depth',p.dot(direction));
                }
            }
        }
    }
    const center = new THREE.Vector3()
        .addScaledVector(right,(span.right[0]+span.right[1])/2)
        .addScaledVector(up,(span.up[0]+span.up[1])/2)
        .addScaledVector(direction,(span.depth[0]+span.depth[1])/2);
    const width = span.right[1]-span.right[0];
    const height = span.up[1]-span.up[0];
    const depth = span.depth[1]-span.depth[0];
    const frameWidth = Math.max(width, height*720/540)*MARGIN;
    return {target:center.toArray(), width:frameWidth, depth, sampled,
            extents:{right:[width,height], depth}};
})()
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--clips", nargs="+", choices=list(CLIPS), default=list(CLIPS))
    arguments = parser.parse_args()
    arguments.output.mkdir(parents=True, exist_ok=True)

    client = Blockbench(arguments.port)
    try:
        model = arguments.project.read_text(encoding="utf-8-sig")
        client.evaluate(OPEN.replace("INPUT", json.dumps(str(arguments.project.resolve()))).replace("MODEL", model))
        framing = client.evaluate(
            FRAMING.replace("SELECT", SELECT)
            .replace("DIRECTION", json.dumps(DIRECTION))
            .replace("CLIPS", json.dumps(CLIPS))
            .replace("MARGIN", json.dumps(MARGIN)))
        print(json.dumps(framing))
        preset = {"direction": DIRECTION, "target": framing["target"], "width": framing["width"],
                  "depth": framing["depth"]}
        script = FRAME.replace("PRESET", json.dumps(preset))
        for clip in arguments.clips:
            duration = CLIPS[clip]
            key = json.dumps("animation.hammerhead." + clip)
            client.evaluate("(() => {" + SELECT + "selectClip(" + key + ");})()")
            frames = []
            for frame in range(round(duration * FPS)):
                uri = client.evaluate("(() => {" + SELECT + f"seek({frame/FPS});return (" + script + ");})()")
                frames.append(Image.open(io.BytesIO(b64decode(uri.split(",", 1)[1]))).convert("RGB"))
            webp = arguments.output / f"preview_{clip}.webp"
            frames[0].save(webp, save_all=True, append_images=frames[1:], duration=round(1000 / FPS),
                           loop=0, quality=88, method=4)
            convert_gif(webp, arguments.output / f"preview_{clip}.gif", 540)
            print(f"{clip}: {len(frames)} frames -> {webp.name} {webp.stat().st_size} B")
    finally:
        client.ws.close()


if __name__ == "__main__":
    main()
