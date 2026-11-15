"""Render the opened animated project using Blockbench. Requires Pillow and websocket-client."""
import argparse,base64,io,json,time,urllib.request
from contextlib import closing
from pathlib import Path
from PIL import Image
import websocket
ROOT=Path(__file__).resolve().parents[1]
REF=ROOT/'reference'
def connect(port):
    tabs=json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/json/list'))
    url=next(t['webSocketDebuggerUrl'] for t in tabs if t['type']=='page' and 'blockbench' in t['url'].lower())
    def js(code):
        with closing(websocket.create_connection(url,suppress_origin=True,timeout=45)) as ws:
            ws.send(json.dumps({'id':1,'method':'Runtime.evaluate','params':{'expression':code,'returnByValue':True,'awaitPromise':True}}))
            while True:
                response=json.loads(ws.recv())
                if response.get('id')==1:break
            result=response['result']
            if 'exceptionDetails' in result:raise RuntimeError(result['exceptionDetails'])
            return result['result'].get('value')
    return js
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--port',type=int,required=True)
    js=connect(parser.parse_args().port)
    if js("Animation.all.map(a=>a.name)")!=['animation.deconstructor.work']:raise RuntimeError('Open the animated project first.')
    js("Modes.options.edit.select();unselectAll();Canvas.updateAll();true")
    view='('+(REF/'views.js').read_text(encoding='utf8')+')'
    for direction in ['front','back','side','oblique']:
        uri=js(view+'('+json.dumps(direction)+')')
        (ROOT/f'preview_{direction}.png').write_bytes(base64.b64decode(uri.split(',')[1]))
    js("Modes.options.animate.select();Animation.all[0].select();true")
    render='('+(REF/'preview.js').read_text(encoding='utf8')+')'
    def frame(t):
        uri=js(render+'('+str(t)+')')
        return Image.open(io.BytesIO(base64.b64decode(uri.split(',')[1]))).convert('RGBA')
    frames=[frame(i/50) for i in range(50)]
    # Compare native endpoint images before GIF quantization, including alpha.
    assert frames[0].tobytes()==frame(1).tobytes()
    sheet=Image.new('RGB',(240*5,160*10))
    rgb=[]
    for i,im in enumerate(frames):
        f=Image.new('RGB',im.size,'#343c40');f.paste(im,mask=im.getchannel('A'))
        sheet.paste(f.resize((240,160)),(240*(i%5),160*(i//5)));rgb.append(f)
    palette=sheet.quantize(256,method=Image.Quantize.MEDIANCUT)
    indexed=[f.quantize(palette=palette,dither=Image.Dither.NONE) for f in rgb]
    path=ROOT/'preview_work.gif'
    indexed[0].save(path,save_all=True,append_images=indexed[1:],duration=20,loop=0,optimize=False,disposal=1)
    with Image.open(path) as gif:
        assert gif.n_frames==50 and gif.info['loop']==0
        for i in range(50):
            gif.seek(i);assert gif.info['duration']==20
    # Review at quarter speed through the normal animation mesh update path from the front and above.
    for pos,target in [([0,18.7,69],[0,18.7,4]),([-14,46,64],[0,18.7,3.5])]:
        js("""(()=>{const p=Preview.selected;p.setProjectionMode(false);
            p.controls.target.fromArray(%s);p.camera.position.fromArray(%s);
            p.camera.fov=24;p.camera.updateProjectionMatrix();p.controls.update();return true;})()"""%
            (json.dumps(target),json.dumps(pos)))
        for i in range(49):
            js("Timeline.setTime("+str(i/48)+");Animator.preview();Preview.selected.render();true")
            time.sleep(.083)
    js("Timeline.setTime(0);Animator.preview();true")
    js(view+'("oblique",false)')
    print('50 verified frames, 1000 ms, seamless native endpoints.')
if __name__=='__main__':main()

