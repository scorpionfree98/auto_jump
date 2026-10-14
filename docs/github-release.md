# GitHub 主分支与自动发布

正式代码使用 GitHub `scorpionfree98/toolkit` 的 `main` 分支。原开发分支保留，不重写其历史，不推送旧的本地 main 历史。

## 发布规则

- 每次推送到 `main` 都运行 Android Build，包含仅文档变更，不以路径过滤跳过。
- 各开发分支推送及 Pull Request 仍构建测试，但不发布 Release。标签推送不触发第二次发布。
- 版本名为 `0.3.<Android Build 的 run_number>`；例如第 5 次运行得到 `0.3.5`、标签 `v0.3.5`、APK `toolkit-0.3.5.apk`。
- Android `versionCode = 1000 + run_number`，新云端版本可同签名覆盖旧版本。运行序号可能因 PR、其他分支或失败构建而跳号，不承诺连续。
- 重跑同一运行保持同版本；已有同提交 Release 不覆盖附件。手动运行 main 会分配新的运行序号并发布。
- 单测、Release Lint、构建、Key 注入检查、APK 内版本检查、签名全部成功，才发布正式 APK。失败不创建新 Release。
- 每次成功构建独立保留，不因下一次 main 推送而取消；只有仍对应当前 main 提交的构建才更新 Latest，避免较旧构建晚结束而倒退 Latest。

## 交付物

Release 包含正式签名 APK、`SHA256SUMS.txt` 及不含私有配置的 `build-info.json`（版本、提交 SHA、运行 ID）。调试包和中间未签名包仅保留为短期构建产物，不作为安装推荐。

签名材料及高德 Key 继续通过 GitHub Actions Secrets 注入，不提交到仓库、发布说明或校验元数据。APK 运行必需的高德 Key 会嵌入应用，Secrets 仅避免源码和构建日志泄露，并不使 APK 内的客户端 Key 不可提取；仍需在服务商侧限制包名及签名。

## 开发约定

CI 通过 Gradle 项目属性 `toolkitVersionName`、`toolkitVersionCode` 注入版本。未指定参数的本地构建仍使用历史默认版本，不能把本地默认包当作新版本发布；正式安装包从 Release 获取。

不得重命名或删除 Android Build 工作流后直接重用这套序号公式，否则新工作流计数可能重新开始。若需新版本系列或迁移工作流，先提高版本系列与 versionCode 基数，确认超过所有已发布安装包。

自动发布仅证明构建、测试与签名通过，不等于真机所有功能或签到业务已验收。
