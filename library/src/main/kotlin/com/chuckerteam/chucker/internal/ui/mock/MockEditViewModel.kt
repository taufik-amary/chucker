package com.chuckerteam.chucker.internal.ui.mock

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.chuckerteam.chucker.internal.data.entity.BODY_FRAMING_HEADERS
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.data.entity.MAX_STATUS_CODE
import com.chuckerteam.chucker.internal.data.entity.MIN_STATUS_CODE
import com.chuckerteam.chucker.internal.data.entity.MapLocalMode
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockType
import com.chuckerteam.chucker.internal.data.entity.formatMockBody
import com.chuckerteam.chucker.internal.data.entity.formatMockHeaders
import com.chuckerteam.chucker.internal.data.entity.parseMockHeaders
import com.chuckerteam.chucker.internal.data.repository.RepositoryProvider
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.HttpURLConnection.HTTP_OK

/** What the form shows, or what was typed in it. */
internal data class MockForm(
    val name: String,
    val type: MockType,
    val targetUrl: String,
    val localMode: MapLocalMode,
    val statusCode: String,
    val headers: String,
    val body: String,
)

/** The form as it opens, and the mock it edits, if it's saved already. */
internal data class LoadedMockForm(
    val form: MockForm,
    val existingMock: Mock?,
)

internal class MockEditViewModel : BaseMockViewModel() {
    private val mutableInvalidName = MutableLiveData<Boolean>()
    private val mutableInvalidTargetUrl = MutableLiveData<Boolean>()
    private val mutableInvalidStatusCode = MutableLiveData<Boolean>()
    private val mutableInvalidHeaders = MutableLiveData<Boolean>()
    private val mutableMockSaved = MutableLiveData<Boolean>()
    private val mutableMockDeleted = MutableLiveData<Boolean>()

    private var loadedForm: LiveData<LoadedMockForm>? = null

    /** The form as it opened, to tell whether anything was changed since. Null until loaded. */
    val initialForm: MockForm?
        get() = loadedForm?.value?.form

    /**
     * The body typed in the form. The body field doesn't save its own state, as a large body would
     * exceed what an Activity can save, so the screen keeps it here across configuration changes.
     */
    var bodyDraft: String? = null

    /** Whether the name of the last attempt to save was missing. */
    val invalidName: LiveData<Boolean> = mutableInvalidName

    /** Whether the Map Remote target of the last attempt to save was rejected. */
    val invalidTargetUrl: LiveData<Boolean> = mutableInvalidTargetUrl

    /** Whether the Map Local status code of the last attempt to save was rejected. */
    val invalidStatusCode: LiveData<Boolean> = mutableInvalidStatusCode

    /** Whether the Map Local headers of the last attempt to save were rejected. */
    val invalidHeaders: LiveData<Boolean> = mutableInvalidHeaders

    /** Emits once the mock is stored, so that the screen doesn't close before it takes effect. */
    val mockSaved: LiveData<Boolean> = mutableMockSaved

    /** Emits once the mock is removed, so that the screen doesn't close before it takes effect. */
    val mockDeleted: LiveData<Boolean> = mutableMockDeleted

    /**
     * Loads the form once per screen: the mock [mockId] if given, otherwise a new Map Local mock
     * answering with the response recorded in the transaction [transactionId], so that it can be
     * edited instead of typed. The Map Local fields a saved mock doesn't have are filled the same way.
     */
    fun loadForm(
        mockId: Long?,
        transactionId: Long?,
    ): LiveData<LoadedMockForm> =
        loadedForm ?: liveData {
            val mock = mockId?.let { attempt { repository.getMock(it) } }
            val recorded = recordedForm(transactionId?.let { loadTransaction(it) })
            emit(LoadedMockForm(mock?.toForm(recorded) ?: recorded, mock))
        }.also { loadedForm = it }

    private suspend fun loadTransaction(transactionId: Long): HttpTransaction? =
        attempt { RepositoryProvider.transaction().getSelectedTransactions(listOf(transactionId)).firstOrNull() }

    /**
     * Saves the mock of [requestMethod] on [requestUrl] if it has a name and the fields of its type
     * are valid, otherwise reports the invalid ones through [invalidName], [invalidTargetUrl],
     * [invalidStatusCode] and [invalidHeaders]. The fields of the other type are kept only if valid,
     * as they are hidden and couldn't show an error.
     *
     * @param mockId the id of the mock being edited, or null to add a new one.
     */
    fun saveMock(
        requestUrl: String,
        requestMethod: String,
        mockId: Long?,
        form: MockForm,
    ) {
        val mock = validate(requestUrl, requestMethod, form)?.copy(id = mockId ?: 0L) ?: return
        viewModelScope.launch {
            if (attempt { repository.saveMock(mock) } != null) {
                mutableMockSaved.value = true
            }
        }
    }

    /** The mock to save, or null after reporting the fields that have to be fixed first. */
    private fun validate(
        requestUrl: String,
        requestMethod: String,
        form: MockForm,
    ): Mock? {
        val name = form.name.trim()
        val target = form.targetUrl.trim().toHttpUrlOrNull()
        val statusCode =
            form.statusCode
                .trim()
                .toIntOrNull()
                ?.takeIf { it in MIN_STATUS_CODE..MAX_STATUS_CODE }
        val validHeaders = parseMockHeaders(form.headers) != null
        val validLocal = statusCode != null && validHeaders
        val isRemote = form.type == MockType.MAP_REMOTE

        mutableInvalidName.value = name.isEmpty()
        mutableInvalidTargetUrl.value = isRemote && target == null
        mutableInvalidStatusCode.value = !isRemote && statusCode == null
        mutableInvalidHeaders.value = !isRemote && !validHeaders
        val typeIsValid = if (isRemote) target != null else validLocal
        if (name.isEmpty() || !typeIsValid) return null

        return Mock(
            requestUrl = requestUrl,
            requestMethod = requestMethod,
            name = name,
            type = form.type,
            targetUrl = target?.toString(),
            localMode = form.localMode.takeIf { validLocal },
            statusCode = statusCode.takeIf { validLocal },
            headers = form.headers.trim().takeIf { validLocal },
            body = form.body.takeIf { validLocal },
            updatedAt = 0L,
        )
    }

    fun deleteMock(mock: Mock) {
        viewModelScope.launch {
            if (attempt { repository.deleteMock(mock.id) } != null) {
                mutableMockDeleted.value = true
            }
        }
    }

    private fun Mock.toForm(recorded: MockForm): MockForm {
        val mode = localMode
        val code = statusCode
        val local =
            if (mode != null && code != null) {
                recorded.copy(
                    localMode = mode,
                    statusCode = code.toString(),
                    headers = headers.orEmpty(),
                    body = body.orEmpty(),
                )
            } else {
                recorded
            }
        return local.copy(name = name, type = type, targetUrl = targetUrl.orEmpty())
    }

    /** A new Map Local mock answering with the response recorded in [transaction]. */
    private fun recordedForm(transaction: HttpTransaction?) =
        MockForm(
            name = "",
            type = MockType.MAP_LOCAL,
            targetUrl = "",
            localMode = MapLocalMode.MOCK,
            statusCode = (transaction?.responseCode ?: HTTP_OK).toString(),
            headers =
                formatMockHeaders(
                    transaction
                        ?.getParsedResponseHeaders()
                        .orEmpty()
                        // They describe how the recorded body was sent, not the body typed in the form.
                        .filterNot { header -> BODY_FRAMING_HEADERS.any { it.equals(header.name, ignoreCase = true) } },
                ),
            body = transaction?.responseBody?.let(::formatMockBody).orEmpty(),
        )
}
