(async()=>{
 const root=globalThis.ASSET_ROOT;
 let data;Blockbench.read([root+'/advanced_syringe_gun.bbmodel'],{},r=>data=JSON.parse(r[0].content));
 if(ModelProject.all.some(p=>!p.saved))throw new Error('Unsaved editor project exists');
 const old=ModelProject.all.find(p=>p.save_path===root+'/advanced_syringe_gun.bbmodel');
 if(old)old.close();
 Codecs.project.load(data,{path:root+'/advanced_syringe_gun.bbmodel'});
 await new Promise(r=>setTimeout(r,600));
 Project.name=data.name;Project.geometry_name='advanced_syringe_gun';
 Project.save_path=root+'/advanced_syringe_gun.bbmodel';Canvas.updateAll();
 const compiled=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 if(compiled.elements.length!==data.elements.length)throw new Error('Cube count changed');
 if(JSON.stringify(compiled.elements)!==JSON.stringify(data.elements))throw new Error('Manual elements changed');
 for(const t of compiled.textures){delete t.path;delete t.relative_path;t.internal=true;}
 for(const g of compiled.groups)g.primary_selected=false;
 Blockbench.writeFile(Project.save_path,{content:JSON.stringify(compiled,null,2)});
 Blockbench.writeFile(root+'/reference/manual_publish_portable.bbmodel',{content:JSON.stringify(compiled,null,2)});
 Blockbench.writeFile(root+'/advanced_syringe_gun.geo.json',{content:Codecs.bedrock.compile()});
 Project.saved=true;
 return {loaded_manual_saved_file:true,editor:Blockbench.version,cubes:Cube.all.length,bones:Group.all.length,
  texture_errors:Texture.all.map(t=>t.error),embedded_texture:compiled.textures.every(t=>t.source?.startsWith('data:image/png;base64,'))};
})()
