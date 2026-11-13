(async()=>{
 const root=globalThis.ASSET_ROOT;
 const read=p=>{let x;Blockbench.read([p],{},r=>x=JSON.parse(r[0].content));return x};
 if(Project.geometry_name!=='advanced_syringe_gun'||!Project.saved)throw new Error('Wrong or unsaved project');
 const others=ModelProject.all.filter(p=>p!==Project).map(p=>({uuid:p.uuid,name:p.name,saved:p.saved}));
 const disk=read(root+'/advanced_syringe_gun.bbmodel'),geo=read(root+'/advanced_syringe_gun.geo.json');
 Project.close();Codecs.project.load(disk,{path:root+'/advanced_syringe_gun.bbmodel'});
 await new Promise(r=>setTimeout(r,600));Canvas.updateAll();Project.name=disk.name;
 const roundtrip=JSON.parse(Codecs.bedrock.compile());
 const bb=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 const bounds=new THREE.Box3();
 for(const c of Cube.all){c.mesh.updateWorldMatrix(true,false);bounds.union(new THREE.Box3().setFromObject(c.mesh));}
 const result={editor:Blockbench.version,format:Format.id,project:Project.name,
  cubes:Cube.all.length,bones:Group.all.length,texture_errors:Texture.all.map(t=>t.error),
  texture_embedded:bb.textures.every(t=>t.source?.startsWith('data:image/png;base64,')),
  geometry_exact_roundtrip:JSON.stringify(roundtrip)===JSON.stringify(geo),
  elements_exact_roundtrip:JSON.stringify(bb.elements)===JSON.stringify(disk.elements),
  groups_exact_roundtrip:JSON.stringify(bb.groups)===JSON.stringify(disk.groups),
  texture_source_exact:bb.textures[0].source===disk.textures[0].source,
  native_world_bounds:{min:bounds.min.toArray(),max:bounds.max.toArray()},
  preexisting_projects_retained:others.every(x=>ModelProject.all.some(p=>p.uuid===x.uuid&&p.saved===x.saved)),
  temporary_previews:Preview.all.filter(p=>p.id.startsWith('advanced_syringe_')).map(p=>p.id),
  client_tested:false};
 Blockbench.writeFile(root+'/reference/reopened_geometry.geo.json',{content:JSON.stringify(roundtrip,null,2)});
 Blockbench.writeFile(root+'/reference/native_reopen_validation.json',{content:JSON.stringify(result,null,2)});
 Project.save_path=root+'/advanced_syringe_gun.bbmodel';Project.saved=true;
 return result;
})()
