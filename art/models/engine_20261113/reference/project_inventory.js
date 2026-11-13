JSON.stringify({projects:ModelProject.all.map(p=>({name:p.name,path:p.save_path,saved:p.saved})),active:Project.name})
