"""Export and inspect the model with Blockbench's own codec and viewport."""

import argparse
import base64
import importlib.util
import json
from pathlib import Path

from audit_coplanar import SNAPSHOT, check_faces


DEST = Path(__file__).resolve().parents[1]
NAME = "engine_6block"

RENDER = r"""
const preview = new Preview({offscreen: true, antialias: true});
const box = new THREE.Box3();
const vertices = [];
for (const cube of Cube.all) {
    const mesh = cube.mesh;
    mesh.updateMatrixWorld(true);
    const positions = mesh.geometry.attributes.position;
    for (let i = 0; i < positions.count; i++) {
        const p = new THREE.Vector3().fromBufferAttribute(positions, i).applyMatrix4(mesh.matrixWorld);
        vertices.push(p);
        box.expandByPoint(p);
    }
}
const center = box.getCenter(new THREE.Vector3());
const scene = Canvas.scene;
const images = {};
const oldBackground = scene.background;
scene.background = new THREE.Color("#303940");
settings.brightness.value = 59;
Canvas.updateShading();
try {
    for (const [name, dir, ortho, width] of [
        ["front", [0, 0, 1], true, 114],
        ["back", [0, 0, -1], true, 114],
        ["side", [-1, 0, 0], true, 73],
        ["oblique", [-1.12, 0.53, 1.1], false, 0],
        ["tail_oblique", [1.2, 0.65, -1.18], false, 0],
    ]) {
        preview.setProjectionMode(ortho);
        preview.resize(1200, 900);
        const cam = preview.camera;
        const direction = new THREE.Vector3(...dir).normalize();
        preview.controls.target.copy(center);
        cam.position.copy(center).addScaledVector(direction, 150);
        cam.lookAt(center);
        if (ortho) {
            cam.zoom = (cam.right - cam.left) / width;
        } else {
            cam.fov = 32;
            cam.updateMatrixWorld(true);
            const tanV = Math.tan(THREE.MathUtils.degToRad(cam.fov / 2));
            const tanH = tanV * cam.aspect;
            const viewRotation = cam.quaternion.clone().invert();
            const minimum = Math.max(...vertices.map(p => {
                const q = p.clone().sub(center).applyQuaternion(viewRotation);
                return q.z + Math.max(Math.abs(q.x) / (tanH * 0.84), Math.abs(q.y) / (tanV * 0.79));
            }));
            cam.position.copy(center).addScaledVector(direction, minimum);
        }
        cam.updateProjectionMatrix();
        Canvas.withoutGizmos(() => {
            preview.render();
            images[name] = preview.canvas.toDataURL("image/png").split(",")[1];
        });
    }
    for (const [name, focus, dir, distance] of [
        ["guard_joint", [-37.4, 25, 13.0], [-0.78, 0.56, 1.38], 44],
        ["tail_joint", [44.0, 27.3, 1.8], [1.76, 0.60, 0.94], 49],
    ]) {
        preview.setProjectionMode(false);
        preview.resize(1200, 900);
        const cam = preview.camera;
        const target = new THREE.Vector3(...focus);
        const direction = new THREE.Vector3(...dir).normalize();
        preview.controls.target.copy(target);
        cam.fov = 33;
        cam.position.copy(target).addScaledVector(direction, distance);
        cam.lookAt(target);
        cam.updateProjectionMatrix();
        Canvas.withoutGizmos(() => {
            preview.render();
            images[name] = preview.canvas.toDataURL("image/png").split(",")[1];
        });
    }
} finally {
    scene.background = oldBackground;
    preview.renderer.dispose();
}
return images;
"""

ROUNDTRIP = r"""
Codecs.bedrock.load(ARGS.model, {path: ARGS.path, no_file: true});
await new Promise(resolve => setTimeout(resolve, 250));
Canvas.updateAll();
const compiled = await Codecs.bedrock.compile({raw: true});
const geometry = compiled["minecraft:geometry"] ? compiled : JSON.parse(compiled);
const projectBox = Canvas.getModelBoundingBox();
return {
    cubes: Cube.all.length,
    groups: Group.all.length,
    bounds: [projectBox.min.toArray(), projectBox.max.toArray()],
    compiled_geometry: geometry,
};
"""

