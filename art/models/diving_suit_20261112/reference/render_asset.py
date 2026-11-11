"""Capture the opened hand-edited asset using Blockbench's native renderer.
Run from repository root: python art/models/diving_suit_20261112/reference/render_asset.py --url http://127.0.0.1:3015/bb-mcp
Open this asset's bbmodel in Blockbench first. No geometry or texture edits.
"""
import argparse,json,base64
from pathlib import Path
from bb_client import Client,structured
P=Path(__file__).resolve().parent;ROOT=P.parent
ap=argparse.ArgumentParser();ap.add_argument('--url',required=True);a=ap.parse_args()
with Client(a.url,timeout=50) as c:
    info=structured(c.tool('get_project_info',{}));uid=info['project']['uuid']
    if info['project']['name']!='diving_suit':raise RuntimeError('Open diving_suit.bbmodel first')
    exported=structured(c.tool('export_model',{'codec_id':'bedrock','max_content_length':2000000}))
    if json.loads(exported['content'])!=json.loads((ROOT/'diving_suit.geo.json').read_text(encoding='utf-8')):raise RuntimeError('Opened geometry differs from delivery; regenerate export first')
    c.tool('risky_eval',{'code':'(()=>{unselectAll();return true;})()'})
    view='diving_suit_native_render';c.tool('create_offscreen_view',{'id':view,'width':1200,'height':900,'antialias':True,'copy_view':'none'})
    report={'source':'Blockbench native offscreen renderer','size':[1200,900],'views':[]}
    try:
        for name,pos,projection in [('front',[0,17,-90],'orthographic'),('back',[0,17,90],'orthographic'),('side',[90,17,0],'orthographic'),('oblique',[44,32,-70],'perspective')]:
            if structured(c.tool('get_project_info',{}))['project']['uuid']!=uid:raise RuntimeError('Active project changed')
            args={'view':view,'position':pos,'target':[0,17,0],'projection':projection,'zoom':.52,'fov':32,'format':'png'}
            r=c.tool('set_camera_angle',args);img=next(v for v in r['content'] if v['type']=='image')
            (ROOT/f'preview_{name}.png').write_bytes(base64.b64decode(img['data']))
            report['views'].append({'name':name,'requested':args,'applied':structured(r).get('view')})
    finally:
        try:c.tool('delete_offscreen_view',{'view':view})
        except RuntimeError as exc:
            if 'onContextMenu is not defined' not in str(exc):raise
            check=structured(c.tool('risky_eval',{'code':'(()=>({remaining:Preview.all.some(p=>p.id==="mcp_offscreen_diving_suit_native_render")}))()'}))
            if check['remaining']:raise
            report['cleanup_warning']='Native controls.dispose error after disposal; Preview registry absence verified.'
    (P/'final_capture.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print('Captured four 1200x900 views')
