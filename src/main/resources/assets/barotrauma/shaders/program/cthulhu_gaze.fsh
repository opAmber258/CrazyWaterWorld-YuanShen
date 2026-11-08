#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float Strength;
uniform float Mode;

in vec2 texCoord;
out vec4 fragColor;

vec2 barrel(vec2 uv, float k) {
    vec2 c = uv - 0.5;
    float r2 = dot(c, c);
    return 0.5 + c * (1.0 + k * r2);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 hueRotate(vec3 color, float angle) {
    const vec3 k = vec3(0.57735);
    float c = cos(angle);
    return color * c + cross(k, color) * sin(angle) + k * dot(k, color) * (1.0 - c);
}

float aspectRatio() {
    return InSize.x / max(InSize.y, 1.0);
}

void main() {
    float s = clamp(Strength, 0.0, 1.0);
    vec3 orig = texture(DiffuseSampler, texCoord).rgb;
    if (s < 0.002) {
        fragColor = vec4(orig, 1.0);
        return;
    }

    // 全模式共用的轻微呼吸/漂移
    vec2 uv = texCoord + vec2(sin(Time * 0.83), cos(Time * 1.07)) * 0.0022 * s;
    vec3 col = orig;

    if (Mode < 0.5) {
        // 0 鱼眼：桶形畸变 + 色散 + 紫黑边缘
        float k = (0.20 + 0.08 * sin(Time * 1.3)) * s;
        vec2 p = barrel(uv, k);
        if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0) {
            fragColor = vec4(vec3(0.03, 0.0, 0.05) * s, 1.0);
            return;
        }
        float ca = 0.0035 * s * (0.7 + 0.3 * sin(Time * 2.1));
        float r = texture(DiffuseSampler, barrel(vec2(p.x + ca, p.y), k)).r;
        float g = texture(DiffuseSampler, p).g;
        float b = texture(DiffuseSampler, barrel(vec2(p.x - ca, p.y), k)).b;
        col = vec3(r, g, b);
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        vec3 weird = mix(vec3(lum) * vec3(0.70, 0.40, 1.25), col.bgr, 0.35);
        col = mix(col, weird, 0.45 * s);
        float vig = 1.0 - smoothstep(0.28, 0.95, length((p - 0.5) * vec2(aspectRatio(), 1.0)));
        col *= mix(1.0, mix(0.22, 1.0, vig), s);
        col += (hash(p * InSize + Time) - 0.5) * 0.06 * s;
        uv = p;
    } else if (Mode < 1.5) {
        // 1 深渊低语：多层波动 + 色相漂移
        uv += vec2(sin(uv.y * 21.0 + Time * 2.1),
                   sin(uv.x * 13.0 - Time * 1.6)) * vec2(0.0045, 0.0035) * s;
        col = texture(DiffuseSampler, uv).rgb;
        float hue = Time * 0.12 + uv.y * 0.22;
        col = mix(col, hueRotate(col, hue), 0.55 * s);
        col = mix(col, col * vec3(0.70, 1.15, 0.95),
                  0.35 * s * (0.5 + 0.5 * sin(Time * 0.9)));
    } else if (Mode < 2.5) {
        // 2 星之彩：异常配色 + 像素化 + 漂移色带
        col = texture(DiffuseSampler, uv).rgb;
        mat3 weird = mat3(0.9, 0.1, 0.5,
                          0.2, 0.4, 0.1,
                          0.6, 0.2, 0.9);
        vec3 tinted = weird * col;
        float bands = 0.5 + 0.5 * sin((uv.y + Time * 0.06) * 46.0);
        tinted *= 0.8 + 0.45 * bands * s;
        float levels = mix(64.0, 5.0, s);
        tinted = floor(tinted * levels + 0.5) / levels;
        col = mix(col, tinted, s);
    } else if (Mode < 3.5) {
        // 3 时间涟漪：从画面中心向外扩散的同心波纹
        vec2 c = uv - 0.5;
        float r = length(c);
        float wave = sin(r * 60.0 - Time * 6.0) * 0.0025 * s / (1.0 + r * 4.0);
        vec2 p = uv + normalize(c + vec2(1.0E-5)) * wave;
        vec3 base = texture(DiffuseSampler, p).rgb;
        col = base * (1.0 + 0.15 * s * sin(r * 20.0 - Time * 4.0));
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(col, vec3(lum), 0.25 * s);
        uv = p;
    } else if (Mode < 4.5) {
        // 4 静电噪点：扫描线 + 电视雪花 + 偶发水平撕裂
        vec2 p = uv + vec2((hash(uv * InSize + Time) - 0.5) * 0.002 * s, 0.0);
        col = texture(DiffuseSampler, p).rgb * (0.85 + 0.15 * sin(uv.y * InSize.y * 1.6 + Time * 8.0));
        float n = hash(floor(uv * InSize * 0.5) + floor(Time * 12.0));
        col += (n - 0.5) * 0.22 * s;
        float glitch = step(0.985, hash(vec2(floor(uv.y * 24.0), floor(Time * 8.0))));
        col = mix(col, col.rbg, glitch * 0.6 * s);
        uv = p;
    } else {
        // 5 梦境漂移：五向柔化 + 色相缓慢循环 + 柔和摆动
        vec2 p = uv + vec2(sin(uv.y * 6.0 + Time * 0.7),
                           cos(uv.x * 5.0 + Time * 0.9)) * 0.004 * s;
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 5; i++) {
            float a = float(i) * 1.256637;
            acc += texture(DiffuseSampler, p + vec2(cos(a), sin(a)) * 0.0035 * s).rgb;
        }
        col = acc / 5.0;
        col = mix(col, hueRotate(col, Time * 0.10), 0.50 * s);
        col = mix(col, col * vec3(1.05, 0.95, 1.10), 0.30 * s);
        uv = p;
    }

    // 全模式暗角呼吸
    float vig2 = 1.0 - smoothstep(0.35, 1.05, length((uv - 0.5) * vec2(aspectRatio(), 1.0)));
    col *= mix(1.0, mix(0.35, 1.0, vig2), 0.55 * s);
    fragColor = vec4(col, 1.0);
}
