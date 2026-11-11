const fs=require('fs'),path=require('path');
const dir=globalThis.REACTOR_ASSET_DIR;
if(!String(Project.save_path||'').replaceAll('\\','/').startsWith(dir+'/')||!Project.saved)throw Error('Unexpected project or unsaved edits; preserve them before verifying');
const filename=path.join(dir,'nuclear_reactor.bbmodel');
const baseline=JSON.parse(fs.readFileSync(path.join(dir,'nuclear_reactor.geo.json'),'utf8'));
const model=JSON.parse(fs.readFileSync(filename,'utf8'));
Codecs.project.load(model,{path:filename});
await new Promise(resolve=>setTimeout(resolve,400));
Canvas.updateAll();
const exported=JSON.parse(Codecs.bedrock.compile());
const equal=JSON.stringify(exported)===JSON.stringify(baseline);
if(!equal)throw Error('Saved model does not reproduce the delivered Bedrock export');
// Bounds from actual cube meshes only: exclude editor selection gizmos.
const box=new THREE.Box3();
for(const c of Cube.all){
 const mesh=c.mesh;if(!mesh||!c.export)continue;
 mesh.updateWorldMatrix(true,false);mesh.geometry.computeBoundingBox();
 box.union(mesh.geometry.boundingBox.clone().applyMatrix4(mesh.matrixWorld));
}
const report={blockbench_version:Blockbench.version,project_reopened:true,native_export_reproduced_exactly:equal,
 format:Format.id,geometry_name:Project.geometry_name,cubes:Cube.all.length,bones:Group.all.length,
 textures:Texture.all.map(t=>({width:t.width,height:t.height,uv_width:t.uv_width,uv_height:t.uv_height,loaded:t.img.complete})),
 world_bbox:{min:box.min.toArray(),max:box.max.toArray(),size:box.getSize(new THREE.Vector3()).toArray()},
 bbox_method:'union of cube mesh geometry bounding boxes transformed by cube world matrices; no selection helpers',
 groups:Group.all.map(g=>({name:g.name,cubes:g.children.filter(c=>c instanceof Cube).length,child_groups:g.children.filter(c=>c instanceof Group).length})),
 animations:Animation.all.length,visual_review:{front:'inspected',back:'inspected',side:'inspected',oblique:'inspected',uv_checker:'UV inherited unchanged from inspected v3; per-face equality verified independently'},
 minecraft_runtime_tested:false};
const compiled=Codecs.project.compile();
fs.writeFileSync(filename,compiled);
const embedded=JSON.parse(compiled).textures[0].source.split(',')[1];
fs.writeFileSync(path.join(dir,'nuclear_reactor_tex.png'),Buffer.from(embedded,'base64'));
fs.writeFileSync(path.join(dir,'nuclear_reactor.geo.json'),Codecs.bedrock.compile());
fs.writeFileSync(path.join(dir,'reference','blockbench_verification.json'),JSON.stringify(report,null,2));
Project.saved=true;Texture.all.forEach(t=>t.saved=true);
const current=Project;
for(const p of [...ModelProject.all]){
 const ownPath=String(p.save_path||'').replaceAll('\\','/').startsWith(dir+'/');
 if(p!==current&&ownPath&&p.saved)await p.close();
}
current.select();
Project.name='nuclear_reactor_texture_v4';Project.saved=true;
return {...report,open_projects:ModelProject.all.map(p=>({name:p.name,saved:p.saved,save_path:p.save_path}))};
