package com.liuguang.media.data.remote

import com.google.gson.annotations.SerializedName

data class VodApiResponse(
    val code: Int? = null,
    val msg: String? = null,
    val page: Int? = null,
    val pagecount: Int? = null,
    val total: Int? = null,
    @SerializedName("class") val `class`: List<VodClass>? = null,
    val list: List<VodItem>? = null
)

data class VodClass(
    @SerializedName("type_id") val type_id: Int,
    @SerializedName("type_name") val type_name: String,
    @SerializedName("type_pid") val type_pid: Int? = null
)

data class VodItem(
    @SerializedName("vod_id") val vod_id: Int,
    @SerializedName("vod_name") val vod_name: String,
    @SerializedName("type_id") val type_id: Int? = null,
    @SerializedName("type_name") val type_name: String? = null,
    @SerializedName("vod_remarks") val vod_remarks: String? = null,
    @SerializedName("vod_isend") val vod_isend: String? = null,
    @SerializedName("isend") val isend: String? = null,
    @SerializedName("vod_serial") val vod_serial: String? = null,
    @SerializedName("vod_pic") val vod_pic: String,
    @SerializedName("vod_actor") val vod_actor: String? = null,
    @SerializedName("vod_director") val vod_director: String? = null,
    @SerializedName("vod_content") val vod_content: String? = null,
    @SerializedName("vod_area") val vod_area: String? = null,
    @SerializedName("vod_pubdate") val vod_pubdate: String? = null,
    @SerializedName("vod_year") val vod_year: String? = null,
    @SerializedName("vod_time") val vod_time: String? = null,
    @SerializedName("vod_time_add") val vod_time_add: String? = null,
    @SerializedName("vod_play_from") val vod_play_from: String? = null,
    @SerializedName("vod_play_url") val vod_play_url: String? = null
)
