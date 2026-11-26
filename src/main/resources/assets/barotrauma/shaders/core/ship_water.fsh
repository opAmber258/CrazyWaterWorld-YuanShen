#version 150

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform mat4 InverseProjection;
uniform mat4 ViewToShip;
uniform float WaveTime;
uniform float EyeWet;
uniform float VolumeFill;
uniform vec3 WaterLight;
uniform float SeaEye;
uniform vec2 SeaBounds;
uniform vec2 SeaRange;
uniform vec2 LitSeaRange;
uniform vec2 SeaWavePhase;
uniform vec3 ViewUp;

in vec3 viewPosition;
in vec3 shipPosition;
in vec2 surface;
out vec4 fragColor;

vec3 scenePosition(vec2 uv) {
    float depth = texture(SceneDepth, uv).r;
    vec4 view = InverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return view.xyz / max(0.000001, view.w);
}

vec3 waterColor(vec3 background, float path) {
    vec3 attenuation = exp(-vec3(0.46, 0.23, 0.14) * path);
    float illumination = clamp(max(background.r, max(background.g, background.b)) * 3.0, 0.0, 1.0);
    return background * attenuation + WaterLight.rgb * illumination * (1.0 - attenuation);
}

float seaDistance(vec3 endPoint) {
    float rise = dot(ViewUp, endPoint);
    float boundary = rise > 0.0 ? SeaBounds.x : SeaBounds.y;
    float fraction = clamp(boundary / max(abs(rise), 0.0001), 0.0, 1.0);
    return length(endPoint) * fraction;
}

const vec3 seaTint = vec3(0.375, 0.400, 0.450);
const vec3 seaBackground = vec3(0.0, 0.0, 5.0 / 255.0);

vec3 seaHaze(vec3 ray) {
    float upward = dot(ray, ViewUp) / max(length(ray), 0.0001);
    float scatter = mix(0.28, 0.68, smoothstep(-0.6, 0.5, upward));
    // Cold Caverns pairs the nearly black background with a blue-green backdrop.
    return mix(seaBackground, WaterLight, scatter);
}

vec3 seaColor(vec3 background, vec3 ray, float waterDistance) {
    // The original outdoor pass mixes the scene with half-strength water color.
    vec3 tint = mix(vec3(1.0), seaTint, 0.5 * smoothstep(0.0, 0.75, waterDistance));
    float unlitFog = smoothstep(SeaRange.x, SeaRange.y, waterDistance);
    float litFog = smoothstep(LitSeaRange.x, LitSeaRange.y, waterDistance);
    float illumination = max(background.r, max(background.g, background.b));
    float fog = mix(unlitFog, litFog, smoothstep(0.025, 0.16, illumination));
    return mix(background, seaHaze(ray), fog) * tint;
}

vec2 seaSample(vec2 uv, vec2 size, vec3 hit, float waterDistance) {
    vec2 texel = 1.0 / size;
    // Two layers of procedural ripples reproduce the moving bump samples without copying the texture.
    vec2 a = vec2(dot(uv, vec2(27.0, 18.0)), dot(uv, vec2(-21.6, 18.0))) + SeaWavePhase;
    vec2 b = vec2(dot(uv, vec2(17.0, -28.0)), dot(uv, vec2(11.05, 28.0))) - SeaWavePhase * 2.0;
    vec2 ripple = 0.5 * (vec2(sin(a.x), cos(a.y)) + vec2(cos(b.x), sin(b.y)));
    float wet = smoothstep(0.25, 2.0, waterDistance);
    // A 1080-line picture bends by at most five vertical pixels; scale with image height.
    vec2 bend = ripple * vec2(1.0, 5.0) * (size.y / 1080.0) * texel;
    vec2 displaced = clamp(uv + bend * wet, texel * 0.5, 1.0 - texel * 0.5);
    float originalDepth = -hit.z;
    float displacedDepth = -scenePosition(displaced).z;
    // Do not pull dry openings or distant background across a nearby silhouette.
    float depthJump = abs(displacedDepth - originalDepth) / (1.0 + min(displacedDepth, originalDepth));
    return mix(uv, displaced, 1.0 - smoothstep(0.018, 0.075, depthJump));
}

