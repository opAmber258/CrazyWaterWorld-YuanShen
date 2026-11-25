"""Forge3D build entry point using Blockbench's evaluated, textured rest pose."""
import json
from pathlib import Path
import os

import bpy
from mathutils import Vector

source = Path(os.environ.get("HAMMERHEAD_FORGE_SOURCE", "candidate")).resolve()
objects = json.loads((source / "native_mesh.json").read_text())
height = 96.79236
offset = Vector((0, -74.34, 18.66113))
atlas = bpy.data.images.load(str(source / "hammerhead_tex.png"))
atlas.pack()

material = bpy.data.materials.new("Hammerhead pixel atlas")
material.use_nodes = True
material.diffuse_color = (0.16, 0.33, 0.29, 1.0)
shader = material.node_tree.nodes.get("Principled BSDF")
shader.inputs["Roughness"].default_value = 0.88
texture = material.node_tree.nodes.new("ShaderNodeTexImage")
texture.image = atlas
texture.interpolation = "Closest"
texture.extension = "CLIP"
material.node_tree.links.new(texture.outputs["Color"], shader.inputs["Base Color"])
material.node_tree.links.new(texture.outputs["Alpha"], shader.inputs["Alpha"])

if "Cube" in bpy.context.scene.objects:
    bpy.data.objects.remove(bpy.context.scene.objects["Cube"], do_unlink=True)
collections = {(): bpy.context.scene.collection}

def bone_collection(path):
    if path not in collections:
        parent = bone_collection(path[:-1])
        group = bpy.data.collections.new(path[-1])
        parent.children.link(group)
        collections[path] = group
    return collections[path]

def index_geometry(cube):
    vertices = []
    correspondence = []
    keys = {}
    for x, y, z in cube["position"]:
        key = (round(x, 6), round(y, 6), round(z, 6))
        if key not in keys:
            keys[key] = len(vertices)
            vertices.append((Vector((x, -z, y)) + offset) / height)
        correspondence.append(keys[key])
    polygons, maps = [], []
    original_indices = cube["indices"]
    if len(original_indices) != 36:
        raise ValueError(f"Unexpected triangle count on {cube['name']}")
    for start in range(0, len(original_indices), 6):
        triangles = [original_indices[start:start+3], original_indices[start+3:start+6]]
        # Keep outward winding and each corner's native atlas coordinate.
        edges = [(a,b) for a,b,c in triangles for a,b in ((a,b),(b,c),(c,a))]
        boundary = {a:b for a,b in edges if (b,a) not in edges}
        if len(boundary) != 4:
            raise ValueError(f"Unexpected face on {cube['name']}")
        order = [next(iter(boundary))]
        for _ in range(3):
            order.append(boundary[order[-1]])
        polygons.append([correspondence[index] for index in order])
        maps.append([cube["uv"][index] for index in order])
    return vertices, polygons, maps

for cube in objects:
    vertices, polygons, maps = index_geometry(cube)
    mesh = bpy.data.meshes.new(cube["name"])
    mesh.from_pydata(vertices, [], polygons)
    mesh.materials.append(material)
    atlas_uv = mesh.uv_layers.new(name="User UV atlas")
    for face, vertices_uv in zip(mesh.polygons, maps):
        for loop, uv in zip(face.loop_indices, vertices_uv):
            atlas_uv.data[loop].uv = uv
    mesh.update()
    record = bpy.data.objects.new(cube["name"], mesh)
    record["Blockbench element UUID"] = cube["uuid"]
    bone_collection(tuple(cube["parents"])).objects.link(record)

