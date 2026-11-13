package com.chuckerteam.chucker.internal.support

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chuckerteam.chucker.util.TestTransactionFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class TransactionDetailsSharableTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `create sharable GET transaction content`() {
        val transaction = TestTransactionFactory.createTransaction("GET")
        val sharableTransaction = TransactionDetailsSharable(transaction, encodeUrls = false)

        val sharedContent = sharableTransaction.toSharableUtf8Content(context)

        assertThat(sharedContent).isEqualTo(TestTransactionFactory.expectedGetHttpTransaction)
    }

    @Test
    fun `create sharable POST transaction content`() {
        val transaction = TestTransactionFactory.createTransaction("POST")
        val sharableTransaction = TransactionDetailsSharable(transaction, encodeUrls = false)

        val sharedContent = sharableTransaction.toSharableUtf8Content(context)

        assertThat(sharedContent).isEqualTo(TestTransactionFactory.expectedHttpPostTransaction)
    }

    @Test
    fun `sharable content of a mapped transaction tells where it was sent`() {
        val transaction =
            TestTransactionFactory.createTransaction("GET").apply {
                mappedUrl = "https://mock.example.com/"
            }
        val sharableTransaction = TransactionDetailsSharable(transaction, encodeUrls = false)

        val sharedContent = sharableTransaction.toSharableUtf8Content(context)

        assertThat(sharedContent).contains("Mock: Map Remote → https://mock.example.com/\n")
    }

    @Test
    fun `sharable content tells when a mock rule was not applied`() {
        val transaction =
            TestTransactionFactory.createTransaction("GET").apply {
                isMockIgnored = true
            }
        val sharableTransaction = TransactionDetailsSharable(transaction, encodeUrls = false)

        val sharedContent = sharableTransaction.toSharableUtf8Content(context)

        assertThat(sharedContent).contains("Mock: Not applied: mocking requires addInterceptor\n")
    }

    @Test
    fun `sharable content tells how a Map Local rule answered`() {
        val transaction =
            TestTransactionFactory.createTransaction("GET").apply {
                mapLocalMode = "OVERRIDE"
            }
        val sharableTransaction = TransactionDetailsSharable(transaction, encodeUrls = false)

        val sharedContent = sharableTransaction.toSharableUtf8Content(context)

        assertThat(sharedContent).contains("Mock: Map Local · Override\n")
    }
}
