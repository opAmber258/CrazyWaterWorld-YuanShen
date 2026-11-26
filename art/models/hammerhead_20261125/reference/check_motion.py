"""Check the retained cruise tracks natively and sample head motion for both clips."""
import argparse
import json
from pathlib import Path

import numpy as np

from render_blockbench import Blockbench, OPEN

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
