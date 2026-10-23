# 今天吃什么：决策体验与职责拆分

## 边界

本轮基于 main `e1690fb4ef4a9da76bc5302bc9ce90bf91c820a2`，渐进提取，不替换数据存储。

- `DecisionState`：双模式、选店、换批、动画、结果消费的屏幕状态；保存导航检查点，重建不恢复半截动画或自动重放结果。
- `DecisionCandidates`：动画开始冻结候选与结果映射，空集不产生结果。
- `MealEligibility`：预算、近期吃过与当天跳过的纯规则，ViewModel 原签名保留，旧行为逐项对照。
- `DecisionRules`：规则编辑组件；转盘页只负责展示与事件衔接。自定义预算拒绝负数和非有限数。
- `MealHistory`：年月日身份、排序、月统计；不再按展示文本分组，避免跨年合并。
- `AppDataStore` / `DataJson` / 备份格式不变。没有数据迁移、清库、应用 ID、版本号、正式签名更改。

## 与导入 PR #37 协作

PR #37 head `5fff5f473949c27337aa0651e9ffb5649d34334b` 引入抖省省分享/OCR/团购模型。本轮不修改它的分支、Manifest、Model、导入组件或店铺业务流程。ViewModel 仅将既有 `blockReason` 函数委托到纯规则；其调用与结果不变。最终以隔离工作区检查组合编译及测试，不能以单独主线通过代替集成验收。

## 设计依据与取舍

调研日期：2026-10-19。

- [Mealie](https://github.com/mealie-recipes/mealie)，[功能说明](https://mealie.io/documentation/getting-started/features/)：将浏览、选择和记录分开，借鉴清晰的信息层级与食物集合组织。其项目为 AGPL-3.0；本轮不复制源码、图标或图片，不引入服务端/账号/菜谱数据库。
- [Tandoor Recipes](https://github.com/TandoorRecipes/recipes)：参考导入之后再整理和确认的流程，适用于现有 PR #37；[license](https://github.com/TandoorRecipes/recipes/blob/develop/LICENSE.md) 为 AGPL-3.0 加 Commons Clause，不将其作为可直接复制的宽松许可代码。
- 保留既有 Compose / Material 3 / 站酷快乐体（OFL），无新增生产依赖。只使用自己的布局与文案。此应用是熟悉线下小店的决策工具，不扩成食谱网站或点餐交易平台。

## 界面

温暖纸色背景、低饱和玫瑰色、左对齐标题与留白、轻描边卡片、统一分段选择。首页压缩为短标题与可选数量、一行偏好摘要、模式、决策卡片、直接选店/候选清单。320dp/1.3倍字体首屏保留抽取入口。记账页以月度摘要和日期组呈现，保留温柔的个人语气。清空记录收进右上更多菜单，并保留原有的二次确认，不新增或自动执行删除。

## 验证入口

普通逻辑测试：`./gradlew testDebugUnitTest`。

主机截图是实际 Compose 渲染，使用合成店铺/菜单/记录，不含用户数据；不等同于模拟器或真机验收。运行方法：在可丢弃 checkout 执行 `python3 tools/ui-snapshots/prepare.py /path/to/checkout`，再跑 `./gradlew testDebugUnitTest --tests '*MealUiSnapshotTest'`。测试工具只加入该临时 checkout，无生产依赖。产物在 `app/build/outputs/meal-ui/`。

正式签名不在此云工作副本中，因此本轮不声称完成正式签名覆盖升级。

## 追加：餐食滚轮与风格主题

按后续设计要求，选择界面改为单列三行老虎机式滚轮。仅中间一行代表所选店铺/餐食；邻行只是预览，没有金额下注、赔率、支付或组合奖励。

- 均匀随机索引在开始时确定，候选内容同时冻结；滚轮动画只是结果的展示，不参与结果计算。系统动画比例为零时立即定位并给出同一结果。离开页面取消当前操作，不写用餐记录。
- 奶油食堂使用纸纹、莓红、圆角与原有手写标题；午夜小馆使用墨蓝、铜金、衬线标题和柔光；薄荷街角使用网格、近直角卡片与无衬线字。旧 strawberry/lavender/mint/peach ID 保持可读，peach 保留奶油风格变体。
- 暗色主题将所有页面的硬编码白色容器改用统一 surface，保证设置、历史、店铺和结果弹窗的可读性。没有添加生产依赖。
- 原 LuckyWheel 数学与旧测试暂保留兼容，本次活动页面不再调用转盘。测试覆盖滚轮终点映射、系统零动画、离开取消、长文本和多风格渲染。
