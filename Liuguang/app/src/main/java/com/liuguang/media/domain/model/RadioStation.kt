package com.liuguang.media.domain.model

data class RadioStation(
    val name: String,
    val url: String,
    val group: String,
    val codec: String = "",
    val bitrate: Int = 0,
    val logo: String = "",
    val country: String = ""
)
