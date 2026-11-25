"""Replace the old ram tips with a widening octagonal copper collar and broad face."""

from copy import deepcopy
from math import atan2, degrees, hypot
from uuid import NAMESPACE_URL, uuid5

import numpy as np
from PIL import Image

from surface_coords import CORNERS, matrix, project


FRAME_ORIGIN = np.array((-0.14868, 32.64765, 59.9676))
FRAME = matrix((5.8, -1.8, 4), FRAME_ORIGIN)[0]
ATTACH_Z, FRONT_Z = 9.85, 20.02
REAR = (22.24, 7.27, 13.03, 15.47)
FRONT = (26.13, 8.54, 15.31, 18.17)
WALL_THICKNESS = .83
SOURCE_CUBES = ("ram_impact", "ram_core", "ram_bevel_dorsal_l", "ram_bevel_ventral_l", "ram_bevel_dorsal_r", "ram_bevel_ventral_r")
EDGE_NAMES = ("right", "upper_right", "upper", "upper_left", "left", "lower_left", "lower", "lower_right")


def euler(turn):
    return np.array((degrees(atan2(turn[2, 1], turn[2, 2])),
                     degrees(atan2(-turn[2, 0], hypot(turn[2, 1], turn[2, 2]))),
                     degrees(atan2(turn[1, 0], turn[0, 0]))))


def rounded(values):
    return np.round(values, 5).tolist()


def ring(settings):
    x_side, y_side, x_top, y_top = settings
    return np.array(((x_side, -y_side), (x_side, y_side), (x_top, y_top), (-x_top, y_top),
                     (-x_side, y_side), (-x_side, -y_side), (-x_top, -y_top), (x_top, -y_top)))


def plate(template, name, face_center, along, across, size):
    normal = np.cross(along, across)
    half = np.asarray(size) / 2
    origin = FRAME_ORIGIN + FRAME @ (face_center - normal * half[2])
    orientation = FRAME @ np.column_stack((along, across, normal))
    cube = deepcopy(template)
    cube.update({"name": f"ram_flare_{name}", "from": rounded(origin - half), "to": rounded(origin + half),
                 "origin": rounded(origin), "rotation": rounded(euler(orientation)),
                 "uuid": str(uuid5(NAMESPACE_URL, f"hammerhead:flare:{name}"))})
    return cube


def walls(template):
    rear, front = ring(REAR), ring(FRONT)
    length = FRONT_Z - ATTACH_Z
    for index, side in enumerate(EDGE_NAMES):
        following = (index + 1) % len(rear)
        start, end = rear[index], rear[following]
        outer_start, outer_end = front[index], front[following]
        tangent = end - start
        initial_width = np.linalg.norm(tangent)
        tangent /= initial_width
        out = np.array((tangent[1], -tangent[0]))
        run = float((outer_start - start) @ out / length)
        shift = run * out
        slant = hypot(1, run)
        along = np.array((*tangent, 0.))
        across = np.array((*shift, 1.)) / slant
        spans = ((0., .60), (.60, 1 - .36 / length)) if index % 2 == 0 else ((0., .34), (.34, .68), (.68, 1 - .36 / length))
        for number, (lo, hi) in enumerate(spans, start=1):
            f = (lo + hi) / 2
            # Use the narrower end width to keep the corners behind the adjacent
            # flared planes. Small backing pieces cover the remaining join inside.
            transverse = (1 - f) * (start + end) / 2 + f * (outer_start + outer_end) / 2
            edge = (1 - lo) * (end - start) + lo * (outer_end - outer_start)
            width = np.linalg.norm(edge) + .26
            position = np.array((*transverse, ATTACH_Z + length * f))
            label = f"wall_{side}_{number}"
            yield plate(template, label, position, along, across, (width, length * (hi - lo) * slant, WALL_THICKNESS)), out


def corner_backing(template):
    rear, front = ring(REAR), ring(FRONT)
    outgoing = np.roll(rear, -1, axis=0) - rear
    outgoing /= np.linalg.norm(outgoing, axis=1)[:, None]
    normals = np.column_stack((outgoing[:, 1], -outgoing[:, 0]))
    axial = FRONT_Z - ATTACH_Z
    span = axial - .43
    for index in (2, 3, 6, 7):
        direction = normals[index] + normals[index - 1]
        direction /= np.linalg.norm(direction)
        slope = (front[index] - rear[index]) / axial
        across = np.array((*slope, 1.))
        slant = np.linalg.norm(across)
        across /= slant
        along = np.cross(across, np.array((*direction, 0.)))
        along /= np.linalg.norm(along)
        xy = rear[index] + slope * span / 2 - direction * .66
        middle = np.array((*xy, ATTACH_Z + span / 2))
        yield plate(template, f"wall_seam_{index}", middle, along, across, (2.5, span * slant, .62)), direction


