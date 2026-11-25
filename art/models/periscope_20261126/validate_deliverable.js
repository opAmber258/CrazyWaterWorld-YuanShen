// Generic offline deliverable validation for java_block art asset packages.
// Usage (from repo root): node art/models/<asset_dir>/reference/validate_deliverable.js <identifier>
// Results printed to stdout; details written to validation_output.json (run artifact, not committed).
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const DIR = path.dirname(__dirname);
const dirName = path.basename(DIR);
const ID = process.argv[2] || dirName.replace(/_\d{8}$/, '');
const results = [];
const check = (name, pass, detail) => { results.push({ name, pass, detail }); console.log((pass ? 'PASS' : 'FAIL') + ' | ' + name + (detail ? ' | ' + detail : '')); };

function decodePNG(buf) {
  let pos = 8, w = 0, h = 0, ct = -1;
  const idat = [];
  while (pos < buf.length) {
    const len = buf.readUInt32BE(pos), type = buf.toString('ascii', pos + 4, pos + 8);
    if (type === 'IHDR') { w = buf.readUInt32BE(pos + 8); h = buf.readUInt32BE(pos + 12); ct = buf[pos + 17]; }
    if (type === 'IDAT') idat.push(buf.subarray(pos + 8, pos + 8 + len));
    pos += 12 + len;
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
      const a = i % ch >= 1 ? cur[i - 1] : 0;
      const b = prev[i];
      const c = (y > 0 && i % ch >= 1) ? prev[i - ch] : 0;
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
  return { w, h, ct, px };
}

const D2R = Math.PI / 180;
function matMul(A, B) { const C = [[0,0,0],[0,0,0],[0,0,0]]; for (let i=0;i<3;i++) for (let j=0;j<3;j++) C[i][j]=A[i][0]*B[0][j]+A[i][1]*B[1][j]+A[i][2]*B[2][j]; return C; }
function rotMat(rx, ry, rz) { // Blockbench applies Euler in Z*Y*X order (verified against author previews)
  const cx=Math.cos(rx*D2R),sx=Math.sin(rx*D2R),cy=Math.cos(ry*D2R),sy=Math.sin(ry*D2R),cz=Math.cos(rz*D2R),sz=Math.sin(rz*D2R);
  const Rx=[[1,0,0],[0,cx,-sx],[0,sx,cx]], Ry=[[cy,0,sy],[0,1,0],[-sy,0,cy]], Rz=[[cz,-sz,0],[sz,cz,0],[0,0,1]];
  return matMul(Rz, matMul(Ry, Rx));
}
function worldBBox(bb) {
  let mn=[1e9,1e9,1e9], mx=[-1e9,-1e9,-1e9];
  for (const el of bb.elements) {
    const O = el.origin || [0,0,0];
    const M = rotMat(...(el.rotation || [0,0,0]));
    for (const X of [el.from[0], el.to[0]]) for (const Y of [el.from[1], el.to[1]]) for (const Z of [el.from[2], el.to[2]]) {
      const rel = [X-O[0], Y-O[1], Z-O[2]];
      const v = [
        M[0][0]*rel[0]+M[0][1]*rel[1]+M[0][2]*rel[2]+O[0],
        M[1][0]*rel[0]+M[1][1]*rel[1]+M[1][2]*rel[2]+O[1],
        M[2][0]*rel[0]+M[2][1]*rel[1]+M[2][2]*rel[2]+O[2]
      ];
      for (let i=0;i<3;i++){ mn[i]=Math.min(mn[i],v[i]); mx[i]=Math.max(mx[i],v[i]); }
    }
  }
  return { min: mn.map(v=>+v.toFixed(3)), max: mx.map(v=>+v.toFixed(3)), size: mx.map((v,i)=>+(v-mn[i]).toFixed(3)) };
}

check('主工程存在且命名合规', fs.existsSync(path.join(DIR, ID + '.bbmodel')), ID + '.bbmodel');
const bb = JSON.parse(fs.readFileSync(path.join(DIR, ID + '.bbmodel'), 'utf8'));
const meta = bb.meta || {};
check('java_block 格式', meta.model_format === 'java_block', meta.model_format);
check('bbmodel format_version 5.0（Blockbench 5.x）', String(meta.format_version) === '5.0', meta.format_version);
const faces = bb.elements.reduce((n, e) => n + Object.values(e.faces || {}).filter(f => f && f.uv).length, 0);
check('元素/面计数', true, bb.elements.length + ' cubes / ' + faces + ' faces / ' + (bb.groups || []).length + ' groups / 0 bones');
check('无隐藏几何', bb.elements.filter(e => e.visibility === false).length === 0, bb.elements.filter(e => e.visibility === false).length + ' hidden elements');
const bbox = worldBBox(bb);
check('世界包围盒', true, 'size ' + bbox.size.join(' × ') + '，min ' + bbox.min.join(',') + '，max ' + bbox.max.join(','));

const texFile = path.join(DIR, ID + '_tex.png');
const texBytes = fs.existsSync(texFile) ? fs.readFileSync(texFile) : null;
check('_tex.png 存在', !!texBytes, ID + '_tex.png');
const tex = bb.textures && bb.textures[0];
const emb = Buffer.from(tex.source.split(',')[1], 'base64');
check('_tex.png 与 bbmodel 内嵌贴图字节一致', texBytes ? texBytes.equals(emb) : false, '');
const t = decodePNG(emb);
const pow2 = (n) => (n & (n - 1)) === 0;
check('贴图尺寸为 2 的幂', pow2(t.w) && pow2(t.h), t.w + '×' + t.h + ' colortype ' + t.ct);
let trans = 0;
for (let i = 3; i < t.px.length; i += 4) if (t.px[i] === 0) trans++;
check('透明像素为真透明（RGBA8）', t.ct === 6 && trans > 0, trans + '/' + t.w * t.h);

for (const f of ['preview_front.png', 'preview_back.png', 'preview_side.png', 'preview_oblique.png']) {
  const p = path.join(DIR, f);
  if (!fs.existsSync(p)) { check('预览 ' + f, false, '缺失'); continue; }
  const d = fs.readFileSync(p);
  const pw = d.readUInt32BE(16), ph = d.readUInt32BE(20);
  check('预览 ' + f + ' 尺寸 1200×900', pw === 1200 && ph === 900, pw + '×' + ph);
}
check('README.md 存在', fs.existsSync(path.join(DIR, 'README.md')));
check('validation.json 存在', fs.existsSync(path.join(DIR, 'validation.json')));

const fails = results.filter(r => !r.pass).length;
console.log('---');
console.log(results.length + ' checks, ' + fails + ' failed');
fs.writeFileSync(path.join(DIR, 'validation_output.json'), JSON.stringify({ date: new Date().toISOString().slice(0, 10), identifier: ID, results }, null, 2));
process.exit(fails ? 1 : 0);
