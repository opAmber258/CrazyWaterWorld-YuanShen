(async()=>{
 const root=globalThis.SYRINGE_ASSET_ROOT;
 let model;Blockbench.read([root+'/syringe_gun.bbmodel'],{},r=>model=JSON.parse(r[0].content));
 if(ModelProject.all.length)throw new Error('Use an empty isolated Blockbench instance');
 Codecs.project.load(model,{path:root+'/syringe_gun.bbmodel'});
 await new Promise(r=>setTimeout(r,400));
 Project.name='\u6ce8\u5c04\u5668\u67aa SyringeGun';Project.geometry_name='syringe_gun';Project.save_path=root+'/syringe_gun.bbmodel';
 Canvas.updateAll();
 const bb=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 for(const t of bb.textures){delete t.path;delete t.relative_path;delete t.file_path;}
 Blockbench.writeFile(Project.save_path,{content:JSON.stringify(bb,null,2)});
 Blockbench.writeFile(root+'/syringe_gun.geo.json',{content:Codecs.bedrock.compile()});
 Project.saved=true;
 return {editor:Blockbench.version,format:Format.id,cubes:Cube.all.length,bones:Group.all.length,logical_uv:[Project.texture_width,Project.texture_height],textures:Texture.all.map(t=>({name:t.name,size:[t.width,t.height],error:t.error})),bone_rotations:Group.all.map(g=>({name:g.name,rotation:g.rotation}))};
})()
