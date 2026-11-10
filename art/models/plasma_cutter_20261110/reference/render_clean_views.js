const fs = require('fs');
const http = require('http');
const path = require('path');

const port = process.env.CDP_PORT || 9231;
const outDir = process.argv[2];
const kind = process.argv[3] || 'clean';
if (!outDir) throw new Error('Usage: node render_clean_views.js <outDir> [clean|display]');
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

  const common = `
    const p = Preview.selected;
    const s = Canvas.scene;
    const modelGroup = s.children.find(o => o.children && o.children.some(c => c.type === 'cube'));
    const box = new THREE.Box3().setFromObject(modelGroup);
    const center = box.getCenter(new THREE.Vector3());
    const oldBg = s.background;
    const grid = s.getObjectByName('grid_group');
    const oldGrid = grid ? grid.visible : null;
    const oldClear = p.renderer.getClearColor(new THREE.Color()).getHex();
    const oldAlpha = p.renderer.getClearAlpha();
    const oldRatio = p.renderer.getPixelRatio();
    const oldSize = [p.renderer.domElement.width, p.renderer.domElement.height];
    s.background = new THREE.Color('#202731');
    p.renderer.setClearColor(0x202731, 1);
    if (grid) grid.visible = false;
    p.renderer.setPixelRatio(1);
    p.renderer.setSize(1200, 900, false);
  `;
  const restore = `
    p.renderer.setPixelRatio(oldRatio);
    p.renderer.setSize(oldSize[0] / oldRatio, oldSize[1] / oldRatio, false);
    s.background = oldBg;
    if (grid) grid.visible = oldGrid;
    p.renderer.setClearColor(oldClear, oldAlpha);
    p.render();
  `;

  const setup = `(async () => {
    Modes.options.edit.select();
    await new Promise(r => setTimeout(r, 250));
    return { mode: Modes.selected ? Modes.selected.id : null, cubes: Cube.all.length };
  })()`;
  console.log('setup', JSON.stringify(await evaluate(setup)));

  const ortho = (name, axis) => {
    const pos = {
      front: 'center.x, center.y, center.z + 60',
      back: 'center.x, center.y, center.z - 60',
      side: 'center.x + 60, center.y, center.z',
      top: 'center.x, center.y + 60, center.z',
    }[axis];
    const up = axis === 'top' ? '0, 0, -1' : '0, 1, 0';
    return `(async () => {
      ${common}
      const cam = new THREE.OrthographicCamera(-10, 10, 7.5, -7.5, 0.1, 500);
      cam.position.set(${pos});
      cam.up.set(${up});
      cam.lookAt(center);
      cam.updateProjectionMatrix();
      p.renderer.render(s, cam);
      const url = p.renderer.domElement.toDataURL('image/png');
      ${restore}
      return url;
    })()`;
  };

  if (kind === 'clean') {
    save('preview_front', await evaluate(ortho('preview_front', 'front')));
    save('preview_back', await evaluate(ortho('preview_back', 'back')));
    save('preview_side', await evaluate(ortho('preview_side', 'side')));
    save('preview_top', await evaluate(ortho('preview_top', 'top')));
    const oblique = `(async () => {
      ${common}
      const cam = new THREE.PerspectiveCamera(38, 4 / 3, 0.1, 500);
      const dir = new THREE.Vector3(16.98, 10.84, 21.875).normalize();
      cam.position.copy(center).addScaledVector(dir, 33.0);
      cam.lookAt(center);
      cam.updateProjectionMatrix();
      p.renderer.render(s, cam);
      const url = p.renderer.domElement.toDataURL('image/png');
      ${restore}
      return url;
    })()`;
    save('preview_oblique', await evaluate(oblique));
  } else if (kind === 'display') {
    const slots = [
      ['thirdperson_righthand', 'loadThirdRight'],
      ['thirdperson_lefthand', 'loadThirdLeft'],
      ['firstperson_righthand', 'loadFirstRight'],
      ['firstperson_lefthand', 'loadFirstLeft'],
      ['gui', 'loadGUI'],
      ['ground', 'loadGround'],
      ['fixed', 'loadFixed'],
    ];
    const enter = `(async () => {
      Modes.options.display.select();
      await new Promise(r => setTimeout(r, 350));
      return { mode: Modes.selected ? Modes.selected.id : null };
    })()`;
    console.log('enter', JSON.stringify(await evaluate(enter)));
    for (const [name, fn] of slots) {
      const expr = `(async () => {
        DisplayMode.${fn}();
        await new Promise(r => setTimeout(r, 350));
        const p = Preview.selected;
        const s = Canvas.scene;
        const oldBg = s.background;
        const grid = s.getObjectByName('grid_group');
        const oldGrid = grid ? grid.visible : null;
        const oldClear = p.renderer.getClearColor(new THREE.Color()).getHex();
        const oldAlpha = p.renderer.getClearAlpha();
        const oldRatio = p.renderer.getPixelRatio();
        const oldSize = [p.renderer.domElement.width, p.renderer.domElement.height];
        const oldAspect = p.camera.aspect;
        s.background = new THREE.Color('#202731');
        p.renderer.setClearColor(0x202731, 1);
        if (grid) grid.visible = false;
        p.renderer.setPixelRatio(1);
        p.renderer.setSize(1200, 900, false);
        p.camera.aspect = 4 / 3;
        p.camera.updateProjectionMatrix();
        p.renderer.render(s, p.camera);
        const url = p.renderer.domElement.toDataURL('image/png');
        p.renderer.setPixelRatio(oldRatio);
        p.renderer.setSize(oldSize[0] / oldRatio, oldSize[1] / oldRatio, false);
        p.camera.aspect = oldAspect;
        p.camera.updateProjectionMatrix();
        s.background = oldBg;
        if (grid) grid.visible = oldGrid;
        p.renderer.setClearColor(oldClear, oldAlpha);
        p.render();
        return url;
      })()`;
      save('preview_display_' + name, await evaluate(expr));
    }
    const exit = `(async () => { Modes.options.edit.select(); await new Promise(r => setTimeout(r, 300)); return { mode: Modes.selected ? Modes.selected.id : null }; })()`;
    console.log('exit', JSON.stringify(await evaluate(exit)));
  } else {
    throw new Error('unknown kind: ' + kind);
  }
  ws.close();
})().catch(e => { console.error(e.stack || String(e)); process.exitCode = 1; });
