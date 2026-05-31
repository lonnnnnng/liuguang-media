# 发布与运维文档

## 分支

当前主分支：

```text
main
```

代码变更应在本地验证后提交到 `main`。APK 发布通过 Git tag 触发。

## 版本规则

使用语义化 tag：

```text
vMAJOR.MINOR.PATCH
```

示例：

- `v1.0.0`
- `v1.0.1`

CI 转换规则：

```text
v1.0.0 -> versionName 1.0.0
v1.0.0 -> versionCode 1000000
```

## 发布流程

1. 本地构建。

```bash
cd Liuguang
./gradlew assembleDebug --stacktrace
```

本地验证正式签名 release APK 时，需要先加载本机签名环境：

```bash
source "$HOME/.android/liuguang-release-signing.env"
VERSION_NAME=1.0.0 VERSION_CODE=1000000 ./gradlew assembleRelease --stacktrace
```

2. 提交并推送代码。

```bash
git add <files>
git commit -m "<message>"
git push origin main
```

3. 创建并推送 tag。

```bash
git tag v1.0.0
git push origin v1.0.0
```

4. 观察 GitHub Actions。

```bash
gh run list --repo lonnnnnng/liuguang-media --workflow "Android APK" --limit 10
gh run watch <run-id> --repo lonnnnnng/liuguang-media --exit-status
```

5. 确认 Release 产物。

```bash
gh release view v1.0.0 --repo lonnnnnng/liuguang-media
```

## APK 签名

当前发布产物是正式 release 签名 APK。推送 `v*` 标签时，GitHub Actions 会恢复 release keystore，执行 `assembleRelease`，并将正式签名的 APK 上传到 GitHub Release。

当前本机 keystore 路径：

```text
$HOME/.android/liuguang-release.jks
```

当前本机签名环境文件：

```text
$HOME/.android/liuguang-release-signing.env
```

当前 GitHub Actions Secrets：

```text
ANDROID_RELEASE_KEYSTORE_BASE64
ANDROID_RELEASE_KEYSTORE_PASSWORD
ANDROID_RELEASE_KEY_ALIAS
ANDROID_RELEASE_KEY_PASSWORD
```

工作流会将 keystore 还原到：

```text
$HOME/.android/liuguang-release.jks
```

并设置 `ANDROID_RELEASE_KEYSTORE_PATH`、`ANDROID_RELEASE_KEYSTORE_PASSWORD`、`ANDROID_RELEASE_KEY_ALIAS`、`ANDROID_RELEASE_KEY_PASSWORD` 供 Gradle 签名使用。

当前正式签名 SHA-256：

```text
5F:25:15:7B:BB:1D:82:26:70:64:BF:B5:98:A2:FA:BB:A8:28:10:D8:E5:1D:B4:1B:2A:D3:A9:8D:F4:4E:01:14
```

## Release APK 验证

下载 Release APK：

```bash
rm -rf /tmp/liuguang-release-v1.0.0
mkdir -p /tmp/liuguang-release-v1.0.0
gh release download v1.0.0 --repo lonnnnnng/liuguang-media --pattern "*.apk" --dir /tmp/liuguang-release-v1.0.0
```

验证签名：

```bash
apksigner verify --print-certs /tmp/liuguang-release-v1.0.0/Liuguang-v1.0.0.apk
```

覆盖安装：

```bash
adb install -r /tmp/liuguang-release-v1.0.0/Liuguang-v1.0.0.apk
adb shell dumpsys package com.liuguang.media | rg "versionCode|versionName"
```

预期：

- `adb install -r` 返回 `Success`。
- `versionName` 与 tag 一致。
- `versionCode` 与 CI 派生值一致。

## 应用内更新运维

当前更新检查地址：

```text
https://api.github.com/repos/lonnnnnng/liuguang-media/releases/latest
```

仓库和 Release 需要保持公开可访问。应用会读取最新稳定 Release，找到其中的 APK asset，然后下载并唤起系统安装器。

发布要求：

- Release 不能是 draft。
- tag 使用 `vMAJOR.MINOR.PATCH`，例如 `v1.0.0`。
- Release assets 必须包含 `.apk` 文件。
- APK 必须使用固定签名，确保可以覆盖安装旧版本。
- 每次发布后需要验证 `releases/latest` 返回 `200`，且返回的版本和 APK 地址正确。

## 回滚策略

如果某个版本存在严重问题：

1. 将有问题的 GitHub Release 删除、下架或改为 prerelease/draft。
2. 发布更高版本号的修复 tag。
3. 确认应用更新检查只会拿到目标稳定版本。

注意：Android 正常安装流程不允许用低 `versionCode` 覆盖高 `versionCode`。

## 运维检查项

- GitHub Actions 是否成功。
- Release 是否存在 APK asset。
- APK 大小是否正常。
- APK 签名 SHA 是否符合预期。
- 应用更新接口是否可访问。
- 默认视频源、直播源是否可访问。
- 目标模拟器或真机是否可播放剧集、直播和在线链接。
