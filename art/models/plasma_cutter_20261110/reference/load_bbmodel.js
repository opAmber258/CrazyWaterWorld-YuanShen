const fs = require('fs');
const http = require('http');
const path = require('path');

const port = Number(process.env.CDP_PORT || 9237);
const file = process.argv[2];
if (!file) throw new Error('Usage: node load_bbmodel.js <file.bbmodel>');

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
  const txt = fs.readFileSync(file, 'utf8');
  const model = JSON.parse(txt);
  const fileName = path.basename(file);
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
  const expr = `(async () => {
    await loadModelFile({path: ${JSON.stringify(fileName)}, content: ${JSON.stringify(txt)}});
    DisplayMode.loadJSON(${JSON.stringify(model.display)});
    await new Promise(r => setTimeout(r, 500));
    return {
      project: Project ? Project.name : null,
      format: Format ? Format.id : null,
      elements: Cube.all.length,
      textures: Texture.all.map(t => ({name:t.name, width:t.width, height:t.height})),
      groups: Group.all.map(g => g.name),
      display: Project.display_settings,
      bounds: [...Cube.all].reduce((a, c) => [Math.min(a[0], c.from[0]), Math.min(a[1], c.from[1]), Math.min(a[2], c.from[2]), Math.max(a[3], c.to[0]), Math.max(a[4], c.to[1]), Math.max(a[5], c.to[2])], [Infinity,Infinity,Infinity,-Infinity,-Infinity,-Infinity])
    };
  })()`;
  const r = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true, userGesture: true });
  if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails));
  console.log(JSON.stringify(r.result.value, null, 2));
  ws.close();
})().catch(e => { console.error(e.stack || String(e)); process.exitCode = 1; });
