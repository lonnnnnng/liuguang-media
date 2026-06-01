# 电视直播源文档

## 适用模块

电视页、直播播放页使用直播源。直播源负责提供频道列表、分组、台标和直播播放地址。

## 支持格式

直播源支持：

- 标准 M3U / M3U8 频道列表。
- 简单文本格式：`频道名,播放地址`。
- 单个 HLS `.m3u8` 测试流。

推荐使用标准 M3U。

## 标准 M3U 示例

```text
#EXTM3U
#EXTINF:-1 tvg-name="CCTV-1" tvg-logo="https://example.com/cctv1.png" group-title="央视",CCTV-1
https://example.com/live/cctv1/index.m3u8
#EXTINF:-1 tvg-name="CCTV-2" tvg-logo="https://example.com/cctv2.png" group-title="央视",CCTV-2
https://example.com/live/cctv2/index.m3u8
```

可识别属性：

| 属性 | 说明 |
| --- | --- |
| `tvg-name` | 频道名，优先级高于逗号后的名称 |
| `tvg-logo` | 台标地址 |
| `group-title` | 频道分组 |

如果缺少 `group-title`，默认归入“默认”分组。

## 简单文本格式

```text
CCTV-1,https://example.com/live/cctv1/index.m3u8
CCTV-2,https://example.com/live/cctv2/index.m3u8
```

简单格式不支持单独台标字段，适合快速测试或极简源。

## 播放地址识别

应用会根据地址判断格式：

| 地址特征 | 识别格式 |
| --- | --- |
| 包含 `.m3u8` | `m3u8` |
| 包含 `.flv` | `flv` |
| 其他 HTTP/HTTPS 地址 | `unknown` |

推荐定制源优先使用 `.m3u8`。`.flv` 可以进入频道列表，但播放稳定性取决于系统和播放器支持。

## 单个 HLS 测试流

如果源地址本身是一个 `.m3u8`，并且返回内容包含 HLS playlist 标记，应用会把它作为一个测试频道解析。

示例：

```text
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
```

识别条件：

```text
URL 包含 .m3u8
内容包含 #EXTM3U
内容包含 #EXT-X-STREAM-INF 或 #EXT-X-TARGETDURATION
```

## 检测逻辑

直播源检测会执行：

1. 请求源地址。
2. 验证 HTTP 状态。
3. 验证返回内容不为空。
4. 按 M3U 或简单格式解析频道。
5. 至少解析到一个频道才算成功。

检测失败时会区分网络异常和接口返回数据异常。

## 定制建议

- 使用 UTF-8 编码。
- 每个 `#EXTINF` 后面紧跟播放 URL。
- 频道名不要为空。
- 分组数量不要过多，移动端筛选更适合少量清晰分组。
- 台标建议使用 PNG/WebP，透明台标在浅色背景下最好带描边或适配白底。
- 单个源频道数量建议控制在 5000 以内。
