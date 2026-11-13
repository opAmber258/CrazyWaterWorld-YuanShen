"""Render native Blockbench viewports through the locally connected MCP plugin."""
from mcp_client import call
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
view='cabinet_delivery'
r=call('create_offscreen_view',{'id':view,'width':1200,'height':900,'antialias':True,'copy_view':'none'})
print(str(r)[:600])
views={
 'front':dict(position=[0,20,100],projection='orthographic',target=[0,20,0],zoom=.42),
 'back':dict(position=[0,20,-100],projection='orthographic',target=[0,20,0],zoom=.42),
 'side':dict(position=[100,20,0],projection='orthographic',target=[0,20,0],zoom=.42),
 'oblique':dict(position=[64,52,94],projection='perspective',target=[0,20,0],fov=32),
}
records={}
for name,args in views.items():
 r=call('set_camera_angle',{'view':view,**args},ROOT/f'preview_{name}.png')
 records[name]={k:v for k,v in r.items() if k!='content'}
 print(name,[c.get('text','')[:240] for c in r.get('content',[]) if c['type']=='text'])
(ROOT/'reference/preview_capture.json').write_text(json.dumps(records,indent=2),encoding='utf8')
