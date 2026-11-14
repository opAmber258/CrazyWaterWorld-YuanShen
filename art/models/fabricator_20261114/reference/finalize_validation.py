"""Collect recorded checks and hashes; never infer Minecraft compatibility."""
import hashlib,json
from pathlib import Path
OUT=Path(__file__).resolve().parent.parent
def read(p):return json.loads((OUT/p).read_text(encoding='utf8'))
a=read('structure_audit.json');r=read('reference/reopened_project_check.json');rt=read('reference/native_roundtrip.json');src=read('reference/source_report.json')
generic={kind:read(f'reference/generic_{kind}_audit.json') for kind in ['bb','geo']}
assert not a['errors'] and r['exact_reopened_export_match'] and rt['exact_native_reexport']
assert all(not g['errors'] for g in generic.values())
assert src['official_name_zh']=='加工台' and src['official_name_en']=='Fabricator'
files=['fabricator.bbmodel','fabricator.geo.json','fabricator_tex.png','preview_front.png','preview_back.png','preview_side.png','preview_oblique.png','README.md','structure_audit.json']
assert all((OUT/f).exists() for f in files)
preserved=read('reference/human_preservation.json');assert preserved['all_preserved']
v={'display_name_zh':'加工台','display_name_en':'Fabricator','asset':'加工台 Fabricator','identifier':'geometry.fabricator','production_source':'人工 + AI','creation_source':'人工 + AI（TakaraMiyuki + GPT-6 / Codex）','creation_tools':'Codex（基于 GPT-6） / Blockbench 5.2.2 / Python / Pillow / local Electron API',
 'contributors':{'human':['TakaraMiyuki'],'ai':[{'name':'GPT-6','tool':'Codex / Blockbench 5.2.2 / Python / Pillow','role':'初稿几何、像素贴图；保留人工调整后重出几何、四视图和验证'}],'division':'TakaraMiyuki 手动修改最终几何、UV 和像素贴图；AI 保留人工版本并完成交付。会话仅提供 GPT-6 基础模型标识，未提供更细版本号。'},
 'date':'2026-11-14','status':'human-edited static art; authorized direct-main publication; upstream selection pending','blockbench_version':r['blockbench_version'],
 'user_direction':'按原作人物比例还原；用户手动修订并明确授权直推主分支及登记 Discussion #39',
 'metrics':{k:a[k] for k in ['cubes','bones','declared_faces','bbox','texture']},
 'checks':{'four_piece_package':True,'identifier_correct':True,'official_names_separately_verified':True,'visible_blockbench_project_reviewed':True,
 'four_final_native_previews_reviewed':True,'embedded_external_png_identical':True,'all_declared_faces_uv_assigned_and_in_bounds':True,
 'geometry_and_uv_export_equivalent':True,'unique_uv_islands_nonoverlapping':True,'native_geo_import_reexport_exact':rt['exact_native_reexport'],'reopened_bbmodel_export_exact':r['exact_reopened_export_match'],'human_geometry_uv_texture_preserved':preserved['all_preserved']},
 'audit':{'independent_errors':a['errors'],'independent_warnings':a['warnings'],'generic':{k:{'errors':g['errors'],'warnings':g['warnings']} for k,g in generic.items()}},
 'previews':a['previews'],'files_sha256':{f:hashlib.sha256((OUT/f).read_bytes()).hexdigest() for f in files},
 'coordinates':{'front':'+Z','floor':'Y=0','centered_axes':['X','Z']},'source_report':'reference/source_report.json',
 'not_tested':['Minecraft runtime','GeckoLib runtime','placement and collision','lighting and emission','animation and interactions','crafting and power','performance'],
 'repository':{'runtime_code_changed':False,'authorized_direct_main':True,'asset_path':'art/models/fabricator_20261114/','discussion':'https://github.com/opAmber258/CrazyWaterWorld-YuanShen/discussions/39','candidate':'A','classification':'待评选；本地 production 保留'},
 'boundary':'已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。'}
(OUT/'validation.json').write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding='utf8')
print('validation.json complete; errors=0; independent warnings='+str(len(a['warnings'])))
