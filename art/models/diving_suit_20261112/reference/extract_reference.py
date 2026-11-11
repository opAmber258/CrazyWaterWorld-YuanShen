"""Extract small, attributed study crops and bilingual XML facts from a local game.
Usage: python reference/extract_reference.py --barotrauma PATH_TO_GAME
Reference pixels are never incorporated in the delivered texture.
"""
from pathlib import Path
import argparse,json,xml.etree.ElementTree as ET
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--barotrauma',type=Path,required=True);a=p.parse_args()
out=Path(__file__).resolve().parent
source=a.barotrauma/'Content/Items/Diving/divinggear.xml'
root=ET.parse(source).getroot();item=next(x for x in root.iter('Item') if x.get('identifier')=='divingsuit')
names={}
for language in ['SimplifiedChinese','English']:
    rel=f'Content/Texts/{language}/{language}Vanilla.xml';texts=ET.parse(a.barotrauma/rel).getroot()
    names[language]={'file':rel,'tag':'entityname.divingsuit','value':texts.find('entityname.divingsuit').text}
helmet=item.find('Wearable').find('sprite');icon=item.find('InventoryIcon')
for label,element in [('official_helmet',helmet),('official_inventory_icon',icon)]:
    x,y,w,h=map(int,element.get('sourcerect').split(','));im=Image.open(a.barotrauma/element.get('texture'));im.crop((x,y,x+w,y+h)).save(out/(label+'.png'))
report={'names':names,'item_file':'Content/Items/Diving/divinggear.xml','identifier':'divingsuit','item_scale':item.get('scale'),'inventory_icon':icon.attrib,'helmet_wearable':helmet.attrib,'body_wearables':[x.attrib for x in item.find('Wearable').findall('sprite')[1:]],'usage':'Visual research only; geometry and atlas authored from scratch. Item scale is not used as Minecraft player scaling.'}
(out/'official_source.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'names':names,'crops':2},ensure_ascii=True))
