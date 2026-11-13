package com.chuckerteam.chucker.internal.data.repository

import androidx.lifecycle.LiveData
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockAction
import com.chuckerteam.chucker.internal.data.entity.MockEndpointWithMocks
import okhttp3.HttpUrl

/**
 * Finds how a call is mocked, if the mock selected for its endpoint is enabled.
 */
internal fun interface MockResolver {
    fun findAction(
        url: HttpUrl,
        method: String,
    ): MockAction?
}

@Suppress("TooManyFunctions")
internal interface MockRepository : MockResolver {
    /** The endpoints that have mocks or were turned on, most recently changed first. */
    fun getEndpoints(): LiveData<List<MockEndpointWithMocks>>

    fun getEndpoint(
        requestUrl: String,
        requestMethod: String,
    ): LiveData<MockEndpointWithMocks>

    suspend fun getMock(id: Long): Mock?

    /**
     * Saves [mock], replacing the one with the same id unless it's 0. A new mock is selected when its
     * endpoint has none selected yet. Returns the id of the mock.
     */
    suspend fun saveMock(mock: Mock): Long

    /** Deletes the mock [id], so that its endpoint no longer has a mock selected if it was this one. */
    suspend fun deleteMock(id: Long)

    suspend fun setEnabled(
        requestUrl: String,
        requestMethod: String,
        isEnabled: Boolean,
    )

    suspend fun selectMock(
        requestUrl: String,
        requestMethod: String,
        mockId: Long,
    )

    /** Deletes the endpoint and all of its mocks. */
    suspend fun deleteEndpoint(
        requestUrl: String,
        requestMethod: String,
    )

    /** Turns off every enabled endpoint, keeping their mocks. Returns how many were turned off. */
    suspend fun disableAll(): Int

    /** Deletes every endpoint and mock. Returns how many endpoints were deleted. */
    suspend fun deleteAll(): Int
}
