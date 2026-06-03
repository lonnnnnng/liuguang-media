package com.liuguang.media.ui.navigation

import android.net.Uri

object Destinations {
    const val HOME = "home"
    const val LIVE = "live"
    const val AUDIO = "audio"
    const val ONLINE = "online"
    const val PODCAST_SOURCE_MANAGEMENT = "podcast_source_management"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val SEARCH_RESULT = "search_result/{keyword}"
    const val DETAIL = "detail/{siteId}/{vodId}"
    const val EPISODE_PLAYER = "episode_player/{siteId}/{vodId}/{episodeUrl}?title={title}&episodeLabel={episodeLabel}"
    const val LIVE_PLAYER = "live_player/{url}?title={title}&group={group}&format={format}&sourceId={sourceId}"
    const val RADIO_PLAYER = "radio_player/{url}?title={title}&group={group}&codec={codec}&bitrate={bitrate}&logo={logo}&sourceId={sourceId}"
    const val HISTORY = "history"
    const val SITE_MANAGEMENT = "site_management"
    const val LIVE_SOURCE_MANAGEMENT = "live_source_management"
    const val RADIO_SOURCE_MANAGEMENT = "radio_source_management"

    fun searchResult(keyword: String) = "search_result/${Uri.encode(keyword)}"
    fun detail(siteId: Long, vodId: String) = "detail/$siteId/$vodId"
    fun episodePlayer(
        siteId: Long,
        vodId: String,
        episodeUrl: String,
        title: String = "",
        episodeLabel: String = ""
    ): String {
        val encodedUrl = Uri.encode(episodeUrl)
        val encodedTitle = Uri.encode(title)
        val encodedEpisodeLabel = Uri.encode(episodeLabel)
        return "episode_player/$siteId/$vodId/$encodedUrl?title=$encodedTitle&episodeLabel=$encodedEpisodeLabel"
    }

    fun livePlayer(
        url: String,
        title: String = "",
        group: String = "",
        format: String = "",
        sourceId: Long = 0L
    ): String {
        val encodedUrl = Uri.encode(url)
        val encodedTitle = Uri.encode(title)
        val encodedGroup = Uri.encode(group)
        val encodedFormat = Uri.encode(format)
        return "live_player/$encodedUrl?title=$encodedTitle&group=$encodedGroup&format=$encodedFormat&sourceId=$sourceId"
    }

    fun radioPlayer(
        url: String,
        title: String = "",
        group: String = "",
        codec: String = "",
        bitrate: Int = 0,
        logo: String = "",
        sourceId: Long = 0L
    ): String {
        val encodedUrl = Uri.encode(url)
        val encodedTitle = Uri.encode(title)
        val encodedGroup = Uri.encode(group)
        val encodedCodec = Uri.encode(codec)
        val encodedLogo = Uri.encode(logo)
        return "radio_player/$encodedUrl?title=$encodedTitle&group=$encodedGroup&codec=$encodedCodec&bitrate=$bitrate&logo=$encodedLogo&sourceId=$sourceId"
    }
}
