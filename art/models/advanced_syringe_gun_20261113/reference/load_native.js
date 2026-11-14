(async()=>{
 const root=globalThis.ASSET_ROOT;
 if(ModelProject.all.some(p=>!p.saved))throw new Error('Unsaved project exists');
 if(Project?.save_path===root+'/advanced_syringe_gun.bbmodel')Project.close();
 let data;Blockbench.read([root+'/reference/authored_input.bbmodel'],{},r=>data=JSON.parse(r[0].content));
 Codecs.project.load(data,{path:root+'/advanced_syringe_gun.bbmodel'});
 await new Promise(r=>setTimeout(r,600));
 Project.name=data.name;Project.geometry_name='advanced_syringe_gun';Project.save_path=root+'/advanced_syringe_gun.bbmodel';
 Canvas.updateAll();const bb=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 Blockbench.writeFile(Project.save_path,{content:JSON.stringify(bb,null,2)});
 Blockbench.writeFile(root+'/advanced_syringe_gun.geo.json',{content:Codecs.bedrock.compile()});
 Project.saved=true;
 return {editor:Blockbench.version,cubes:Cube.all.length,bones:Group.all.length,texture_errors:Texture.all.map(t=>t.error)};
})()
