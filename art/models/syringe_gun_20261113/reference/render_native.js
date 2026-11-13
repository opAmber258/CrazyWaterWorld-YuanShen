(async()=>{
 const root=globalThis.SYRINGE_ASSET_ROOT;
 if(Project.geometry_name!=='syringe_gun') throw new Error('Wrong project');
 const p=new Preview({id:'syringe_delivery',offscreen:true});
 p.resize(1200,900);p.renderer.setPixelRatio(1);
 const oldBackground=Canvas.scene.background;
 const metadata={renderer:'Blockbench 5.2.2 native Preview / Three.js',size:[1200,900],background:'#202a2d',views:{}};
 const views={front:{pos:[.225,3.675,70],ortho:true},back:{pos:[.225,3.675,-70],ortho:true},side:{pos:[70,3.675,0],ortho:true},top:{pos:[.225,70,0],ortho:true},oblique:{pos:[16,12,25],ortho:false},rear_oblique:{pos:[-16,12,-25],ortho:false}};
 try{
   Canvas.scene.background=new THREE.Color('#202a2d');
   for(const [name,v] of Object.entries(views)){
     p.setProjectionMode(v.ortho);
     p.resize(1200,900);
     p.camera.position.fromArray(v.pos);p.controls.target.set(.225,3.675,0);
     if(v.ortho){p.camera.zoom=1.38;p.camera.updateProjectionMatrix();}else{p.setFOV(36);}
     p.controls.enableDamping=false;p.controls.update();
     await new Promise(r=>setTimeout(r,90));
     let data;
     p.screenshot({crop:false,width:1200,height:900},s=>data=s);
     if(!data)throw new Error('No screenshot');
     Blockbench.writeFile(root+'/preview_'+name+'.png',{savetype:'image',content:data});
     metadata.views[name]={projection:v.ortho?'orthographic':'perspective',camera:p.camera.position.toArray(),target:p.controls.target.toArray(),zoom:p.camera.zoom,fov:p.camPers.fov};
   }
   Blockbench.writeFile(root+'/reference/render_conditions.json',{content:JSON.stringify(metadata,null,2)});
 }finally{Canvas.scene.background=oldBackground;p.delete();}
 const live=Preview.selected;
 live.loadAnglePreset({projection:'perspective',position:[16,12,25],target:[.225,3.675,0],fov:36});
 return {captured:Object.keys(views),activeProject:Project.name,temporaryPreviewRemoved:!Preview.all.includes(p)};
})()
