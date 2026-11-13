(async()=>{
const fs=require('fs');
const path=Project.save_path;
if(!path.endsWith('engine_20261113\\engine.bbmodel')&&!path.endsWith('engine_20261113/engine.bbmodel'))throw Error('Wrong project');
const dir=require('path').dirname(path);
const live=JSON.parse(Codecs.bedrock.compile());
const disk=JSON.parse(fs.readFileSync(dir+'/engine.geo.json','utf8'));
fs.writeFileSync(dir+'/reference/reopened_export.geo.json',JSON.stringify(live,null,2));
const report={project:Project.name,blockbench_version:Blockbench.version,cubes:Cube.all.length,bones:Group.all.length,textures:Texture.all.length,format:Format.id,exact_reopened_export_match:JSON.stringify(live)===JSON.stringify(disk),saved:Project.saved};
fs.writeFileSync(dir+'/reference/reopened_project_check.json',JSON.stringify(report,null,2));
return report;
})()
