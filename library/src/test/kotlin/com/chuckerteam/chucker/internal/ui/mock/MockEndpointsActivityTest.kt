package com.chuckerteam.chucker.internal.ui.mock

import android.content.DialogInterface
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.MutableLiveData
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.internal.data.entity.MockEndpoint
import com.chuckerteam.chucker.internal.data.entity.MockEndpointWithMocks
import com.chuckerteam.chucker.internal.data.repository.MockRepository
import com.chuckerteam.chucker.internal.data.repository.RepositoryProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
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
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
internal class MockEndpointsActivityTest {
    private val repository = mockk<MockRepository>(relaxUnitFun = true)
    private val rules = MutableLiveData<List<MockEndpointWithMocks>>()

    @Before
    fun setUp() {
        every { repository.getEndpoints() } returns rules
        coEvery { repository.disableAll() } returns 1
        coEvery { repository.deleteAll() } returns 2
        mockkObject(RepositoryProvider)
        every { RepositoryProvider.initialize(any()) } just runs
        every { RepositoryProvider.mock() } returns repository
    }

    @After
    fun tearDown() = unmockkAll()

    private fun launch(vararg shownRules: MockEndpointWithMocks): MockEndpointsActivity {
        rules.value = shownRules.toList()
        return Robolectric
            .buildActivity(MockEndpointsActivity::class.java)
            .setup()
            .get()
            .also { idle() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun MockEndpointsActivity.menuItem(id: Int) = shadowOf(this).optionsMenu.findItem(id)

    private fun rule(isEnabled: Boolean): MockEndpointWithMocks {
        val method = if (isEnabled) "GET" else "POST"
        return MockEndpointWithMocks(
            requestUrl = "https://example.com/users",
            requestMethod = method,
            endpoint = MockEndpoint("https://example.com/users", method, isEnabled, null, updatedAt = 0L),
            mocks = emptyList(),
        )
    }

    @Test
    fun `actions are hidden without rules`() {
        val activity = launch()

        assertThat(activity.menuItem(R.id.disable_all_rules).isVisible).isFalse()
        assertThat(activity.menuItem(R.id.delete_all_rules).isVisible).isFalse()
    }

    @Test
    fun `disable all is hidden when every rule is off already`() {
        val activity = launch(rule(isEnabled = false))

        assertThat(activity.menuItem(R.id.disable_all_rules).isVisible).isFalse()
        assertThat(activity.menuItem(R.id.delete_all_rules).isVisible).isTrue()
    }

    @Test
    fun `disable all turns off the rules right away`() {
        val activity = launch(rule(isEnabled = true), rule(isEnabled = false))
        assertThat(activity.menuItem(R.id.disable_all_rules).isVisible).isTrue()

        shadowOf(activity).clickMenuItem(R.id.disable_all_rules)
        idle()

        coVerify(exactly = 1) { repository.disableAll() }
    }

    @Test
    fun `delete all asks for confirmation first`() {
        val activity = launch(rule(isEnabled = true))

        shadowOf(activity).clickMenuItem(R.id.delete_all_rules)
        idle()
        coVerify(exactly = 0) { repository.deleteAll() }

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        coVerify(exactly = 1) { repository.deleteAll() }
    }

    @Test
    fun `cancelling the confirmation deletes nothing`() {
        val activity = launch(rule(isEnabled = true))

        shadowOf(activity).clickMenuItem(R.id.delete_all_rules)
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()

        coVerify(exactly = 0) { repository.deleteAll() }
    }
}
