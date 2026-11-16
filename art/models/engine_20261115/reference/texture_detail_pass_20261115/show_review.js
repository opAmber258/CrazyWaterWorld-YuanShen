(()=>{
Timeline.pause();Animation.all.forEach(a=>a.playing=false);Animator.showDefaultPose();Modes.options.edit.select();
unselectAllElements();Preview.selected.setProjectionMode(false);
Preview.selected.controls.target.set(-.55,22,.525);Preview.selected.camera.position.set(-64,53,78);Preview.selected.controls.update();Preview.selected.render();
return {project:Project.name,path:Project.save_path,saved:Project.saved,cubes:Cube.all.length,bones:Group.all.length,clips:Animation.all.length};
})()