DUCT_CHECK = r"""
const wall = Cube.all.filter(cube => /^(barrel_wall|barrel_miter|rolled_mouth|gearbox_flange)_/.test(cube.name));
const back = Cube.all.filter(cube => cube.name.includes("bulkhead_"));
const ray = new THREE.Raycaster(undefined, undefined, 0, 24);
let wallRays = 0, wallGaps = 0, rearRays = 0, rearGaps = 0, sweepGaps = 0;
const origin = new THREE.Vector3(), direction = new THREE.Vector3();
const walls = wall.map(c => c.mesh), bulkhead = back.map(c => c.mesh);
for (let x = -47.65; x <= -29.07; x += 0.185) {
    origin.set(x, 23.25, 0);
    for (let angle = 0; angle < 720; angle++) {
        const a = angle * Math.PI / 360;
        ray.set(origin, direction.set(0, Math.sin(a), Math.cos(a)));
        wallRays++;
        if (!ray.intersectObjects(walls, false).length) wallGaps++;
    }
}
function stageClearance(pattern) {
    let radialBound = 0, minX = Infinity, maxX = -Infinity, minWallRadius = Infinity, gaps = 0;
    const stage = Cube.all.filter(cube => pattern.test(cube.name));
    for (const cube of stage) {
        const mesh = cube.mesh;
        const vertices = mesh.geometry.attributes.position;
        mesh.updateMatrixWorld(true);
        for (let i = 0; i < vertices.count; i++) {
            const p = new THREE.Vector3().fromBufferAttribute(vertices, i).applyMatrix4(mesh.matrixWorld);
            radialBound = Math.max(radialBound, Math.hypot(p.y - 23.25, p.z));
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
        }
    }
    const slices = 25, angles = 720;
    for (let i = 0; i < slices; i++) {
        origin.set(THREE.MathUtils.lerp(minX, maxX, i / (slices - 1)), 23.25, 0);
        for (let angle = 0; angle < angles; angle++) {
            const a = angle * Math.PI * 2 / angles;
            ray.set(origin, direction.set(0, Math.sin(a), Math.cos(a)));
            const [hit] = ray.intersectObjects(walls, false);
            if (hit) minWallRadius = Math.min(minWallRadius, hit.distance);
            else gaps++;
        }
    }
    return {radialBound, minX, maxX, rays: slices * angles, gaps, wallClearance: minWallRadius - radialBound};
}
const outer = stageClearance(/^(pitched_blade|blade_root)_/);
const inner = stageClearance(/^inner_pitched_blade_/);
const axialGap = inner.minX - outer.maxX;
direction.set(1, 0, 0);
const apothem = 14.175;
const limit = apothem * Math.SQRT2;
for (let y = -apothem; y <= apothem; y += 0.125) {
    for (let z = -apothem; z <= apothem; z += 0.125) {
        if (Math.abs(y) + Math.abs(z) > limit) continue;
        ray.set(origin.set(-34.5, 23.25 + y, z), direction);
        rearRays++;
        if (!ray.intersectObjects(bulkhead, false).length) rearGaps++;
    }
}
const shell = Cube.all.filter(cube => /^(main_cast_body|tail_cast_internal_web|tail_crown|drive_crown|cast_drive_neck|barrel_|gearbox_flange|bearing_bulkhead|(?:front|rear)_continuous_cast_shoulder)/.test(cube.name))
    .map(cube => cube.mesh);
let junctionRays = 0, junctionGaps = 0;
for (const [x0, x1, y0, y1] of [[-28.77, -24.6, 18, 35.97], [37.56, 43.695, 31.62, 38.445]]) {
    for (let x = x0; x <= x1; x += 0.15) {
        for (let y = y0; y <= y1; y += 0.15) {
            ray.set(origin.set(x, y, 21), direction.set(0, 0, -1));
            junctionRays++;
            if (!ray.intersectObjects(shell, false).length) junctionGaps++;
        }
    }
}
return {wallRays, wallGaps, rearRays, rearGaps, junctionRays, junctionGaps, outer, inner, axialGap};
"""


def normalized(data):
    """Ignore floating-point representation error in roundtripped coordinates."""
    if isinstance(data, float):
        return round(data, 5)
    if isinstance(data, list):
        return [normalized(elem) for elem in data]
    if isinstance(data, dict):
        return {key: normalized(value) for key, value in data.items()}
    return data


def load_adapter(adapter_path):
    spec = importlib.util.spec_from_file_location("forge3d_blockbench_adapter", adapter_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adapter", required=True, type=Path,
                        help="Path to the Forge3D scripts/forge3d/bb/app.py adapter.")
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    adapter = load_adapter(args.adapter)
    project = DEST / f"{NAME}.bbmodel"
    export = DEST / f"{NAME}.geo.json"
    with adapter.Blockbench() as app:
        native = app.open(project)
        assert native["format"] == "bedrock" and not native["problems"], native
        assert not native["untextured_faces"] and all(not t["error"] for t in native["textures"]), native
        for angle, data in app.js(RENDER).items():
            (DEST / f"preview_{angle}.png").write_bytes(base64.b64decode(data))
        coplanar = check_faces(app.js(SNAPSHOT))
        assert coplanar["faces_checked"] == native["cubes"] * 6
        assert not coplanar["positive_area_overlaps"], coplanar
        duct = app.js(DUCT_CHECK)
        assert not (duct["wallGaps"] or duct["rearGaps"] or duct["junctionGaps"] or duct["outer"]["gaps"] or duct["inner"]["gaps"]), duct
        assert min(duct["outer"]["wallClearance"], duct["inner"]["wallClearance"], duct["axialGap"]) > 0, duct
        app.export("bedrock", export)
        geometry = json.loads(export.read_text(encoding="utf-8"))
        roundtrip = app.js(ROUNDTRIP, model=geometry, path=str(export))
        recompiled = roundtrip.pop("compiled_geometry")
        original_bones = geometry["minecraft:geometry"][0]["bones"]
        reopened_bones = recompiled["minecraft:geometry"][0]["bones"]
        roundtrip["bone_geometry_and_uv_match"] = normalized(original_bones) == normalized(reopened_bones)
        assert roundtrip["bone_geometry_and_uv_match"], "Native geometry roundtrip changed bones, cubes or UVs."
        assert (roundtrip["cubes"], roundtrip["groups"]) == (native["cubes"], native["groups"])
        args.report.write_text(json.dumps({"project": native, "roundtrip": roundtrip,
                                           "coplanar": coplanar, "duct": duct}, indent=2), encoding="utf-8")
    print(f"Blockbench {native['version']}: {native['cubes']} cubes, {native['groups']} groups")
    print("Native Bedrock export and seven 1200x900 viewport previews saved.")


if __name__ == "__main__":
    main()







