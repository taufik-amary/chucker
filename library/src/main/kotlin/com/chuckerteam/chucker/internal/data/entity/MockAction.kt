package com.chuckerteam.chucker.internal.data.entity

import android.content.Context
import com.chuckerteam.chucker.R
import okhttp3.HttpUrl

/** What happens to a call matching the selected [Mock] of an enabled [MockEndpoint]. */
internal sealed interface MockAction {
    /** The name of the [Mock], recorded with the call. */
    val name: String

    /** The call is sent to [target] instead. */
    data class MapRemote(
        val target: HttpUrl,
        override val name: String,
    ) : MockAction

    /** The call is answered with this response, with or without reaching the server depending on [mode]. */
    data class MapLocal(
        val mode: MapLocalMode,
        val statusCode: Int,
        val headers: String,
        val body: String,
        override val name: String,
    ) : MockAction
}

/**
 * How a call was mocked, from what was recorded about it: the URL it was sent to by Map Remote,
 * or the [MapLocalMode] name of the Map Local mock that answered it, after the [mockName].
 * Null if it wasn't mocked.
 */
internal fun getMockSummary(
    context: Context,
    mappedUrl: String?,
    mapLocalMode: String?,
    mockName: String?,
): String? {
    val how =
        when {
            mappedUrl != null -> context.getString(R.string.chucker_mock_summary_map_remote, mappedUrl)
            mapLocalMode == MapLocalMode.MOCK.name -> context.getString(R.string.chucker_mock_summary_map_local_mock)
            mapLocalMode == MapLocalMode.OVERRIDE.name ->
                context.getString(R.string.chucker_mock_summary_map_local_override)
            else -> return null
        }
    return if (mockName.isNullOrBlank()) how else context.getString(R.string.chucker_mock_summary_named, mockName, how)
}
