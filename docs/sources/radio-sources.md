# 电台源文档

## 适用模块

音频页的“电台”模块和电台播放页使用电台源。电台源负责提供网络电台列表、分组、台标、编码和播放地址。

## 支持格式

电台源支持：

- Radio Browser JSON API。
- 自定义 JSON。
- M3U / M3U8 电台列表。
- PLS 播放列表。
- 简单文本格式。
- 直连音频流。

## JSON 格式

JSON 可以是数组，也可以是对象中包含 `stations` 或 `list` 数组。

数组示例：

```json
[
  {
    "name": "Hit FM",
    "url": "https://example.com/hitfm.mp3",
    "favicon": "https://example.com/hitfm.png",
    "tags": "音乐",
    "country": "CN",
    "codec": "MP3",
    "bitrate": 128
  }
]
```

对象示例：

```json
{
  "stations": [
    {
      "stationName": "News Radio",
      "streamUrl": "https://example.com/news.aac",
      "logo": "https://example.com/news.png",
      "category": "新闻"
    }
  ]
}
```

字段兼容：

| 用途 | 支持字段 |
| --- | --- |
| 播放地址 | `url_resolved`、`url`、`stream`、`streamUrl`、`playUrl` |
| 名称 | `name`、`title`、`stationName` |
| 分组 | `tags`、`group`、`category`、`genre`、`country`、`language` |
| 编码 | `codec`、`format` |
| 码率 | `bitrate` |
| 台标 | `favicon`、`logo`、`tvg_logo` |
| 国家 | `country`、`countrycode` |

播放地址和名称不能为空。

## M3U 格式

```text
#EXTM3U
#EXTINF:-1 tvg-name="Hit FM" tvg-logo="https://example.com/hitfm.png" group-title="音乐",Hit FM
https://example.com/hitfm.mp3
```

可识别：

```text
tvg-name
tvg-logo
group-title
```

## PLS 格式

```text
[playlist]
File1=https://example.com/hitfm.mp3
Title1=Hit FM
File2=https://example.com/news.aac
Title2=News Radio
```

`FileN` 是播放地址，`TitleN` 是电台名称。

## 简单文本格式

```text
Hit FM,https://example.com/hitfm.mp3
News Radio,https://example.com/news.aac
https://example.com/live.mp3
```

只有 URL 的直连行会用域名推断电台名称。

## 直连音频流

直连音频流支持：

```text
.mp3
.aac
.ogg
.opus
.flac
.m4a
```

直连音频源会通过 Range 请求探测可用性。

## 地址格式推断

| 后缀 | 显示格式 |
| --- | --- |
| `.mp3` | MP3 |
| `.aac` | AAC |
| `.ogg` | OGG |
| `.opus` | OPUS |
| `.flac` | FLAC |
| `.m4a` | M4A |
| `.m3u8` | HLS |
| `.pls` | PLS |
| 其他 | AUDIO |

## 检测逻辑

电台源检测会执行：

1. 判断是否为直连音频流。
2. 直连音频流使用 Range 请求探测。
3. 非直连源请求完整内容。
4. 根据 JSON、PLS、M3U、简单文本顺序解析。
5. 至少解析出一个电台才算成功。

单个源最多解析 2000 个电台。

## 定制建议

- 大型电台源优先使用 JSON，字段表达更完整。
- 播放地址优先使用稳定的 MP3/AAC/HLS。
- 台标建议适配浅色背景。
- 分组字段保持短词，例如“音乐”“新闻”“交通”。
- 如果使用 Radio Browser API，建议带 `hidebroken=true`。
