#version 150

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform mat4 InverseProjection;
uniform mat4 ViewToShip;
uniform float WaveTime;
uniform float EyeWet;
uniform float VolumeFill;
uniform vec4 WaterLight;
uniform float SeaEye;
uniform vec2 SeaBounds;
uniform vec2 SeaRange;
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
    return background * attenuation + WaterLight.rgb * (1.0 - attenuation);
}

vec3 seaColor(vec3 background, vec3 endPoint) {
    float rise = dot(ViewUp, endPoint);
    float boundary = rise > 0.0 ? SeaBounds.x : SeaBounds.y;
    float fraction = clamp(boundary / max(abs(rise), 0.0001), 0.0, 1.0);
    float waterDistance = length(endPoint) * fraction;
    vec3 attenuated = waterColor(background, min(12.0, waterDistance * WaterLight.w));
    return mix(attenuated, WaterLight.rgb, smoothstep(SeaRange.x, SeaRange.y, waterDistance));
}

void main() {
    vec2 size = vec2(textureSize(SceneColor, 0));
    vec2 uv = gl_FragCoord.xy / size;
    bool stream = surface.x < 0.0;
    vec3 hit = scenePosition(uv);
    if (VolumeFill > 1.5) {
        // Open sea uses one color lookup. Refraction is applied at actual water interfaces.
        fragColor = vec4(seaColor(texture(SceneColor, uv).rgb, hit), 1.0);
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
        background = seaColor(background, viewPosition);
    }
    vec3 color = waterColor(background, min(path * weight, 5.0));
    float sheen = edge * (0.038 + 0.019 * sin(a * 0.6 + b * 0.4));
    color = mix(color, max(color, vec3(0.15, 0.31, 0.35)), sheen);
    if (stream) {
        float crest = smoothstep(0.56, 0.94, sin(a * 1.4 - b * 0.7) * 0.65 + cos(b * 0.9) * 0.35);
        color += vec3(0.075, 0.112, 0.13) * crest * surface.y * film;
    }
    fragColor = vec4(color, 1.0);
}
