const fs = require('fs');
const http = require('http');

const port = process.env.CDP_PORT || 9231;
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
  const list = await targets();
  const page = list.find(t => t.type === 'page');
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
  const model = JSON.parse(txt);
  const expr = `(async () => {
    await loadModelFile({path: 'welding_tool.bbmodel', content: ${JSON.stringify(txt)}});
    ${model.display ? `DisplayMode.loadJSON(${JSON.stringify(model.display)});` : ''}
    await new Promise(r => setTimeout(r, 300));
    return {project: Project ? Project.name : null, elements: Cube.all.length, textures: Texture.all.length,
            groups: Group.all.length, textureSize: Texture.all[0] ? [Texture.all[0].width, Texture.all[0].height] : null,
            display: Project.display_settings};
  })()`;
  const r = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true, userGesture: true });
  if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails));
  console.log(JSON.stringify(r.result.value));
  ws.close();
})().catch(e => { console.error(e.stack || String(e)); process.exitCode = 1; });
