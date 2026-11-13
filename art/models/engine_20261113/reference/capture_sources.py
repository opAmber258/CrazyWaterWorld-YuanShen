"""Capture source evidence: python reference/capture_sources.py GAME_INSTALL_ROOT"""
import hashlib,json,sys,xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image
root=Path(sys.argv[1]);out=Path(__file__).resolve().parent
paths={'definition':'Content/Items/Engine/engine.xml','atlas':'Content/Items/Engine/Engines.png','chinese':'Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml','english':'Content/Texts/English/EnglishVanilla.xml'}
item=next(e for e in ET.parse(root/paths['definition']).getroot().iter('Item') if e.attrib.get('identifier')=='engine')
sprite=item.find('Sprite')
entries={}
for lang in ['chinese','english']:
    element=ET.parse(root/paths[lang]).getroot().find('.//entityname.engine')
    entries[lang]={'key':'entityname.engine','value':element.text,'file':paths[lang]}
report={'identifier':'engine','scale':float(item.attrib['Scale']),'sprite':sprite.attrib,'localization':entries,'source_sha256':{key:hashlib.sha256((root/path).read_bytes()).hexdigest() for key,path in paths.items()},'paths_relative_to_game_root':paths,'proportion_assumptions':{'reference_character_pixels':120,'minecraft_character_bb':32,'k_bb_per_pixel':.5*32/120},'depth_and_back':'artistic inference; not present in original 2D sprite'}
im=Image.open(root/paths['atlas']).crop((0,0,659,317));im.save(out/'original_engine_crop.png')
report['sprite_alpha_bbox']=im.getbbox()
(out/'source_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
print(json.dumps(entries,ensure_ascii=False))
