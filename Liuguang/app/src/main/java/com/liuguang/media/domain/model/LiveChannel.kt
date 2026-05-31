package com.liuguang.media.domain.model

data class LiveChannel(
    val name: String,
    val url: String,
    val group: String,
    val format: String,
    val logo: String = ""
)
