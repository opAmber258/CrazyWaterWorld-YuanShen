(()=>{
const fs=require('fs'),dir=require('path').dirname(Project.save_path),base=JSON.parse(fs.readFileSync(dir+'/reference/physical_panels_20261115/baseline_engine.bbmodel','utf8'));
const oldIds=new Set(base.elements.map(e=>e.uuid)),added=Cube.all.filter(c=>!oldIds.has(c.uuid));
const originalPositions=new Map();
function obb(c){c.mesh.geometry.computeBoundingBox();const b=c.mesh.geometry.boundingBox,m=c.mesh.matrixWorld,center=b.getCenter(new THREE.Vector3()).applyMatrix4(m),s=b.getSize(new THREE.Vector3()).multiplyScalar(.5);const cols=[new THREE.Vector3().setFromMatrixColumn(m,0),new THREE.Vector3().setFromMatrixColumn(m,1),new THREE.Vector3().setFromMatrixColumn(m,2)];return{center,half:[s.x,s.y,s.z].map((v,i)=>v*cols[i].length()),axes:cols.map(v=>v.normalize())};}
function overlap(a,b){const delta=b.center.clone().sub(a.center),axes=[...a.axes,...b.axes];for(const x of a.axes)for(const y of b.axes){const v=new THREE.Vector3().crossVectors(x,y);if(v.lengthSq()>1e-12)axes.push(v.normalize());}return axes.every(axis=>Math.abs(delta.dot(axis))<a.axes.reduce((s,v,i)=>s+Math.abs(v.dot(axis))*a.half[i],0)+b.axes.reduce((s,v,i)=>s+Math.abs(v.dot(axis))*b.half[i],0)-1e-5);}
Timeline.pause();Animation.all.forEach(a=>a.playing=false);Animator.showDefaultPose();Canvas.scene.updateMatrixWorld(true);
for(const c of Cube.all)originalPositions.set(c.uuid,c.mesh.matrixWorld.clone());
Modes.options.animate.select();const anim=Animation.all.find(a=>a.name==='animation.engine.running');anim.select();Animation.all.forEach(a=>a.playing=a===anim);
const statics=added.map(c=>({cube:c,box:obb(c)})),hits=[],moving=new Set();let tests=0;
for(let i=0;i<60;i++){
 const t=i/25;Timeline.setTime(t);Animator.preview();Canvas.scene.updateMatrixWorld(true);
 for(const c of Cube.all.filter(c=>oldIds.has(c.uuid))){
  if(c.mesh.matrixWorld.elements.every((v,j)=>Math.abs(v-originalPositions.get(c.uuid).elements[j])<1e-7))continue;
  moving.add(c.name);const b=obb(c);
  for(const s of statics){tests++;if(overlap(s.box,b))hits.push({time:t,added:s.cube.name,moving:c.name});}
 }
}
Animation.all.forEach(a=>a.playing=false);Animator.showDefaultPose();Modes.options.edit.select();
const report={method:'Native mesh OBB separating-axis tests; 60 samples across the 2.4 second combined loop',added_static_cubes:added.length,moving_original_cubes:moving.size,tested_pairs:tests,intersections:hits,limitation:'Sampled mesh check, not continuous collision or Minecraft validation'};
fs.writeFileSync(dir+'/reference/physical_panels_20261115/motion_clearance.json',JSON.stringify(report,null,2));return report;
})()
