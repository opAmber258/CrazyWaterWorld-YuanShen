#version 150

uniform float AspectRatio;
uniform float GlassVisor;

in vec2 uv;
out vec4 fragColor;

void main() {
    vec2 point = uv * 2.0 - 1.0;
    point.y -= 0.035;
    // Preserve a visible side frame on wide displays without shrinking the central view vertically.
    point.x *= pow(max(1.0, AspectRatio / (16.0 / 9.0)), 0.35);
    vec2 aperture = mix(vec2(0.91, 0.80), vec2(0.98, 0.89), GlassVisor);
    vec2 edge = abs(point) / aperture;
    float outline = max(max(edge.x, edge.y), (edge.x + edge.y) * 0.616) - 1.0;
    float aa = max(fwidth(outline), 0.001);
    float shell = smoothstep(-aa, aa, outline);
    float rim = 1.0 - smoothstep(0.034, 0.136, outline);
    float seal = 1.0 - smoothstep(0.008, 0.025, abs(outline - 0.028));
    float upper = smoothstep(-0.8, 0.65, point.y);
    vec3 enamel = mix(vec3(0.030, 0.042, 0.041), vec3(0.086, 0.102, 0.097), upper);
    vec3 frame = mix(vec3(0.009, 0.013, 0.014), enamel, rim);
    frame = mix(frame, vec3(0.008, 0.011, 0.012), seal * 0.78);

    // A subtle reflection at the glass edge leaves the clear centre transparent.
    float glassEdge = smoothstep(-0.27, -0.012, outline);
    float reflection = glassEdge * (0.024 + 0.026 * upper);
    reflection *= mix(1.0, 0.7, GlassVisor);
    float alpha = mix(reflection, 0.985, shell);
    vec3 lens = vec3(0.24, 0.38, 0.40);
    vec3 color = mix(lens * reflection, frame * 0.985, shell) / max(alpha, 0.0001);
    fragColor = vec4(color, alpha);
}
