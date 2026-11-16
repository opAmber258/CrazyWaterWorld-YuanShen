"""Readable diagnostic contact sheet from existing native animation frames."""
from pathlib import Path
from PIL import Image,ImageDraw
REF=Path(__file__).resolve().parent
sheet=Image.new('RGB',(960,640),'#151c1b');draw=ImageDraw.Draw(sheet)
jobs=[('rotor',0),('rotor',2),('rotor',4),('rotor',7),('pistons',0),('pistons',7),('pistons',15),('pistons',22)]
for j,(name,i) in enumerate(jobs):
    source=Image.open(REF/'animation_frames'/f'{name}_{i:03}.png').convert('RGB').resize((240,300))
    sheet.paste(source,((j%4)*240,(j//4)*320));draw.text(((j%4)*240+8,(j//4)*320+302),f'{name} t={i/25:.2f}s',fill='white')
sheet.save(REF/'motion_contact_sheet.png')
