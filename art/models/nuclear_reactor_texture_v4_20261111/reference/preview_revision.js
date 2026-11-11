const fs=require('fs'),path=require('path');
const dir=globalThis.REACTOR_ASSET_DIR;
const box=new THREE.Box3().setFromObject(Project.model_3d),center=box.getCenter(new THREE.Vector3());
const p=MediaPreview;
const record={renderer:'Blockbench native MediaPreview, Canvas.withoutGizmos',width:1200,height:900,views:{}};
for(const [name,d] of Object.entries({front:[0,0,1],back:[0,0,-1],side:[1,0,0],oblique:[.65,.28,1]})){
 const ortho=name!=='oblique';p.setProjectionMode(ortho);p.resize(1200,900);
 p.controls.target.copy(center);
 const radius=box.getSize(new THREE.Vector3()).length();
 p.camera.position.copy(center).add(new THREE.Vector3(...d).normalize().multiplyScalar(Math.max(51,radius*1.45)));
 if(ortho){p.camOrtho.zoom=Math.min(.81,29/radius);p.camOrtho.updateProjectionMatrix();}else p.setFOV(36);
 p.controls.update();p.camera.lookAt(center);p.camera.updateMatrixWorld();p.renderer.setClearColor(0,0);
 Canvas.withoutGizmos(()=>{p.render();fs.writeFileSync(path.join(dir,'preview_'+name+'.png'),Buffer.from(p.canvas.toDataURL('image/png').split(',')[1],'base64'));});
 record.views[name]={projection:ortho?'orthographic':'perspective',position:p.camera.position.toArray(),target:center.toArray(),zoom:ortho?p.camOrtho.zoom:null,fov:ortho?null:36};
}
fs.writeFileSync(path.join(dir,'reference','preview_settings.json'),JSON.stringify(record,null,2));
Preview.selected.copyView(p);Preview.selected.render();
return {name:Project.name,cubes:Cube.all.length,bones:Group.all.length,bbox:{min:box.min.toArray(),max:box.max.toArray(),size:box.getSize(new THREE.Vector3()).toArray()}};
