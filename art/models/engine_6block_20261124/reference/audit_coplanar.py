"""Find positive-area coplanar overlaps in the evaluated Blockbench cube faces."""

import numpy as np


SNAPSHOT = """
return Cube.all.flatMap(cube => {
    const mesh = cube.mesh;
    mesh.updateMatrixWorld(true);
    mesh.geometry.computeBoundingBox();
    const {min, max} = mesh.geometry.boundingBox;
    const lower = min.toArray(), upper = max.toArray();
    const faces = [];
    for (let axis = 0; axis < 3; axis++) {
        const u = (axis + 1) % 3, v = (axis + 2) % 3;
        for (const side of [0, 1]) {
            const points = [[0, 0], [1, 0], [1, 1], [0, 1]].map(([a, b]) => {
                const x = [];
                x[axis] = (side ? upper : lower)[axis];
                x[u] = (a ? upper : lower)[u];
                x[v] = (b ? upper : lower)[v];
                return new THREE.Vector3(...x).applyMatrix4(mesh.matrixWorld).toArray();
            });
            faces.push({name: cube.name, face: "xyz"[axis] + (side ? "+" : "-"), points});
        }
    }
    return faces;
});
"""

PLANE_EPS = 0.00002
AREA_EPS = 0.0001


def cross(a, b):
    return a[0] * b[1] - a[1] * b[0]


def polygon_area(points):
    return abs(sum(cross(a, b) for a, b in zip(points, points[1:] + points[:1]))) / 2


def sorted_polygon(points):
    center = points.mean(axis=0)
    diff = points - center
    return list(points[np.argsort(np.arctan2(diff[:, 1], diff[:, 0]))])


def clip(subject, boundary):
    """Intersect two convex counter-clockwise polygons."""
    for a, b in zip(boundary, boundary[1:] + boundary[:1]):
        if not subject:
            break
        edge = b - a
        out = []
        previous = subject[-1]
        last_side = cross(edge, previous - a)
        for point in subject:
            side = cross(edge, point - a)
            if (side >= 0) != (last_side >= 0):
                out.append(previous + (point - previous) * last_side / (last_side - side))
            if side >= 0:
                out.append(point)
            previous, last_side = point, side
        subject = out
    return subject


def check_faces(faces):
    points = np.array([face["points"] for face in faces], dtype=float)
    normals = np.cross(points[:, 1] - points[:, 0], points[:, 2] - points[:, 0])
    normals /= np.linalg.norm(normals, axis=1)[:, None]
    parallels = np.abs(normals @ normals.T) > 1 - 1e-11
    low, high = points.min(axis=1), points.max(axis=1)
    overlaps = []
    for i, face in enumerate(faces):
        n = normals[i]
        axis = np.argmax(abs(n))
        projection = [index for index in range(3) if index != axis]
        a = sorted_polygon(points[i][:, projection])
        potentially_touching = np.all((low[i] <= high + PLANE_EPS) & (high[i] >= low - PLANE_EPS), axis=1)
        for j in np.flatnonzero(parallels[i] & potentially_touching):
            other = faces[j]
            if j <= i or face["name"] == other["name"]:
                continue
            if np.max(np.abs((points[j] - points[i, 0]) @ n)) > PLANE_EPS:
                continue
            b = sorted_polygon(points[j][:, projection])
            area = polygon_area(clip(a, b)) / abs(n[axis])
            if area > AREA_EPS:
                overlaps.append({
                    "a": [face["name"], face["face"]], "b": [other["name"], other["face"]],
                    "area_bb2": round(float(area), 5),
                })
    return {"faces_checked": len(faces), "plane_tolerance_bb": PLANE_EPS,
            "minimum_overlap_bb2": AREA_EPS, "positive_area_overlaps": overlaps}
