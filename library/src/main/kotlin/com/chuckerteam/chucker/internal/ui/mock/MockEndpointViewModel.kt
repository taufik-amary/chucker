package com.chuckerteam.chucker.internal.ui.mock

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.chuckerteam.chucker.internal.data.entity.MockEndpointWithMocks
import kotlinx.coroutines.launch

/** The mocks of one endpoint, which one is applied, and whether mocking is on. */
internal class MockEndpointViewModel : BaseMockViewModel() {
    private var endpoint: LiveData<MockEndpointWithMocks>? = null

    fun endpoint(
        requestUrl: String,
        requestMethod: String,
    ): LiveData<MockEndpointWithMocks> {
        val loaded = endpoint ?: repository.getEndpoint(requestUrl, requestMethod)
        endpoint = loaded
        return loaded
    }

    fun setEnabled(
        requestUrl: String,
        requestMethod: String,
        isEnabled: Boolean,
    ) {
        viewModelScope.launch { attempt { repository.setEnabled(requestUrl, requestMethod, isEnabled) } }
    }

    fun selectMock(
        requestUrl: String,
        requestMethod: String,
        mockId: Long,
    ) {
        viewModelScope.launch { attempt { repository.selectMock(requestUrl, requestMethod, mockId) } }
    }

    fun deleteEndpoint(
        requestUrl: String,
        requestMethod: String,
    ) {
        viewModelScope.launch { attempt { repository.deleteEndpoint(requestUrl, requestMethod) } }
    }
}
