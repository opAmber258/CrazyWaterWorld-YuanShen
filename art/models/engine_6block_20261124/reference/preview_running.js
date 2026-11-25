const codec = AnimationCodec.codecs.bedrock;
const clips = Animation.all.slice();
const compiled = codec.compileFile(clips);
const clip = clips.find(a => a.name === ARGS.run);
clip.select();
for (const animation of clips) animation.playing = animation === clip;
const scene = Canvas.scene;
const background = scene.background;
scene.background = new THREE.Color("#303940");
settings.brightness.value = 59;
Canvas.updateShading();
const views = [
    {size: [736, 540], aim: [0, 24, 0], from: [-.88, .45, 1.25], width: 112},
    {size: [544, 540], aim: [0, 40.8, -4], from: [.035, 1.0, -.81], width: 103}
].map(({size, aim, from, width}) => {
    const preview = new Preview({offscreen: true, antialias: true});
    preview.setProjectionMode(true);
    preview.resize(...size);
    const camera = preview.camera;
    const target = new THREE.Vector3(...aim);
    const direction = new THREE.Vector3(...from).normalize();
    preview.controls.target.copy(target);
    camera.position.copy(target).addScaledVector(direction, 143);
    camera.lookAt(target);
    camera.zoom = (camera.right - camera.left) / width;
    camera.updateProjectionMatrix();
    return preview;
});
const at = time => {
    Timeline.setTime(time, true);
    Animator.preview();
    scene.updateMatrixWorld(true);
};
const snapshot = () => Object.fromEntries(Cube.all.map(cube => [cube.name, [...cube.mesh.matrixWorld.elements]]));
const deck = Cube.all.find(c => c.name === "recessed_roof_cover");
const base = Cube.all.find(c => c.name === "front_red_channel");
const blades = ["pitched_blade_1", "inner_pitched_blade_1"].map(name => Cube.all.find(c => c.name === name).mesh);
const bladePositions = () => blades.map(mesh => {
    const point = new THREE.Box3().setFromObject(mesh).getCenter(new THREE.Vector3());
    return [point.x, point.y - 23.25, point.z];
});
const fixed = new Set(["skid_and_isolators", "propeller_guard",
                       "louver_access_doors", "control_bay", "service_covers", "coolant_and_harness"]);
at(0);
const start = snapshot();
const rotorStart = bladePositions();
const staticCubes = Cube.all.filter(cube => fixed.has(cube.parent.name)).map(cube => cube.name);
const maxDiff = (reference, state, names) => Math.max(0, ...names.map(name =>
    Math.max(...state[name].map((v, i) => Math.abs(v - reference[name][i])))));
const checks = [];
const frames = [];
try {
    for (let index = 0; index <= 128; index++) {
        const time = index * ARGS.duration / 128;
        at(time);
        let state = snapshot();
        checks.push({
            time,
            blades: bladePositions(),
            fixed: maxDiff(start, state, staticCubes),
            roof_top: new THREE.Box3().setFromObject(deck.mesh).max.y,
            floor: new THREE.Box3().setFromObject(base.mesh).min.y,
            motion: maxDiff(start, state, Object.keys(state)),
            run: Object.fromEntries(ARGS.probes.map(name => {
                const group = Group.all.find(g => g.name === name);
                return [name, group.mesh.scale.y];
            }))
        });
    }
    for (let index = 0; index < 48; index++) {
        at(index * .05);
            const pair = [];
            Canvas.withoutGizmos(() => {
                for (const preview of views) {
                    preview.render();
                    pair.push(preview.canvas.toDataURL("image/png").split(",")[1]);
                }
            });
            frames.push(pair);
    }
} finally {
    at(0);
    scene.background = background;
    for (const view of views) view.renderer.dispose();
}
for (const animation of clips) animation.remove(false);
const imported = codec.loadFile({json: compiled});
return {compiled, reimported: codec.compileFile(imported), frames, checks, rotorStart};
