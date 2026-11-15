JSON.stringify({
 buildFile:Animator.buildFile.toString().slice(0,2500),
 preview:Animator.preview.toString().slice(0,2200),
 stack:Animator.stackAnimations.toString().slice(0,1200),
 loadFile:Animator.loadFile.toString().slice(0,1500),
 boneAnimator:Animation.selected?Object.getOwnPropertyNames(Object.getPrototypeOf(Animation.selected.getBoneAnimator(Group.all.find(g=>g.name==='rotor')))):[],
 groups:Group.all.filter(g=>g.name==='rotor'||g.name.includes('moving')).map(g=>({name:g.name,origin:g.origin,mesh_position:g.mesh.position.toArray(),mesh_rotation:g.mesh.rotation.toArray()}))
})
