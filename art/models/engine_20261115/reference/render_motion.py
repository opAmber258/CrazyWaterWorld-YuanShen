"""Render motion with Blockbench itself, then encode GIF previews with Pillow."""
import json
from pathlib import Path
from PIL import Image
from bb_mcp_client import BlockbenchMCP
OUT=Path(__file__).resolve().parent.parent
c=BlockbenchMCP()
code=r'''(()=>{
const fs=require('fs'),dir=DIR,frameDir=dir+'/reference/animation_frames';fs.mkdirSync(frameDir,{recursive:true});
Modes.options.animate.select();const p=Preview.selected,scene=Canvas.scene;
const roots=new Set();for(const c of Cube.all){let root=c.mesh;while(root&&root.parent&&root.parent!==scene)root=root.parent;if(root)roots.add(root);}
const auxiliary=scene.children.filter(o=>o.name!=='lights'&&!roots.has(o));const oldVisibility=auxiliary.map(o=>o.visible);
const bg=scene.background,clear=p.renderer.getClearColor(new THREE.Color()).getHex(),alpha=p.renderer.getClearAlpha(),ratio=p.renderer.getPixelRatio(),size=[p.renderer.domElement.width,p.renderer.domElement.height];
const axisNames=new Set(['X','Y','Z','NX','NY','NZ','XY','YZ','XZ','XYZE','E']);const axes=[];scene.traverse(o=>{if(axisNames.has(o.name)||o.name.startsWith('axis_line')||o.type==='GridHelper')axes.push(o);});const oldAxes=axes.map(o=>o.visible);
const jobs=[
 {name:'running',clip:'animation.engine.running',length:2.4,frames:60,width:960,height:720,center:[0,21,0],direction:[-1,.52,1.25],distance:129,fov:35},
 {name:'rotor',clip:'animation.engine.rotor_spin',length:.8,frames:20,width:600,height:600,center:[-39.6,21,0],direction:[-1,.15,.3],distance:62,fov:36},
 {name:'pistons',clip:'animation.engine.pistons',length:1.2,frames:30,width:600,height:600,center:[24.2,11.5,16.5],direction:[-.28,.15,1],distance:32,fov:36}
];
const reports=[];
try{
 auxiliary.forEach(o=>o.visible=false);axes.forEach(o=>o.visible=false);scene.background=null;p.renderer.setClearColor(0x151c1b,1);p.renderer.setPixelRatio(1);
 for(const job of jobs){
  const anim=Animation.all.find(a=>a.name===job.clip);anim.select();Animation.all.forEach(a=>a.playing=a===anim);
  const center=new THREE.Vector3(...job.center),camera=new THREE.PerspectiveCamera(job.fov,job.width/job.height,.1,1000);
  camera.position.copy(center).addScaledVector(new THREE.Vector3(...job.direction).normalize(),job.distance);camera.lookAt(center);camera.updateProjectionMatrix();
  p.renderer.setSize(job.width,job.height,false);
  for(let i=0;i<job.frames;i++){
   Timeline.setTime(i/25);Animator.preview();scene.updateMatrixWorld(true);
   p.renderer.setPixelRatio(1);p.renderer.setSize(job.width,job.height,false);p.renderer.setClearColor(0x151c1b,1);p.renderer.clear(true,true,true);
   Canvas.withoutGizmos(()=>p.renderer.render(scene,camera));
   fs.writeFileSync(frameDir+'/'+job.name+'_'+String(i).padStart(3,'0')+'.png',Buffer.from(p.renderer.domElement.toDataURL('image/png').split(',')[1],'base64'));
  }
  reports.push({...job,renderer:'Blockbench WebGL / Canvas.withoutGizmos'});
 }
}finally{
 Animation.all.forEach(a=>a.playing=false);Animator.showDefaultPose();Modes.options.edit.select();
 scene.background=bg;auxiliary.forEach((o,i)=>o.visible=oldVisibility[i]);axes.forEach((o,i)=>o.visible=oldAxes[i]);
 p.renderer.setPixelRatio(ratio);p.renderer.setSize(size[0]/ratio,size[1]/ratio,false);p.renderer.setClearColor(clear,alpha);p.render();
}
fs.writeFileSync(dir+'/reference/motion_render_report.json',JSON.stringify(reports,null,2));return reports;
})()'''.replace('DIR',json.dumps(OUT.as_posix()))
jobs=c.eval_js(code)
for job in jobs:
    frames=[Image.open(OUT/'reference/animation_frames'/f"{job['name']}_{i:03}.png").convert('RGB') for i in range(job['frames'])]
    # One shared palette prevents color flicker between frames.
    sheet=Image.new('RGB',(frames[0].width*5,frames[0].height*3))
    for i in range(15):sheet.paste(frames[round(i*(len(frames)-1)/14)],((i%5)*frames[0].width,(i//5)*frames[0].height))
    palette=sheet.quantize(colors=128,method=Image.Quantize.MEDIANCUT)
    frames=[im.quantize(palette=palette,dither=Image.Dither.NONE) for im in frames]
    target=OUT/f"preview_{job['name']}.gif"
    frames[0].save(target,save_all=True,append_images=frames[1:],duration=40,loop=0,optimize=False,disposal=2)
    print(target.name,job['frames'],'frames',target.stat().st_size,'bytes')
