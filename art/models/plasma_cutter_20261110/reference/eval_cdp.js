const http = require('http');
const port = Number(process.env.CDP_PORT || 9237);
const code = process.argv.slice(2).join(' ');
if (!code) throw new Error('Usage: node eval_cdp.js "<expression>"');
(async () => {
  const targets = await new Promise((resolve, reject) => {
    http.get({ host: '127.0.0.1', port, path: '/json/list' }, res => {
      let body = '';
      res.on('data', c => body += c);
      res.on('end', () => resolve(JSON.parse(body)));
    }).on('error', reject);
  });
  const page = targets.find(t => t.type === 'page');
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
  ws.onmessage = e => {
    const m = JSON.parse(e.data);
    if (m.id !== 1) return;
    if (m.error) { console.error(JSON.stringify(m.error)); process.exitCode = 1; }
    else if (m.result.exceptionDetails) { console.error(JSON.stringify(m.result.exceptionDetails)); process.exitCode = 1; }
    else console.log(JSON.stringify(m.result.result.value, null, 2));
    ws.close();
  };
  ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression: code, awaitPromise: true, returnByValue: true, userGesture: true } }));
})().catch(e => { console.error(e.stack || String(e)); process.exitCode = 1; });
