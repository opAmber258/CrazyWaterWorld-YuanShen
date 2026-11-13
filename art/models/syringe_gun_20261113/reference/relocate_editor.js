(()=>{
 const root=globalThis.SYRINGE_ASSET_ROOT;
 if(Project.geometry_name!=='syringe_gun'||!Project.saved)throw new Error('Wrong or unsaved project');
 Project.save_path=root+'/syringe_gun.bbmodel';
 Project.saved=true;
 return {path:Project.save_path,saved:Project.saved};
})()
