"""Capture cited local game evidence; optional game directory argument."""
import hashlib,json,sys,xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image
OUT=Path(__file__).resolve().parent.parent;REF=OUT/'reference'
if len(sys.argv)!=2:raise SystemExit('usage: python reference/capture_sources.py <Barotrauma installation>')
game=Path(sys.argv[1]);files={
 'zh':'Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml',
 'en':'Content/Texts/English/EnglishVanilla.xml',
 'definition':'Content/Items/Fabricators/fabricators.xml',
 'atlas':'Content/Items/Fabricators/FabricatorsAndDeconstructors.png'}
def entry(path):
 root=ET.parse(path).getroot();return root.find('.//entityname.fabricator').text
item=next(e for e in ET.parse(game/files['definition']).getroot() if e.get('identifier')=='fabricator')
sprite=item.find('Sprite');x,y,w,h=map(int,sprite.get('sourcerect').split(','))
crop=Image.open(game/files['atlas']).convert('RGBA').crop((x,y,x+w,y+h));crop.save(REF/'original_fabricator_crop.png')
crop.resize((w*2,h*2),Image.Resampling.NEAREST).save(REF/'original_fabricator_enlarged.png')
report={'official_name_zh':entry(game/files['zh']),'official_name_en':entry(game/files['en']),
 'name_key':'entityname.fabricator','source_files':{key:{'relative_path':path,'sha256':hashlib.sha256((game/path).read_bytes()).hexdigest()} for key,path in files.items()},
 'item':item.attrib,'sprite':sprite.attrib,'decorative_sprites':[s.attrib for s in item.findall('DecorativeSprite')],
 'alpha_visible_bbox':crop.getchannel('A').getbbox(),
 'online_reference':'https://barotraumagame.com/wiki/Fabricator_%26_Deconstructor',
 'user_reference':'user_reference.png','style_reference':'art-vault/curated/engine_20261113/preview_oblique.png',
 'style_learning':'低对比灰绿金属、真实百叶层次、按功能组织体块、少量定位磨损；不复制几何、UV 或贴图。',
 'assumptions':{'character_visual_height_px':120,'minecraft_character_visual_height_bb':32,'formula':'K = 0.5 * 32 / 120; width = 336 * K; height = 400 * K','suggested_size_bb':[44.8,53.3333333333],'final_size_bb':[44,52.5,18.7]},
 'artistic_inference':['depth and rear layout','support risers and feet','exposed gantry, spindle and machining bed','static wheel and cable cross-sections','angled crown in three dimensions'],
 'date':'2026-11-14'}
(REF/'source_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({k:report[k] for k in ['official_name_zh','official_name_en','sprite','alpha_visible_bbox']},ensure_ascii=False))
