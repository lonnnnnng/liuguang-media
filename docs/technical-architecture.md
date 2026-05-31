# 技术架构文档

## 总览

Liuguang 是一个原生 Android 应用，使用 Kotlin 和 Jetpack Compose 构建。整体分层如下：

```text
UI Screens
  -> ViewModels
    -> Repositories
      -> Remote APIs / Room DAOs / Player Manager
```

## UI 层

位置：

```text
Liuguang/app/src/main/java/com/liuguang/media/ui
```

职责：

- 使用 Compose 构建页面和组件。
- 承接导航、页面状态展示、用户操作入口。
- 保持和原型一致的深色影院风格。
- 从 ViewModel 收集状态并渲染。

主要目录：

- `ui/navigation`：底部导航、路由定义、页面参数传递。
- `ui/screens/home`：首页内容、刷新、分页。
- `ui/screens/detail`：影视详情和剧集选择。
- `ui/screens/player`：剧集播放页和直播播放页。
- `ui/screens/live`：直播频道列表、搜索、分组和换源。
- `ui/screens/online`：在线 M3U8/M3U 链接解析。
- `ui/screens/settings`：设置页、更新检查入口。
- `ui/components`：通用组件和影院风格组件。

## ViewModel 层

ViewModel 负责页面状态管理和用户动作处理，状态主要通过 Kotlin Flow 暴露给 Compose 页面。

职责：

- 调用 Repository 获取数据。
- 将数据转换为页面可展示状态。
- 处理刷新、加载更多、解析播放、下载更新等动作。
- 捕获异常并转换为用户可读提示。

## Repository 层

位置：

```text
Liuguang/app/src/main/java/com/liuguang/media/data/repository
```

主要仓库：

- `VodRepository`：请求影视列表、详情和分类。
- `LiveRepository`：管理直播源，拉取并解析 M3U/M3U8 内容。
- `HistoryRepository`：管理播放历史。
- `SiteRepository`：管理视频源。
- `AppUpdateRepository`：检查 GitHub Release、比较版本、下载 APK 并上报进度。

## 本地存储

Room 数据库：

```text
Liuguang/app/src/main/java/com/liuguang/media/data/local/AppDatabase.kt
```

主要实体：

- `VideoSiteEntity`
- `LiveSourceEntity`
- `HistoryEntity`

默认数据源：

```text
Liuguang/app/src/main/java/com/liuguang/media/data/local/DefaultSources.kt
```

本地数据用于保存视频源、直播源和历史记录，确保应用重启后仍可恢复用户配置。

## 远程数据

影视源使用常见 `provide/vod` 类型 API，通过 Retrofit 请求。

直播源通过 OkHttp 拉取 M3U 内容后本地解析。解析支持：

- 标准 `#EXTINF` 格式。
- 简单 `频道名,http://url` 格式。
- 直接 HLS playlist 作为测试频道。

## 播放器

播放器管理：

```text
Liuguang/app/src/main/java/com/liuguang/media/player/PlayerManager.kt
```

核心能力：

- 使用 AndroidX Media3 ExoPlayer。
- 使用 OkHttp DataSource 进行网络播放。
- 支持 HLS。
- 通过 TransferListener 统计网络传输字节，用于实时网速展示。
- 提供播放、暂停、恢复、seek、倍速、当前位置和时长读取。
- `stopAndRelease()` 会停止播放、清空媒体、清理画面、释放 ExoPlayer 和监听器。

播放页离开时必须调用清理逻辑，避免后台声音和旧画面残留。

## 应用更新架构

检测接口：

```text
https://api.github.com/repos/lonnnnnng/liuguang-media/releases/latest
```

流程：

1. 设置页点击检测更新。
2. `AppUpdateRepository` 请求 GitHub 最新 Release。
3. 使用 Release tag 或 name 与 `BuildConfig.VERSION_NAME` 比较。
4. 找到第一个 APK asset。
5. UI 展示当前版本、新版本、更新说明和安装包大小。
6. 下载 APK 到应用 cache 目录，并实时更新进度。
7. 下载完成后通过 `FileProvider` 唤起 Android 系统安装器。

公开 Release 要求：

- GitHub 仓库和 Release 必须对普通用户公开可访问。
- 最新 Release 不能是 draft，并且应作为稳定版本发布。
- Release tag 需要使用 `vMAJOR.MINOR.PATCH` 格式，应用内展示时会去掉前导 `v`。
- Release assets 必须包含 `.apk` 文件，并提供有效 `browser_download_url`。
- APK 必须与已安装应用使用一致签名，否则 Android 会拒绝覆盖安装。

## 依赖注入

Hilt 模块：

```text
Liuguang/app/src/main/java/com/liuguang/media/di/AppModule.kt
```

职责：

- 提供 Room Database 和 DAO。
- 提供 Retrofit、OkHttp。
- 注册 Room 数据库迁移。

## 构建配置

模块配置：

```text
Liuguang/app/build.gradle.kts
```

当前配置：

- `applicationId`：`com.liuguang.media`
- `namespace`：`com.liuguang.media`
- `minSdk`：26
- `targetSdk`：35
- `compileSdk`：35
- Java/Kotlin Target：17
- 默认 `versionName`：`1.0.0`
- 默认 `versionCode`：`1`

CI 会通过环境变量覆盖版本：

- `v1.0.0` -> `VERSION_NAME=1.0.0`
- `v1.0.0` -> `VERSION_CODE=1000000`

## CI/CD

工作流：

```text
.github/workflows/android-apk.yml
```

能力：

- `main`、PR、手动触发、`v*` 标签均可构建。
- `main`、PR、手动触发构建 debug APK artifact。
- 标签构建恢复正式 release keystore，执行 `assembleRelease`，创建 GitHub Release。
- 支持从 `ANDROID_RELEASE_KEYSTORE_BASE64` 等 Secrets 恢复固定正式签名。
- 输出 APK 签名证书 SHA，便于排查覆盖安装问题。

## 技术决策

- 使用 Compose 便于快速迭代 UI 原型。
- 使用 Media3/ExoPlayer，避免自研播放器核心。
- 使用 Room 保存源配置和历史数据。
- M3U 解析在客户端完成，降低服务端依赖。
- 更新检查不在 APK 内存放任何私密凭据。

## 已知技术债

- GitHub Actions 当前提示 Node.js 20 actions 将弃用，后续需要升级相关 action 版本。
- 第三方源返回格式不稳定，解析逻辑后续需要继续增强。
