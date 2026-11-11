"""Small local Blockbench MCP client. No dependencies beyond stdlib.
python bb_rpc.py --code script.js (runs risky_eval in the user's existing BB MCP).
Port is configurable; the production instance uses 3015 to avoid other sessions.
"""
import json, urllib.request, sys, argparse
from pathlib import Path

def rpc(method,params=None):
    global seq
    seq+=1
    body={'jsonrpc':'2.0','id':seq,'method':method}
    if params is not None:body['params']=params
    headers={'Content-Type':'application/json','Accept':'application/json, text/event-stream'}
    if session:headers['Mcp-Session-Id']=session
    req=urllib.request.Request(url,data=json.dumps(body).encode(),headers=headers)
    with opener.open(req,timeout=45) as r:
        data=r.read().decode()
        if data.startswith('event:') or data.startswith('data:'):
            data=next(line[6:] for line in data.splitlines() if line.startswith('data: '))
        return json.loads(data),r.headers.get('Mcp-Session-Id')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=3015);p.add_argument('--code');p.add_argument('--eval');p.add_argument('--list',action='store_true');a=p.parse_args()
    url=f'http://127.0.0.1:{a.port}/bb-mcp';opener=urllib.request.build_opener(urllib.request.ProxyHandler({}));seq=0;session=None
    init,session=rpc('initialize',{'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'codex-nuclear-reactor','version':'1'}})
    if a.list:
        d,_=rpc('tools/list');print(json.dumps([t for t in d['result']['tools'] if t['name'] in ['risky_eval','get_project_info','create_project']],ensure_ascii=True));sys.exit()
    code=Path(a.code).read_text(encoding='utf-8') if a.code else a.eval
    if a.code:
        code='\n'.join(line for line in code.splitlines() if not line.strip().startswith('//'))
        asset_dir=Path(a.code).resolve().parent.parent.as_posix()
        code='(async()=>{ globalThis.REACTOR_ASSET_DIR='+json.dumps(asset_dir)+';\n'+code+'\n})()'
    d,_=rpc('tools/call',{'name':'risky_eval','arguments':{'code':code}})
    print(json.dumps(d,ensure_ascii=True))
    if d.get('error') or d.get('result',{}).get('isError'):sys.exit(1)
