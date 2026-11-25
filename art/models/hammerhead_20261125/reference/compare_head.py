"""Compare the terminal rim using matching native cameras."""
import argparse
from pathlib import Path

from PIL import Image,ImageDraw,ImageFont

def compose(before,after,output,font):
    rows=(("head_turn","左前斜视",(174,160,1120,891)),
          ("head_top","俯视", (215,0,1010,697)),
          ("head_profile","侧视", (157,108,1160,881)))
    width=700
    sheet=Image.new("RGB",(2*width+64,1680),"#1a252c")
    pen=ImageDraw.Draw(sheet)
    major=ImageFont.truetype(font,26)
    minor=ImageFont.truetype(font,19)
    for i,(label,folder) in enumerate((("上一版 · 锤面前鼓",before),("锤面外沿向四周扩张",after))):
        x=22+i*(width+20)
        pen.text((x+12,20),label,font=major,fill="#dce8e8")
        for row,(view,title,crop) in enumerate(rows):
            y=76+row*531
            pen.line((x,y,x+width,y),fill="#385058",width=1)
            pen.text((x+12,y+8),title,font=minor,fill="#a5c6c2")
            im=Image.open(folder/f"preview_{view}.png").convert("RGB").crop(crop)
            im.thumbnail((width-20,471),Image.Resampling.LANCZOS)
            sheet.paste(im,(x+(width-im.width)//2,y+36))
    sheet.save(output,optimize=True)

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("before",type=Path)
    p.add_argument("after",type=Path)
    p.add_argument("output",type=Path)
    p.add_argument("--font",default="C:/Windows/Fonts/msyh.ttc")
    a=p.parse_args()
    compose(a.before,a.after,a.output,a.font)



