(async()=>{
 const root=globalThis.SYRINGE_ASSET_ROOT;
 if(ModelProject.all.length!==1||Project.geometry_name!=='syringe_gun')throw new Error('Unexpected projects');
 Project.saved=true;Project.close();
 let raw;Blockbench.read([root+'/syringe_gun.bbmodel'],{},r=>raw=r[0].content);
 Codecs.project.load(JSON.parse(raw),{path:root+'/syringe_gun.bbmodel'});
 await new Promise(r=>setTimeout(r,300));
 Project.name='\u6ce8\u5c04\u5668\u67aa SyringeGun';Project.geometry_name='syringe_gun';Project.box_uv=false;
 Project.save_path=root+'/syringe_gun.bbmodel';Canvas.updateAll();
 Blockbench.writeFile(Project.save_path,{content:JSON.stringify(Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false}),null,2)});
 Blockbench.writeFile(root+'/syringe_gun.geo.json',{content:Codecs.bedrock.compile()});Project.saved=true;
 return {name:Project.name,cubes:Cube.all.length,groups:Group.all.length};
})()
