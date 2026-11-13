(async()=>{
 const root=globalThis.ASSET_ROOT;
 if(Project.geometry_name!=='advanced_syringe_gun')throw new Error('Wrong project');
 const p=new Preview({id:'advanced_syringe_delivery',offscreen:true});
 p.resize(1200,900);p.renderer.setPixelRatio(1);
 const oldBackground=Canvas.scene.background;
 const target=[.4,3.4,0];
 const views={front:{pos:[.4,3.4,70],ortho:true},back:{pos:[.4,3.4,-70],ortho:true},
  side:{pos:[70,3.4,0],ortho:true},top:{pos:[.4,70,0],ortho:true},
  oblique:{pos:[16,12,28],ortho:false},rear_oblique:{pos:[-16,12,-28],ortho:false}};
 const metadata={renderer:'Blockbench native Preview / Three.js',editor:Blockbench.version,
  dimensions:[1200,900],background:'#202a2d',views:{}};
 try{
  Canvas.scene.background=new THREE.Color('#202a2d');
  for(const [name,v] of Object.entries(views)){
   p.setProjectionMode(v.ortho);p.resize(1200,900);p.camera.position.fromArray(v.pos);
   p.controls.target.fromArray(target);p.controls.enableDamping=false;
   if(v.ortho){p.camera.zoom=1.38;p.camera.updateProjectionMatrix();}else{p.setFOV(36);}
   p.controls.update();await new Promise(r=>setTimeout(r,120));
   let data;p.screenshot({crop:false,width:1200,height:900},s=>data=s);
   if(!data)throw new Error('Screenshot missing');
   Blockbench.writeFile(root+'/preview_'+name+'.png',{savetype:'image',content:data});
   metadata.views[name]={projection:v.ortho?'orthographic':'perspective',camera:p.camera.position.toArray(),
    target:p.controls.target.toArray(),zoom:p.camera.zoom,fov:p.camPers.fov};
  }
  Blockbench.writeFile(root+'/reference/render_conditions.json',{content:JSON.stringify(metadata,null,2)});
 }finally{Canvas.scene.background=oldBackground;p.delete();}
 Preview.selected.loadAnglePreset({projection:'perspective',position:[16,12,28],target,fov:36});
 return {captured:Object.keys(views),temporaryPreviewRemoved:!Preview.all.includes(p),project:Project.name};
})()