def front_face(template):
    x_side, y_side, x_top, y_top = FRONT
    along, across = np.array((1., 0., 0.)), np.array((0., 1., 0.))
    yield plate(template, "face_cross", (0, 0, FRONT_Z), along, across, (2 * x_side, 2 * y_side, .62)), None
    yield plate(template, "face_upright", (0, 0, FRONT_Z - .016), along, across, (2 * x_top, 2 * y_top, .62)), None

    outer = ring(FRONT)
    for index in (1, 3, 5, 7):
        start, end = outer[index], outer[(index + 1) % len(outer)]
        step = end - start
        width = np.linalg.norm(step)
        tangent = step / width
        inward = np.array((-tangent[1], tangent[0]))
        # This narrow in-plane return reaches the crossing center strips while
        # its outer edge follows the exact edge of the octagon.
        depth = abs(inward[0] * step[0]) + .42
        xy = (start + end) / 2 + inward * depth / 2
        face_position = np.array((*xy, FRONT_Z - .037))
        label = f"face_{EDGE_NAMES[index]}"
        yield plate(template, label, face_position, np.array((*tangent, 0.)), np.array((*inward, 0.)),
                    (width, depth, .66)), -inward


def reserve(mask, cubes):
    for cube in cubes:
        for face in cube["faces"].values():
            x0, y0, x1, y1 = face["uv"]
            x0, x1 = sorted((x0, x1))
            y0, y1 = sorted((y0, y1))
            mask[int(y0):int(np.ceil(y1)), int(x0):int(np.ceil(x1))] = True


def allocate(occupied, width, height):
    sums = np.pad(occupied.astype(np.int32), ((1, 0), (1, 0))).cumsum(0).cumsum(1)
    windows = sums[height:, width:] - sums[:-height, width:] - sums[height:, :-width] + sums[:-height, :-width]
    options = np.argwhere(windows == 0)
    if not len(options):
        raise ValueError(f"No safe UV rectangle of {width}×{height} texels fits the 256 atlas")
    y, x = map(int, options[0])
    occupied[y:y + height, x:x + width] = True
    return [x, y, x + width, y + height]


def side_sources(source):
    result = []
    for name in SOURCE_CUBES:
        cube = source[name]
        turn, shift = matrix(cube["rotation"], np.asarray(cube["origin"]))
        lower, higher = np.array((cube["from"], cube["to"]))
        for face, (left, right, end, normal) in CORNERS.items():
            if face in ("south", "north"):
                continue
            corners = lower + (higher - lower) * np.array((left, right, end))
            axis = (FRAME.T @ turn @ normal)[:2]
            direction = axis / np.linalg.norm(axis)
            face_center = (corners[1:] + corners[0]) / 2
            radial = ((face_center.mean(axis=0) @ turn.T + shift - FRAME_ORIGIN) @ FRAME)[:2] @ direction
            uv = np.asarray(cube["faces"][face]["uv"]).reshape(2, 2)
            result.append((direction, float(radial), turn, shift, corners, uv))
    return result


def projected_side(pixels, donors, direction, x, y, z, progress):
    _, _, turn, shift, corners, uv = max(donors, key=lambda donor: round(float(direction @ donor[0]), 2) * 600 + donor[1])
    fade = 1 + progress * .16
    depth = 9.65 + (z - ATTACH_Z) * .44
    old_world = np.stack((x / fade, y / fade, depth), axis=-1) @ FRAME.T + FRAME_ORIGIN
    in_cube = (old_world - shift) @ turn
    horizontal, vertical = corners[1:] - corners[0]
    blend = np.stack(((in_cube - corners[0]) @ horizontal / (horizontal @ horizontal),
                      (in_cube - corners[0]) @ vertical / (vertical @ vertical)), axis=-1)
    texel = (uv[0] + blend * (uv[1] - uv[0])).astype(int)
    texel = np.clip(texel, uv.min(axis=0), uv.max(axis=0) - 1)
    return pixels[texel[..., 1], texel[..., 0], :3].astype(float)


