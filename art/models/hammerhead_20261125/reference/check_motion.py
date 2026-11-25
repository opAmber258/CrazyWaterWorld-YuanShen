"""Check the retained cruise tracks natively, sample head motion and render both clips."""
import argparse
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image

from render_blockbench import Blockbench, OPEN, RENDER

CLIPS={"swim_cruise":3.0,"swim_fast":1.5}
SELECT = """
function selectClip(name) {
    const clip=Animation.all.find(a=>a.name===name);
    if (!clip || clip.loop!=='loop') throw new Error('Missing loop '+name);
    Modes.options.animate.select();
    clip.select();
    for (const a of Animation.all) a.playing=a===clip;
    return clip;
}
function seek(t) {
    Timeline.setTime(t);
    Animator.preview();
    Canvas.scene.updateMatrixWorld(true);
}
"""
SAMPLE = """
(() => {
    FUNCTIONS
    const model={};
    for (const key of KEYS) {
        const clip=selectClip('animation.hammerhead.'+key);
        const result=[];
        for (let n=0;n<=Math.round(40*clip.length);n++) {
            seek(n/40);
            result.push(Group.all.map(g=>[g.name,g.mesh.matrixWorld.elements.slice()]));
        }
        model[key]=result;
    }
    return model;
})()
""".replace("FUNCTIONS",SELECT).replace("KEYS",json.dumps(list(CLIPS)))

CROP={"direction":[-.67,.68,1.52],"target":[0,34,55],"width":95}
FRAME_RENDER=RENDER.replace("const aspect = 4/3;", "const aspect = 720/540;")
FRAME_RENDER=FRAME_RENDER.replace("renderer.setSize(1200,900,false);","renderer.setSize(720,540,false);")
FRAME_RENDER=FRAME_RENDER.replace("PRESET",json.dumps(CROP))

def load(client,path):
    model=path.read_text(encoding="utf-8-sig")
    return client.evaluate(OPEN.replace("INPUT",json.dumps(str(path.resolve()))).replace("MODEL",model))

def assert_poses(before,after):
    summary={}
    for clip in CLIPS:
        assert len(before[clip])==len(after[clip])==round(CLIPS[clip]*40)+1
        worst=0
        for source,target in zip(before[clip],after[clip],strict=True):
            a,b=dict(source),dict(target)
            assert a.keys()==b.keys()
            worst=max(worst,max(float(np.max(np.abs(np.asarray(a[n])-b[n]))) for n in a))
        assert worst<.001
        summary[clip]={"samples":len(before[clip]),"max_matrix_delta":worst}
    return summary

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("source",type=Path)
    p.add_argument("output",type=Path)
    p.add_argument("--port",type=int,default=9426)
    args=p.parse_args()
    bb=Blockbench(args.port)
    try:
        load(bb,args.source)
        source_export=bb.evaluate("AnimationCodec.codecs.bedrock.compileFile(Animation.all)")
        source_samples=bb.evaluate(SAMPLE)
        geo=load(bb,args.output/"hammerhead.bbmodel")
        assert geo["animationCount"]==2
        target_export=bb.evaluate("AnimationCodec.codecs.bedrock.compileFile(Animation.all)")
        assert source_export==target_export
        target_samples=bb.evaluate(SAMPLE)
        unchanged=assert_poses(source_samples,target_samples)
        (args.output/"hammerhead.animation.json").write_text(json.dumps(target_export,indent=2)+"\n",encoding="utf-8")
        from base64 import b64decode
        for clip,duration in CLIPS.items():
            key=json.dumps("animation.hammerhead."+clip)
            bb.evaluate("(() => {"+SELECT+"selectClip("+key+");})()")
            frames=[]
            for frame in range(round(duration*20)):
                uri=bb.evaluate("(() => {"+SELECT+f"seek({frame/20});return ("+FRAME_RENDER+").image;})()")
                frames.append(Image.open(io.BytesIO(b64decode(uri.split(",",1)[1]))).convert("RGB"))
            frames[0].save(args.output/f"preview_{clip}_head.webp",save_all=True,append_images=frames[1:],
                           duration=50,loop=0,quality=88,method=4)
        bb.evaluate("""(async () => {
            setupProject(Formats.bedrock);
            await new Promise(resolve => setTimeout(resolve, 120));
            Codecs.bedrock.parse(GEO,'models/entity/hammerhead.geo.json',{switch_to_existing_tab:false});
            Project.name='hammerhead';
            AnimationCodec.codecs.bedrock.loadFile({path:'hammerhead.animation.json',json:MOTION});
        })()""".replace("GEO",geo["geo"]).replace("MOTION",json.dumps(target_export)))
        roundtrip=assert_poses(target_samples,bb.evaluate(SAMPLE))
        proof={"blockbench":geo["version"],"animation_export_exact":True,"unchanged_clip_poses":unchanged,
               "geometry_and_animation_reimport":roundtrip}
        (args.output/"motion_check.json").write_text(json.dumps(proof,indent=2)+"\n",encoding="utf-8")
        print(json.dumps(proof))
    finally:
        bb.ws.close()

if __name__=="__main__":
    main()
