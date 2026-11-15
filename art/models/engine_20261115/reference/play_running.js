(()=>{
Modes.options.animate.select();
Timeline.pause();Animation.all.forEach(a=>a.playing=false);
const a=Animation.all.find(a=>a.name==='animation.engine.running');a.select();a.playing=true;
Timeline.setTime(0);Animator.preview();
Preview.selected.setProjectionMode(false);
Preview.selected.controls.target.set(0,21,0);Preview.selected.camera.position.set(-64,52,78);Preview.selected.controls.update();
Timeline.start();
return {project:Project.name,path:Project.save_path,saved:Project.saved,clip:a.name,loop:a.loop,length:a.length};
})()
