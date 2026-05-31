# 开发指南

## 仓库布局

Android 项目位于：

```text
Liuguang/
```

除非命令显式指定路径，否则 Gradle 命令建议在 `Liuguang` 目录下执行。

## 环境准备

- JDK 17。
- Android SDK Platform 35。
- Android Build Tools 35.0.0 或兼容版本。
- Android 模拟器或真机。
- `adb` 可在命令行使用。

## 常用命令

构建：

```bash
cd Liuguang
./gradlew assembleDebug --stacktrace
```

安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

启动：

```bash
adb shell monkey -p com.liuguang.media -c android.intent.category.LAUNCHER 1
```

查看版本：

```bash
adb shell dumpsys package com.liuguang.media | rg "versionCode|versionName"
```

## 代码约定

- 遵循当前仓库 Kotlin 和 Compose 写法。
- UI 改动优先复用 `ui/components` 下的共享组件。
- 页面不要直接操作数据库或网络，优先通过 ViewModel 和 Repository。
- 播放相关生命周期清理必须放在播放页或对应 ViewModel 中处理。
- 不要在代码、资源文件或 APK 中写入私钥、Token 等敏感信息。

## 新增页面流程

1. 在 `ui/screens/<feature>` 下新增页面。
2. 如页面需要状态或数据访问，新增对应 ViewModel。
3. 在 `ui/navigation/Destinations.kt` 中新增路由。
4. 在 `ui/navigation/AppNavGraph.kt` 中注册页面。
5. 优先复用 `CinemaComponents.kt` 中的通用样式。
6. 在 `docs/testing-and-acceptance.md` 中补充验收项。

## 新增或调整数据源

默认数据源位置：

```text
Liuguang/app/src/main/java/com/liuguang/media/data/local/DefaultSources.kt
```

调整默认源时需要注意：

- 如果涉及已持久化数据结构变化，需要检查是否需要 Room migration。
- 不应无故覆盖用户已经手动维护的数据源。
- 默认源变化应写入发布说明或文档。

## 播放器改动

共享播放逻辑位置：

```text
Liuguang/app/src/main/java/com/liuguang/media/player/PlayerManager.kt
```

发布前至少验证：

- 剧集播放。
- 直播播放。
- 在线 M3U8 播放。
- 全屏播放。
- 离开播放页后停止后台声音。
- 分辨率、码率、实时网速仍可正常展示。

## 文档维护

功能或流程变化后，应同步更新相关文档：

- 用户可见功能变化：`README.md`、`docs/requirements.md`。
- 业务流程变化：`docs/business.md`。
- 架构、依赖或模块变化：`docs/technical-architecture.md`。
- 测试和验收变化：`docs/testing-and-acceptance.md`。
- 发布、签名、更新变化：`docs/release-and-operations.md`。
