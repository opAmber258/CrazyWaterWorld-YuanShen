// Software renderer for Blockbench java_block bbmodel files.
// Renders orthographic front/back/side/top + perspective oblique views with
// the embedded texture, to verify geometry and compute the world-space bbox.
// Usage: node render_bbmodel.js <file.bbmodel> <outdir>
const fs = require('fs');
const zlib = require('zlib');

const path = require('path');
const bbPath = process.argv[2];
const outDir = process.argv[3] || '.';
fs.mkdirSync(outDir, { recursive: true });

const bb = JSON.parse(fs.readFileSync(bbPath, 'utf8'));

// ---------- PNG decode (embedded texture) ----------
function decodePNG(buf) {
  let pos = 8, w = 0, h = 0, ct = -1, bd = -1, interlace = -1;
  const idat = [];
  while (pos < buf.length) {
    const len = buf.readUInt32BE(pos), type = buf.toString('ascii', pos + 4, pos + 8);
    if (type === 'IHDR') { w = buf.readUInt32BE(pos + 8); h = buf.readUInt32BE(pos + 12);
      bd = buf[pos + 16]; ct = buf[pos + 17]; interlace = buf[pos + 20]; }
    if (type === 'IDAT') idat.push(buf.subarray(pos + 8, pos + 8 + len));
    pos += 12 + len;
  }
  if (bd !== 8 || (ct !== 6 && ct !== 2) || interlace !== 0) {
    throw new Error('unsupported PNG: bitDepth=' + bd + ' colorType=' + ct + ' interlace=' + interlace);
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const ch = ct === 6 ? 4 : 3;
  const stride = w * ch + 1;
  const px = Buffer.alloc(w * h * 4);
  let prev = Buffer.alloc(w * ch);
  for (let y = 0; y < h; y++) {
    const f = raw[y * stride];
    const line = raw.subarray(y * stride + 1, (y + 1) * stride);
    const cur = Buffer.alloc(w * ch);
    for (let i = 0; i < w * ch; i++) {
      const x = line[i];
      // PNG 预测按“同一通道”的左邻像素：a/c 都用 i - ch，不是 i - 1。
      const a = i >= ch ? cur[i - ch] : 0;
      const b = prev[i];
      const c = (y > 0 && i >= ch) ? prev[i - ch] : 0;
      let v;
      if (f === 0) v = x;
      else if (f === 1) v = x + a;
      else if (f === 2) v = x + b;
      else if (f === 3) v = x + ((a + b) >> 1);
      else if (f === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); v = x + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c); }
      cur[i] = v & 255;
    }
    for (let x = 0; x < w; x++) {
      px[(y * w + x) * 4] = cur[x * ch];
      px[(y * w + x) * 4 + 1] = cur[x * ch + 1];
      px[(y * w + x) * 4 + 2] = cur[x * ch + 2];
      px[(y * w + x) * 4 + 3] = ch === 4 ? cur[x * ch + 3] : 255;
    }
    prev = cur;
  }
  return { w, h, px };
}

const texBuf = Buffer.from(bb.textures[0].source.split(',')[1], 'base64');
const TEX = decodePNG(texBuf);
const resW = (bb.resolution && bb.resolution.width) || TEX.w;
const resH = (bb.resolution && bb.resolution.height) || TEX.h;

// ---------- rotation ----------
const D2R = Math.PI / 180;
function matMul(A, B) {
  const C = [[0,0,0],[0,0,0],[0,0,0]];
  for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++)
    C[i][j] = A[i][0]*B[0][j] + A[i][1]*B[1][j] + A[i][2]*B[2][j];
  return C;
}
function rotMat(rx, ry, rz, order) {
  const cx = Math.cos(rx*D2R), sx = Math.sin(rx*D2R);
  const cy = Math.cos(ry*D2R), sy = Math.sin(ry*D2R);
  const cz = Math.cos(rz*D2R), sz = Math.sin(rz*D2R);
  const Rx = [[1,0,0],[0,cx,-sx],[0,sx,cx]];
  const Ry = [[cy,0,sy],[0,1,0],[-sy,0,cy]];
  const Rz = [[cz,-sz,0],[sz,cz,0],[0,0,1]];
  if (order === 'ZYX') return matMul(Rz, matMul(Ry, Rx));
  if (order === 'XYZ') return matMul(Rx, matMul(Ry, Rz));
  if (order === 'ZYX_neg') return matMul(Rz, matMul(Ry, Rx)); // sign variant applied at input
  throw new Error('order');
}
function apply(M, v) {
  return [
    M[0][0]*v[0] + M[0][1]*v[1] + M[0][2]*v[2],
    M[1][0]*v[0] + M[1][1]*v[1] + M[1][2]*v[2],
    M[2][0]*v[0] + M[2][1]*v[1] + M[2][2]*v[2]
  ];
}

