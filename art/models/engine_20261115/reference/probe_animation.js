(()=>{
const fs=require('fs'),dir=require('path').dirname(Project.save_path);
Modes.options.animate.select();
const rotor=Group.all.find(g=>g.name==='rotor');
const left=Group.all.find(g=>g.name==='piston_left_moving'),right=Group.all.find(g=>g.name==='piston_right_moving');
function pose(clip,t){
 const a=Animation.all.find(a=>a.name===clip);a.select();Animation.all.forEach(b=>b.playing=b===a);
 Timeline.setTime(t);Animator.preview();Canvas.scene.updateMatrixWorld(true);
 const leafs=Cube.all.filter(c=>c.name.startsWith('rotor_blade_'));
 let maxRadius=0,xmin=Infinity,xmax=-Infinity;
 for(const c of leafs){const attr=c.mesh.geometry.attributes.position;for(let i=0;i<attr.count;i++){
  const v=new THREE.Vector3().fromBufferAttribute(attr,i).applyMatrix4(c.mesh.matrixWorld);
  maxRadius=Math.max(maxRadius,Math.hypot(v.y-21,v.z));xmin=Math.min(xmin,v.x);xmax=Math.max(xmax,v.x);
 }}
 return {time:t,rotor_degrees:rotor.mesh.rotation.x*180/Math.PI,left_y:left.mesh.position.y,right_y:right.mesh.position.y,blade_radius:maxRadius,blade_x:[xmin,xmax]};
}
const samples={};for(const a of Animation.all){samples[a.name]=[0,a.length/8,a.length/4,a.length/2,a.length*.75,a.length].map(t=>pose(a.name,t));}
const report={version:Blockbench.version,samples};
fs.writeFileSync(dir+'/reference/native_animation_samples.json',JSON.stringify(report,null,2));
Animation.all.forEach(a=>a.playing=false);Animator.showDefaultPose();Modes.options.edit.select();
return report;
})()
