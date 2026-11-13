from pathlib import Path
import json
from mcp_client import call
ROOT=Path(__file__).resolve().parents[1]
for codec,ext in [('project','bbmodel'),('bedrock','geo.json')]:
 r=call('export_model',{'codec_id':codec,'max_content_length':1000000,'result_format':'text'})
 (ROOT/'reference'/f'native_export_{codec}_response.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf8')
 d=r.get('structuredContent')
 if not d:
  texts=[c['text'] for c in r.get('content',[]) if c.get('type')=='text']
  for t in texts:
   try:d=json.loads(t);break
   except ValueError:pass
 if not d or not isinstance(d,dict):raise RuntimeError(str(r)[:500])
 print(codec,list(d.keys()))
 content=d.get('content') or d.get('metadata',{}).get('content')
 if content is None:raise RuntimeError(str(d)[:1000])
 if not isinstance(content,str):content=json.dumps(content,ensure_ascii=False,indent=2)
 (ROOT/f'large_steel_cabinet.{ext}').write_text(content,encoding='utf8')
 print('saved',ext,len(content))
