"""Extract the active embedded PNG from the authoritative current model.
Does not reconstruct geometry or rearrange UV. Native export: export_current.js.
"""
from pathlib import Path
import json,base64,hashlib

root=Path(__file__).resolve().parents[1]
m=json.loads((root/'syringe_gun.bbmodel').read_text(encoding='utf-8-sig'))
assert len(m['textures'])==1,'Inspect texture usage before changing a multi-texture model'
source=m['textures'][0]['source'];assert source.startswith('data:image/png;base64,')
png=base64.b64decode(source.split(',')[1])
(root/'syringe_gun_tex.png').write_bytes(png)
print(json.dumps({'geometry_changed':False,'uv_changed':False,'png_sha256':hashlib.sha256(png).hexdigest()}))
