(async()=>{
const fs=require('fs'),dir=require('path').dirname(Project.save_path),editable=Project;
const canonical=o=>JSON.stringify((function sort(v){if(Array.isArray(v))return v.map(sort);if(v&&typeof v==='object')return Object.fromEntries(Object.keys(v).sort().map(k=>[k,sort(v[k])]));return v;})(o));
const diskGeo=JSON.parse(fs.readFileSync(dir+'/engine.geo.json','utf8'));
const diskAnim=JSON.parse(fs.readFileSync(dir+'/engine.animation.json','utf8'));
const compileAnimations=()=>AnimationCodec.codecs.bedrock.compileFile(Animation.all);
const report={blockbench_version:Blockbench.version,reopened_project_geometry_match:canonical(JSON.parse(Codecs.bedrock.compile()))===canonical(diskGeo),reopened_project_animation_match:canonical(compileAnimations())===canonical(diskAnim)};
await loadModelFile({path:dir+'/engine.geo.json',content:fs.readFileSync(dir+'/engine.geo.json','utf8')});
const imported=Project;
if(!Animation.all.length)AnimationCodec.codecs.bedrock.loadFile({path:dir+'/engine.animation.json',content:fs.readFileSync(dir+'/engine.animation.json','utf8')});
report.geo_import_reexport_match=canonical(JSON.parse(Codecs.bedrock.compile()))===canonical(diskGeo);
report.animation_import_reexport_match=canonical(compileAnimations())===canonical(diskAnim);
report.imported_cubes=Cube.all.length;report.imported_bones=Group.all.length;report.imported_clips=Animation.all.map(a=>a.name);
fs.writeFileSync(dir+'/reference/native_roundtrip.json',JSON.stringify(report,null,2));
Animation.all.forEach(a=>{a.saved=true;a.playing=false;});imported.saved=true;await imported.close(true);editable.select();
Animation.all.forEach(a=>a.playing=false);Animator.showDefaultPose();Modes.options.edit.select();
const box=new THREE.Box3();Cube.all.forEach(c=>{c.mesh.updateMatrixWorld(true);box.expandByObject(c.mesh);});
report.bbox={min:box.min.toArray(),max:box.max.toArray(),size:box.getSize(new THREE.Vector3()).toArray()};
fs.writeFileSync(dir+'/reference/native_roundtrip.json',JSON.stringify(report,null,2));
return report;
})()
