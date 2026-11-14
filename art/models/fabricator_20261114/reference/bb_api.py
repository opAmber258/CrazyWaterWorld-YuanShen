"""Use Blockbench's existing local Electron debug API. No plugin or settings changes."""
import json, urllib.request, websocket, itertools, sys
from pathlib import Path
class BlockbenchMCP:
 def __init__(self,port=9224):
  targets=json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/json/list'))
  pages=[t for t in targets if t.get('type')=='page' and '/app.asar/index.html' in t.get('url','')]
  if len(pages)!=1:raise RuntimeError('Need unique Blockbench page')
  self.ws=websocket.create_connection(pages[0]['webSocketDebuggerUrl'],suppress_origin=True,timeout=90);self.ids=itertools.count(1)
 def eval_js(self,code):
  i=next(self.ids);self.ws.send(json.dumps({'id':i,'method':'Runtime.evaluate','params':{'expression':code,'awaitPromise':True,'returnByValue':True}}))
  while True:
   r=json.loads(self.ws.recv())
   if r.get('id')==i:break
  if 'error' in r:raise RuntimeError(r['error'])
  result=r['result']
  if 'exceptionDetails' in result:raise RuntimeError(result['exceptionDetails'])
  return result.get('result',{}).get('value')
if __name__=='__main__':
 c=BlockbenchMCP();print(json.dumps(c.eval_js(Path(sys.argv[1]).read_text(encoding='utf8')),ensure_ascii=False))
