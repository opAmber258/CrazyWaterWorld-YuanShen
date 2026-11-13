(async()=>{
 const root=globalThis.ASSET_ROOT;
 if(Project.geometry_name!=='advanced_syringe_gun'||!Project.saved)throw new Error('Wrong or unsaved project');
 let candidates;Blockbench.read([root+'/reference/face_cleanup_candidates.json'],{},r=>candidates=JSON.parse(r[0].content));
 const targets=[...new Set(candidates.map(c=>Cube.all.find(e=>e.uuid===c.uuid)))];
 if(targets.some(e=>!e))throw new Error('Cube missing');
 Undo.initEdit({elements:targets});
 for(const c of candidates)Cube.all.find(e=>e.uuid===c.uuid).faces[c.face].texture=null;
 Undo.finishEdit('Remove verified fully enclosed faces');Canvas.updateAll();
 const bb=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 Blockbench.writeFile(Project.save_path,{content:JSON.stringify(bb,null,2)});
 Blockbench.writeFile(root+'/advanced_syringe_gun.geo.json',{content:Codecs.bedrock.compile()});
 Project.saved=true;
 return {removed_faces:candidates.length,cubes:Cube.all.length};
})()
