package com.liuguang.media.data.repository

import com.google.gson.JsonSyntaxException
import com.google.gson.stream.MalformedJsonException
import com.liuguang.media.data.remote.VodApiResponse
import com.liuguang.media.domain.model.LiveChannel
import com.liuguang.media.domain.model.PodcastFeed
import com.liuguang.media.domain.model.RadioStation
import retrofit2.HttpException
import java.io.EOFException
import java.io.IOException

data class LiveSourceCheckResponse(
    val httpCode: Int,
    val contentType: String?,
    val rawContent: String,
    val channels: List<LiveChannel>,
    val latencyMs: Long = 0
)

data class RadioSourceCheckResponse(
    val httpCode: Int,
    val contentType: String?,
    val rawContent: String,
    val stations: List<RadioStation>,
    val latencyMs: Long = 0
)

data class PodcastSourceCheckResponse(
    val httpCode: Int,
    val contentType: String?,
    val rawContent: String,
    val feed: PodcastFeed,
    val latencyMs: Long = 0
)

data class VideoSiteCheckResponse(
    val httpCode: Int,
    val contentType: String?,
    val rawContent: String,
    val response: VodApiResponse,
    val searchKeyword: String? = null,
    val searchResultCount: Int? = null,
    val latencyMs: Long = 0
)

class SourceHttpException(
    val statusCode: Int,
    message: String,
    val rawContent: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class SourceDataException(
    message: String,
    val rawContent: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

enum class SourceCheckFailureReason(
    val label: String,
    val statusText: String
) {
    Network(label = "网络原因", statusText = "网络异常"),
    Data(label = "接口返回数据原因", statusText = "数据异常"),
    Unknown(label = "未知原因", statusText = "检测失败")
}

fun classifySourceCheckFailure(error: Throwable): SourceCheckFailureReason {
    val chain = generateSequence(error) { it.cause }.toList()
    return when {
        chain.any {
            it is SourceDataException ||
                it is SourceHttpException ||
                it is HttpException ||
                it is JsonSyntaxException ||
                it is MalformedJsonException ||
                it is EOFException ||
                it is IllegalStateException
        } -> SourceCheckFailureReason.Data

        chain.any { it is IOException || it is IllegalArgumentException } -> SourceCheckFailureReason.Network
        else -> SourceCheckFailureReason.Unknown
    }
}

fun sourceCheckFailureMessage(
    reason: SourceCheckFailureReason,
    error: Throwable
): String {
    val detail = generateSequence(error) { it.cause }
        .firstOrNull { !it.message.isNullOrBlank() }
        ?.message
        ?: error::class.java.simpleName

    return when (reason) {
        SourceCheckFailureReason.Network -> "无法连接到接口，请检查网络、DNS、证书、超时或地址是否可访问。$detail"
        SourceCheckFailureReason.Data -> "接口已返回响应或发生了解析错误，但返回状态、格式或内容不符合预期。$detail"
        SourceCheckFailureReason.Unknown -> "检测失败。$detail"
    }
}

fun sourceCheckReturnedContent(error: Throwable): String? {
    return generateSequence(error) { it.cause }
        .mapNotNull {
            when (it) {
                is SourceDataException -> it.rawContent
                is SourceHttpException -> it.rawContent
                else -> null
            }
        }
        .firstOrNull { it.isNotBlank() }
}