void main() {
    vec2 size = vec2(textureSize(SceneColor, 0));
    vec2 uv = gl_FragCoord.xy / size;
    bool stream = surface.x < 0.0;
    vec3 hit = scenePosition(uv);
    if (VolumeFill > 1.5) {
        float waterDistance = seaDistance(hit);
        if (waterDistance >= LitSeaRange.y) {
            fragColor = vec4(seaHaze(hit) * mix(vec3(1.0), seaTint, 0.5), 1.0);
            return;
        }
        vec2 sampleUV = seaSample(uv, size, hit, waterDistance);
        fragColor = vec4(seaColor(texture(SceneColor, sampleUV).rgb, hit, waterDistance), 1.0);
        return;
    }
    if (VolumeFill > 0.5) {
        // With the eye underwater, opaque geometry before the first interface only absorbs light.
        fragColor = vec4(waterColor(texture(SceneColor, uv).rgb, min(length(hit), 5.0)), 1.0);
        return;
    }

    float sceneDistance = -hit.z;
    float interfaceDistance = -viewPosition.z;
    float behind = sceneDistance - interfaceDistance;
    vec3 planeNormal = cross(dFdx(viewPosition), dFdy(viewPosition));
    float normalGap = abs(dot(hit - viewPosition, planeNormal))
            * inversesqrt(max(dot(planeNormal, planeNormal), 1.0e-18));
    if ((!stream && gl_FrontFacing == (EyeWet > 0.5))
            || behind <= max(0.0025, interfaceDistance * 0.00025)) {
        discard;
    }

    // At a grazing angle a few millimetres of water can cover many pixels. Its optical weight
    // comes from the separation from the actual deck along the interface normal.
    float film = stream ? smoothstep(0.003, 0.105, -surface.x) : smoothstep(0.033, 0.145, normalGap);
    float weight = EyeWet > 0.5 ? 1.0 : film;
    if (weight <= 0.0001) {
        discard;
    }
    float wave = stream ? film : smoothstep(0.24, 0.86, surface.y) * weight;
    float edge = stream ? (0.24 + surface.y * 0.52) * film
            : wave * exp(-max(surface.x, 0.0) * 10.0);
    float a = dot(shipPosition.xz, vec2(7.6, 3.2)) + shipPosition.y * (stream ? -9.0 : 2.8)
            - WaveTime * (stream ? 12.0 : -2.6);
    float b = dot(shipPosition.xz, vec2(-4.1, 9.0)) - shipPosition.y * 2.0 - WaveTime * (stream ? 8.0 : 1.9);
    vec2 sampleUV = uv;
    if (wave > 0.001) {
        vec2 ripple = vec2(sin(a) + 0.45 * cos(b), cos(b) + 0.40 * sin(a - b));
        float distortion = wave * 0.65 + edge * (stream ? 2.2 : 1.35);
        vec2 displaced = clamp(uv + ripple * distortion / size, 0.5 / size, 1.0 - 0.5 / size);
        vec3 displacedHit = scenePosition(displaced);
        float candidateDistance = -displacedHit.z;
        // A displaced reading must stay behind the water and on the same scene surface.
        float transmission = smoothstep(0.015, 0.12, min(behind, candidateDistance - interfaceDistance));
        transmission *= 1.0 - smoothstep(0.025, 0.10,
                abs(candidateDistance - sceneDistance) / (1.0 + min(candidateDistance, sceneDistance)));
        sampleUV = mix(uv, displaced, transmission);
    }

    float path = EyeWet > 0.5 ? length(viewPosition) : length(hit - viewPosition);
    if (stream) {
        path = min(path, 1.65 * -surface.x);
    } else if (EyeWet < 0.5 && surface.x > 0.0) {
        // A ray entering through the side of a water volume may leave through the top.
        float rise = (ViewToShip * vec4(hit - viewPosition, 0.0)).y;
        if (rise > surface.x) {
            path *= surface.x / rise;
        }
    }
    vec3 background = texture(SceneColor, sampleUV).rgb;
    if (SeaEye > 0.5) {
        background = seaColor(background, viewPosition, seaDistance(viewPosition));
    }
    vec3 color = waterColor(background, min(path * weight, 5.0));
    // 只有被照亮的场景能给水面提供散射、边缘反光和水花亮度。
    float illumination = clamp(max(background.r, max(background.g, background.b)) * 4.0, 0.0, 1.0);
    float sheen = edge * illumination * (0.038 + 0.019 * sin(a * 0.6 + b * 0.4));
    color = mix(color, max(color, vec3(0.15, 0.31, 0.35)), sheen);
    if (stream) {
        float crest = smoothstep(0.56, 0.94, sin(a * 1.4 - b * 0.7) * 0.65 + cos(b * 0.9) * 0.35);
        color += vec3(0.075, 0.112, 0.13) * crest * surface.y * film * illumination;
    }
    fragColor = vec4(color, 1.0);
}
