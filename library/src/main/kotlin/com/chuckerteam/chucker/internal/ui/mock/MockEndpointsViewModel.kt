package com.chuckerteam.chucker.internal.ui.mock

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chuckerteam.chucker.internal.data.entity.MockEndpointWithMocks
import kotlinx.coroutines.launch

/** Every endpoint with mocks, and actions on all of them at once. */
internal class MockEndpointsViewModel : BaseMockViewModel() {
    private val mutableDisabled = MutableLiveData<Int>()
    private val mutableDeleted = MutableLiveData<Int>()

    val endpoints: LiveData<List<MockEndpointWithMocks>> = repository.getEndpoints()

    /** Emits how many endpoints [disableAll] turned off, once their mocks no longer apply. */
    val disabled: LiveData<Int> = mutableDisabled

    /** Emits how many endpoints [deleteAll] removed, once their mocks no longer apply. */
    val deleted: LiveData<Int> = mutableDeleted

    fun disableAll() {
        viewModelScope.launch {
            attempt { repository.disableAll() }?.let { mutableDisabled.value = it }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            attempt { repository.deleteAll() }?.let { mutableDeleted.value = it }
        }
    }
}
