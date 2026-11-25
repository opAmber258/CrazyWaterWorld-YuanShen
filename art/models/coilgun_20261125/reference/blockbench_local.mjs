// Run one script in a local Blockbench instance started with a DevTools port.
import { mkdir, readFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';

const portFile = join(process.env.TEMP, 'coilgun-scaled-aged-r9-20261125-qa', 'DevToolsActivePort');
const port = (await readFile(portFile, 'utf8')).split(/\r?\n/)[0];
if (!/^\d+$/.test(port)) throw new Error('Invalid Blockbench DevTools port');
const targets = await (await fetch('http://127.0.0.1:' + port + '/json/list')).json();
const target = targets.find(entry => entry.type === 'page' && entry.url.endsWith('/Blockbench/resources/app.asar/index.html'));
if (!target) throw new Error('Blockbench renderer was not found');
const scriptPath = resolve(process.argv[2]);
const directory = dirname(dirname(scriptPath));
await mkdir(join(directory, 'reference', 'frames'), { recursive: true });
const root = JSON.stringify(directory);
const source = await readFile(scriptPath, 'utf8');
const expression = '{ const COILGUN_ROOT = ' + root + ';\n' + source + '\n}';
const socket = new WebSocket(target.webSocketDebuggerUrl);
try {
    await new Promise((resolve, reject) => {
        socket.addEventListener('open', resolve, { once: true });
        socket.addEventListener('error', reject, { once: true });
    });
    const response = await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('Blockbench renderer did not reply')), 100_000);
        socket.addEventListener('message', ev => {
            const message = JSON.parse(ev.data);
            if (message.id === 1) {
                clearTimeout(timer);
                resolve(message);
            }
        });
        socket.send(JSON.stringify({
            id: 1, method: 'Runtime.evaluate',
            params: { expression, awaitPromise: true, returnByValue: true, userGesture: true },
        }));
    });
    if (response.error || response.result.exceptionDetails) {
        throw new Error(JSON.stringify(response.error || response.result.exceptionDetails));
    }
    console.log(JSON.stringify(response.result.result.value, null, 2));
} finally {
    socket.close();
}






















