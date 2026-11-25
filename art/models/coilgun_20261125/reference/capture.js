(async () => {
 const root=COILGUN_ROOT;
 const path=PathModule.join(root,'coilgun_runtime.bbmodel');
 const read=path=>new Promise(resolve=>Blockbench.read([path],{},files=>resolve(JSON.parse(files[0].content))));
 const raw=await read(path);
 Codecs.project.load(raw,{path});
 await Promise.all(Texture.all.map(texture=>texture.img.decode()));
 const geometry=Codecs.bedrock.compile();
 const geo=typeof geometry === 'string' ? JSON.parse(geometry) : geometry;
 const clips=AnimationCodec.codecs.bedrock.compileFile(Animation.all);
 const write=(name,data)=>new Promise(resolve=>Blockbench.writeFile(PathModule.join(root,name),{content:JSON.stringify(data,null,2)+'\n'},resolve));
 await write('coilgun.geo.json',geo);
 await write('coilgun.animation.json',clips);
 const compare=await read(PathModule.join(root,'coilgun.bbmodel'));
 const view=new Preview({id:'coilgun_r9_audit',offscreen:true,antialias:true});
 view.renderer.setPixelRatio(1);
 view.resize(1200,900);
 view.controls.enableRotate=true;
 view.angle=null;
 const camera=(position,target,fov=38,orthographic=false,muzzleView=false)=>{
  if (!muzzleView) {
   const scale=entry=>[.8*entry[0],.8*entry[1],.8*entry[2]-5.04];
   position=scale(position); target=scale(target);
   if(orthographic) fov *= .8;
  }
  view.setProjectionMode(orthographic);
  view.camera.position.fromArray(position);
  view.controls.target.fromArray(target);
  if(orthographic) {
   const extents=fov;
   view.camOrtho.left=-extents*4/3;
   view.camOrtho.right=extents*4/3;
   view.camOrtho.bottom=-extents;
   view.camOrtho.top=extents;
   view.camOrtho.zoom=1;
  } else {
   view.camPers.fov=fov;
  }
  view.camera.updateProjectionMatrix();
  view.controls.update();
 };
 const save=name=>new Promise(resolve=>Screencam.screenshotPreview(view,{crop:false},url=>{
  Blockbench.writeFile(PathModule.join(root,name),{content:url,savetype:'image'},resolve);
 }));
 const render=async()=>{
  await Promise.all(Texture.all.map(texture=>texture.img.decode()));
  await new Promise(r=>setTimeout(r,300));
  Canvas.updateAll();
 };
 const load=async (model,name)=>{
  Codecs.project.load(model,{path:PathModule.join(root,name)});
  await render();
 };
 const frames=(name)=>'reference/frames/'+name+'.png';
 const sample=()=>{
  const groups=Object.fromEntries(Group.all.map(g=>[g.name,g.mesh]));
  return {t:Timeline.time,barrel:groups.barrel_recoil.position.z,
          flashes:['wedge','fork','slant','burst'].map(n=>{
           const flash=groups['flash_'+n]; return flash.position.z>0 ? flash.scale.x : 0;
          })};
 };
 const snap=(time)=>{
  Timeline.setTime(time); Animator.preview();
  return sample();
 };
 const clipsInEditor=[
  ['single','.fire_single'],
  ['burst','.fire_burst']
 ];
 const progress=name=>{ globalThis.coilgunQA=name; };
 const testPlayback=(clip, firstDeltaMs, stepMs)=>{
  clip.select();
  snap(0);
  BarItems.play_animation.onClick();
  const observed=[sample()];
  try {
   for(let i=0; i<Math.ceil(840/stepMs)+2 && Timeline.playing; i++){
    const delta=i===0 ? firstDeltaMs : stepMs;
    Timeline.last_frame_timecode=performance.now()-delta;
    Timeline.loop();
    observed.push(sample());
   }
  } finally {
   Timeline.pause();
  }
  return observed;
 };
 try {
  const nativeCounts={bedrockCubes:Cube.all.length,bedrockBones:Group.all.length,
                     bedrockFaces:geo['minecraft:geometry'][0].bones.flatMap(b=>b.cubes||[]).reduce((n,c)=>n+Object.keys(c.uv).length,0)};
  Modes.options.edit.select();
  await render();
  camera([120,75,42],[14,51,-1],24);
  await save(frames('runtime_right'));
  progress('runtime_rendered');
  await load(compare,'coilgun.bbmodel');
  Modes.options.animate.select();
  progress('new_loaded');
  const glow=Texture.all.find(t=>t.name==='muzzle_glow.png');
  if (!glow?.getOwnMaterial().uniforms.EMISSIVE.value) throw new Error('Missing emissive flash material');
  const data={version:Blockbench.version, emissive: true, previewCubeCount:Cube.all.length, flashMeshCount:Mesh.all.length, ...nativeCounts};
  for (const [short,suffix] of clipsInEditor) {
   const clip=Animation.all.find(a=>a.name.endsWith(suffix));
   clip.select();
   progress(short+'_play_started');
   const playProfiles=Object.fromEntries([[16.667,16.667],[33.333,33.333],[115,33.333]].map(([first,step])=>[
    String(first), testPlayback(clip,first,step)
   ]));
   const points=[];
   for (let index=0; index<=Math.ceil(clip.length*40); index++) {
    points.push(snap(Math.min(index*.025,clip.length)));
   }
   data[short]={length:clip.length,playProfiles,points};
  }

  progress('play_profiles_done');
  const single=Animation.all.find(a=>a.name.endsWith('.fire_single'));
  single.select();
  snap(single.length);
  Modes.options.edit.select();
  camera([120,75,42],[14,51,-1],24);
  await save('preview_armor_right.png');
  camera([-120,75,42],[-14,51,-1],24);
  await save('preview_armor_left.png');
  camera([0,34,193],[0,34,0],52,true);
  await save('preview_front.png');
  camera([0,34,-193],[0,34,0],52,true);
  await save('preview_back.png');
  camera([193,34,0],[0,34,0],56,true);
  await save('preview_side.png');
  camera([155,93,135],[0,36,7],38);
  await save('preview_oblique.png');

  progress('static_renders_done');
  Modes.options.animate.select();
  view.resize(720,540);
  camera([155,93,135],[0,36,7],39);
  for(const [name,suffix] of clipsInEditor) {
   const clip=Animation.all.find(a=>a.name.endsWith(suffix));
   clip.select();
   for(let i=0;i<Math.ceil(clip.length*40);i++){
    snap(i/40);
    await save(frames(name+'_'+String(i).padStart(2,'0')));
   }
   snap(clip.length);
   await save(frames(name+'_rest'));
  }
  const burst=Animation.all.find(a=>a.name.endsWith('.fire_burst'));
  burst.select();
  const muzzle=Locator.all.find(locator=>locator.name==='muzzle_flash_origin').mesh;
  const wedge=Group.all.find(g=>g.name==='flash_wedge').mesh;
  snap(.1083);
  muzzle.updateWorldMatrix(true,false); wedge.updateWorldMatrix(true,false);
  const offset=muzzle.getWorldPosition(new THREE.Vector3()).distanceTo(wedge.getWorldPosition(new THREE.Vector3()));
  if(offset > .01) throw new Error('Flash anchor offset from muzzle: '+offset);
  data.flashAnchorError=offset;
  for(let i=0;i<4;i++){
   snap(.0833+i*.2+.025);
   muzzle.updateWorldMatrix(true,false);
   const [x,y,z]=muzzle.getWorldPosition(new THREE.Vector3()).toArray();
   camera([x+46.4,y+15.2,z+53.6],[x,y,z+6.4],27,false,true);
   await save(frames('flash_'+(i+1)));
  }

  progress('frames_done');
  await write('reference/native_check.json',data);
  return {version:data.version, ...nativeCounts, 
          singleAfterDelay:data.single.playProfiles["115"][1], burstAfterDelay:data.burst.playProfiles["115"][1]};
 } finally {
  view.renderer.dispose();
  Preview.all.remove(view);
  view.node.remove();
 }
})();






