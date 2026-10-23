# 今天吃什么：核心流程重构切片

本文件记录 PR54 最初的核心流程切片与验收。该切片及 PR52 菜名去重修复已在 PR56 整合进后续 v2.4 交付；v2.3 保持原发布不变。其余页面仍保留现版，不代表完整产品视觉重构已完成。

## 实际观察与取舍

用旧实现的真实 Android Compose host 渲染和拖拉杆测试复现：机器与结果堆叠，确认跌出首屏；三个条件入口都打开长偏好页；打开偏好立即清结果；机器/重抽/保存同时出现；店铺与餐食两种状态混在主路径。

主流程只做完整餐食：条件 → 单卷轴与拉杆 → 结果 → 换一个/调整条件 → 就吃这个。

- 固定底部主操作，详情可滚动，确认无需找按钮。
- 单卷轴的一张餐签同时包含店铺、菜品、价格，不组合独立随机字段。
- 同一条件池在看完一轮前不重复。耗尽后明确告知重新开始。
- 条件是草稿；取消或原样应用保留结果，真正更改才清结果并解释。
- “就吃这个”只确认当前选择；明确“吃过了，记一餐”才写入原有历史。此区分没有改旧存档格式。
- 无候选展示每个排除原因；不自动放宽规则、不假抽。
- 选择和条件草稿支持系统保存状态恢复；转动过程中退出不记账。
- 保留原有截图导入预览、来源/时间字段、店铺与历史数据，并包含 PR52 trim 去重修复。

## 使用现有实现

使用现有 Kotlin / Jetpack Compose / Material 3 / Animatable。没有新运行时框架。参考 Android 固定操作区、48dp 触达与语义规范，以及音乐随机队列避免短期重复的交互：

- https://developer.android.com/develop/ui/compose/quick-guides/content/display-app-bar
- https://developer.android.com/develop/ui/compose/accessibility/semantics
- https://support.google.com/accessibility/android/answer/7101858
- https://support.spotify.com/us/article/shuffle-play/

## 可复现验证

普通构建不引入截图依赖。显式开启原生 Compose 测试套件：

```bash
./gradlew -PunsignedRelease=true -PmealUiEvidence=true testDebugUnitTest
./gradlew -PunsignedRelease=true testDebugUnitTest assembleRelease lintDebug
```

截图在 app/build/outputs/meal-flow，测试源码在 tools/ui-snapshots/flow。测试渲染的是实际 App 及真实 NavigationBar，不是 HTML 原型或导航替身。

当前原生UI套件13项；含UI的全量184项均通过。普通构建单元测试171项，lintDebug无错误（29条既有/非阻塞警告、1条提示）；最终图标变更后再跑同一套检查。

已覆盖 360×720、320×640/130% 与 320×640/200% 字体；点击、拉杆拖动、确认选择不记账、明确记账一次、取消/应用条件、无候选、退出转动、恢复结果与已确认选择、同池不重复。

限制：这是 Robolectric native graphics 的真实 Compose host 渲染与交互，并非 Android 模拟器或真机验收。触觉、音频、外部地图/团购 App 跳转、系统 IME 和手机性能仍需后续设备验证。其余店铺/导入/历史/主题页面仍保留现版，尚未宣称整 App 重构完成。
