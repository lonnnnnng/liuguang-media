package com.liuguang.media.data.local

import com.liuguang.media.data.local.entity.LiveSourceEntity
import com.liuguang.media.data.local.entity.RadioSourceEntity
import com.liuguang.media.data.local.entity.VideoSiteEntity

object DefaultSources {
    const val DEFAULT_LIVE_SOURCE_URL = "https://raw.githubusercontent.com/lonnnnnng/iptv-api/refs/heads/master/output/user_result.m3u"
    const val PLAYBACK_TEST_LIVE_SOURCE_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    const val DEFAULT_RADIO_SOURCE_URL = "https://de1.api.radio-browser.info/json/stations/topclick/120?hidebroken=true"
    const val RADIO_BROWSER_CHINA_SOURCE_URL = "https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/CN?limit=120&hidebroken=true"
    const val LEGACY_DEFAULT_LIVE_SOURCE_URL = "https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/index.m3u"
    const val LEGACY_IPV6_LIVE_SOURCE_URL = "https://live.fanmingming.com/tv/m3u/ipv6.m3u"

    val videoSites = listOf(
        VideoSiteEntity(
            name = "无尽资源",
            apiUrl = "https://api.wujinapi.me/api.php/provide/vod/",
            enabled = true,
            sortOrder = 1,
            lastCheckStatus = "可播放",
            isDefault = true
        ),
        VideoSiteEntity(
            name = "量子资源",
            apiUrl = "https://cj.lziapi.com/api.php/provide/vod/",
            enabled = true,
            sortOrder = 2
        ),
        VideoSiteEntity(
            name = "非凡资源",
            apiUrl = "https://cj.ffzyapi.com/api.php/provide/vod/",
            enabled = true,
            sortOrder = 3
        )
    )

    val liveSources = listOf(
        LiveSourceEntity(
            name = "IPTV直播源",
            url = DEFAULT_LIVE_SOURCE_URL,
            enabled = true,
            sortOrder = 1
        ),
        LiveSourceEntity(
            name = "播放测试源",
            url = PLAYBACK_TEST_LIVE_SOURCE_URL,
            enabled = true,
            sortOrder = 2,
            lastCheckStatus = "可播放"
        )
    )

    val radioSources = listOf(
        RadioSourceEntity(
            name = "热门网络电台",
            url = DEFAULT_RADIO_SOURCE_URL,
            enabled = true,
            sortOrder = 1
        ),
        RadioSourceEntity(
            name = "中文网络电台",
            url = RADIO_BROWSER_CHINA_SOURCE_URL,
            enabled = true,
            sortOrder = 2
        )
    )
}