def paint(atlas, assemblies, removed, retained, source):
    pixels = np.array(atlas)
    occupied = np.zeros((256, 256), dtype=bool)
    opened = occupied.copy()
    reserve(occupied, retained)
    reserve(opened, removed)
    occupied |= (pixels[..., 3] != 0) & ~opened
    donors = side_sources(source)
    u0, v0, u1, v1 = map(int, source["ram_tip_center"]["faces"]["south"]["uv"])
    front_patch = pixels[v0:v1, u0:u1, :3].astype(float)
    height, width = front_patch.shape[:2]
    horizontal_tone = np.median(front_patch, axis=1)

    jobs = []
    for cube, direction in assemblies:
        size = np.asarray(cube["to"]) - np.asarray(cube["from"])
        for face, (left, right, end, _) in CORNERS.items():
            axes = (np.asarray((right, end)) - left) * size
            diagonal_front = cube["name"].startswith("ram_flare_face_") and direction is not None and face == "south"
            density = 1.34 if diagonal_front else .61
            w, h = np.maximum(2, np.rint(density * np.linalg.norm(axes, axis=1))).astype(int)
            jobs.append((int(w), int(h), cube, face, direction))

    for w, h, cube, face, direction in sorted(jobs, key=lambda job: (-job[1], -job[0])):
        uv = allocate(occupied, w, h)
        cube["faces"][face] = {"uv": uv, "texture": 0}
        turn, shift = matrix(cube["rotation"], np.asarray(cube["origin"]))
        world, _, _ = project(cube, face, uv, turn, shift)
        x, y, z = np.moveaxis((world - FRAME_ORIGIN) @ FRAME, -1, 0)
        u = np.clip(((.5 + x / (2 * FRONT[0])) * width).astype(int), 0, width - 1)
        v = np.clip(((.5 - y / (2 * FRONT[3])) * height).astype(int), 0, height - 1)
        color = front_patch[v, u]
        if cube["name"].startswith("ram_flare_face_"):
            # Suppress the highest-contrast grain only where the rotated corner
            # pixels meet the native frontal pixel grid.
            on_corner = (np.abs(x) > FRONT[2] - 2.2) & (np.abs(y) > FRONT[1] - 2.1)
            on_join = (np.abs(np.abs(x) - FRONT[2]) < 3.1) | (np.abs(np.abs(y) - FRONT[1]) < 3.)
            fade = np.where(on_corner, np.where(on_join, .67, .44), 0.)[..., None]
            color = color * (1 - fade) + horizontal_tone[v] * fade
        ochre = color * .93 + np.array((14, 7, 3))
        if cube["name"].startswith("ram_flare_wall_"):
            progress = np.clip((z - ATTACH_Z) / (FRONT_Z - ATTACH_Z), 0, 1)
            skin = projected_side(pixels, donors, direction, x, y, z, progress)
            forward = np.where(progress < .28, .08, np.where(progress < .57, .29, np.where(progress < .81, .50, .69)))
            rim_brightness = np.where(progress > .85, 1., 0.) * (1.4 + 4.5 * (max(0., direction[1])))
            ochre = skin * (1 - forward[..., None]) + ochre * forward[..., None] + rim_brightness[..., None]
        rgb = np.uint8(np.clip(np.rint(ochre), 0, 255))
        rgba = np.concatenate((rgb, np.full((*rgb.shape[:2], 1), 255, dtype=np.uint8)), axis=-1)
        atlas.paste(Image.fromarray(rgba), (uv[0], uv[1]))


def apply(model, atlas):
    source = {cube["name"]: cube for cube in model["elements"]}
    removed = [cube for cube in model["elements"] if cube["name"].startswith("ram_tip_")]
    assert len(removed) >= 6
    ids = {cube["uuid"] for cube in removed}
    retained = [cube for cube in model["elements"] if cube["uuid"] not in ids]
    template = source["ram_tip_center"]
    assemblies = [*walls(template), *corner_backing(template), *front_face(template)]
    paint(atlas, assemblies, removed, retained, source)
    geometry = [cube for cube, _ in assemblies]
    hammer = next(group["uuid"] for group in model["groups"] if group["name"] == "hammer")

    def assign(nodes):
        for node in nodes:
            if not isinstance(node, dict):
                continue
            if node["uuid"] == hammer:
                children = node["children"]
                first = next(index for index, child in enumerate(children) if isinstance(child, str) and child in ids)
                rest = [child for child in children[first:] if not isinstance(child, str) or child not in ids]
                node["children"] = [*children[:first], *(cube["uuid"] for cube in geometry), *rest]
                return True
            if assign(node["children"]):
                return True
        return False

    assert assign(model["outliner"])
    model["elements"] = [*retained, *geometry]
