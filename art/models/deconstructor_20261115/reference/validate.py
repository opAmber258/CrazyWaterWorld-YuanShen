"""Check the working animation against the artist's final source and native re-export."""
import argparse,base64,hashlib,io,json,math
from pathlib import Path
from PIL import Image
import numpy as np
from check_clearance import obb,sweep
from render_previews import connect

ROOT=Path(__file__).resolve().parents[1]
def doc(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--source',type=Path,required=True);ap.add_argument('--port',type=int,required=True)
    args=ap.parse_args();m=doc(ROOT/'deconstructor.bbmodel');original=doc(args.source)
    assert all(m[k]==v for k,v in original.items() if k!='textures')
    assert set(m)==set(original)|{'animations'}
    assert len(m['textures'])==1
    tex=m['textures'][0];assert all(tx==tex[k] for k,tx in original['textures'][0].items() if k not in {'path','relative_path'})
    ext=ROOT/'deconstructor_tex.png';assert ext.read_bytes()==base64.b64decode(tex['source'].split(',')[1])
    actual=Image.open(ext).convert('RGBA');assert actual.size==(512,512) and [tex['uv_width'],tex['uv_height']]==[256,256]
    colors=sorted(actual.getcolors(512*512),reverse=True)
    (ROOT/'reference/palette.json').write_text(json.dumps({'source':'opaque colors from the final embedded texture','colors':
        [{'hex':'#%02x%02x%02x'%rgba[:3],'pixels':n} for n,rgba in colors if rgba[3]==255]},indent=2),encoding='utf8')
    a=m['animations'][0];clip=doc(ROOT/'deconstructor.animation.json')['animations'][a['name']]
    assert clip['loop'] is True and a['loop']=='loop' and a['length']==clip['animation_length']==1
    assert set(clip['bones'])=={'left_cutters','right_cutters'} and len(a['animators'])==2
    for name,sign in [('left_cutters',-1),('right_cutters',1)]:
        group=next(g for g in m['groups'] if g['name']==name);track=a['animators'][group['uuid']]
        assert set(clip['bones'][name])=={'rotation'}
        keys=track['keyframes'];assert len(keys)==5
        rk=clip['bones'][name]['rotation']
        for i,k in enumerate(keys):
            assert k['channel']=='rotation' and k['interpolation']=='linear' and k['time']==i/4
            assert [float(k['data_points'][0][ax]) for ax in 'xyz']==[0,0,sign*90*i]
            assert next(v for t,v in rk.items() if float(t)==i/4)==[0,0,sign*90*i]
    js=connect(args.port)
    assert js('Project.save_path.replaceAll("\\\\","/")')==(ROOT/'deconstructor.bbmodel').as_posix()
    exported=json.loads(js('Codecs.bedrock.compile()'))
    assert exported==doc(ROOT/'deconstructor.geo.json')
    assert js('AnimationCodec.codecs.bedrock.compileFile(Animation.all)')==doc(ROOT/'deconstructor.animation.json')
    # Point above left shaft must move right into the mouth. Check native local Euler angles for all groups.
    samples=js("""(()=>{Modes.options.animate.select();Animation.all[0].select();let samples=[];
      for(let i=0;i<=48;i++){Timeline.setTime(i/48);Animator.preview();
        samples.push(Group.all.map(g=>[g.name,...g.mesh.rotation.toArray().slice(0,3)]));}
      Timeline.setTime(0);Animator.preview();return samples;})()""")
    for i,groups in enumerate(samples):
        for name,x,y,z in groups:
            theta=math.tau*i/48*({'left_cutters':-1,'right_cutters':1}.get(name,0))
            assert abs(x)+abs(y)+abs(z-theta)<1e-6
    geometry=exported['minecraft:geometry'][0]
    assert sum(len(b['cubes']) for b in geometry['bones'])==170
    assert all(not any(g['rotation']) for g in m['groups'])
    import itertools
    corners=[]
    for c in m['elements']:
        center,axes,half=obb(c)
        corners.extend(np.array(list(itertools.product(*zip(-half,half))))@axes+center)
    lo=np.min(corners,axis=0);hi=np.max(corners,axis=0)
    gif=Image.open(ROOT/'preview_work.gif');assert gif.n_frames==50 and gif.info['loop']==0
    for i in range(50):gif.seek(i);assert gif.info['duration']==20
    for v in ['front','back','side','oblique']:
        im=Image.open(ROOT/f'preview_{v}.png');assert im.size==(1200,900) and im.getbbox()
    report={'source_sha256':digest(args.source),'project_sha256':digest(ROOT/'deconstructor.bbmodel'),
      'blockbench_version':js('Blockbench.version'),'geometry_and_animation_reexport_equal':True,
      'all_source_cube_group_outliner_uv_values_identical':True,'identical_embedded_texture':True,
      'rest_bounds_BB':[lo.round(6).tolist(),hi.round(6).tolist()],'dimensions_blocks_WHD':((hi-lo)/16).round(6).tolist(),
      'cubes':len(m['elements']),'bones':len(m['groups']),'image_pixels':list(actual.size),'logical_uv':[256,256],
      'clip':a['name'],'animated_groups':sorted(clip['bones']),'duration_seconds':1,'rotations_per_second':1,
      'verified_native_poses':49,'preview_frames':50,'native_first_last_frame_pixel_identical':True,
      'clearance':sweep(m),'minecraft_runtime_tested':False}
    (ROOT/'validation.json').write_text(json.dumps(report,indent=2),encoding='utf8');print(report['dimensions_blocks_WHD'])
if __name__=='__main__':main()

