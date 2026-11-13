(()=>{
 if(Project.geometry_name!=='syringe_gun')throw new Error('Wrong project');
 Modes.options.edit.select();const p=Preview.selected;
 p.setProjectionMode(false);p.resize();p.setFOV(36);
 p.camera.position.set(17,11,38);p.controls.target.set(.225,3.675,0);p.controls.enableDamping=false;p.controls.update();p.render();
 return {view:p.id,dimensions:[p.width,p.height],camera:p.camera.position.toArray(),target:p.controls.target.toArray(),project:Project.name};
})()
