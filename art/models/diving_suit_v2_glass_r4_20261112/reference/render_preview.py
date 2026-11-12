"""Targeted native edits and reversible offscreen previews of the glass candidate."""
from pathlib import Path
import argparse,base64,json,time
from bb_client import Client,structured
R=Path(__file__).resolve().parent;D=R.parent;NAME='diving_suit_v2_glass_r4'
def write(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding='utf-8')
def js(v):return json.dumps(v,ensure_ascii=True).replace('/',r'\u002f')
ap=argparse.ArgumentParser();ap.add_argument('action',choices=['render']);ap.add_argument('--url',default='http://127.0.0.1:3015/bb-mcp');a=ap.parse_args()
with Client(a.url,timeout=55) as c:
 def ev(s):return structured(c.tool('risky_eval',{'code':s}))
 if a.action=='render':
  active=structured(c.tool('get_project_info',{}))['project']
  assert active['name']==NAME,'Open this candidate first'
  out=structured(c.tool('export_model',{'codec_id':'bedrock','max_content_length':2000000}))
  assert json.loads(out['content'])==json.loads((D/(NAME+'.geo.json')).read_text(encoding='utf-8')),'Opened geometry differs; preserve manual edits and re-export first'
  tex=ev('(()=>({source:Texture.all[0].getBase64()}))()')['source']
  from PIL import Image
  import io
  assert Image.open(io.BytesIO(base64.b64decode(tex.split(',')[-1]))).convert('RGBA').tobytes()==Image.open(D/(NAME+'_tex.png')).convert('RGBA').tobytes(),'Opened texture differs; preserve manual edits and re-export first'
  write(R/'live_state.json',{'uuid':active['uuid']})
 def guard():
  p=structured(c.tool('get_project_info',{}))['project'];state=json.loads((R/'live_state.json').read_text())
  assert p['uuid']==state['uuid'] and p['name']==NAME,'Project changed; preserving user state'
 if a.action=='render':
  guard();view='diving_suit_glass_check'
  state=ev('(()=>({selection:Cube.selected.map(e=>e.uuid),visor:Cube.all.find(e=>e.name==="translucent_visor").visibility,dummy:Group.all.find(g=>g.name==="_preview_player_head_DO_NOT_EXPORT").visibility,dummy_cube:Cube.all.find(e=>e.name==="_preview_generic_player_head").visibility,rot:Group.all.map(g=>({uuid:g.uuid,rotation:g.mesh.rotation.toArray()}))}))()')
  ev('(()=>{unselectAll();return true;})()')
  ev('(()=>{const source=Texture.all[0].getMaterial();const opaque=source.clone(),glass=source.clone();opaque.map=source.map;glass.map=source.map;opaque.uniforms.map.value=source.uniforms.map.value;glass.uniforms.map.value=source.uniforms.map.value;opaque.transparent=false;opaque.depthWrite=true;glass.transparent=true;glass.depthWrite=false;globalThis.viewportR4Capture={opaque,glass,meshes:Cube.all.map(e=>({uuid:e.uuid,material:e.mesh.material,order:e.mesh.renderOrder}))};for(const e of Cube.all){e.mesh.material=e.name==="translucent_visor"?glass:opaque;e.mesh.renderOrder=e.name==="translucent_visor"?10:0;}return true;})()')
  c.tool('create_offscreen_view',{'id':view,'width':1200,'height':900,'antialias':True,'copy_view':'none'})
  records=[]
  try:
   for label,dummy,pose in [('asset',False,None),('empty',False,None),('aperture',False,None),('worn',True,None),('turn',True,[0,35,0])]:
    folder=D if label=='asset' else R/label;folder.mkdir(parents=True,exist_ok=True)
    ev('(()=>{const g=Group.all.find(g=>g.name==="_preview_player_head_DO_NOT_EXPORT");g.visibility='+js(dummy)+';g.children[0].visibility='+js(dummy)+';Cube.all.find(e=>e.name==="translucent_visor").visibility='+js(state['visor'] if label!='aperture' else False)+';Canvas.updateVisibility();'+('const h=Group.all.find(g=>g.name==="armorHead");h.mesh.rotation.set(0,Math.degToRad(35),0);h.mesh.updateMatrixWorld(true);' if pose else '')+'return true;})()')
    cams=[('front',[0,17.5,-90],[0,17.5,0],'orthographic',.50,32),('back',[0,17.5,90],[0,17.5,0],'orthographic',.50,32),('side',[90,17.5,0],[0,17.5,0],'orthographic',.50,32),('oblique',[44,34,-74],[0,17.5,0],'perspective',.50,32)] if label=='asset' else [('front',[0,28.5,-90],[0,28.5,0],'orthographic',1.2,25),('oblique',[28,38,-55],[0,28.5,0],'perspective',1.2,22),('side',[90,28.5,0],[0,28.5,0],'orthographic',1.2,25)]
    if label=='turn':cams=cams[:2]
    if label=='empty':cams=[('front',[0,28.5,-90],[0,28.5,0],'orthographic',1.2,25),('lower_back',[18,13,40],[0,25,0],'perspective',1.2,22),('lower_front',[24,12,-45],[0,25,0],'perspective',1.2,22),('rear_oblique',[28,33,50],[0,28.5,0],'perspective',1.2,24),('upper_back',[26,43,48],[0,29,0],'perspective',1.2,24)]
    if label=='worn':cams.append(('rear_oblique',[28,33,50],[0,28.5,0],'perspective',1.2,24))
    if label=='aperture':cams=cams[:2]
    for name,pos,target,proj,zoom,fov in cams:
     guard();args={'view':view,'position':pos,'target':target,'projection':proj,'zoom':zoom,'fov':fov,'format':'png'}
     c.tool('set_camera_angle',args)
     time.sleep(.08)
     res=c.tool('set_camera_angle',args);img=next(x for x in res['content'] if x['type']=='image')
     (folder/('preview_'+name+'.png')).write_bytes(base64.b64decode(img['data']));records.append({'label':label,'name':name,'camera':args})
  finally:
   ev('(()=>{const s='+js(state)+';const dummy=Group.all.find(g=>g.name==="_preview_player_head_DO_NOT_EXPORT");dummy.visibility=s.dummy;dummy.children[0].visibility=s.dummy_cube;Cube.all.find(e=>e.name==="translucent_visor").visibility=s.visor;for(const x of s.rot){const g=Group.all.find(g=>g.uuid===x.uuid);g.mesh.rotation.fromArray(x.rotation);g.mesh.updateMatrixWorld(true);}Canvas.updateVisibility();for(const id of s.selection){const e=Cube.all.find(e=>e.uuid===id);if(e)e.selectLow();}return true;})()')
   ev('(()=>{for(const x of viewportR4Capture.meshes){const e=Cube.all.find(e=>e.uuid===x.uuid);e.mesh.material=x.material;e.mesh.renderOrder=x.order;}viewportR4Capture.opaque.dispose();viewportR4Capture.glass.dispose();delete globalThis.viewportR4Capture;return true;})()')
   try:c.tool('delete_offscreen_view',{'view':view})
   except RuntimeError as ex:
    if 'onContextMenu is not defined' not in str(ex):raise
    assert not ev('(()=>({exists:Preview.all.some(p=>p.id==="mcp_offscreen_diving_suit_glass_check")}))()')['exists']
  write(R/'capture.json',{'source':'Blockbench native offscreen renderer','preview_materials':'Temporary native material clones: opaque/cutout shell and player, translucent glass with depth test and no depth write, glass drawn last. Original materials restored.','restored':True,'views':records});print('Captured asset, worn, turn previews')
