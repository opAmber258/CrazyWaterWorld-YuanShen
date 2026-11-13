import json, urllib.request, base64, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent
def call(name, args=None, image_path=None):
    if not (ROOT/'mcp_session.json').exists():
        url='http://[::1]:3000/bb-mcp'
        req=urllib.request.Request(url,json.dumps({'jsonrpc':'2.0','id':1,'method':'initialize','params':{'protocolVersion':'2025-03-26','capabilities':{},'clientInfo':{'name':'cabinet-art-tools','version':'1'}}}).encode(),headers={'Content-Type':'application/json','Accept':'application/json, text/event-stream'})
        op=urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with op.open(req,timeout=15) as r:
            cfg={'url':url,'headers':{'Accept':'application/json, text/event-stream','Mcp-Session-Id':r.headers.get('Mcp-Session-Id','')}}
            r.read()
        (ROOT/'mcp_session.json').write_text(json.dumps(cfg),encoding='utf8')
    cfg=json.loads((ROOT/'mcp_session.json').read_text(encoding='utf8'))
    req=urllib.request.Request(cfg['url'],json.dumps({'jsonrpc':'2.0','id':13,'method':'tools/call','params':{'name':name,'arguments':args or {}}}).encode(),headers={'Content-Type':'application/json',**cfg['headers']})
    op=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with op.open(req,timeout=90) as r: data=json.load(r)
    result=data.get('result',data)
    if image_path:
        for c in result.get('content',[]):
            if c.get('type')=='image': Path(image_path).write_bytes(base64.b64decode(c['data']))
    return result
if __name__=='__main__':
    name=sys.argv[1]
    args=json.loads(Path(sys.argv[2]).read_text(encoding='utf8')) if len(sys.argv)>2 else {}
    result=call(name,args,sys.argv[3] if len(sys.argv)>3 else None)
    print(json.dumps({**result,'content':[c if c.get('type')!='image' else {'type':'image','saved':len(c.get('data',''))} for c in result.get('content',[])]},ensure_ascii=False))
