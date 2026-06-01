# 流光 / liuguang-media

流光是一个 Android 综合媒体播放客户端，仓库名为 `liuguang-media`，当前 Android 应用模块为 `Liuguang`，包名为 `com.liuguang.media`。

项目围绕四类播放场景建设：

- 在线影视列表浏览、搜索、详情和剧集播放。
- IPTV 直播源解析和直播播放。
- 电台和播客音频聚合播放。
- 在“我的”页面手动输入 M3U8 或 M3U 链接后解析播放。

当前界面采用明亮、紧凑的媒体应用风格，底部主导航为 `片库`、`电视`、`音频`、`我的`。

## 当前功能

- 片库：展示影视内容，支持搜索入口、下拉刷新、上滑加载更多。
- 搜索：支持关键词搜索，并跳转到搜索结果列表。
- 详情：展示海报、基础信息、简介、播放源、播放线路和紧凑剧集列表。
- 剧集播放：支持返回、标题和当前集数展示、源地址展示和复制、快退、播放/暂停、快进、全屏、倍速最高 4 倍、分辨率/码率/实时网速展示。
- 电视：支持 M3U 直播源解析、频道搜索、分组筛选、直播源切换。
- 直播播放：布局和剧集播放页保持一致，支持频道标题、源地址复制、投屏、播放/暂停、全屏、分辨率/码率/网速展示。
- 音频：聚合电台和播客，支持音频播放、系统媒体通知和锁屏控制。
- 在线播放：在“我的”页面进入，支持输入 M3U8 或 M3U 链接，解析后进入对应播放流程。
- 我的：支持播放历史、在线播放、视频源管理、直播源管理、电台源管理、播客源管理、重置应用、免责声明、检测更新。
- CI：GitHub Actions 可构建 APK，并在推送 `v*` 标签时创建 GitHub Release。

## 技术栈

- 开发语言：Kotlin
- UI：Jetpack Compose、Material 3
- 导航：Navigation Compose
- 依赖注入：Hilt
- 本地存储：Room
- 网络：Retrofit、OkHttp
- 播放：AndroidX Media3 / ExoPlayer，支持 HLS
- 图片加载：Coil
- 异步：Kotlin Coroutines、Flow
- 构建：Gradle Kotlin DSL
- CI/CD：GitHub Actions

## 项目结构

```text
.
├── .github/workflows/android-apk.yml
├── prototypes/
│   └── liuguang-ui-concept.html
└── Liuguang/
    ├── app/build.gradle.kts
    └── app/src/main/
        ├── AndroidManifest.xml
        ├── java/com/liuguang/media/
        │   ├── data/
        │   ├── di/
        │   ├── domain/
        │   ├── player/
        │   └── ui/
        └── res/
```

核心目录说明：

- `data/local`：Room 数据库、DAO、默认数据源配置。
- `data/remote`：影视 API 数据模型和 Retrofit Service。
- `data/repository`：影视、直播、历史、源管理、应用更新等仓库。
- `domain`：播放模型和解析辅助逻辑。
- `player`：共享 Media3/ExoPlayer 播放器管理。
- `ui/navigation`：路由和导航图。
- `ui/screens`：片库、详情、播放、电视、音频、我的、在线和源管理等页面。
- `ui/components`：通用 UI 组件。

## 开发环境

推荐开发环境：

- macOS 或 Linux。
- Android Studio。
- JDK 17。
- Android SDK Platform 35。
- Android Build Tools 35.0.0 或兼容版本。
- 使用仓库内置 Gradle Wrapper。

本地构建：

```bash
cd Liuguang
chmod +x ./gradlew
./gradlew assembleDebug --stacktrace
```

Debug APK 输出位置：

```text
Liuguang/app/build/outputs/apk/debug/app-debug.apk
```

手动安装到模拟器：

```bash
adb install -r Liuguang/app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.liuguang.media -c android.intent.category.LAUNCHER 1
```

## 测试环境

当前主要手动验证环境：

- 模拟器：`Pixel_9`
- 应用包名：`com.liuguang.media`
- 基线版本：`1.0.0`
- 已验证发布版本：`1.0.0`
- 已验证结果：GitHub Release APK 在签名一致时可以覆盖安装旧版本。

常用检查命令：

```bash
adb devices
adb shell dumpsys package com.liuguang.media | rg "versionCode|versionName"
```

重点测试范围：

- 应用启动和底部导航。
- 片库下拉刷新和上滑加载更多。
- 搜索、详情、剧集播放流程。
- 电视直播源解析、频道筛选、直播播放流程。
- 音频页电台、播客列表和播放流程。
- 在线 M3U8/M3U 链接解析和播放流程。
- 离开播放页后播放器是否释放，是否还存在后台声音或旧画面。
- 当前源地址复制功能。
- 分辨率、码率、实时网速展示。
- 设置页检测更新流程。

## CI 和发布

GitHub Actions 工作流：

```text
.github/workflows/android-apk.yml
```

工作流能力：

- PR、手动触发、`v*` 标签均可构建 APK。
- PR 和手动触发构建 debug APK artifact。
- 推送 `v*` 标签时创建 GitHub Release，并上传正式 release 签名的 `Liuguang-<tag>.apk`。
- 根据 tag 派生版本号。例如 `v1.0.0` 会生成 `versionName=1.0.0`。
- 根据 tag 派生版本码。例如 `v1.0.0` 会生成 `versionCode=1000000`。
- 支持通过 `ANDROID_RELEASE_KEYSTORE_BASE64` 等 Secrets 固定正式签名，保证后续版本可以覆盖安装。

发布新版本：

```bash
git tag v1.0.0
git push origin v1.0.0
```

## 应用内更新说明

当前应用检测更新接口：

```text
https://api.github.com/repos/lonnnnnng/liuguang-media/releases/latest
```

仓库已设置为 public，应用会直接读取最新公开 GitHub Release。要让普通用户可以正常应用内更新，需要同时满足：

- Release 不是 draft。
- Release tag 版本号高于当前安装版本，例如 `v1.0.1` 高于 `1.0.0`。
- Release assets 中包含 `.apk` 文件。
- APK 与已安装应用使用一致签名，否则 Android 会拒绝覆盖安装。

## 文档

- [需求文档](docs/requirements.md)
- [业务文档](docs/business.md)
- [技术架构文档](docs/technical-architecture.md)
- [测试与验收文档](docs/testing-and-acceptance.md)
- [发布与运维文档](docs/release-and-operations.md)
- [开发指南](docs/development-guide.md)
- [定制源文档](docs/sources/README.md)

## 注意事项

本项目提供的是播放客户端、源管理和播放体验实现。影视和直播内容来自配置的数据源，数据源可用性、合法性、版权合规性需要由项目运营方在分发前自行确认。
