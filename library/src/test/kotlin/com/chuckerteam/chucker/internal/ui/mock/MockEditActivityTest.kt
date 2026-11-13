package com.chuckerteam.chucker.internal.ui.mock

import android.app.Application
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.MapLocalMode
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockType
import com.chuckerteam.chucker.internal.data.repository.MockRepository
import com.chuckerteam.chucker.internal.data.repository.RepositoryProvider
import com.google.android.material.textfield.TextInputLayout
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
internal class MockEditActivityTest {
    private val repository = mockk<MockRepository>(relaxUnitFun = true)
    private val savedMock = slot<Mock>()

    @Before
    fun setUp() {
        coEvery { repository.saveMock(capture(savedMock)) } returns 1L
        mockkObject(RepositoryProvider)
        every { RepositoryProvider.initialize(any()) } just runs
        every { RepositoryProvider.mock() } returns repository
    }

    @After
    fun tearDown() = unmockkAll()

    private fun launch(
        mockId: Long? = null,
        isNetworkInterceptor: Boolean = false,
    ): ActivityController<MockEditActivity> {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val intent =
            MockEditActivity.createIntent(
                application,
                REQUEST_URL,
                "GET",
                mockId = mockId,
                isNetworkInterceptor = isNetworkInterceptor,
            )
        return Robolectric
            .buildActivity(MockEditActivity::class.java, intent)
            .setup()
            .also { idle() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun MockEditActivity.layout(id: Int) = findViewById<TextInputLayout>(id)

    private fun MockEditActivity.isShown(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

    private fun TextInputLayout.input() = editText?.text.toString()

    private fun TextInputLayout.setInput(text: String) {
        editText?.setText(text)
    }

    private fun MockEditActivity.save() {
        findViewById<Button>(R.id.save).performClick()
        idle()
    }

    @Test
    fun `new mock of the endpoint starts as a map local`() {
        val activity = launch().get()

        assertThat(activity.findViewById<TextView>(R.id.requestUrl).text.toString()).isEqualTo(REQUEST_URL)
        assertThat(activity.layout(R.id.nameLayout).input()).isEmpty()
        assertThat(activity.isShown(R.id.mapLocalSection)).isTrue()
        assertThat(activity.isShown(R.id.mapRemoteSection)).isFalse()
        assertThat(activity.layout(R.id.statusCodeLayout).input()).isEqualTo("200")
    }

    @Test
    fun `selecting a type shows only its fields`() {
        val activity = launch().get()

        activity.findViewById<RadioGroup>(R.id.typeGroup).check(R.id.typeMapRemote)

        assertThat(activity.isShown(R.id.mapRemoteSection)).isTrue()
        assertThat(activity.isShown(R.id.mapLocalSection)).isFalse()
    }

    @Test
    fun `typed mock is saved with its name`() {
        val activity = launch().get()
        activity.layout(R.id.nameLayout).setInput("Server down")
        activity.findViewById<RadioGroup>(R.id.localModeGroup).check(R.id.localModeOverride)
        activity.layout(R.id.statusCodeLayout).setInput("503")
        activity.layout(R.id.bodyLayout).setInput("down")

        activity.save()

        val mock = savedMock.captured
        assertThat(mock.name).isEqualTo("Server down")
        assertThat(mock.requestUrl).isEqualTo(REQUEST_URL)
        assertThat(mock.localMode).isEqualTo(MapLocalMode.OVERRIDE)
        assertThat(mock.statusCode).isEqualTo(503)
        assertThat(mock.body).isEqualTo("down")
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `missing name and invalid fields are shown`() {
        val activity = launch().get()
        activity.layout(R.id.statusCodeLayout).setInput("42")

        activity.save()

        assertThat(activity.layout(R.id.nameLayout).error).isNotNull()
        assertThat(activity.layout(R.id.statusCodeLayout).error).isNotNull()
        coVerify(exactly = 0) { repository.saveMock(any()) }
    }

    @Test
    fun `saved mock is shown and can be deleted`() {
        coEvery { repository.getMock(3L) } returns
            Mock(
                id = 3L,
                requestUrl = REQUEST_URL,
                requestMethod = "GET",
                name = "Staging",
                type = MockType.MAP_REMOTE,
                targetUrl = "https://mock.example.com/",
                localMode = null,
                statusCode = null,
                headers = null,
                body = null,
                updatedAt = 0L,
            )

        val activity = launch(mockId = 3L).get()

        assertThat(activity.layout(R.id.nameLayout).input()).isEqualTo("Staging")
        assertThat(activity.findViewById<RadioGroup>(R.id.typeGroup).checkedRadioButtonId).isEqualTo(R.id.typeMapRemote)
        assertThat(activity.layout(R.id.targetUrlLayout).input()).isEqualTo("https://mock.example.com/")

        shadowOf(activity).clickMenuItem(R.id.delete_mock)
        idle()

        coVerify(exactly = 1) { repository.deleteMock(3L) }
    }

    @Test
    fun `edited mock is saved under its id`() {
        coEvery { repository.getMock(3L) } returns null
        val activity = launch(mockId = 3L).get()
        activity.layout(R.id.nameLayout).setInput("Renamed")

        activity.save()

        assertThat(savedMock.captured.id).isEqualTo(3L)
    }

    @Test
    fun `typed body survives a configuration change`() {
        val controller = launch()
        controller.get().layout(R.id.bodyLayout).setInput("typed body")

        controller.recreate()
        idle()

        assertThat(controller.get().layout(R.id.bodyLayout).input()).isEqualTo("typed body")
    }

    @Test
    fun `searching the body counts the matches and moves between them`() {
        val activity = launch().get()
        val body = activity.findViewById<android.widget.EditText>(R.id.bodyInput)
        body.setText("first match, second MATCH")
        val search =
            MockBodySearch(
                body,
                activity.findViewById(R.id.scrollView),
                activity.findViewById(R.id.searchBar),
                activity.findViewById(R.id.searchSummary),
            )
        val summary = activity.findViewById<TextView>(R.id.searchSummary)

        search.search("match")
        assertThat(activity.isShown(R.id.searchBar)).isTrue()
        assertThat(summary.text.toString()).isEqualTo("1 / 2")

        search.next()
        assertThat(summary.text.toString()).isEqualTo("2 / 2")
        search.next()
        assertThat(summary.text.toString()).isEqualTo("1 / 2")
        search.previous()
        assertThat(summary.text.toString()).isEqualTo("2 / 2")

        search.close()
        assertThat(activity.isShown(R.id.searchBar)).isFalse()
        assertThat(body.selectionStart).isEqualTo(20)
        assertThat(body.selectionEnd).isEqualTo(25)
        assertThat(body.text.getSpans(0, body.length(), android.text.style.BackgroundColorSpan::class.java)).isEmpty()
    }

    @Test
    fun `searching text that is not in the body says so`() {
        val activity = launch().get()
        val body = activity.findViewById<android.widget.EditText>(R.id.bodyInput)
        body.setText("nothing here")
        val search =
            MockBodySearch(
                body,
                activity.findViewById(R.id.scrollView),
                activity.findViewById(R.id.searchBar),
                activity.findViewById(R.id.searchSummary),
            )

        search.search("missing")

        assertThat(activity.findViewById<TextView>(R.id.searchSummary).text.toString()).isEqualTo("No results")
        search.close()
        assertThat(body.text.toString()).isEqualTo("nothing here")
    }

    private fun MockEditActivity.pressBack() {
        onBackPressedDispatcher.onBackPressed()
        idle()
    }

    private fun latestDialog() = ShadowDialog.getLatestDialog() as AlertDialog?

    @Test
    fun `leaving an unchanged form closes it right away`() {
        val activity = launch().get()

        activity.pressBack()

        assertThat(activity.isFinishing).isTrue()
        assertThat(latestDialog()).isNull()
    }

    @Test
    fun `leaving a changed form asks first, and keeping it stays on the form`() {
        val activity = launch().get()
        activity.layout(R.id.bodyLayout).setInput("edited body")

        activity.pressBack()

        assertThat(activity.isFinishing).isFalse()
        val dialog = latestDialog()!!
        assertThat(dialog.isShowing).isTrue()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()
        assertThat(activity.isFinishing).isFalse()
        assertThat(activity.layout(R.id.bodyLayout).input()).isEqualTo("edited body")
    }

    @Test
    fun `discarding the changes closes the form without saving`() {
        val activity = launch().get()
        activity.findViewById<RadioGroup>(R.id.typeGroup).check(R.id.typeMapRemote)

        activity.onSupportNavigateUp()
        idle()
        latestDialog()!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        assertThat(activity.isFinishing).isTrue()
        coVerify(exactly = 0) { repository.saveMock(any()) }
    }

    @Test
    fun `leaving while searching closes the search first`() {
        val activity = launch().get()
        val search = shadowOf(activity).optionsMenu.findItem(R.id.search_body)
        search.expandActionView()
        idle()

        activity.pressBack()

        assertThat(search.isActionViewExpanded).isFalse()
        assertThat(activity.isFinishing).isFalse()
    }

    @Test
    fun `call of a network interceptor shows a notice and cannot be saved`() {
        val activity = launch(isNetworkInterceptor = true).get()

        assertThat(activity.isShown(R.id.networkInterceptorNotice)).isTrue()
        assertThat(activity.findViewById<Button>(R.id.save).isEnabled).isFalse()
    }

    private companion object {
        const val REQUEST_URL = "https://example.com/users"
    }
}
