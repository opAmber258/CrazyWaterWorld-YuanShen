(async()=>{
const current=Project;
const gray=ModelProject.all.find(p=>p.name==='engine_gray'&&p.save_path?.includes('engine_20261113')&&p.saved);
if(gray){gray.select();await gray.close(true);current.select();}
unselectAllElements();Canvas.updateAll();
Preview.selected.setProjectionMode(false);
Preview.selected.controls.target.set(0,21,0);
Preview.selected.camera.position.set(-90,70,115);
Preview.selected.controls.update();Preview.selected.render();
return {active:Project.name,cubes:Cube.all.length,saved:Project.saved};
})()
