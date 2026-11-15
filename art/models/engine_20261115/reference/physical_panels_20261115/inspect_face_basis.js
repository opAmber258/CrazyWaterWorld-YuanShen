(()=>{
const c=Cube.all.find(c=>c.name==='crown'),g=c.mesh.geometry;
return {faces:Object.fromEntries(Object.entries(c.faces).map(([k,f])=>[k,{uv:f.uv,rotation:f.rotation}])),groups:g.groups,attributes:{position:Array.from(g.attributes.position.array),uv:Array.from(g.attributes.uv.array)},indices:g.index?Array.from(g.index.array):null,meshPosition:c.mesh.position.toArray(),meshRotation:c.mesh.rotation.toArray()};
})()
