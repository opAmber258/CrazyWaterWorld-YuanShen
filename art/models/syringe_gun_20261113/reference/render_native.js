(async()=>{
 const root=globalThis.SYRINGE_ASSET_ROOT;
 if(Project.geometry_name!=='syringe_gun') throw new Error('Wrong project');
 const box=new THREE.Box3();
 for(const e of Cube.all){e.mesh.updateMatrixWorld(true);e.mesh.geometry.computeBoundingBox();box.union(e.mesh.geometry.boundingBox.clone().applyMatrix4(e.mesh.matrixWorld));}
 const center=box.getCenter(new THREE.Vector3()),size=box.getSize(new THREE.Vector3());
 const zoom=Math.min(1200/(40*size.x),900/(40*size.y))*.78;
 const oldBackground=Canvas.scene.background;
 const metadata={renderer:'Blockbench 5.2.2 native Preview / Three.js',size:[1200,900],background:'#202a2d',model_bounds:{min:box.min.toArray(),max:box.max.toArray()},views:{}};
 const views={front:{offset:[0,0,70],ortho:true},back:{offset:[0,0,-70],ortho:true},side:{offset:[70,0,0],ortho:true},top:{offset:[0,70,.001],ortho:true},oblique:{offset:[18,11,29],ortho:false},rear_oblique:{offset:[-18,11,-29],ortho:false}};
 try{
   Canvas.scene.background=new THREE.Color('#202a2d');
   for(const [name,v] of Object.entries(views)){
     const p=new Preview({id:'syringe_delivery_'+name,offscreen:true});p.renderer.setPixelRatio(1);
     try{
     p.setProjectionMode(v.ortho);
     p.resize(1200,900);
     p.camera.position.copy(center).add(new THREE.Vector3().fromArray(v.offset));p.controls.target.copy(center);
     if(v.ortho){p.camera.zoom=zoom;p.camera.updateProjectionMatrix();}else{p.setFOV(36);}
     p.controls.enableDamping=false;p.controls.update();
     await new Promise(r=>setTimeout(r,90));
     let data;
     p.screenshot({crop:false,width:1200,height:900},s=>data=s);
     if(!data)throw new Error('No screenshot');
     Blockbench.writeFile(root+'/preview_'+name+'.png',{savetype:'image',content:data});
     metadata.views[name]={projection:v.ortho?'orthographic':'perspective',camera:p.camera.position.toArray(),target:p.controls.target.toArray(),zoom:p.camera.zoom,fov:p.camPers.fov};
     }finally{p.delete();}
   }
   Blockbench.writeFile(root+'/reference/render_conditions.json',{content:JSON.stringify(metadata,null,2)});
 }finally{Canvas.scene.background=oldBackground;}
 const live=Preview.selected;
 live.setProjectionMode(false);live.resize();live.setFOV(36);live.camera.position.copy(center).add(new THREE.Vector3(18,11,38));live.controls.target.copy(center);live.controls.update();
 return {captured:Object.keys(views),activeProject:Project.name,temporaryPreviewRemoved:!Preview.all.some(p=>p.id.startsWith('syringe_delivery_'))};
})()
