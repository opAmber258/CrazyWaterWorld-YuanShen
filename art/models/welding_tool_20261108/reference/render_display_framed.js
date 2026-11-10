const fs = require('fs');
const http = require('http');
const path = require('path');

const port = process.env.CDP_PORT || 9231;
const outDir = process.argv[2];
const only = process.argv[3] || 'all';
const margin = Number(process.argv[4] || 1.06);
if (!outDir) throw new Error('Usage: node render_display_framed.js <outDir> [all|slot...] [margin]');
fs.mkdirSync(outDir, { recursive: true });

function targets() {
  return new Promise((resolve, reject) => {
    http.get({ host: '127.0.0.1', port, path: '/json/list' }, res => {
      let body = '';
      res.on('data', c => (body += c));
      res.on('end', () => resolve(JSON.parse(body)));
    }).on('error', reject);
  });
}

(async () => {
  const list = await targets();
  const page = list.find(t => t.type === 'page');
  if (!page) throw new Error('No page target');
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
  let id = 1;
  const pending = new Map();
  ws.onmessage = e => {
    const m = JSON.parse(e.data);
    const h = pending.get(m.id);
    if (h) { pending.delete(m.id); h(m); }
  };
  const send = (method, params) => new Promise((res, rej) => {
    const i = id++;
    pending.set(i, m => (m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result)));
    ws.send(JSON.stringify({ id: i, method, params }));
  });
  const evaluate = async expression => {
    const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, userGesture: true });
    if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails));
    return r.result.value;
  };
  const save = (name, url) => {
    if (!url || !url.startsWith('data:image/png')) throw new Error('no image for ' + name + ': ' + String(url).slice(0, 120));
    const file = path.join(outDir, name + '.png');
    fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'));
    console.log(file, fs.statSync(file).size);
  };

  console.log('setup', JSON.stringify(await evaluate(`(async () => {
    Modes.options.edit.select();
    await new Promise(r => setTimeout(r, 250));
    return { mode: Modes.selected ? Modes.selected.id : null, cubes: Cube.all.length };
  })()`)));

  console.log('enter', JSON.stringify(await evaluate(`(async () => {
    Modes.options.display.select();
    await new Promise(r => setTimeout(r, 350));
    return { mode: Modes.selected ? Modes.selected.id : null };
  })()`)));

  const slots = [
    ['thirdperson_righthand', 'loadThirdRight'],
    ['thirdperson_lefthand', 'loadThirdLeft'],
    ['firstperson_righthand', 'loadFirstRight'],
    ['firstperson_lefthand', 'loadFirstLeft'],
    ['gui', 'loadGUI'],
    ['ground', 'loadGround'],
    ['fixed', 'loadFixed'],
  ].filter(s => only === 'all' || only.split(',').includes(s[0]) || only.split(',').includes(s[1]));

  for (const [name, fn] of slots) {
    const expr = `(async () => {
      DisplayMode.${fn}();
      await new Promise(r => setTimeout(r, 450));
      const p = Preview.selected;
      const s = Canvas.scene;
      const oldBg = s.background;
      const grid = s.getObjectByName('grid_group');
      const oldGrid = grid ? grid.visible : null;
      const oldClear = p.renderer.getClearColor(new THREE.Color()).getHex();
      const oldAlpha = p.renderer.getClearAlpha();
      const oldRatio = p.renderer.getPixelRatio();
      const oldSize = [p.renderer.domElement.width, p.renderer.domElement.height];

      const gizmo = s.children.find(o => o.name !== 'display_area' && o.getObjectByName && o.getObjectByName('XY') && o.getObjectByName('XY').type === 'Mesh');
      const oldGizmo = gizmo ? gizmo.visible : null;
      if (gizmo) gizmo.visible = false;

      const sceneOnly = ${name.startsWith('firstperson') || name === 'gui' || name === 'fixed'};
      const hiddenTop = [];
      if (sceneOnly) {
        for (const o of s.children) {
          if (o.name === 'display_area' || o.name === 'lights' || o.name === 'outline_group' || o.name === 'grid_group') continue;
          hiddenTop.push([o, o.visible]);
          o.visible = false;
        }
      }

      const visible = o => {
        let n = o;
        while (n) { if (n.visible === false) return false; n = n.parent; }
        return true;
      };
      const box = new THREE.Box3();
      s.traverse(o => {
        if (o.isMesh && o !== gizmo && visible(o) && o.geometry) {
          o.updateWorldMatrix(true, false);
          if (!o.geometry.boundingBox) o.geometry.computeBoundingBox();
          const b = o.geometry.boundingBox.clone().applyMatrix4(o.matrixWorld);
          box.union(b);
        }
      });
      const sphere = box.getBoundingSphere(new THREE.Sphere());
      const fov = 45;
      const aspect = 4 / 3;
      const halfV = THREE.MathUtils.degToRad(fov / 2);
      const halfH = Math.atan(Math.tan(halfV) * aspect);
      const dist = sphere.radius / Math.sin(Math.min(halfV, halfH)) * ${margin};
      const dir = new THREE.Vector3(0.6524428084466527, -0.38553438680938584, 0.6524428084466526);
      const cam = new THREE.PerspectiveCamera(fov, aspect, 0.1, 5000);
      cam.position.copy(sphere.center).addScaledVector(dir, -dist);
      cam.lookAt(sphere.center);
      cam.updateProjectionMatrix();

      s.background = new THREE.Color('#202731');
      p.renderer.setClearColor(0x202731, 1);
      if (grid) grid.visible = false;
      p.renderer.setPixelRatio(1);
      p.renderer.setSize(1200, 900, false);
      p.renderer.render(s, cam);
      const url = p.renderer.domElement.toDataURL('image/png');

      p.renderer.setPixelRatio(oldRatio);
      p.renderer.setSize(oldSize[0] / oldRatio, oldSize[1] / oldRatio, false);
      s.background = oldBg;
      if (grid) grid.visible = oldGrid;
      if (gizmo) gizmo.visible = oldGizmo;
      for (const [o, v] of hiddenTop) o.visible = v;
      p.renderer.setClearColor(oldClear, oldAlpha);
      p.render();
      return { url: url, box: [box.min.toArray(), box.max.toArray()], dist: dist, radius: sphere.radius };
    })()`;
    const r = await evaluate(expr);
    if (r && r.box) console.log(name, 'box', JSON.stringify(r.box), 'dist', r.dist.toFixed(2));
    save('preview_display_' + name, r.url);
  }

  console.log('exit', JSON.stringify(await evaluate(`(async () => { Modes.options.edit.select(); await new Promise(r => setTimeout(r, 300)); return { mode: Modes.selected ? Modes.selected.id : null }; })()`)));
  ws.close();
})().catch(e => { console.error(e.stack || String(e)); process.exitCode = 1; });
