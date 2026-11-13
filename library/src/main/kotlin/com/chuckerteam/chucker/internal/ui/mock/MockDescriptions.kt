package com.chuckerteam.chucker.internal.ui.mock

import android.content.Context
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.MapLocalMode
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockType

/** How [this] mocks calls, such as "Map Local · Mock · 503". */
internal fun Mock.describe(context: Context): String =
    when (type) {
        MockType.MAP_REMOTE -> context.getString(R.string.chucker_mock_summary_map_remote, targetUrl.orEmpty())
        MockType.MAP_LOCAL -> {
            val mode =
                when (localMode) {
                    MapLocalMode.OVERRIDE -> context.getString(R.string.chucker_mock_map_local_mode_override)
                    else -> context.getString(R.string.chucker_mock_map_local_mode_mock)
                }
            context.getString(R.string.chucker_mock_rule_map_local, mode, statusCode ?: 0)
        }
    }
