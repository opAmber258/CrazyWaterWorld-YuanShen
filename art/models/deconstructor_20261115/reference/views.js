function renderView(view, forFile=true) {
    const p = forFile ? Screencam.NoAAPreview : Preview.selected;
    const target = [0,20,0];
    const poses = {front:[0,20,110],back:[0,20,-110],side:[-110,20,0],oblique:[-49,50,94]};
    if(view==='oblique') {target[0]=-1.35;target[1]=19.3;target[2]=-.72;poses.oblique=[-50.35,49.3,93.28];}
    const ortho = view !== 'oblique';
    p.setProjectionMode(ortho);
    if(forFile) {p.renderer.setPixelRatio(1);p.resize(1200,900);}
    p.controls.target.fromArray(target);
    p.camera.position.fromArray(poses[view]);
    p.camera.up.set(0,1,0);
    if(ortho) p.camera.zoom=p.height/40/48;
    else {p.camera.fov=27;p.camera.aspect=p.width/p.height;}
    p.camera.updateProjectionMatrix();
    p.controls.update();
    p.camera.lookAt(p.controls.target);
    let result;
    Canvas.withoutGizmos(()=>{
        p.render();
        if(forFile) result=p.canvas.toDataURL('image/png');
    });
    return result || true;
}
