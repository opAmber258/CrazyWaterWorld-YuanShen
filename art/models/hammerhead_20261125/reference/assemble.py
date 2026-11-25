"""Apply the localized hammer cap patch to the two-animation Blockbench project."""
import argparse
import base64
import io
import json
from pathlib import Path

from PIL import Image

from parts.head.patch import apply

def build(source,output):
    model=json.loads(source.read_text(encoding="utf-8-sig"))
    texture,=model["textures"]
    atlas=Image.open(io.BytesIO(base64.b64decode(texture["source"].split(",",1)[1]))).convert("RGBA")
    assert atlas.size==(256,256)
    apply(model,atlas)
    out=io.BytesIO()
    atlas.save(out,format="PNG",optimize=True)
    png=out.getvalue()
    texture["source"]="data:image/png;base64,"+base64.b64encode(png).decode("ascii")
    output.mkdir(parents=True,exist_ok=True)
    (output/"hammerhead_tex.png").write_bytes(png)
    (output/"hammerhead.bbmodel").write_text(json.dumps(model,ensure_ascii=False,separators=(",",":")),encoding="utf-8")

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("source",type=Path)
    p.add_argument("--output",type=Path,required=True)
    args=p.parse_args()
    build(args.source,args.output)
