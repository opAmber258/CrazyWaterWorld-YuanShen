"""Prove manual checkpoint geometry, UV, groups and PNG survive native saving."""
import json,base64,hashlib
from pathlib import Path
OUT=Path(__file__).resolve().parent.parent;src=OUT/'reference/manual_checkpoint_20261114'
before=json.loads((src/'fabricator.bbmodel').read_text(encoding='utf8'));after=json.loads((OUT/'fabricator.bbmodel').read_text(encoding='utf8'))
checks={}
for field in ['name','from','to','origin','faces','uuid','type']:
 checks['elements_'+field]=[e.get(field) for e in before['elements']]==[e.get(field) for e in after['elements']]
checks['elements_rotation']=[e.get('rotation',[0,0,0]) for e in before['elements']]==[e.get('rotation',[0,0,0]) for e in after['elements']]
checks['group_semantics']=[{k:g.get(k,[0,0,0] if k in ['origin','rotation'] else None) for k in ['uuid','name','origin','rotation']} for g in before['groups']]==[{k:g.get(k,[0,0,0] if k in ['origin','rotation'] else None) for k in ['uuid','name','origin','rotation']} for g in after['groups']]
def semantic_tree(node):
 if isinstance(node,str):return node
 return {'uuid':node['uuid'],'children':[semantic_tree(ch) for ch in node['children']]}
checks['hierarchy']=[semantic_tree(n) for n in before['outliner']]==[semantic_tree(n) for n in after['outliner']]
checks['logical_resolution']=before['resolution']==after['resolution']
checks['embedded_png_bytes']=before['textures'][0]['source']==after['textures'][0]['source']
checks['external_png_bytes']=(src/'fabricator_tex.png').read_bytes()==(OUT/'fabricator_tex.png').read_bytes()
result={'source':'reference/manual_checkpoint_20261114/','checks':checks,'all_preserved':all(checks.values()),'manual_project_sha256':hashlib.sha256((src/'fabricator.bbmodel').read_bytes()).hexdigest(),'texture_sha256':hashlib.sha256((OUT/'fabricator_tex.png').read_bytes()).hexdigest()}
(OUT/'reference/human_preservation.json').write_text(json.dumps(result,indent=2),encoding='utf8');print(json.dumps(result))
assert result['all_preserved']