// ---------- geometry collection ----------
// faces: north=-Z, south=+Z, west=-X, east=+X, up=+Y, down=-Y
// uv corner mapping viewed from outside the face
const ORDERS = process.env.ROT_ORDER || 'XYZ';
const NEGXY = process.env.NEG_XY === '1'; // negate x,y rotation signs
let triangles = [];
const cubeWorld = []; // per-cube transformed corners bbox
for (const el of bb.elements) {
  const [x0, y0, z0] = el.from, [x1, y1, z1] = el.to;
  let rx = 0, ry = 0, rz = 0;
  if (el.rotation) { rx = el.rotation[0]; ry = el.rotation[1]; rz = el.rotation[2]; }
  if (NEGXY) { rx = -rx; ry = -ry; }
  const M = rotMat(rx, ry, rz, ORDERS);
  const O = el.origin || [0, 0, 0];
  const T = (v) => {
    const rel = [v[0]-O[0], v[1]-O[1], v[2]-O[2]];
    const r = apply(M, rel);
    return [r[0]+O[0], r[1]+O[1], r[2]+O[2]];
  };
  const c = [
    T([x0,y0,z0]), T([x1,y0,z0]), T([x1,y1,z0]), T([x0,y1,z0]),
    T([x0,y0,z1]), T([x1,y0,z1]), T([x1,y1,z1]), T([x0,y1,z1])
  ];
  // world bbox of this cube
  const wb = c.reduce((acc, p) => acc.map((v, i) => [Math.min(v[0], p[i]), Math.max(v[1], p[i])]), [[1e9,-1e9],[1e9,-1e9],[1e9,-1e9]]);
  cubeWorld.push({ name: el.name, uuid: el.uuid, min: wb.map(p=>p[0]), max: wb.map(p=>p[1]) });

  const F = el.faces || {};
  const addQuad = (pA, pB, pC, pD, uv) => {
    if (!uv) return;
    const [u1, v1, u2, v2] = uv;
    const s = (u, v) => [u / resW * TEX.w, v / resH * TEX.h];
    const tA = s(u1, v1), tB = s(u2, v1), tC = s(u2, v2), tD = s(u1, v2);
    triangles.push({ p: [pA, pB, pC], t: [tA, tB, tC] });
    triangles.push({ p: [pA, pC, pD], t: [tA, tC, tD] });
  };
  if (F.north && F.north.uv) addQuad(c[3], c[2], c[1], c[0], F.north.uv); // viewed from -Z: TL=(x1,y1,z0) TR=(x0? ...)
  // For identification renders exact uv orientation is secondary; use a consistent mapping:
  // north: TL=(x1,y1,z0) TR=(x0,y1,z0) BR=(x0,y0,z0) BL=(x1,y0,z0) — mirrors +X; acceptable preview approximation? NO — use correct:
  // Correct per-face mapping (MC convention, viewed from outside):
  if (F.north && F.north.uv) { triangles.pop(); triangles.pop(); addQuadCorrect(c[2], c[3], c[0], c[1], F.north.uv); }
  function addQuadCorrect(pTL, pTR, pBR, pBL, uv) {
    const [u1, v1, u2, v2] = uv;
    const s = (u, v) => [u / resW * TEX.w, v / resH * TEX.h];
    const tTL = s(u1, v1), tTR = s(u2, v1), tBR = s(u2, v2), tBL = s(u1, v2);
    triangles.push({ p: [pTL, pTR, pBR], t: [tTL, tTR, tBR] });
    triangles.push({ p: [pTL, pBR, pBL], t: [tTL, tBR, tBL] });
  }
  if (F.south && F.south.uv) addQuadCorrect(c[4], c[5], c[1], c[0] /*placeholder*/, F.south.uv);
  if (F.north && F.north.uv) { /* handled above */ }
}
// NOTE: the mapping above got tangled; rebuild cleanly below.
triangles = [];
for (const el of bb.elements) {
  const [x0, y0, z0] = el.from, [x1, y1, z1] = el.to;
  let rx = 0, ry = 0, rz = 0;
  if (el.rotation) { rx = el.rotation[0]; ry = el.rotation[1]; rz = el.rotation[2]; }
  if (NEGXY) { rx = -rx; ry = -ry; }
  const M = rotMat(rx, ry, rz, ORDERS);
  const O = el.origin || [0, 0, 0];
  const T = (v) => { const rel = [v[0]-O[0], v[1]-O[1], v[2]-O[2]]; const r = apply(M, rel); return [r[0]+O[0], r[1]+O[1], r[2]+O[2]]; };
  const c = [
    T([x0,y0,z0]), T([x1,y0,z0]), T([x1,y1,z0]), T([x0,y1,z0]), // 0 NWB(-Z bottom-left...) indices: 0:(x0,y0,z0) 1:(x1,y0,z0) 2:(x1,y1,z0) 3:(x0,y1,z0)
    T([x0,y0,z1]), T([x1,y0,z1]), T([x1,y1,z1]), T([x0,y1,z1])  // 4:(x0,y0,z1) 5:(x1,y0,z1) 6:(x1,y1,z1) 7:(x0,y1,z1)
  ];
  const q = (TL, TR, BR, BL, uv) => {
    if (!uv) return;
    const [u1, v1, u2, v2] = uv;
    const s = (u, v) => [u / resW * TEX.w, v / resH * TEX.h];
    const tTL = s(u1, v1), tTR = s(u2, v1), tBR = s(u2, v2), tBL = s(u1, v2);
    triangles.push({ p: [TL, TR, BR], t: [tTL, tTR, tBR] });
    triangles.push({ p: [TL, BR, BL], t: [tTL, tBR, tBL] });
  };
  const F = el.faces || {};
  // north (-Z), viewed from -Z looking +Z: +X is on the LEFT; TL=(x1,y1,z0)
  if (F.north && F.north.uv) q(c[2], c[3], c[0], c[1], F.north.uv);
  // south (+Z), viewed from +Z looking -Z: +X on the RIGHT; TL=(x0,y1,z1)
  if (F.south && F.south.uv) q(c[7], c[6], c[5], c[4], F.south.uv);
  // west (-X), viewed from -X looking +X: +Z on the RIGHT; TL=(x0,y1,z0)
  if (F.west && F.west.uv) q(c[3], c[7], c[4], c[0], F.west.uv);
  // east (+X), viewed from +X looking -X: +Z on the LEFT; TL=(x1,y1,z1)
  if (F.east && F.east.uv) q(c[6], c[2], c[1], c[5], F.east.uv);
  // up (+Y), viewed from above looking -Y: north(-Z) at top; TL=(x0,y1,z0)
  if (F.up && F.up.uv) q(c[3], c[2], c[6], c[7], F.up.uv); // TL=(x0,y1,z0) TR=(x1,y1,z0) BR=(x1,y1,z1) BL=(x0,y1,z1)
  // down (-Y), viewed from below looking +Y: north(-Z) at top; TL=(x0,y0,z0)
  if (F.down && F.down.uv) q(c[0], c[1], c[5], c[4], F.down.uv);
}

