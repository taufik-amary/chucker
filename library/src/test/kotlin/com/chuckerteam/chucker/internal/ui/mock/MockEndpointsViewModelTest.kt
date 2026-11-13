package com.chuckerteam.chucker.internal.ui.mock

import android.database.sqlite.SQLiteException
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.chuckerteam.chucker.api.Chucker
import com.chuckerteam.chucker.internal.data.repository.MockRepository
import com.chuckerteam.chucker.internal.data.repository.RepositoryProvider
import com.chuckerteam.chucker.internal.support.Logger
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
internal class MockEndpointsViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<MockRepository>(relaxUnitFun = true)
    private val defaultLogger = Chucker.logger

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        Chucker.logger = mockk<Logger>(relaxUnitFun = true)
        every { repository.getEndpoints() } returns MutableLiveData()
        mockkObject(RepositoryProvider)
        every { RepositoryProvider.mock() } returns repository
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Chucker.logger = defaultLogger
        unmockkAll()
    }

    @Test
    fun `disabling and deleting all report how many endpoints were changed`() {
        coEvery { repository.disableAll() } returns 3
        coEvery { repository.deleteAll() } returns 4
        val viewModel = MockEndpointsViewModel()

        viewModel.disableAll()
        viewModel.deleteAll()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.disabled.value).isEqualTo(3)
        assertThat(viewModel.deleted.value).isEqualTo(4)
    }

    @Test
    fun `failing to delete all is reported instead of crashing`() {
        coEvery { repository.deleteAll() } throws SQLiteException("disk full")
        val viewModel = MockEndpointsViewModel()

        viewModel.deleteAll()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.databaseFailed.value).isTrue()
        assertThat(viewModel.deleted.value).isNull()
    }

    @Test
    fun `endpoint screen changes are sent to the repository`() {
        val viewModel = MockEndpointViewModel()

        viewModel.setEnabled(URL, "GET", true)
        viewModel.selectMock(URL, "GET", 5L)
        viewModel.deleteEndpoint(URL, "GET")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.setEnabled(URL, "GET", true) }
        coVerify(exactly = 1) { repository.selectMock(URL, "GET", 5L) }
        coVerify(exactly = 1) { repository.deleteEndpoint(URL, "GET") }
    }

    private companion object {
        const val URL = "https://example.com/users"
    }
}
