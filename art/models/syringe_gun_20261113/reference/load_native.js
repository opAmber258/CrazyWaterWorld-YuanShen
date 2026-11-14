(async()=>{
 const path=PathModule; const fs={readFileSync:(p)=>{let x;Blockbench.read([p],{},r=>x=r[0].content);return x},writeFileSync:(p,s)=>Blockbench.writeFile(p,{content:s})};
 const root=globalThis.SYRINGE_ASSET_ROOT;
 if(ModelProject.all.length) throw new Error('Expected empty isolated Blockbench instance');
 const data=JSON.parse(fs.readFileSync(path.join(root,'syringe_gun.bbmodel'),'utf8'));
 Codecs.project.load(data,{path:path.join(root,'syringe_gun.bbmodel')});
 await new Promise(r=>setTimeout(r,500));
 Project.name='注射器枪 SyringeGun';Project.geometry_name='syringe_gun';Project.box_uv=false;
 Project.save_path=path.join(root,'syringe_gun.bbmodel');
 Canvas.updateAll();
 const bb=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 fs.writeFileSync(Project.save_path,JSON.stringify(bb,null,2));
 fs.writeFileSync(path.join(root,'syringe_gun.geo.json'),Codecs.bedrock.compile());
 Project.saved=true;
 return {version:Blockbench.version,format:Format.id,cubes:Cube.all.length,groups:Group.all.length,textures:Texture.all.map(t=>({name:t.name,width:t.width,height:t.height}))};
})()
