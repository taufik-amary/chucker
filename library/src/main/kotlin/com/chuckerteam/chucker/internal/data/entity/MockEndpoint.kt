package com.chuckerteam.chucker.internal.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Whether calls of [requestMethod] on [requestUrl] are mocked, and with which of its [Mock]s.
 * Nothing is applied while it's disabled or while no mock is selected.
 */
@Entity(tableName = "mock_endpoints", primaryKeys = ["requestUrl", "requestMethod"])
internal data class MockEndpoint(
    @ColumnInfo(name = "requestUrl") val requestUrl: String,
    @ColumnInfo(name = "requestMethod") val requestMethod: String,
    @ColumnInfo(name = "isEnabled") val isEnabled: Boolean,
    /** The id of the [Mock] applied, or null if none is selected. */
    @ColumnInfo(name = "activeMockId") val activeMockId: Long?,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
)

/** An endpoint with its mocks, as shown in the Chucker UI. */
internal data class MockEndpointWithMocks(
    val requestUrl: String,
    val requestMethod: String,
    /** Null until the endpoint is turned on for the first time. */
    val endpoint: MockEndpoint?,
    val mocks: List<Mock>,
) {
    val isEnabled: Boolean get() = endpoint?.isEnabled == true

    val activeMock: Mock? get() = endpoint?.activeMockId?.let { id -> mocks.firstOrNull { it.id == id } }
}
