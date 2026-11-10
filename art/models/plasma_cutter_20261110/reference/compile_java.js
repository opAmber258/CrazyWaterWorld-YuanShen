const fs = require('fs');
const http = require('http');
const port = Number(process.env.CDP_PORT || 9237);
const out = process.argv[2];
if (!out) throw new Error('Usage: node compile_java.js <out.json>');
(async () => {
  const targets = await new Promise((resolve, reject) => {
    http.get({ host: '127.0.0.1', port, path: '/json/list' }, res => {
      let body = '';
      res.on('data', c => body += c);
      res.on('end', () => resolve(JSON.parse(body)));
    }).on('error', reject);
  });
  const page = targets.find(t => t.type === 'page');
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
  const r = await send('Runtime.evaluate', {
    expression: "JSON.parse(Codecs.java_block.compile({}))",
    awaitPromise: true,
    returnByValue: true,
    userGesture: true,
  });
  if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails));
  fs.writeFileSync(out, JSON.stringify(r.result.value, null, 2) + '\n');
  console.log(out, fs.statSync(out).size, r.result.value.elements.length, 'elements');
  ws.close();
})().catch(e => { console.error(e.stack || String(e)); process.exitCode = 1; });
