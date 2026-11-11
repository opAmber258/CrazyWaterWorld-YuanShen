"""Downsample the four native previews for normal-size visual inspection."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parent.parent
sheet=Image.new('RGB',(720,600),'#303A3C');draw=ImageDraw.Draw(sheet)
font=ImageFont.load_default(size=16)
for i,view in enumerate(('front','oblique','side','back')):
 im=Image.open(ROOT/f'preview_{view}.png').convert('RGBA').resize((360,270),Image.Resampling.NEAREST)
 x,y=(i%2)*360,(i//2)*300
 sheet.paste(im,(x,y+24),im);draw.text((x+12,y+4),view,fill='#DFE1D8',font=font)
sheet.save(ROOT/'reference/readability_review.png')
