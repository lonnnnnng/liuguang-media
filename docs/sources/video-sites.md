# 片库视频源文档

## 适用模块

片库、搜索、详情页、剧集播放页使用视频源。视频源负责提供影视列表、搜索结果、详情信息和剧集播放地址。

## 支持格式

视频源支持常见 `provide/vod` 类型 JSON API，接口地址通常类似：

```text
https://example.com/api.php/provide/vod/
```

应用会在该基础地址后追加查询参数：

```text
?ac=videolist&pg=1
?ac=videolist&pg=1&wd=关键词
?ac=detail&ids=影片ID
```

## 单个源配置

在“我的 -> 视频源管理”中手动添加时，需要填写：

```text
名称：资源站名称
地址：https://example.com/api.php/provide/vod/
```

地址必须是可直接请求的 HTTP/HTTPS API 地址，不支持网页首页地址。

## 批量导入格式

批量导入只支持 `lite.json`，不再兼容旧 Base58 或其他历史格式。

示例：

```json
{
  "wujin": {
    "name": "无尽资源",
    "api": "https://api.wujinapi.me/api.php/provide/vod/"
  },
  "liangzi": {
    "name": "量子资源",
    "api": "https://cj.lziapi.com/api.php/provide/vod/"
  }
}
```

字段要求：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `name` | 否 | 展示名称。为空时使用外层 key |
| `api` | 是 | 视频源 API 基础地址 |

导入时会按 `api` 地址去重。

## 接口返回要求

列表和搜索接口推荐返回：

```json
{
  "code": 1,
  "msg": "success",
  "page": 1,
  "pagecount": 10,
  "total": 100,
  "class": [
    {
      "type_id": 1,
      "type_name": "电影",
      "type_pid": 0
    }
  ],
  "list": [
    {
      "vod_id": 123,
      "vod_name": "影片名称",
      "type_id": 1,
      "type_name": "电影",
      "vod_remarks": "更新至12集",
      "vod_pic": "https://example.com/poster.jpg",
      "vod_year": "2026",
      "vod_time": "2026-06-01 12:00:00"
    }
  ]
}
```

详情接口需要额外返回播放字段：

```json
{
  "list": [
    {
      "vod_id": 123,
      "vod_name": "影片名称",
      "vod_pic": "https://example.com/poster.jpg",
      "vod_actor": "演员",
      "vod_director": "导演",
      "vod_content": "简介",
      "vod_area": "地区",
      "vod_remarks": "更新至12集",
      "vod_isend": 0,
      "vod_serial": "0",
      "vod_year": "2026",
      "vod_time": "2026-06-01 12:00:00",
      "vod_play_from": "线路一$$$线路二",
      "vod_play_url": "第1集$https://example.com/ep1/index.m3u8#第2集$https://example.com/ep2/index.m3u8"
    }
  ]
}
```

## 播放地址规则

`vod_play_from` 和 `vod_play_url` 按资源站常见格式解析：

```text
线路一$$$线路二
第1集$url1#第2集$url2$$$第1集$url3#第2集$url4
```

当前剧集播放只统计和展示 `.m3u8` 链接。非 `.m3u8` 的网页播放地址、解析页地址、广告页地址会被过滤，不会计入可播放条目数。

字段语义注意：

- 响应根部的 `total` 表示当前列表或搜索结果总条数，不是影片剧集数。
- `vod_remarks` 通常是更新状态文案，例如 `更新至16集`、`第16集`、`第16集完结`。
- `isend` / `vod_isend` 可作为完结状态参考，常见取值为 `1` 完结、`0` 连载；部分资源站可能缺失。
- `vod_serial` 是部分 MacCMS 详情接口返回的连载/更新字段，不同资源站可能返回 `0`、当前集数或空值。
- `vod_total` 属于部分资源站扩展字段，不在通用 Provide/Vod 文档中稳定定义；实际源里可能返回 `20`、`1`、`0` 或缺失，不能直接作为当前可播放剧集总数。
- 应用详情页不展示“共 X 集”。详情头部展示“已完结/连载中 + 当前更新集数”（例如“连载中 · 更新至 201 集”“已完结 · 已更新 36 集”），当前更新集数优先从 `vod_remarks` 提取，其次参考 `vod_serial`，再兜底使用播放标签中的最大正片集数。
- 播放源、播放线路和选集区域展示的是可播放 `.m3u8` 条目数，用于选线路和播放，不代表作品总集数。

支持的剧集地址示例：

```text
https://example.com/video/episode-1/index.m3u8
//example.com/video/episode-1/index.m3u8
```

不推荐：

```text
https://example.com/play.html?id=123
https://example.com/share/abc
```

## 检测逻辑

视频源检测会执行：

1. 请求 `ac=videolist&pg=1`。
2. 验证 HTTP 状态。
3. 验证返回内容是 JSON。
4. 验证存在 `list`、`class` 或分页元信息。
5. 从第一页取一个影片名称，再请求搜索接口。
6. 搜索接口必须能返回影片列表。

批量检测超时默认 10 秒，可在“我的 -> 网络设置”调整。

## 定制建议

- API 必须稳定返回 JSON，不要返回 HTML。
- `vod_id`、`vod_name`、`vod_pic` 尽量完整。
- `vod_time` 或 `vod_time_add` 建议提供，搜索结果会按更新时间倒排。
- 播放地址优先提供 HLS `.m3u8`。
- 多线路时把可播放、速度快的线路放前面。
