package com.chuckerteam.chucker.internal.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chuckerteam.chucker.internal.support.FormatUtils
import okhttp3.Headers
import okhttp3.HttpUrl

/**
 * A named way to mock calls of [requestMethod] on [requestUrl], such as one response per test case.
 * Several can be saved for an endpoint, and its [MockEndpoint] tells which one is applied.
 *
 * The settings of both [MockType]s are kept, so that switching between them doesn't lose what was
 * typed, but only the ones of [type] are applied.
 *
 * [requestUrl] is stored in the form returned by [toMockRuleUrl], so a mock matches an endpoint
 * regardless of the query parameters of a particular call.
 */
@Entity(tableName = "mocks", indices = [Index("requestUrl", "requestMethod")])
internal data class Mock(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "requestUrl") val requestUrl: String,
    @ColumnInfo(name = "requestMethod") val requestMethod: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: MockType,
    /** Where [MockType.MAP_REMOTE] sends the calls. */
    @ColumnInfo(name = "targetUrl") val targetUrl: String?,
    /** How [MockType.MAP_LOCAL] answers the calls, with [statusCode], [headers] and [body]. */
    @ColumnInfo(name = "localMode") val localMode: MapLocalMode?,
    @ColumnInfo(name = "statusCode") val statusCode: Int?,
    /** One `Name: value` header per line, as typed in the Chucker UI. */
    @ColumnInfo(name = "headers") val headers: String?,
    @ColumnInfo(name = "body") val body: String?,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
)

internal enum class MockType {
    /** The call is sent to another URL. */
    MAP_REMOTE,

    /** The response is written in the Chucker UI. */
    MAP_LOCAL,
}

internal enum class MapLocalMode {
    /** The call never reaches the server: the rule is the whole response. */
    MOCK,

    /** The call reaches the server, then the rule replaces the status, body and the headers it names. */
    OVERRIDE,
}

/**
 * Headers describing how a body is sent. Copied from a recorded response, they don't match the body
 * of a rule, which is sent as is.
 */
internal val BODY_FRAMING_HEADERS = listOf("Content-Length", "Content-Encoding", "Transfer-Encoding")

internal const val MIN_STATUS_CODE = 100
internal const val MAX_STATUS_CODE = 599

internal fun HttpUrl.toMockRuleUrl(): String =
    newBuilder()
        .query(null)
        .fragment(null)
        .build()
        .toString()

/**
 * Parses one `Name: value` header per line, ignoring blank lines. Null if any line isn't a valid header:
 * the form only tells which field is invalid, so the reason given by OkHttp isn't needed.
 */
@Suppress("SwallowedException")
internal fun parseMockHeaders(text: String): Headers? =
    try {
        Headers
            .Builder()
            .apply {
                text
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { add(it) }
            }.build()
    } catch (e: IllegalArgumentException) {
        null
    }

/**
 * [body] laid out on several lines if it's a JSON object or array, such as the minified body of a
 * recorded response, so that it can be edited. Anything else is returned as is.
 */
internal fun formatMockBody(body: String): String {
    val trimmed = body.trim()
    val isJson = trimmed.startsWith("{") || trimmed.startsWith("[")
    return if (isJson) FormatUtils.formatJson(body) else body
}

/** Formats [headers] the way [parseMockHeaders] reads them. */
internal fun formatMockHeaders(headers: List<HttpHeader>): String {
    val lines = headers.map { "${it.name}: ${it.value}" }
    return lines.joinToString(separator = "\n")
}
