function renderCutterFrame(time) {
    Timeline.setTime(time);
    Animator.preview();
    const p=Screencam.NoAAPreview;
    p.setProjectionMode(false);
    p.renderer.setPixelRatio(1);p.resize(720,480);
    p.controls.target.set(0,18.7,3.5);
    p.camera.position.set(-14,46,64);
    p.camera.aspect=1.5;p.camera.fov=22.5;
    p.camera.updateProjectionMatrix();p.controls.update();p.camera.lookAt(p.controls.target);
    let png;
    Canvas.withoutGizmos(()=>{p.render();png=p.canvas.toDataURL('image/png')});
    return png;
}
