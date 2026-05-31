package com.liuguang.media.domain.model

data class PodcastFeed(
    val title: String,
    val description: String,
    val imageUrl: String,
    val link: String,
    val episodes: List<PodcastEpisode>
)

data class PodcastEpisode(
    val title: String,
    val description: String,
    val audioUrl: String,
    val audioType: String,
    val imageUrl: String,
    val pubDate: String,
    val duration: String
)

data class PodcastLibraryEpisode(
    val subscriptionId: Long,
    val feedTitle: String,
    val feedImageUrl: String,
    val episode: PodcastEpisode
)
