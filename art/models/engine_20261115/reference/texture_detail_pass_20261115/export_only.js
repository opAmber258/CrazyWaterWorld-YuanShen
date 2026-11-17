(()=>{
const fs=require('fs'),dir=require('path').dirname(Project.save_path);
if(!dir.endsWith('engine_20261115'))throw Error('Unexpected project');
fs.writeFileSync(dir+'/engine.geo.json',Codecs.bedrock.compile());
fs.writeFileSync(dir+'/engine.animation.json',JSON.stringify(AnimationCodec.codecs.bedrock.compileFile(Animation.all),null,2));
return {cubes:Cube.all.length,bones:Group.all.length,clips:Animation.all.map(a=>a.name),logical_uv:[Project.texture_width,Project.texture_height],project_saved:Project.saved};
})()
