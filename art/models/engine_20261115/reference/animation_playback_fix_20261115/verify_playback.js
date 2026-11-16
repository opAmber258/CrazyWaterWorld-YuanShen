(()=>{
const fs=require('fs'),dir=require('path').dirname(Project.save_path),errors=[],samples=[];
const moving=['rotor','piston_left_moving','piston_right_moving'];
const expect=(yes,message)=>{if(!yes)errors.push(message);};
Modes.options.animate.select();Timeline.pause();
expect(Animation.all[0].name==='animation.engine.running','Default clip is not combined');
expect(Animation.selected?.name==='animation.engine.running','First animation-mode entry did not select combined');
expect(Timeline.playback_speed===100,'Playback speed is not 100%');
for(const a of Animation.all){
 a.select();Animation.all.forEach(b=>b.playing=b===a);
 expect(a.path.replaceAll('\\','/').endsWith('/production/engine_20261115/engine.animation.json'),'Wrong animation output path');
 for(const t of [0,.15,.3,.45,.6,.7999,.8,1.1999,1.2,2.3999,2.4].filter(t=>t<=a.length)){
  Timeline.setTime(t);Animator.preview();Canvas.scene.updateMatrixWorld(true);
  const values=Object.fromEntries(moving.map(name=>{const g=Group.all.find(g=>g.name===name);return[name,{world:g.mesh.getWorldPosition(new THREE.Vector3()).toArray(),rotation:g.mesh.rotation.x}];}));
  const rotor=a.name!=='animation.engine.pistons',pump=a.name!=='animation.engine.rotor_spin';
  const phase=1.5*Math.cos(t*300*Math.PI/180);
  expect(Math.abs(values.rotor.rotation-(rotor?t*450*Math.PI/180:0))<.05*Math.PI/180,a.name+' rotor at '+t);
  expect(Math.abs(values.piston_left_moving.world[1]-(10.5+(pump?phase:0)))<1e-6,a.name+' left piston at '+t);
  expect(Math.abs(values.piston_right_moving.world[1]-(10.5-(pump?phase:0)))<1e-6,a.name+' right piston at '+t);
  samples.push({clip:a.name,time:t,values});
 }
}
Animation.all.forEach(a=>a.playing=false);Animator.showDefaultPose();Modes.options.edit.select();
const result={errors,default_clip:'animation.engine.running',playback_speed_percent:Timeline.playback_speed,rotor_period_seconds:.8,piston_period_seconds:1.2,piston_stroke_bb:3,pistons_phase_difference_degrees:180,rotor_tolerance_degrees:.05,note:'Near-keyframe samples can snap to the native keyframe within 0.0001 seconds.',samples};
fs.writeFileSync(dir+'/reference/animation_playback_fix_20261115/playback_validation.json',JSON.stringify(result,null,2));return{errors,samples:samples.length,default_clip:result.default_clip};
})()
