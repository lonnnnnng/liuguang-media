package com.liuguang.media.domain.model

data class EpisodeItem(
    val groupName: String,
    val label: String,
    val url: String
)

data class EpisodeGroup(
    val name: String,
    val episodes: List<EpisodeItem>
)
