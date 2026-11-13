(async()=>{
 const root=globalThis.SYRINGE_ASSET_ROOT;
 const read=p=>{let x;Blockbench.read([p],{},r=>x=JSON.parse(r[0].content));return x};
 if(Project.geometry_name!=='syringe_gun'||!Project.saved||ModelProject.all.length!==1)throw new Error('Project changed or unsaved');
 const disk=read(root+'/syringe_gun.bbmodel'),geo=read(root+'/syringe_gun.geo.json');
 Project.close();Codecs.project.load(disk,{path:root+'/syringe_gun.bbmodel'});
 await new Promise(r=>setTimeout(r,500));Canvas.updateAll();
 Project.name=disk.name;
 const roundtrip=JSON.parse(Codecs.bedrock.compile());
 const bb=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 const result={editor:Blockbench.version,format:Format.id,project_name:Project.name,cubes:Cube.all.length,bones:Group.all.length,texture_errors:Texture.all.map(t=>t.error),texture_embedded:bb.textures.every(t=>t.source?.startsWith('data:image/png;base64,')),geometry_exact_roundtrip:JSON.stringify(roundtrip)===JSON.stringify(geo),elements_exact_roundtrip:JSON.stringify(bb.elements)===JSON.stringify(disk.elements),groups_exact_roundtrip:JSON.stringify(bb.groups)===JSON.stringify(disk.groups),texture_source_exact:bb.textures[0].source===disk.textures[0].source,temporary_previews:Preview.all.filter(p=>p.id.startsWith('syringe_')).map(p=>p.id),client_tested:false};
 Blockbench.writeFile(root+'/reference/reopened_geometry.geo.json',{content:JSON.stringify(roundtrip,null,2)});
 Blockbench.writeFile(root+'/reference/native_reopen_validation.json',{content:JSON.stringify(result,null,2)});
 Project.save_path=root+'/syringe_gun.bbmodel';Project.saved=true;
 return result;
})()
