(async()=>{
 const root=globalThis.ASSET_ROOT;
 const read=p=>{let x;Blockbench.read([p],{},r=>x=r[0].content);return JSON.parse(x)};
 const before=ModelProject.all.map(p=>({name:p.name,uuid:p.uuid,saved:p.saved}));
 if(ModelProject.all.some(p=>p.geometry_name==='advanced_syringe_gun'))throw new Error('Asset already open; use reload_authored.js');
 if(before.some(p=>!p.saved))throw new Error('Unsaved project exists; stop before switching');
 const data=read(root+'/reference/authored_input.bbmodel');
 Codecs.project.load(data,{path:root+'/advanced_syringe_gun.bbmodel'});
 await new Promise(r=>setTimeout(r,600));
 Project.name='高级注射器枪 AdvancedSyringeGun';Project.geometry_name='advanced_syringe_gun';Project.box_uv=false;
 Project.save_path=root+'/advanced_syringe_gun.bbmodel';Canvas.updateAll();
 const bb=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 Blockbench.writeFile(Project.save_path,{content:JSON.stringify(bb,null,2)});
 Blockbench.writeFile(root+'/advanced_syringe_gun.geo.json',{content:Codecs.bedrock.compile()});
 Project.saved=true;
 const result={version:Blockbench.version,format:Format.id,cubes:Cube.all.length,bones:Group.all.length,
  textures:Texture.all.map(t=>({name:t.name,width:t.width,height:t.height,error:t.error})),
  preexisting_projects:before,current_project_uuid:Project.uuid,
  note:'Native project codec loaded authored input into a new tab; existing projects retained.'};
 Blockbench.writeFile(root+'/reference/native_creation.json',{content:JSON.stringify(result,null,2)});
 return result;
})()
