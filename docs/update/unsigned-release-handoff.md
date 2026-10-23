# v2.3 构建、签名与发布接续

后续 v2.4 的整合与交付依据见 [v2.4-delivery.md](v2.4-delivery.md)；本文件保留 v2.3 的历史验收记录，不作为新版本候选重复构建。

v2.3 / versionCode 6 已于 2026-10-19 正式发布到原私有仓库：[Release 与下载附件](https://github.com/h516df/kaifanla/releases/tag/v2.3)。原 v2.2 及已有资产保留，仓库可见性未变。

- 固定构建源码：`c8425ee5838ef7541089a98fb3dbdf69c87f323a`；PR #51 合并提交 `0bca604018ff2fcc23d35f06250388eb431774a3`，合并源码树与构建源码完全一致。
- [云端 CI 39252394988](https://github.com/h516df/kaifanla/actions/runs/39252394988) 的单元测试、debug 与未签 release 构建成功；整体失败来自 Actions artifact storage quota，不能标为全绿。
- 本机在既有隔离工作区复用 Gradle 缓存和原受保护签名配置，仅离线执行 `:app:assembleRelease`；未重跑全套测试、未启动设备或模拟器、未改变 VPN。未迁移或更换密钥。
- 包名 `com.jintianchishenme.app`，版本 2.3/code 6，非调试包，ARM 双 ABI。APK 为 23,291,037 字节，APK v2 签名及 zipalign 校验通过。
- 证书 SHA-256：`cd1c51e824a2f3db38f85ae8bcb98b84a3b8024f0840f1c5dcab3072a9bb4e4d`。
- APK SHA-256：`276fee301f147b811242a1dceb0c291fc16a459286275bdbdb417055488101c9`。
- 三个发行附件（APK、SHA256.txt、release-notes.json）已从发布后的 Release 重新下载，逐一校验与本地交付物一致。说明 JSON 与 APK 内置内容相同。APK 未发现签名材料、用户存档或常见凭据标记。

尚未验收：手机系统安装、保留数据覆盖升级、设备 UI 和实际多平台分享。用户明确排除本轮手机/模拟器验收，自行通过 GitHub App 下载。包名、证书和递增版本符合覆盖升级基础条件，不等于已经完成真机升级验收。

应用内正式更新发行源仍未配置，门禁关闭；当前下载路径为原私有仓库 Release。本轮不配置公开发行源，不扩展功能。

上传曾因 GitHub 接收超时或链路停滞失败；已先核对副作用，仅删除本轮失败产生的 starter 占位，未覆盖已完成资产。最终使用系统 curl 和既有代理完成传输；认证由现有 gh 登录在匿名进程管道中提供，未输出、落盘或放入命令参数。后续更新应继续先检查已有 tag/Release/资产，禁止覆盖已有发布。
