#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ProjMat;
uniform mat4 ViewToShip;
uniform float VolumeFill;

out vec3 viewPosition;
out vec3 shipPosition;
out vec2 surface;

void main() {
    gl_Position = VolumeFill > 0.5 ? vec4(Position.xy, 0.0, 1.0) : ProjMat * vec4(Position, 1.0);
    viewPosition = Position;
    shipPosition = (ViewToShip * vec4(Position, 1.0)).xyz;
    surface = UV0;
}
