(async()=>{
 const root=globalThis.ASSET_ROOT;
 if(Project.geometry_name!=='advanced_syringe_gun'||!Project.saved)throw new Error('Wrong or unsaved project');
 const targets=Cube.all.filter(c=>c.name.startsWith('rear_ring_rim_'));
 if(targets.length!==12)throw new Error('Expected twelve rim segments');
 const before=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 Blockbench.writeFile(root+'/reference/live_before_true_circle.bbmodel',{content:JSON.stringify(before,null,2)});
 const cx=-5.7,cy=6.3,radius=2.55,width=.55,depth=1.12;
 Undo.initEdit({elements:targets});
 for(let i=0;i<12;i++){
  const c=targets.find(c=>c.name==='rear_ring_rim_'+i);
  const a=i*Math.PI/6,b=(i+1)*Math.PI/6;
  const p=[cx+radius*Math.cos(a),cy+radius*Math.sin(a)],q=[cx+radius*Math.cos(b),cy+radius*Math.sin(b)];
  const dx=q[0]-p[0],dy=q[1]-p[1],length=Math.hypot(dx,dy);
  c.from.V3_set(p[0]-width/2,p[1]-.025,-depth/2);
  c.to.V3_set(p[0]+width/2,p[1]+length+.025,depth/2);
  c.origin.V3_set(p[0],p[1],0);c.rotation.V3_set(0,0,-Math.atan2(dx,dy)*180/Math.PI);
 }
 Undo.finishEdit('Restore equal-radius circular rear ring');Canvas.updateAll();
 const bb=Codecs.project.compile({raw:true,bitmaps:true,absolute_paths:false});
 const ids=new Set(targets.map(c=>c.uuid));
 const old=new Map(before.elements.map(e=>[e.uuid,e]));
 if(bb.elements.filter(e=>!ids.has(e.uuid)).some(e=>JSON.stringify(e)!==JSON.stringify(old.get(e.uuid))))throw new Error('Non-rim change');
 Blockbench.writeFile(Project.save_path,{content:JSON.stringify(bb,null,2)});
 Blockbench.writeFile(root+'/advanced_syringe_gun.geo.json',{content:Codecs.bedrock.compile()});
 Blockbench.writeFile(root+'/reference/round_input.bbmodel',{content:JSON.stringify(bb,null,2)});
 Project.saved=true;
 const bounds=new THREE.Box3();for(const c of targets){c.mesh.updateWorldMatrix(true,false);bounds.union(new THREE.Box3().setFromObject(c.mesh));}
 const size=bounds.getSize(new THREE.Vector3());
 const report={radius_x:radius,radius_y:radius,diameters:[size.x,size.y],width_height_ratio:size.x/size.y,
  equal_diameters_within_float_error:Math.abs(size.x-size.y)<1e-5,non_rim_elements_unchanged:68,
  center:[cx,cy,0],depth,texture_unchanged:bb.textures[0].source===before.textures[0].source};
 Blockbench.writeFile(root+'/reference/true_circle_check.json',{content:JSON.stringify(report,null,2)});
 Preview.selected.loadAnglePreset({projection:'orthographic',position:[.4,3.4,70],target:[.4,3.4,0]});
 return report;
})()
