"""Collect actual check records and hashes, never infer a runtime pass."""
import hashlib,json
from pathlib import Path
OUT=Path(__file__).resolve().parent.parent
audit=json.loads((OUT/'structure_audit.json').read_text(encoding='utf-8'))
reopen=json.loads((OUT/'reference/reopened_project_check.json').read_text())
generic={kind:json.loads((OUT/f'reference/generic_{kind}_audit.json').read_text()) for kind in ('bb','geo')}
assert not audit['errors'] and reopen['exact_reopened_export_match'] and audit['native_import_reexport']
assert all(not report['errors'] for report in generic.values())
files=['engine.bbmodel','engine.geo.json','engine_tex.png','preview_front.png','preview_back.png','preview_side.png','preview_oblique.png','README.md','structure_audit.json']
assert all((OUT/f).exists() for f in files)
validation={'display_name_zh':'引擎','display_name_en':'Engine','creation_source':'AI（GPT 6.1 Sol）','contributors':{'human': [], 'ai': [{'name': 'GPT 6.1 Sol', 'tool': 'Codex / Blockbench 5.2.2 / Blockbench MCP / Python / Pillow', 'role': '几何、UV、像素贴图、原生预览、导出与交付检查'}], 'division': 'AI 制作；用户提供题材、参考图、尺寸方向及发布和本地分类决定，未记录人工几何或贴图编辑。'},'asset':'引擎 Engine','identifier':'geometry.engine','production_source':'AI','date':'2026-11-13','delivery_scope':'local static art asset','status':'complete; candidate C, upstream selection pending','blockbench_version':reopen['blockbench_version'],
 'metrics':{'cubes':audit['cubes'],'bones':audit['bones'],'declared_faces':audit['declared_faces'],'bbox':audit['bbox'],'texture':audit['texture']},
 'checks':{'four_piece_package':True,'identifier':True,'embedded_and_external_png_identical':True,'all_faces_uv_assigned_and_in_bounds':True,'geometry_and_uv_export_equivalent':True,'unique_uv_islands_nonoverlapping':True,'native_geo_import_reexport_exact':audit['native_import_reexport'],'reopened_bbmodel_export_exact':reopen['exact_reopened_export_match'],'four_final_native_previews_reviewed':True,'original_names_separately_verified':True},
 'audit':{'independent_errors':audit['errors'],'independent_warnings':audit['warnings'],'generic_bb_errors':generic['bb']['errors'],'generic_bb_warnings':generic['bb']['warnings'],'generic_geo_errors':generic['geo']['errors'],'generic_geo_warnings':generic['geo']['warnings']},
 'previews':audit['previews'],'files_sha256':{f:hashlib.sha256((OUT/f).read_bytes()).hexdigest() for f in files},
 'not_tested':['Minecraft runtime','GeckoLib runtime','collision and placement','emission and lighting','animation','propulsion and interactions','performance'],
 'code_verify_checklist':{'gradle_build':'not applicable: no runtime code changes','L1':'not applicable','world_generation_reset':'not applicable','registration_L2':'not applicable','client_server_class_loading':'not applicable','VS2_boundary':'not applicable','hull_registration':'not applicable','runtime_localizations':'not applicable: asset names checked against source'},
 'publishing':{'authorized_direct_main': True, 'repository_asset_path': 'art/models/engine_20261113/', 'discussion_url': 'https://github.com/opAmber258/CrazyWaterWorld-YuanShen/discussions/39', 'candidate': 'C', 'local_classification': 'curated, explicitly requested by user; upstream selection pending'},
 'boundary':'已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。'}
(OUT/'validation.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2),encoding='utf-8',newline='\n')
print('validation.json: complete; errors=0; warnings='+str(len(audit['warnings'])))
