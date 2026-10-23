# 餐食选择机：实现与核对范围

默认先应用本地现实条件，再从所有符合条件的餐食中抽取一次。三个卷轴展示同一个冻结的店铺、菜品和价格组合，不独立拼接结果。单候选直接显示；动画取消不保存，系统动画比例为零时直接停轮。结果由用户确认后只记录一次。

## 条件与数据

- 预算是已录入菜品或套餐价上限，不代表人均或整桌费用。团购条件和实际付款价需打开店铺页面核对。未知价格明确提示，可选择是否纳入有预算的候选。
- 最近吃过依据现有店名、菜名和历史时间匹配；重命名可能影响识别。无候选时逐项调整，不自动放宽。
- 步行分钟为用户可选记录的估计，出发地变化后需要重新核对。未填店铺仍可使用，也可明确排除未知。地址不能推断真实距离或营业状态。
- 店铺和备份增加可缺省字段，既有菜单、历史、应用 ID 保留。原先选店再选菜模式保留在高级偏好。

## 路线通路

保留现有系统地图地址查询入口，让用户核对真实路线。高德导航 URI 要求目的地经纬度，当前数据模型没有，因此不能把地址假装坐标。Google Maps URL 支持地址形式的 walking destination，但境内可用性不保证，此轮不新增默认外部请求或定位权限。

- https://lbs.amap.com/api/uri-api/guide/travel/route
- https://developers.google.com/maps/documentation/urls/get-started

## 设计参考与许可

- PixiJS 官方 Slots 示例：学习裁切卷轴、不同速度与停轮，不引入 WebView 或复制示例资产。https://pixijs.com/8.x/examples/advanced/slots/
- pixi-reels（MIT，Spine 另有许可）及 pixi-slot-machine（MIT 最小示例）：仅参考状态模型，不复制运行时或商标。https://github.com/schmooky/pixi-reels 、 https://github.com/weyoss/pixi-slot-machine
- Chicken Tender：先收敛可接受选择，再结束犹豫的产品原则。当前没有联网协商。https://www.chickentenderapp.com/

机身、灯牌、拉杆、鼓轮与出票口均为原创 Compose 绘制，无真钱、下注、付费或购买路径。三种既有主题 ID 向后兼容；草图只作设计评审，最终需实际渲染与交互测试，不把草图计为应用截图。

## 独立组合：截图报价可靠性

组合来源是 PR37 的导入实现和 PR50 的餐食选择机及更新门禁。新增价格证据、来源平台、实际导入时间、可未知的观察日期；同名报价不自动覆盖，同平台且真实商品标识一致的条目也仅提示，由用户查看旧新差异后选择替换，保存时复核旧值快照。多金额角色不明确时不按大小猜价，保留候选金额让用户填本次适用价；未确认条件价不参与有效预算或实付记录。

默认只保存已核对字段及条件；识别原文供预览，可编辑去除无关隐私，显式选择后才长期存本机并进入备份。原始图片仍是识别缓存，确认或取消后清理；App 内裁剪与自选图片证据留存尚未实现，不宣称具备。

跨类别交互参考：GnuCash 导入匹配器的人工复核覆盖；Paperless-ngx 的完全重复与疑似重复区别；OSS Document Scanner 的裁剪/原图对照。仅参考流程，不复制 GPL/框架源码或资产。
- https://www.gnucash.org/docs/v5/C/gnucash-manual/trans-import.html
- https://docs.paperless-ngx.com/usage/#duplicate-documents
- https://github.com/ossappscollective/OSS-DocumentScanner

继续使用项目已有 bundled 中文 ML Kit，不引入新 OCR 框架。端侧文字识别不等同于 SDK 绝无性能遥测；不宣传零遥测，不新增默认上传用户图片。
- https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- https://developers.google.com/ml-kit/terms

### 组合候选基线与验收边界

- PR37 原作者导入功能：`5fff5f473949c27337aa0651e9ffb5649d34334b`。本分支保留其贡献及来源，不修改作者分支。
- PR50 机身 / 主题 / 安全更新门禁：`68efb89d2a3e2f11aa8c37129bc81dcdd6c3f59d`。其 CI 单元测试与 debug/release 构建成功，Actions artifact 上传因存储配额失败。
- 两者最初组合本地 134 条普通测试和 debug 构建通过。本次可靠性实现另行通过 160/160 普通测试、debug 与 release 构建；不混用两轮计数。
- 独立 draft PR51 的 5e4554f6 远端 CI 单元测试与双包构建成功，只有 artifact 配额导致上传失败。新增导入预览 host 2/2 通过，覆盖编辑金额、无效输入、显式提交、默认不留原文和窄屏未确认金额。截图本地已验，host Compose/native graphics 不等于真机键盘或分享验收。正式源、正式签名覆盖升级和系统安装授权仍待单独验收。
