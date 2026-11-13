(async()=>{
 const root=globalThis.ASSET_ROOT;
 if(Project.geometry_name!=='advanced_syringe_gun'||!Project.saved)throw new Error('Wrong or unsaved project');
 let disk;Blockbench.read([Project.save_path],{},r=>disk=JSON.parse(r[0].content));
 const live=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 const normal=v=>JSON.parse(JSON.stringify(v,(_,x)=>typeof x==='number'?Math.round(x*1e5)/1e5:x));
 if(JSON.stringify(normal(live.elements))!==JSON.stringify(normal(disk.elements)))throw new Error('Live edits differ materially from saved file');
 Blockbench.writeFile(root+'/reference/publish_input.bbmodel',{content:JSON.stringify(disk,null,2)});
 Project.close();Codecs.project.load(disk,{path:root+'/advanced_syringe_gun.bbmodel'});
 await new Promise(r=>setTimeout(r,600));Canvas.updateAll();Project.name=disk.name;
 Blockbench.writeFile(root+'/advanced_syringe_gun.geo.json',{content:Codecs.bedrock.compile()});
 Project.save_path=root+'/advanced_syringe_gun.bbmodel';Project.saved=true;
 return {refreshed:true,preserved_saved_geometry:true,cubes:Cube.all.length};
})()
