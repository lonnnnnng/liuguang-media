# 播客源文档

## 适用模块

音频页的“播客”模块和播客播放流程使用播客源。播客源负责提供订阅信息、节目列表、封面、发布时间和节目音频地址。

## 支持格式

播客源支持标准 RSS / Atom XML 订阅地址。

示例：

```text
https://example.com/podcast/feed.xml
https://example.com/feed.rss
```

## Feed 基本字段

推荐提供：

```xml
<rss version="2.0">
  <channel>
    <title>播客名称</title>
    <description>播客简介</description>
    <link>https://example.com/podcast</link>
    <image>
      <url>https://example.com/cover.jpg</url>
    </image>
  </channel>
</rss>
```

也支持：

```xml
<itunes:image href="https://example.com/cover.jpg" />
```

## 节目条目

RSS 条目使用 `item`：

```xml
<item>
  <title>第 1 期</title>
  <description>节目简介</description>
  <pubDate>Mon, 01 Jun 2026 12:00:00 +0800</pubDate>
  <itunes:duration>42:30</itunes:duration>
  <enclosure
    url="https://example.com/audio/episode-1.mp3"
    type="audio/mpeg" />
</item>
```

Atom 条目使用 `entry`：

```xml
<entry>
  <title>第 1 期</title>
  <summary>节目简介</summary>
  <published>2026-06-01T12:00:00+08:00</published>
  <link href="https://example.com/audio/episode-1.m4a" type="audio/mp4" />
</entry>
```

## 音频地址识别

应用会从这些位置提取节目音频：

```xml
<enclosure url="..." type="audio/..." />
<link href="..." type="audio/..." />
<media:content url="..." type="audio/..." />
<link>https://example.com/audio/episode.mp3</link>
```

节目音频链接支持：

```text
.mp3
.m4a
.aac
.ogg
.opus
.wav
或 URL 中包含 /audio/
```

## 支持的文本字段

Feed 级别：

| 用途 | 支持标签 |
| --- | --- |
| 标题 | `title` |
| 简介 | `description`、`subtitle` |
| 主页 | `link` |
| 封面 | `image/url`、`itunes:image href` |

节目级别：

| 用途 | 支持标签 |
| --- | --- |
| 标题 | `title` |
| 简介 | `description`、`summary`、`content:encoded` |
| 发布时间 | `pubDate`、`published`、`updated` |
| 时长 | `itunes:duration`、`duration` |
| 单集封面 | `image`、`itunes:image href` |

## 发布时间格式

聚合页会按发布时间倒排。推荐使用：

```text
EEE, dd MMM yyyy HH:mm:ss Z
yyyy-MM-dd'T'HH:mm:ssXXX
yyyy-MM-dd HH:mm:ss
```

示例：

```text
Mon, 01 Jun 2026 12:00:00 +0800
2026-06-01T12:00:00+08:00
2026-06-01 12:00:00
```

## 检测和刷新逻辑

添加或刷新播客源时，应用会：

1. 请求订阅地址。
2. 验证 HTTP 状态。
3. 验证 XML 内容不为空。
4. 解析 Feed 标题、简介、封面和节目列表。
5. 至少解析到一个带音频地址的节目才算成功。

## 定制建议

- 使用标准 RSS 2.0 或 Atom。
- 每个节目都提供 `enclosure`，兼容性最好。
- 音频 URL 应该可直接播放，不要依赖网页跳转。
- 封面使用 HTTPS 图片地址。
- 发布时间尽量带时区，便于正确排序。
