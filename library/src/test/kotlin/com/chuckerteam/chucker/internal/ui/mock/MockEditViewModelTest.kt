package com.chuckerteam.chucker.internal.ui.mock

import android.database.sqlite.SQLiteException
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.chuckerteam.chucker.api.Chucker
import com.chuckerteam.chucker.internal.data.entity.HttpHeader
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.entity.MapLocalMode
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockType
import com.chuckerteam.chucker.internal.data.repository.HttpTransactionRepository
import com.chuckerteam.chucker.internal.data.repository.MockRepository
import com.chuckerteam.chucker.internal.data.repository.RepositoryProvider
import com.chuckerteam.chucker.internal.support.Logger
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
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
internal class MockEditViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<MockRepository>(relaxUnitFun = true)
    private val transactionRepository = mockk<HttpTransactionRepository>()
    private val savedMock = slot<Mock>()

    private val defaultLogger = Chucker.logger

    private lateinit var viewModel: MockEditViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        Chucker.logger = mockk<Logger>(relaxUnitFun = true)
        coEvery { repository.saveMock(capture(savedMock)) } returns 1L
        mockkObject(RepositoryProvider)
        every { RepositoryProvider.mock() } returns repository
        every { RepositoryProvider.transaction() } returns transactionRepository
        viewModel = MockEditViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Chucker.logger = defaultLogger
        unmockkAll()
    }

    private fun form(
        name: String = "Server down",
        type: MockType = MockType.MAP_LOCAL,
        targetUrl: String = TARGET_URL,
        statusCode: String = "503",
        headers: String = "Retry-After: 10",
    ) = MockForm(name, type, targetUrl, MapLocalMode.OVERRIDE, statusCode, headers, BODY)

    private fun save(
        form: MockForm = form(),
        mockId: Long? = null,
    ) {
        viewModel.saveMock(REQUEST_URL, "GET", mockId, form)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun verifyNothingSaved() {
        coVerify(exactly = 0) { repository.saveMock(any()) }
        assertThat(viewModel.mockSaved.value).isNull()
    }

    private fun loadForm(
        mockId: Long? = null,
        transactionId: Long? = TRANSACTION_ID,
    ): LoadedMockForm? {
        val form = viewModel.loadForm(mockId, transactionId)
        form.observeForever { }
        testDispatcher.scheduler.advanceUntilIdle()
        return form.value
    }

    @Test
    fun `new mock is saved with its name and the settings of both types`() {
        save(form(name = "  Server down "))

        val mock = savedMock.captured
        assertThat(mock.id).isEqualTo(0L)
        assertThat(mock.requestUrl).isEqualTo(REQUEST_URL)
        assertThat(mock.name).isEqualTo("Server down")
        assertThat(mock.type).isEqualTo(MockType.MAP_LOCAL)
        assertThat(mock.targetUrl).isEqualTo(TARGET_URL)
        assertThat(mock.statusCode).isEqualTo(503)
        assertThat(mock.headers).isEqualTo("Retry-After: 10")
        assertThat(mock.body).isEqualTo(BODY)
        assertThat(viewModel.mockSaved.value).isTrue()
    }

    @Test
    fun `edited mock keeps its id`() {
        save(mockId = 7L)

        assertThat(savedMock.captured.id).isEqualTo(7L)
    }

    @Test
    fun `mock without a name is rejected`() {
        save(form(name = "  "))

        assertThat(viewModel.invalidName.value).isTrue()
        verifyNothingSaved()
    }

    @Test
    fun `invalid fields of the selected map local type are rejected`() {
        save(form(statusCode = "600", headers = "nope"))

        assertThat(viewModel.invalidStatusCode.value).isTrue()
        assertThat(viewModel.invalidHeaders.value).isTrue()
        assertThat(viewModel.invalidTargetUrl.value).isFalse()
        verifyNothingSaved()
    }

    @Test
    fun `invalid target of the selected map remote type is rejected`() {
        save(form(type = MockType.MAP_REMOTE, targetUrl = "not a url"))

        assertThat(viewModel.invalidTargetUrl.value).isTrue()
        assertThat(viewModel.invalidStatusCode.value).isFalse()
        verifyNothingSaved()
    }

    @Test
    fun `invalid settings of the other type are dropped instead of rejected`() {
        save(form(type = MockType.MAP_REMOTE, statusCode = "abc"))

        val mock = savedMock.captured
        assertThat(mock.targetUrl).isEqualTo(TARGET_URL)
        assertThat(mock.statusCode).isNull()
        assertThat(mock.body).isNull()
    }

    @Test
    fun `failing to save is reported instead of crashing`() {
        coEvery { repository.saveMock(any()) } throws SQLiteException("disk full")

        save()

        assertThat(viewModel.databaseFailed.value).isTrue()
        assertThat(viewModel.mockSaved.value).isNull()
    }

    @Test
    fun `cancellation is not reported as a database failure`() {
        coEvery { repository.saveMock(any()) } throws CancellationException("cancelled")

        save()

        assertThat(viewModel.databaseFailed.value).isNull()
    }

    @Test
    fun `deleting a mock is reported once it is removed`() {
        viewModel.deleteMock(EXISTING_MOCK)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteMock(EXISTING_MOCK.id) }
        assertThat(viewModel.mockDeleted.value).isTrue()
    }

    @Test
    fun `form of a new mock is a map local filled from the recorded response`() {
        val transaction =
            HttpTransaction().apply {
                responseCode = 404
                responseBody = """{"error":"missing"}"""
                setResponseHeaders(
                    listOf(
                        HttpHeader("Content-Type", "application/json"),
                        HttpHeader("Content-Length", "19"),
                        HttpHeader("content-encoding", "gzip"),
                    ),
                )
            }
        coEvery { transactionRepository.getSelectedTransactions(listOf(TRANSACTION_ID)) } returns listOf(transaction)

        val loaded = loadForm()!!

        assertThat(loaded.existingMock).isNull()
        assertThat(loaded.form.name).isEmpty()
        assertThat(loaded.form.type).isEqualTo(MockType.MAP_LOCAL)
        assertThat(loaded.form.statusCode).isEqualTo("404")
        assertThat(loaded.form.headers).isEqualTo("Content-Type: application/json")
        // Laid out on several lines, as servers usually send json minified.
        assertThat(loaded.form.body).isEqualTo("{\n  \"error\": \"missing\"\n}")
    }

    @Test
    fun `form of a saved mock shows the mock`() {
        coEvery { repository.getMock(EXISTING_MOCK.id) } returns EXISTING_MOCK

        val loaded = loadForm(mockId = EXISTING_MOCK.id, transactionId = null)!!

        assertThat(loaded.existingMock).isEqualTo(EXISTING_MOCK)
        assertThat(loaded.form.name).isEqualTo("Old")
        assertThat(loaded.form.targetUrl).isEqualTo(TARGET_URL)
        assertThat(loaded.form.localMode).isEqualTo(MapLocalMode.OVERRIDE)
        assertThat(loaded.form.statusCode).isEqualTo("500")
        assertThat(loaded.form.body).isEqualTo("old")
    }

    @Test
    fun `form of a map remote mock fills its map local settings from the recorded response`() {
        val remoteOnly =
            EXISTING_MOCK.copy(type = MockType.MAP_REMOTE, localMode = null, statusCode = null, body = null)
        coEvery { repository.getMock(EXISTING_MOCK.id) } returns remoteOnly
        coEvery { transactionRepository.getSelectedTransactions(listOf(TRANSACTION_ID)) } returns
            listOf(HttpTransaction().apply { responseCode = 201 })

        val loaded = loadForm(mockId = EXISTING_MOCK.id)!!

        assertThat(loaded.form.type).isEqualTo(MockType.MAP_REMOTE)
        assertThat(loaded.form.statusCode).isEqualTo("201")
    }

    @Test
    fun `form is loaded once per screen`() {
        val first = viewModel.loadForm(null, null)

        assertThat(viewModel.loadForm(null, null)).isSameInstanceAs(first)
    }

    private companion object {
        const val REQUEST_URL = "https://example.com/users"
        const val TARGET_URL = "https://mock.example.com/users"
        const val BODY = """{"ok":true}"""
        const val TRANSACTION_ID = 7L
        val EXISTING_MOCK =
            Mock(
                id = 3L,
                requestUrl = REQUEST_URL,
                requestMethod = "GET",
                name = "Old",
                type = MockType.MAP_LOCAL,
                targetUrl = TARGET_URL,
                localMode = MapLocalMode.OVERRIDE,
                statusCode = 500,
                headers = "",
                body = "old",
                updatedAt = 0L,
            )
    }
}
