package com.chuckerteam.chucker.internal.ui.mock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.databinding.ChuckerActivityMockEndpointBinding
import com.chuckerteam.chucker.internal.data.entity.MockEndpointWithMocks
import com.chuckerteam.chucker.internal.data.entity.toMockRuleUrl
import com.chuckerteam.chucker.internal.data.model.DialogData
import com.chuckerteam.chucker.internal.support.showDialog
import com.chuckerteam.chucker.internal.ui.BaseChuckerActivity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Turns mocking of an endpoint on or off, and chooses which of its mocks is applied. */
internal class MockEndpointActivity : BaseChuckerActivity() {
    private val viewModel: MockEndpointViewModel by viewModels()

    private lateinit var binding: ChuckerActivityMockEndpointBinding
    private lateinit var requestUrl: String
    private lateinit var requestMethod: String
    private var transactionId: Long? = null
    private var isNetworkInterceptor = false
    private var state: MockEndpointWithMocks? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_REQUEST_URL)?.toHttpUrlOrNull()?.toMockRuleUrl()
        val method = intent.getStringExtra(EXTRA_REQUEST_METHOD)?.uppercase()
        if (url == null || method == null) {
            finish()
            return
        }
        requestUrl = url
        requestMethod = method
        transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, NONE).takeIf { it != NONE }
        // Mocking has to happen before the call reaches the network, which a network interceptor can't do.
        isNetworkInterceptor = intent.getBooleanExtra(EXTRA_NETWORK_INTERCEPTOR, false)

        binding = ChuckerActivityMockEndpointBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, binding.appBarLayout)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.endpoint.text = getString(R.string.chucker_mock_endpoint, requestMethod, requestUrl)
        binding.networkInterceptorNotice.isVisible = isNetworkInterceptor
        binding.enabledSwitch.isEnabled = !isNetworkInterceptor
        binding.newMock.isEnabled = !isNetworkInterceptor
        binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.mocksSection.isVisible = isChecked
            if (isChecked != state?.isEnabled) viewModel.setEnabled(requestUrl, requestMethod, isChecked)
        }
        binding.newMock.setOnClickListener { openMock(mockId = null) }

        val adapter =
            MockAdapter(
                onMockChosen = { mock -> viewModel.selectMock(requestUrl, requestMethod, mock.id) },
                onMockEdited = { mock -> openMock(mock.id) },
            )
        binding.mocksRecyclerView.adapter = adapter
        binding.mocksRecyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        viewModel.endpoint(requestUrl, requestMethod).observe(this) { endpoint ->
            state = endpoint
            binding.enabledSwitch.isChecked = endpoint.isEnabled
            binding.mocksSection.isVisible = endpoint.isEnabled
            val chosenId = endpoint.activeMock?.id
            adapter.submitList(endpoint.mocks.map { MockAdapter.Item(it, isChosen = it.id == chosenId) })
            binding.mocksRecyclerView.isVisible = endpoint.mocks.isNotEmpty()
            binding.chooseHint.isVisible = endpoint.mocks.isNotEmpty()
            binding.emptyText.isVisible = endpoint.mocks.isEmpty()
            invalidateOptionsMenu()
        }
        viewModel.databaseFailed.observe(this) { showToast(getString(R.string.chucker_mock_failed)) }
    }

    private fun openMock(mockId: Long?) {
        startActivity(
            MockEditActivity.createIntent(
                this,
                requestUrl,
                requestMethod,
                mockId = mockId,
                transactionId = transactionId,
                isNetworkInterceptor = isNetworkInterceptor,
            ),
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chucker_mock_endpoint, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.delete_endpoint).isVisible = state?.endpoint != null
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == R.id.delete_endpoint) {
            showDialog(
                DialogData(
                    title = getString(R.string.chucker_mock_delete_endpoint),
                    message = getString(R.string.chucker_mock_delete_endpoint_confirmation),
                    positiveButtonText = getString(R.string.chucker_mock_delete_all_positive),
                    negativeButtonText = getString(R.string.chucker_cancel),
                ),
                onPositiveClick = { viewModel.deleteEndpoint(requestUrl, requestMethod) },
                onNegativeClick = null,
            )
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val EXTRA_REQUEST_URL = "request_url"
        private const val EXTRA_REQUEST_METHOD = "request_method"
        private const val EXTRA_TRANSACTION_ID = "transaction_id"
        private const val EXTRA_NETWORK_INTERCEPTOR = "network_interceptor"
        private const val NONE = -1L

        fun start(
            context: Context,
            requestUrl: String,
            requestMethod: String,
            transactionId: Long? = null,
            isNetworkInterceptor: Boolean = false,
        ) {
            context.startActivity(createIntent(context, requestUrl, requestMethod, transactionId, isNetworkInterceptor))
        }

        fun createIntent(
            context: Context,
            requestUrl: String,
            requestMethod: String,
            transactionId: Long? = null,
            isNetworkInterceptor: Boolean = false,
        ): Intent =
            Intent(context, MockEndpointActivity::class.java)
                .putExtra(EXTRA_REQUEST_URL, requestUrl)
                .putExtra(EXTRA_REQUEST_METHOD, requestMethod)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId ?: NONE)
                .putExtra(EXTRA_NETWORK_INTERCEPTOR, isNetworkInterceptor)
    }
}
