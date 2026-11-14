"""Run a task script in the local Blockbench debugging instance (port 9225).
Requires websocket-client; no credentials or MCP configuration involved.
Usage: python reference/bb_native.py reference/script.js
"""
import json, sys, urllib.request
from pathlib import Path
import websocket

def evaluate(code):
    tabs=json.load(urllib.request.urlopen('http://127.0.0.1:9225/json/list'))
    tab=next(t for t in tabs if t['type']=='page' and 'app.asar/index.html' in t['url'])
    ws=websocket.create_connection(tab['webSocketDebuggerUrl'], suppress_origin=True, timeout=90)
    try:
        ws.send(json.dumps({'id':1,'method':'Runtime.evaluate','params':{'expression':code,'awaitPromise':True,'returnByValue':True}}))
        while True:
            r=json.loads(ws.recv())
            if r.get('id')==1:
                if 'exceptionDetails' in r.get('result',{}):
                    raise RuntimeError(json.dumps(r['result']['exceptionDetails'],ensure_ascii=False))
                return r.get('result',{}).get('result',{}).get('value')
    finally: ws.close()

if __name__=='__main__':
    if len(sys.argv)>1:
        script=Path(sys.argv[1]).resolve()
        code='globalThis.ASSET_ROOT='+json.dumps(str(script.parent.parent).replace('\\','/'))+';\n'+script.read_text(encoding='utf-8-sig')
    else:code='({version:Blockbench.version,projects:ModelProject.all.map(p=>({name:p.name,uuid:p.uuid,saved:p.saved})),formats:Object.keys(Formats)})'
    print(json.dumps(evaluate(code),ensure_ascii=True,indent=2))
