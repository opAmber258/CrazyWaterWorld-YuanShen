function addCutterAnimation() {
    const spin = new Animation({name:'animation.deconstructor.work',loop:'loop',length:1,snapping:48}).add();
    for (const [name, sign] of [['left_cutters',-1],['right_cutters',1]]) {
        const group=Group.all.find(g=>g.name===name);
        if (!group) throw Error('Missing cutter group '+name);
        const track=spin.getBoneAnimator(group);
        for(let quarter=0;quarter<=4;quarter++) {
            track.addKeyframe({channel:'rotation',time:quarter/4,interpolation:'linear',
                data_points:[{x:0,y:0,z:sign*quarter*90}]});
        }
    }
    spin.setLength(1);
    return spin.getUndoCopy();
}
