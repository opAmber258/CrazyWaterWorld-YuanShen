# 资产验证记录与共用工具

适用所有格式的纯美术资产。交付规则见 [交付约定 §3.2、§6](ASSET_DELIVERY_CONVENTION.md#32-说明性文件)；本文只说明数据格式与操作。Python 3.10+，依赖 Pillow，安装入口为 `tools/requirements.txt`。

## 日常入口

从仓库根运行，资产路径也可指向本地 production 工程：

```powershell
python tools/asset_delivery.py status art/models/<资产目录>
```

默认只输出标识、状态、少量规格和最多 8 个待处理项；`unresolved_count` 保留总数。`--full` 才展开元数据、有效检查状态和原始验收证据。status 不改文件，也不把整个 bbmodel 或内嵌图片输出到上下文。

## 初始化与刷新

```powershell
python tools/asset_delivery.py refresh art/models/<资产目录>
```

只写 `validation.json`。新记录的人工字段由作者在此文件填写一次：

```json
{
  "schema_version": 2,
  "asset_id": "small_pump",
  "display_name_zh": "小型泵",
  "display_name_en": "SmallPump",
  "contributors": {"human": ["贡献者"], "ai": [], "division": "实际分工"},
  "sources": {"zh": "简中 XML 条目标识与值", "en": "英文 XML 条目标识与值"}
}
```

也可用 `--metadata <JSON文件>` 一次性导入这些人工字段；该临时输入不是新的长期清单。后续只编辑 validation 中的人工字段。共用工具管理 `inputs`、`measurements`、`checks`、`status`、`unresolved`、`validator`，并从 contributors 生成 `creation_source`；不要手工复制或修改这些派生字段。

默认跟踪根目录工程、导出、动画、`*_tex.png` 与 `preview_*.png`。非标准位置的交付依赖（例如四状态备用贴图）在同一记录声明 `"tracked_files":{"textures":["reference/screen_states/half.png"]}`；可用分类为 geometry/textures/previews。文件必须位于资产目录内，缺失会阻止完成。不要将无关原作参考图加入交付依赖。

- `inputs`：几何、贴图、预览三组内容摘要。模型像素与几何分开跟踪；说明文字不进入这些摘要。
- `measurements`：从实际工程/导出提取的计数与实际 PNG 尺寸。没有实现的测量不写成已验证；包围盒由原生测量提供，保存在下述验收证据中。
- `acceptance`：明确登记的实际操作证据；包含操作者、时间、工具版本、说明及该项依赖的内容摘要。
- `checks` / `status`：每次 status 重新计算，不能沿用旧文件中的 `complete` 或布尔值。退出码 `0` 表示本工具覆盖的检查与验收记录齐备，`1` 表示未完成/旧格式，`2` 表示输入或操作错误。

## 实际操作后登记验收

先 refresh，再在 Blockbench 对这些文件进行相应检查，最后登记。命令只记录具名声明，不会替你重开、导出、看图或启动 Minecraft；不可在未操作时执行“passed”。

```powershell
python tools/asset_delivery.py attest art/models/<资产目录> --check editor_reopen --by <操作者> --tool "Blockbench 5.2.2" --note "已重开，贴图和结构正常"
python tools/asset_delivery.py attest art/models/<资产目录> --check native_reexport --by <操作者> --tool "Blockbench 5.2.2" --note "原生重导出逐字段比对一致" --details <原生测量JSON>
python tools/asset_delivery.py attest art/models/<资产目录> --check visual_preview --by <操作者> --tool "Blockbench 5.2.2" --note "已核对当前四向预览、投影方式和材质"
```

原生测量 JSON 是实际检查的输入证据，合入 acceptance 后无需在其他文件再抄一份：

```json
{"comparison":"equal","bounds":{"min":[-4,0,-8],"max":[4,8.5,8],"unit":"bb_unit"}}
```

示例数值不代表任何资产的实测值。失败使用 `--result failed`。输入在 refresh 后又发生相关变化时，attest 拒绝登记；先刷新并检查变更后的文件。

| 变化 | 需要更新的验收 |
| --- | --- |
| 几何、UV、分组、导出、动画 | 重开、原生导出比对、视觉预览 |
| 贴图/内嵌像素 | 重开与视觉预览；几何未变时复用原生导出比对 |
| 预览图片 | 视觉预览 |
| README 等说明文字 | 保留以上有效验收，仅复核文档 |

内容不匹配时显示 `stale`，未验收显示 `unverified`；不会为了刷新报告自动填写通过。恢复到相同内容时可复用仍匹配的原始证据。自动检查每次重新读取文件，不依赖旧的成功标记。

## 生成登记条目

资产已推送并验证远端后，在仓库内运行：

```powershell
python tools/asset_delivery.py card art/models/<资产目录> --commit <已推送commit>
```

只打印 Discussion 候选条目，不联网、不发送消息。条目含名称、来源、commit 和定位链接，规格/验收链接到同一提交的 validation；不重复抄计数。预览使用同一 commit 的文件链接，不嵌入浮动 main 图片。命令不能证明 commit 已存在远端，发布者仍须核实。

## 历史资产

旧记录默认只读，不自动批量改写。资产下次修改时，显式备份后迁移人工元数据：

```powershell
python tools/asset_delivery.py refresh art/models/<资产目录> --legacy-backup ../workspace/<资产>_validation_before.json
```

备份必须在资产目录之外、不能覆盖已有文件，并且不提交该本地备份。旧的成功标记不会继承为新证据；只有真实保存了文件摘要的新版验收才可复用。历史 detailed audit 可保留，但正常发布不默认读取它。

## 工具边界

本工具做文件齐备性、PNG 规格、计数、单工程单贴图时的像素一致性和证据失效判断；不实现第二套渲染器、完整模型 schema、旋转/包围盒求解或游戏兼容性验证。多变体贴图匹配、原生导出语义、投影方式与视觉质量由相应验收负责。没有原生比对和原生包围盒证据，不能给出完整交付结论。开发验证：`python -m unittest discover -s tools/tests -p test_asset_delivery.py -v`。
