"""Extract original-game reference only; the delivery atlas is independently drawn.

Usage: python reference/capture_sources.py --game-root <Barotrauma directory>
"""
import argparse
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

def main():
    p=argparse.ArgumentParser();p.add_argument('--game-root',type=Path,required=True);a=p.parse_args()
    game=a.game_root;ref=Path(__file__).resolve().parent
    item=next(x for x in ET.parse(game/'Content/Items/Diving/divinggear.xml').getroot().iter('Item') if x.get('identifier')=='underwaterscooter')
    sprite=item.find('Sprite');source=game/'Content/Items/Diving'/sprite.get('texture')
    x,y,w,h=map(int,sprite.get('sourcerect').split(','));im=Image.open(source).crop((x,y,x+w,y+h));im.save(ref/'underwater_scooter_source_sprite.png');im.resize((w*6,h*6),Image.Resampling.NEAREST).save(ref/'underwater_scooter_source_sprite_6x.png')
    icon=item.find('InventoryIcon');x,y,w,h=map(int,icon.get('sourcerect').split(','));Image.open(game/icon.get('texture')).crop((x,y,x+w,y+h)).save(ref/'underwater_scooter_source_inventory.png')
    entry='entityname.underwaterscooter';names={}
    for lang in ['SimplifiedChinese','English']:
        file=f'Content/Texts/{lang}/{lang}Vanilla.xml';root=ET.parse(game/file).getroot();node=next(x for x in root.iter() if x.tag==entry)
        names[lang]={'file':file,'key':entry,'value':node.text}
    evidence={'item_identifier':'underwaterscooter','names':names,'item_definition':'Content/Items/Diving/divinggear.xml','scale':item.get('Scale'),'sprite':sprite.attrib,'inventory':icon.attrib,
              'wiki':'https://barotraumagame.com/wiki/Underwater_Scooter','reference_use':'Original sprite visual reference only. New geometry and pixel atlas authored independently.','source_sha256':hashlib.sha256(source.read_bytes()).hexdigest()}
    (ref/'source_evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'source_crop':[140,102],'names_verified':list(names)},ensure_ascii=True))
if __name__=='__main__':main()
