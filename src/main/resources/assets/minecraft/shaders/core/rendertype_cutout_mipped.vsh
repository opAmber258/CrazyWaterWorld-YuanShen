#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 normal;


uniform float Kaleido;

// Continuous space fold: radial sine folds (non-monotonic at strong tiers) plus a
// six-lobe angular bend. No mirroring, so the mesh never cracks at sector seams.
vec4 barotrauma_kaleido(vec4 view) {
    if (Kaleido <= 0.001) {
        return view;
    }
    float r = length(view.xy);
    if (r < 0.0001) {
        return view;
    }
    float ang = atan(view.y, view.x);
    // Six-lobe angular bend: continuous in angle, so adjacent sectors join smoothly.
    float lobe = sin(ang * 6.0 + r * 0.35 + view.z * 0.04);
    float a = ang + Kaleido * 0.18 * lobe;
    // Radial fold: r stays monotonic while A < P/(2*pi); stronger tiers make it
    // fold back on itself, so distant geometry repeats at nearer radii.
    const float PERIOD = 12.0;
    float amplitude = Kaleido * 2.4;
    float radial = r + amplitude * sin(r * 6.2831853 / PERIOD - view.z * 0.03);
    radial = max(radial, 0.0);
    float scale = 1.0 + Kaleido * 0.08 * lobe;
    float z = view.z * (1.0 - 0.06 * Kaleido);
    return vec4(cos(a) * radial * scale, sin(a) * radial * scale, z, 1.0);
}

void main() {
    vec3 pos = Position + ChunkOffset;
    vec4 kaleidoView = ModelViewMat * vec4(pos, 1.0);
    gl_Position = ProjMat * barotrauma_kaleido(kaleidoView);

    vertexDistance = fog_distance(ModelViewMat, pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
}
