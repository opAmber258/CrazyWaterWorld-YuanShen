(async()=>{
 const root=globalThis.SYRINGE_ASSET_ROOT;
 const read=p=>{let x;Blockbench.read([p],{},r=>x=JSON.parse(r[0].content));return x};
 if(Project.geometry_name!=='syringe_gun')throw new Error('Wrong project');
 const report=read(root+'/reference/face_cleanup_audit.json');
 const tex=Texture.all[0].uuid;
 Undo.initEdit({elements:Cube.all});
 for(const e of Cube.all)for(const f of Object.values(e.faces))f.texture=tex;
 Canvas.updateAll();
 const p=new Preview({id:'syringe_face_review',offscreen:true});p.renderer.setPixelRatio(1);
 const old=Canvas.scene.background;Canvas.scene.background=new THREE.Color('#202a2d');
 const views=[[16,12,25],[-16,12,-25]];
 function pixels(pos){
  p.setProjectionMode(false);p.resize(1200,900);p.camera.position.fromArray(pos);p.controls.target.set(.225,3.675,0);p.setFOV(36);p.controls.enableDamping=false;p.controls.update();
  let url;p.screenshot({crop:false,width:1200,height:900},s=>url=s);
  return new Promise(resolve=>{const im=new Image();im.onload=()=>{const c=document.createElement('canvas');c.width=1200;c.height=900;const ctx=c.getContext('2d');ctx.drawImage(im,0,0);resolve(ctx.getImageData(0,0,1200,900).data)};im.src=url});
 }
 const base=[];for(const v of views)base.push(await pixels(v));
 const accepted=[],retained=[];
 try{
  for(const row of (report.geometric_candidates||report.removed_faces)){
   const c=Cube.all.find(e=>e.name===row.element);c.faces[row.face].texture=null;Canvas.updateAll();
   const counts=[];
   for(let n=0;n<views.length;n++){
    const a=await pixels(views[n]);let changed=0;
    for(let i=0;i<a.length;i+=4)if(a[i]!==base[n][i]||a[i+1]!==base[n][i+1]||a[i+2]!==base[n][i+2]||a[i+3]!==base[n][i+3])changed++;
    counts.push(changed);
   }
   if(counts.every(n=>n===0))accepted.push(row);
   else{c.faces[row.face].texture=tex;Canvas.updateAll();retained.push({...row,changed_pixels:counts,reason:'Retained because native perspective raster differs'});}
  }
  Undo.finishEdit('Conservatively remove verified invisible faces');
  const out={candidate_count:report.removed_faces.length,accepted,retained,views,criterion:'Strict RGBA equality at 1200x900 in both perspective views after each incremental removal; full six-view file comparison follows'};
  Blockbench.writeFile(root+'/reference/verified_face_selection.json',{content:JSON.stringify(out,null,2)});
  Project.save_path=root+'/syringe_gun.bbmodel';
  Blockbench.writeFile(Project.save_path,{content:JSON.stringify(Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false}),null,2)});
  Blockbench.writeFile(root+'/syringe_gun.geo.json',{content:Codecs.bedrock.compile()});Project.saved=true;
  return {accepted:accepted.length,retained:retained.length,details:retained};
 }finally{Canvas.scene.background=old;p.delete();}
})()
