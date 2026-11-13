package com.chuckerteam.chucker.internal.ui.mock

import android.app.Application
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.MapLocalMode
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockEndpoint
import com.chuckerteam.chucker.internal.data.entity.MockEndpointWithMocks
import com.chuckerteam.chucker.internal.data.entity.MockType
import com.chuckerteam.chucker.internal.data.repository.MockRepository
import com.chuckerteam.chucker.internal.data.repository.RepositoryProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
internal class MockEndpointActivityTest {
    private val repository = mockk<MockRepository>(relaxUnitFun = true)
    private val endpoint = MutableLiveData<MockEndpointWithMocks>()

    @Before
    fun setUp() {
        every { repository.getEndpoint(REQUEST_URL, "GET") } returns endpoint
        mockkObject(RepositoryProvider)
        every { RepositoryProvider.initialize(any()) } just runs
        every { RepositoryProvider.mock() } returns repository
    }

    @After
    fun tearDown() = unmockkAll()

    private fun mock(
        id: Long,
        name: String,
    ) = Mock(id, REQUEST_URL, "GET", name, MockType.MAP_LOCAL, null, MapLocalMode.MOCK, 503, "", "", 0L)

    private fun state(
        isEnabled: Boolean,
        activeMockId: Long? = null,
        vararg mocks: Mock,
    ) = MockEndpointWithMocks(
        requestUrl = REQUEST_URL,
        requestMethod = "GET",
        endpoint = MockEndpoint(REQUEST_URL, "GET", isEnabled, activeMockId, 0L),
        mocks = mocks.toList(),
    )

    private fun launch(
        state: MockEndpointWithMocks,
        isNetworkInterceptor: Boolean = false,
    ): MockEndpointActivity {
        endpoint.value = state
        val application = ApplicationProvider.getApplicationContext<Application>()
        // The URL of a call: the screen is about its endpoint, without the query.
        val intent =
            MockEndpointActivity.createIntent(
                application,
                "$REQUEST_URL?page=1",
                "get",
                transactionId = 9L,
                isNetworkInterceptor = isNetworkInterceptor,
            )
        return Robolectric
            .buildActivity(MockEndpointActivity::class.java, intent)
            .setup()
            .get()
            .also { idle() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun MockEndpointActivity.isShown(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

    private fun MockEndpointActivity.row(position: Int): View {
        val list = findViewById<RecyclerView>(R.id.mocksRecyclerView)
        list.measure(0, 0)
        list.layout(0, 0, 1000, 1000)
        return list.findViewHolderForAdapterPosition(position)!!.itemView
    }

    @Test
    fun `mocks are hidden while mocking is off`() {
        val activity = launch(state(isEnabled = false, mocks = arrayOf(mock(1L, "Server down"))))

        assertThat(activity.findViewById<SwitchCompat>(R.id.enabledSwitch).isChecked).isFalse()
        assertThat(activity.isShown(R.id.mocksSection)).isFalse()
    }

    @Test
    fun `turning mocking on shows the mocks and saves it`() {
        val activity = launch(state(isEnabled = false))

        activity.findViewById<SwitchCompat>(R.id.enabledSwitch).isChecked = true
        idle()

        assertThat(activity.isShown(R.id.mocksSection)).isTrue()
        assertThat(activity.isShown(R.id.emptyText)).isTrue()
        coVerify(exactly = 1) { repository.setEnabled(REQUEST_URL, "GET", true) }
    }

    @Test
    fun `chosen mock is checked and tapping another one chooses it`() {
        val mocks = arrayOf(mock(1L, "Happy path"), mock(2L, "Server down"))
        val activity = launch(state(isEnabled = true, activeMockId = 1L, *mocks))

        assertThat(activity.row(0).findViewById<RadioButton>(R.id.selected).isChecked).isTrue()
        assertThat(activity.row(1).findViewById<RadioButton>(R.id.selected).isChecked).isFalse()

        activity.row(1).performClick()
        idle()

        coVerify(exactly = 1) { repository.selectMock(REQUEST_URL, "GET", 2L) }
    }

    @Test
    fun `editing a mock opens it`() {
        val activity = launch(state(isEnabled = true, activeMockId = 1L, mock(1L, "Happy path")))

        activity.row(0).findViewById<Button>(R.id.edit).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertThat(intent.component?.className).isEqualTo(MockEditActivity::class.java.name)
        assertThat(intent.getLongExtra("mock_id", -1L)).isEqualTo(1L)
        assertThat(intent.getStringExtra("request_url")).isEqualTo(REQUEST_URL)
    }

    @Test
    fun `new mock is filled from the call the screen was opened from`() {
        val activity = launch(state(isEnabled = true))

        activity.findViewById<Button>(R.id.newMock).performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertThat(intent.getLongExtra("mock_id", -1L)).isEqualTo(-1L)
        assertThat(intent.getLongExtra("transaction_id", -1L)).isEqualTo(9L)
        assertThat(intent.getStringExtra("request_method")).isEqualTo("GET")
    }

    @Test
    fun `call of a network interceptor shows a notice and cannot be mocked`() {
        val activity = launch(state(isEnabled = false), isNetworkInterceptor = true)

        assertThat(activity.isShown(R.id.networkInterceptorNotice)).isTrue()
        assertThat(activity.findViewById<SwitchCompat>(R.id.enabledSwitch).isEnabled).isFalse()
    }

    private companion object {
        const val REQUEST_URL = "https://example.com/users"
    }
}