// ---------- world bbox ----------
let wmin = [1e9, 1e9, 1e9], wmax = [-1e9, -1e9, -1e9];
for (const t of triangles) for (const p of t.p)
  for (let i = 0; i < 3; i++) { wmin[i] = Math.min(wmin[i], p[i]); wmax[i] = Math.max(wmax[i], p[i]); }
console.log('WORLD BBOX min', wmin.map(v=>+v.toFixed(3)), 'max', wmax.map(v=>+v.toFixed(3)), 'size', wmax.map((v,i)=>+(v-wmin[i]).toFixed(3)));

// per-cube world bbox print (for bottle identification)
console.log('--- per-cube world bbox ---');
for (const cw of cubeWorld) {
  console.log(cw.name.padEnd(16), 'min', cw.min.map(v=>+v.toFixed(2)).join(','), 'max', cw.max.map(v=>+v.toFixed(2)).join(','));
}

// ---------- rasterizer ----------
function render(view, W, H, file) {
  // camera basis
  let f, r, u, ortho = true, persp = null;
  if (view === 'front') { f = [0, 0, 1]; }        // 正视：从 -Z 看屏幕面（交付文档定义）
  else if (view === 'back') { f = [0, 0, -1]; }   // 背视：从 +Z 看背面
  else if (view === 'side') { f = [-1, 0, 0]; }   // from +X looking -X: muzzle(-Z) on right
  else if (view === 'side2') { f = [1, 0, 0]; }
  else if (view === 'top') { f = [0, -1, 0]; }
  else if (view === 'oblique') {
    ortho = false;
    const az = 40 * D2R, el = 30 * D2R, dist = 40;
    const dir = [Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)];
    persp = { eye: [wmax[0]/2 + dir[0]*dist, (wmin[1]+wmax[1])/2 + dir[1]*dist, (wmin[2]+wmax[2])/2 + dir[2]*dist], fov: 35 * D2R };
    f = dir.map(v => -v);
  }
  // 顶视图的视线和 Y 轴平行，用 Y 当 up 会叉积为零（NaN）；改用 Z 轴。
  const up0 = view === 'top' ? [0, 0, -1] : [0, 1, 0];
  if (!f) throw new Error('view');
  r = norm(cross(f, up0));
  u = cross(r, f);
  f = norm(f);

  const cx = (wmin[0]+wmax[0])/2, cy = (wmin[1]+wmax[1])/2, cz = (wmin[2]+wmax[2])/2;
  const span = Math.max(wmax[0]-wmin[0], wmax[1]-wmin[1], wmax[2]-wmin[2]) * 1.15;
  const img = Buffer.alloc(W * H * 4);
  const zbuf = new Float64Array(W * H).fill(Infinity);

  const project = (p) => {
    const rel = [p[0]-cx, p[1]-cy, p[2]-cz];
    const x = dot(rel, r), y = dot(rel, u), z = dot(rel, f);
    // 画布是 1200×900：横向归一化要乘 H/W，否则等长边在屏幕上横向拉伸 4/3。
    const aspect = H / W;
    if (ortho) return { x: (x / span) * aspect + 0.5, y: 0.5 - y / span, z };
    const ez = dot(rel, f) + 40; // distance along view axis from eye plane
    const scale = 0.5 / (ez * Math.tan(persp.fov / 2));
    return { x: 0.5 + x * scale * aspect, y: 0.5 - y * scale, z: ez };
  };

  for (const t of triangles) {
    const [A, B, C] = t.p.map(project);
    const [tA, tB, tC] = t.t;
    const minX = Math.max(0, Math.floor(Math.min(A.x, B.x, C.x) * W));
    const maxX = Math.min(W - 1, Math.ceil(Math.max(A.x, B.x, C.x) * W));
    const minY = Math.max(0, Math.floor(Math.min(A.y, B.y, C.y) * H));
    const maxY = Math.min(H - 1, Math.ceil(Math.max(A.y, B.y, C.y) * H));
    const area = (B.x - A.x) * (C.y - A.y) - (B.y - A.y) * (C.x - A.x);
    if (Math.abs(area) < 1e-12) continue;
    // world normal for shading
    const pA = t.p[0], pB = t.p[1], pC = t.p[2];
    const e1 = [pB[0]-pA[0], pB[1]-pA[1], pB[2]-pA[2]];
    const e2 = [pC[0]-pA[0], pC[1]-pA[1], pC[2]-pA[2]];
    let n = cross(e1, e2); n = norm(n);
    const light = norm([0.35, 0.85, 0.4]);
    let shade = 0.62 + 0.38 * Math.abs(dot(n, light));
    for (let py = minY; py <= maxY; py++) {
      for (let px = minX; px <= maxX; px++) {
        const sx = (px + 0.5) / W, sy = (py + 0.5) / H;
        const w0 = ((B.x - A.x) * (sy - A.y) - (B.y - A.y) * (sx - A.x)) / area;
        const w1 = ((C.x - B.x) * (sy - B.y) - (C.y - B.y) * (sx - B.x)) / area;
        const w2 = 1 - w0 - w1;
        if (w0 < 0 || w1 < 0 || w2 < 0) continue;
        // check barycentric in triangle ABC via sub-areas — w0/w1 trick above is incomplete; do standard:
        const l0 = ((B.x - sx) * (C.y - sy) - (B.y - sy) * (C.x - sx)) / area;
        const l1 = ((C.x - sx) * (A.y - sy) - (C.y - sy) * (A.x - sx)) / area;
        const l2 = 1 - l0 - l1;
        if (l0 < 0 || l1 < 0 || l2 < 0) continue;
        // 透视深度要按 1/z 插值；线性插值会让较远的面错误遮住较近的面。
        const depth = ortho
            ? l0 * A.z + l1 * B.z + l2 * C.z
            : 1 / (l0 / A.z + l1 / B.z + l2 / C.z);
        const idx = py * W + px;
        if (depth >= zbuf[idx]) continue;
        let tu, tv;
        if (ortho) {
          tu = l0 * tA[0] + l1 * tB[0] + l2 * tC[0];
          tv = l0 * tA[1] + l1 * tB[1] + l2 * tC[1];
        } else {
          // 透视：UV 按 1/z 加权，否则深度不同的端点会拉伸纹理、对角线接缝。
          const q0 = l0 / A.z, q1 = l1 / B.z, q2 = l2 / C.z;
          const qs = q0 + q1 + q2;
          tu = (q0 * tA[0] + q1 * tB[0] + q2 * tC[0]) / qs;
          tv = (q0 * tA[1] + q1 * tB[1] + q2 * tC[1]) / qs;
        }
        const tx = Math.min(TEX.w - 1, Math.max(0, Math.floor(tu)));
        const ty = Math.min(TEX.h - 1, Math.max(0, Math.floor(tv)));
        const ti = (ty * TEX.w + tx) * 4;
        const alpha = TEX.px[ti + 3];
        if (alpha < 8) continue;
        zbuf[idx] = depth;
        const o = idx * 4;
        img[o] = Math.min(255, TEX.px[ti] * shade);
        img[o + 1] = Math.min(255, TEX.px[ti + 1] * shade);
        img[o + 2] = Math.min(255, TEX.px[ti + 2] * shade);
        img[o + 3] = 255;
      }
    }
  }
  writePNG(path.join(outDir, file), W, H, img);
  console.log('rendered', file);
}

// ---------- minimal PNG encoder ----------
function crc32(buf) {
  let table = crc32.table;
  if (!table) {
    table = crc32.table = new Int32Array(256);
    for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1); table[n] = c; }
  }
  let c = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) c = table[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function writePNG(file, w, h, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  }
  const png = Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 6 })),
    chunk('IEND', Buffer.alloc(0))
  ]);
  fs.writeFileSync(file, png);
}
function cross(a, b) { return [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]; }
function dot(a, b) { return a[0]*b[0] + a[1]*b[1] + a[2]*b[2]; }
function norm(a) { const l = Math.hypot(...a); return [a[0]/l, a[1]/l, a[2]/l]; }

const W = 1200, H = 900;
for (const v of ['front', 'back', 'side', 'top', 'oblique']) render(v, W, H, `render_${v}.png`);
console.log('ROT_ORDER =', ORDERS, 'NEG_XY =', process.env.NEG_XY || '0');
