#version 150

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform sampler2D ShadowDepth;
uniform mat4 SceneProjection;
uniform mat4 InverseProjection;
uniform vec4 BeamOrigin;
uniform vec4 BeamDirection;
uniform float BeamRange;
uniform float FilledBeam;
uniform vec4 PlayerHalo;
uniform float DeepScene;
uniform float RemoveDarkFloor;
uniform float KeepDimScene;
uniform float ScatterPass;

in vec2 uv;
out vec4 fragColor;

vec3 unproject(vec2 coord, float depth) {
    vec4 p = InverseProjection * vec4(coord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

const float CONE_EDGE = 0.895;

float coneProfile(float angle) {
    // 宽而柔和的外缘，加上中心较集中的反光杯热点。
    return smoothstep(CONE_EDGE, 0.962, angle) * (0.49 + 0.51 * smoothstep(0.964, 0.999, angle));
}

// Express distance on the 12-block handheld beam curve before applying falloff.
const float HANDHELD_RANGE = 12.0;

float rangeFalloff(float d) {
    return (1.0 - smoothstep(8.0, HANDHELD_RANGE, d)) / (1.0 + d * d * 0.04);
}

float visibility(vec3 from, vec3 to) {
    float strongest = 0.0;
    float nextStrongest = 0.0;
    // 只用可见深度：有限步数沿光路走，排除门框、墙面和设备挡住的表面。
    for (int step = 0; step < 9; ++step) {
        float t = (float(step) + 0.55) / 9.5;
        vec3 probe = mix(from, to, t);
        vec4 clip = SceneProjection * vec4(probe, 1.0);
        if (clip.w <= 0.0) {
            continue;
        }
        vec3 ndc = clip.xyz / clip.w;
        if (abs(ndc.x) >= 1.0 || abs(ndc.y) >= 1.0 || ndc.z <= -1.0 || ndc.z >= 1.0) {
            continue;
        }
        vec2 coord = ndc.xy * 0.5 + 0.5;
        float depth = texture(ShadowDepth, coord).r;
        if (depth >= 0.999999) {
            continue;
        }
        float separation = unproject(coord, depth).z - probe.z;
        // 斜着照时，正常的近邻深度差会变大。用距离容差避免表面给自身投下条纹。
        float blocker = smoothstep(0.045 + 0.008 * abs(probe.z),
                0.14 + 0.016 * abs(probe.z), separation);
        nextStrongest = max(nextStrongest, min(strongest, blocker));
        strongest = max(strongest, blocker);
        if (nextStrongest >= 0.999) {
            break;
        }
    }
    // 一次深度命中可能来自柜门边缘的单个像素。多个命中才形成深阴影。
    return 1.0 - max(nextStrongest, strongest * 0.22);
}

float toLinear(float color) {
    return color <= 0.04045 ? color / 12.92 : pow((color + 0.055) / 1.055, 2.4);
}

float toDisplay(float color) {
    return color <= 0.0031308 ? color * 12.92 : 1.055 * pow(color, 1.0 / 2.4) - 0.055;
}

vec3 whiteLight(vec3 scene, float exposure) {
    float peak = max(scene.r, max(scene.g, scene.b));
    if (peak <= 0.0 || exposure <= 0.0) {
        return vec3(0.0);
    }
    float linearPeak = toLinear(peak);
    float room = 1.0 - linearPeak;
    float gain = room * exposure / max(room + linearPeak * exposure, 0.0001);
    // Minecraft 把光图乘在纹理 RGB 上。补光也共用一个倍率，保留贴图通道比例。
    float boost = toDisplay(linearPeak * (1.0 + gain)) / peak;
    return scene * max(boost - 1.0, 0.0);
}

// Clip to the light's range, forward half-space and cone sides before sampling.
vec2 beamSegment(vec3 origin, vec3 ray, float reach) {
    float rayStart = dot(origin, ray);
    float sphereDiscriminant = rayStart * rayStart - dot(origin, origin) + BeamRange * BeamRange;
    if (sphereDiscriminant <= 0.0) return vec2(0.0);
    float halfChord = sqrt(sphereDiscriminant);
    vec2 segment = vec2(max(0.0, -rayStart - halfChord), min(reach, -rayStart + halfChord));

    float axialStart = dot(origin, BeamDirection.xyz);
    float axialStep = dot(ray, BeamDirection.xyz);
    if (abs(axialStep) < 0.00001) {
        if (axialStart <= 0.0) return vec2(0.0);
    } else if (axialStep > 0.0) {
        segment.x = max(segment.x, -axialStart / axialStep);
    } else {
        segment.y = min(segment.y, -axialStart / axialStep);
    }
    if (segment.y <= segment.x) return vec2(0.0);

    float edgeSquared = CONE_EDGE * CONE_EDGE;
    float a = axialStep * axialStep - edgeSquared;
    float b = axialStep * axialStart - edgeSquared * rayStart;
    float c = axialStart * axialStart - edgeSquared * dot(origin, origin);
    if (abs(a) < 0.00001) {
        if (abs(b) < 0.00001) return c > 0.0 ? segment : vec2(0.0);
        float crossing = -c / (2.0 * b);
        return b > 0.0 ? vec2(max(segment.x, crossing), segment.y)
                : vec2(segment.x, min(segment.y, crossing));
    }

    float discriminant = b * b - a * c;
    if (discriminant <= 0.0) return a > 0.0 ? segment : vec2(0.0);
    float root = sqrt(discriminant);
    float first = (-b - root) / a;
    float second = (-b + root) / a;
    if (a < 0.0) return vec2(max(segment.x, second), min(segment.y, first));

    // The other interval belongs to the reverse cone and is outside the forward half-space.
    if (segment.x < first) return vec2(segment.x, min(segment.y, first));
    return vec2(max(segment.x, second), segment.y);
}

void main() {
    float depth = texture(SceneDepth, uv).r;
    vec3 point = unproject(uv, depth < 0.999999 ? depth : 0.99);
    // 导数必须在分支前算。真实深度给出表面朝向，图像边界走保守亮度。
    vec3 dx = dFdx(point);
    vec3 dy = dFdy(point);
    vec3 normal = cross(dx, dy);
    vec3 offset = point - BeamOrigin.xyz;
    float distanceToLight = length(offset);
    vec3 ray = offset / max(distanceToLight, 0.0001);
    float surface = 0.0;
    bool scatterInWater = BeamDirection.w > 0.5;
    float distanceScale = HANDHELD_RANGE / BeamRange;
    if (ScatterPass <= 0.0 && BeamOrigin.w > 0.0 && depth < 0.999999 && distanceToLight < BeamRange) {
        float beamDistance = distanceToLight * distanceScale;
        float profile = coneProfile(dot(ray, BeamDirection.xyz)) * rangeFalloff(beamDistance);
        if (profile > 0.0) {
            float lambert = 0.55;
            if (dot(normal, normal) > 1.0e-14) {
                float confidence = 1.0 - smoothstep(0.12, 0.32, max(length(dx), length(dy)));
                lambert = mix(lambert, max(0.0, dot(normalize(normal), -ray)), confidence);
            }
            surface = profile * mix(0.12, 1.0, lambert) * visibility(BeamOrigin.xyz, point);
            if (scatterInWater) {
                surface *= exp(-0.075 * beamDistance);
            }
        }
    }

    float scatter = 0.0;
    vec3 hazeColor = scatterInWater ? vec3(0.51, 0.72, 0.82) : vec3(1.0);
    bool scatterThisPass = scatterInWater ? ScatterPass >= 0.0 : ScatterPass <= 0.0;
    if (FilledBeam > 0.5 && BeamOrigin.w > 0.0 && scatterThisPass) {
        // Only the volume in front of the first opaque surface can reach the camera.
        vec3 start = unproject(uv, 0.0);
        vec3 viewRay = normalize(point - start);
        float reach = depth >= 0.999999 ? length(start - BeamOrigin.xyz) + BeamRange : length(point - start);
        vec2 segment = beamSegment(start - BeamOrigin.xyz, viewRay, reach);
        float span = segment.y - segment.x;
        if (span > 0.0) {
            for (int step = 0; step < 8; ++step) {
                float t = segment.x + span * (float(step) + 0.5) / 8.0;
                vec3 delta = start + viewRay * t - BeamOrigin.xyz;
                float d = length(delta);
                float profile = coneProfile(dot(delta / max(d, 0.0001), BeamDirection.xyz));
                float beamDistance = d * distanceScale;
                float attenuation = scatterInWater ? exp(-0.15 * beamDistance - 0.016 * t)
                                                   : exp(-0.055 * beamDistance);
                scatter += profile * rangeFalloff(beamDistance) * attenuation;
            }
            float viewAlignment = dot(viewRay, BeamDirection.xyz);
            float sideScatter = 1.0 - 0.45 * viewAlignment * viewAlignment;
            scatter *= span * distanceScale * sideScatter * (scatterInWater ? 0.28 : 0.12) / 8.0;
            // A long view down the axis stays readable without washing out the scene.
            scatter /= 1.0 + 2.0 * scatter;
        }
    }
    if (ScatterPass > 0.0) {
        if (scatter <= 0.0) discard;
        // The water before a far rock is lit just like the water on adjacent empty rays.
        fragColor = vec4(hazeColor * scatter * BeamOrigin.w, 0.0);
        return;
    }
    float halo = 0.0;
    if (PlayerHalo.w > 0.0 && depth < 0.999999) {
        halo = 1.0 - smoothstep(0.45, 2.6, length(point - PlayerHalo.xyz));
    }
    if (surface + scatter + halo <= 0.0 && RemoveDarkFloor <= 0.0) {
        discard;
    }
    vec3 scene = texture(SceneColor, uv).rgb;
    float beamExposure = surface * BeamOrigin.w * 88.0;
    if (DeepScene > 0.5) {
        beamExposure *= mix(5.0, 1.4, KeepDimScene);
    }
    // The gain depends on distance only, so bright and dark texels keep their order.
    float nearExposure = halo * mix(4.0, 1.7, KeepDimScene);
    float scenePeak = max(scene.r, max(scene.g, scene.b));
    // Colorful Lighting 混合天空和方块项，未照亮的表面最多得到两个 8 位光图码值。
    float existingLight = smoothstep(2.0 / 255.0, 3.0 / 255.0, scenePeak);
    float exposure = max(beamExposure, nearExposure);
    vec3 reflected = whiteLight(scene, exposure);
    vec3 base = RemoveDarkFloor > 0.5 ? scene * max(existingLight, KeepDimScene) : vec3(0.0);
    vec3 haze = hazeColor * scatter;
    fragColor = vec4(base + reflected + haze * BeamOrigin.w, RemoveDarkFloor);
}
