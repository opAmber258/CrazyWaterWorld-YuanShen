(async()=>{
 const root=globalThis.ASSET_ROOT;
 if(Project.geometry_name!=='advanced_syringe_gun'||!Project.saved)throw new Error('Wrong or unsaved project');
 const previous=ModelProject.all.filter(p=>p!==Project).map(p=>({uuid:p.uuid,name:p.name,saved:p.saved}));
 Project.close();
 let data;Blockbench.read([root+'/reference/authored_input.bbmodel'],{},r=>data=JSON.parse(r[0].content));
 Codecs.project.load(data,{path:root+'/advanced_syringe_gun.bbmodel'});
 await new Promise(r=>setTimeout(r,600));
 Project.name='高级注射器枪 AdvancedSyringeGun';Project.geometry_name='advanced_syringe_gun';
 Project.save_path=root+'/advanced_syringe_gun.bbmodel';Canvas.updateAll();
 const bb=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 Blockbench.writeFile(Project.save_path,{content:JSON.stringify(bb,null,2)});
 Blockbench.writeFile(root+'/advanced_syringe_gun.geo.json',{content:Codecs.bedrock.compile()});
 Project.saved=true;
 return {cubes:Cube.all.length,groups:Group.all.length,previous_projects_retained:previous.every(x=>ModelProject.all.some(p=>p.uuid===x.uuid&&p.saved===x.saved))};
})()
