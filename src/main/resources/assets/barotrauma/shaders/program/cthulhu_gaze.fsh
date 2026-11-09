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
        // 0 鱼眼：更强的桶形畸变 + RGB 色散 + 紫黑边缘（FOV 收得比早先浅）
        float k = (0.30 + 0.10 * sin(Time * 1.3)) * s;
        vec2 p = barrel(uv, k);
        if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0) {
            fragColor = vec4(vec3(0.03, 0.0, 0.05) * s, 1.0);
            return;
        }
        float ca = 0.0045 * s * (0.7 + 0.3 * sin(Time * 2.1));
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
        // 3 时间涟漪：更强的同心波纹 + 波纹色散
        vec2 c = uv - 0.5;
        float r = length(c);
        float wave = sin(r * 44.0 - Time * 5.5) * 0.008 * s / (1.0 + r * 2.2);
        vec2 p = uv + normalize(c + vec2(1.0E-5)) * wave;
        vec3 base = texture(DiffuseSampler, p).rgb;
        float ring = 0.5 + 0.5 * sin(r * 44.0 - Time * 5.5);
        // 波纹亮带 + 轻微 RGB 分离
        float ca = 0.004 * s * ring;
        float rr = texture(DiffuseSampler, p + vec2(ca, 0.0)).r;
        float bb = texture(DiffuseSampler, p - vec2(ca, 0.0)).b;
        col = vec3(rr, base.g, bb);
        col *= 1.0 + 0.22 * s * (ring - 0.5);
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(col, vec3(lum), 0.20 * s);
        uv = p;
    } else if (Mode < 4.5) {
        // 4 静电噪点：2-3 像素高的细行带撕裂 + 扫描线 + 雪花
        float band = floor(uv.y * InSize.y / 3.0);          // 每 3 像素一条行带
        float tick = floor(Time * 16.0);                    // 每 1/16 秒重掷一次，单条持续时间更短
        float tearSeed = hash(vec2(band, tick));
        float tear = step(0.97, tearSeed);                  // 约 3% 的细行带被撕开
        // 少数行带跳得更远
        float mag = 0.020 + 0.060 * step(0.90, hash(vec2(band, tick + 17.0)));
        float tearAmt = (hash(vec2(band, tick + 3.0)) - 0.5) * mag * 2.0 * s;
        vec2 p = uv + vec2(tear * tearAmt, 0.0);
        // 未被撕裂的行也有细小横向抖动
        p.x += (hash(uv * InSize + Time) - 0.5) * 0.002 * s;

        // 被撕的细行带做 RGB 分离，错位越远分离越大
        float split = tear * (0.003 + abs(tearAmt) * 0.20);
        float rr = texture(DiffuseSampler, p + vec2(split, 0.0)).r;
        float gg = texture(DiffuseSampler, p).g;
        float bb = texture(DiffuseSampler, p - vec2(split, 0.0)).b;
        col = vec3(rr, gg, bb);

        // 扫描线 + 雪花
        col *= 0.85 + 0.15 * sin(uv.y * InSize.y * 1.6 + Time * 8.0);
        float n = hash(floor(uv * InSize * 0.5) + floor(Time * 12.0));
        col += (n - 0.5) * 0.22 * s;
        uv = p;
    } else if (Mode < 5.5) {
        // 5 梦境漂移：规则的网格错位 + 每格独立透视缩放 + 接缝压暗。
        // 与正弦波动类不同：位移在格子内部是常量，边界出现明显的透视错位。
        vec2 gridSize = vec2(6.0, 4.0);
        vec2 cell = floor(uv * gridSize);
        vec2 local = fract(uv * gridSize);
        float t = Time * 0.35;
        // 每格一个缓慢变化的偏移（规律、不随机）
        vec2 shift = vec2(sin(cell.y * 1.7 + t), cos(cell.x * 1.9 - t)) * (0.06 + 0.03 * s) * s;
        // 每格以自身中心做轻微透视缩放，制造“透视错位”
        float zoom = 1.0 + 0.14 * s * sin(cell.x * 0.9 + cell.y * 1.3 + t);
        vec2 centered = (local - 0.5) * zoom;
        vec2 sampleUv = (cell + 0.5 + centered + shift) / gridSize;
        sampleUv = clamp(sampleUv, vec2(0.001), vec2(0.999));
        col = texture(DiffuseSampler, sampleUv).rgb;
        // 接缝：格子边界压暗，像拼接错位的墙面
        float edge = min(min(local.x, 1.0 - local.x), min(local.y, 1.0 - local.y));
        float seam = 1.0 - smoothstep(0.0, 0.055, edge);
        col *= mix(1.0, 0.70, seam * 0.85 * s);
        // 阈限空间的低饱和暖调
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(col, vec3(lum) * vec3(1.06, 1.00, 0.94), 0.35 * s);
        uv = sampleUv;
    } else if (Mode < 6.5) {
        // 6 平面凝视：透视压平由投影矩阵完成（CameraProjectionMixin），
        // 这里只补一点边缘压暗与极细横向扫描，强化“世界被摊平”的观感。
        float v = smoothstep(0.40, 1.0, length((uv - 0.5) * vec2(aspectRatio(), 1.0)));
        col = mix(col, vec3(0.03, 0.02, 0.06), v * 0.30 * s);
        col *= 1.0 - 0.035 * s * (0.5 + 0.5 * sin(uv.y * InSize.y * 0.7 + Time * 1.2));
    } else {
        // 7 万花筒：几何折叠在核心顶点着色器里完成（Kaleido uniform），
        // 后处理不叠加像素位移，只保留下面的全模式暗角。
    }

    // 全模式暗角呼吸
    float vig2 = 1.0 - smoothstep(0.35, 1.05, length((uv - 0.5) * vec2(aspectRatio(), 1.0)));
    col *= mix(1.0, mix(0.35, 1.0, vig2), 0.55 * s);
    fragColor = vec4(col, 1.0);
}
