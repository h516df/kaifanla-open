# 开饭啦 🎰

一个安卓「选择困难」救星：拉一下餐食滚轮，挑出完整的一餐——店、菜、价格一起给你。

## 功能

- **挑选完整的一餐**：单卷轴同时显示店铺、餐食和价格，可拉杆、换一个或调整条件；同一候选池看完前不重复
- **选择规则**：今日预算（按团购价）、最近 N 天吃过的不抽、「今天不想吃」（第二天自动恢复）
- **店铺**：店名、图标（100+ 常用美食表情 + 系统全部表情，输入店名自动推荐）、地址、团购/地图链接；一键导航、打开团购页
- **菜单**：每样吃的有原价和团购价；支持批量粘贴整段菜单，也可以「📷 识别截图」从团购页或菜单照片里自动认出菜名和价格，认出来还能改和删
- **吃饭小本本**：「就吃这个」确认选择，「吃过了，记一餐」才记账；月度统计吃了几顿、花了多少、团购省了多少
- **我的**：专属称呼、4 套主题皮肤、音效开关、备份/恢复、分享菜单/粘贴导入
- **风格主题**：奶油食堂、午夜小馆、薄荷街角，以及保留的蜜桃变体；背景、标题字体、卡片与滚轮机身随风格联动，支持系统关闭动画

数据全部存在手机本地，不联网也能用；OCR 用的是 ML Kit bundled 中文模型，模型打进 APK，离线可用、不需要 Google Play 服务。

## 构建

需要 JDK 17+（开发时用 21）和 Android SDK 36。

```powershell
.\gradlew.bat testDebugUnitTest  # 单元测试
.\gradlew.bat assembleDebug      # 测试版，包名带 .debug，可以和正式版装在同一台手机上
.\gradlew.bat assembleRelease    # 正式版，约 22 MB（中文 OCR 的模型和 native 库打进了包里）
```

## 签名

**本仓库不含任何签名密钥。** 想出自己的正式包，自己生成一个：

```powershell
keytool -genkeypair -v -keystore keystore/release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mykey
```

然后在项目根目录建 `keystore.properties`：

```properties
storeFile=../keystore/release.jks
storePassword=你的口令
keyAlias=mykey
keyPassword=你的口令
```

没有 `keystore.properties` 时 `assembleRelease` 会退回 debug 签名，仍可编译和安装测试。

## 用到的开源项目

| 项目 | 用途 |
|---|---|
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | 界面 |
| [androidx.emoji2 emojipicker](https://developer.android.com/develop/ui/views/text-and-emoji/emoji-picker) | 「更多表情」选择器 |
| [Google ML Kit 文字识别（中文）](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) | 「📷 识别截图」：bundled 版，模型打进 APK，离线可用 |
| [站酷快乐体 ZCOOL KuaiLe](https://fonts.google.com/specimen/ZCOOL+KuaiLe)（OFL） | 可爱字体 |
| [lucky-canvas](https://github.com/buuing/lucky-canvas) | 转盘样式参考（未引入代码） |

## 关于这个仓库

这是一个从私人自用版本整理出来的**脱敏公开版**：示例店铺、地址、菜品价格与界面截图都已替换或移除，仓库里不含任何真实店铺、个人数据或签名密钥。原作者用它自己那份不公开。

许可证见 [LICENSE](LICENSE)。