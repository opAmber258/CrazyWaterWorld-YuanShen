"""Native right/top/bottom inspection renders of the current frozen geometry."""
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from bb_mcp_client import BlockbenchMCP
HERE=Path(__file__).resolve().parent;OUT=HERE.parent.parent
c=BlockbenchMCP()
code=r'''(()=>{
const fs=require('fs'),dir=DIR,p=Preview.selected,scene=Canvas.scene;
Timeline.pause();Animation.all.forEach(a=>a.playing=false);Animator.showDefaultPose();Modes.options.edit.select();
const roots=new Set();for(const c of Cube.all){let r=c.mesh;while(r&&r.parent&&r.parent!==scene)r=r.parent;if(r)roots.add(r);}
const aux=scene.children.filter(o=>o.name!=='lights'&&!roots.has(o)),vis=aux.map(o=>o.visible);
const axes=[];scene.traverse(o=>{if(new Set(['X','Y','Z','NX','NY','NZ','XY','YZ','XZ','XYZE','E']).has(o.name)||o.name.startsWith('axis_line')||o.type==='GridHelper')axes.push(o);});const avis=axes.map(o=>o.visible);
const bg=scene.background,clear=p.renderer.getClearColor(new THREE.Color()).getHex(),alpha=p.renderer.getClearAlpha(),ratio=p.renderer.getPixelRatio(),oldSize=[p.renderer.domElement.width,p.renderer.domElement.height];
const box=new THREE.Box3();Cube.all.forEach(c=>{c.mesh.updateMatrixWorld(true);box.expandByObject(c.mesh);});const center=box.getCenter(new THREE.Vector3()),size=box.getSize(new THREE.Vector3());
try{
 scene.background=null;aux.forEach(o=>o.visible=false);axes.forEach(o=>o.visible=false);
 for(const view of ['detail']){
  const target=new THREE.Vector3(22,34,1),camera=new THREE.PerspectiveCamera(36,4/3,.1,500);
  camera.position.copy(target).add(new THREE.Vector3(51,36,53));camera.lookAt(target);camera.updateProjectionMatrix();
  p.renderer.setPixelRatio(1);p.renderer.setSize(1200,900,false);p.renderer.setClearColor(0x000000,0);p.renderer.clear(true,true,true);
  Canvas.withoutGizmos(()=>p.renderer.render(scene,camera));
  fs.writeFileSync(dir+'/reference/physical_panels_20261115/preview_'+view+'.png',Buffer.from(p.renderer.domElement.toDataURL('image/png').split(',')[1],'base64'));
 }
}finally{scene.background=bg;aux.forEach((o,i)=>o.visible=vis[i]);axes.forEach((o,i)=>o.visible=avis[i]);p.renderer.setPixelRatio(ratio);p.renderer.setSize(oldSize[0]/ratio,oldSize[1]/ratio,false);p.renderer.setClearColor(clear,alpha);p.render();}
return {views:['detail'],bbox:{min:box.min.toArray(),max:box.max.toArray(),size:size.toArray()}};
})()'''.replace('DIR',json.dumps(OUT.as_posix()))
print(c.eval_js(code))
