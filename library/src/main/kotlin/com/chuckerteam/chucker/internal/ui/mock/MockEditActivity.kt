package com.chuckerteam.chucker.internal.ui.mock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.databinding.ChuckerActivityMockEditBinding
import com.chuckerteam.chucker.internal.data.entity.MapLocalMode
import com.chuckerteam.chucker.internal.data.entity.Mock
import com.chuckerteam.chucker.internal.data.entity.MockType
import com.chuckerteam.chucker.internal.data.model.DialogData
import com.chuckerteam.chucker.internal.support.showDialog
import com.chuckerteam.chucker.internal.ui.BaseChuckerActivity

/** Adds a mock to an endpoint, or edits one of its mocks. */
internal class MockEditActivity : BaseChuckerActivity() {
    private val viewModel: MockEditViewModel by viewModels()

    private lateinit var binding: ChuckerActivityMockEditBinding
    private lateinit var requestUrl: String
    private lateinit var requestMethod: String
    private var mockId: Long? = null
    private var existingMock: Mock? = null
    private lateinit var bodySearch: MockBodySearch
    private var searchItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_REQUEST_URL)
        val method = intent.getStringExtra(EXTRA_REQUEST_METHOD)
        if (url == null || method == null) {
            finish()
            return
        }
        requestUrl = url
        requestMethod = method
        mockId = intent.getLongExtra(EXTRA_MOCK_ID, NONE).takeIf { it != NONE }
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, NONE).takeIf { it != NONE }

        binding = ChuckerActivityMockEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, binding.appBarLayout)
        val title = if (mockId == null) R.string.chucker_mock_new_title else R.string.chucker_mock_edit_title
        binding.toolbar.setTitle(title)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.method.text = requestMethod
        binding.requestUrl.text = requestUrl
        binding.typeGroup.setOnCheckedChangeListener { _, _ -> updateSections() }
        binding.save.setOnClickListener { save() }
        bodySearch = MockBodySearch(binding.bodyInput, binding.scrollView, binding.searchBar, binding.searchSummary)
        binding.searchNext.setOnClickListener { bodySearch.next() }
        binding.searchPrevious.setOnClickListener { bodySearch.previous() }
        onBackPressedDispatcher.addCallback(this) { leave() }
        // Mocking has to happen before the call reaches the network, which a network interceptor can't do.
        val isNetworkInterceptor = intent.getBooleanExtra(EXTRA_NETWORK_INTERCEPTOR, false)
        binding.networkInterceptorNotice.isVisible = isNetworkInterceptor
        binding.save.isEnabled = !isNetworkInterceptor
        updateSections()

        viewModel.loadForm(mockId, transactionId).observe(this) { loaded ->
            onFormLoaded(loaded, restoreInput = savedInstanceState == null)
        }
        observeErrors()
        viewModel.mockSaved.observe(this) { closeWith(R.string.chucker_mock_saved) }
        viewModel.mockDeleted.observe(this) { closeWith(R.string.chucker_mock_deleted) }
        viewModel.databaseFailed.observe(this) { showToast(getString(R.string.chucker_mock_failed)) }
    }

    private fun observeErrors() {
        viewModel.invalidName.observe(this) { invalid ->
            binding.nameLayout.error = getString(R.string.chucker_mock_invalid_name).takeIf { invalid }
        }
        viewModel.invalidTargetUrl.observe(this) { invalid ->
            binding.targetUrlLayout.error = getString(R.string.chucker_mock_invalid_url).takeIf { invalid }
        }
        viewModel.invalidStatusCode.observe(this) { invalid ->
            binding.statusCodeLayout.error = getString(R.string.chucker_mock_invalid_status_code).takeIf { invalid }
        }
        viewModel.invalidHeaders.observe(this) { invalid ->
            binding.headersLayout.error = getString(R.string.chucker_mock_invalid_headers).takeIf { invalid }
        }
    }

    /** Shows the fields of the selected type, and the search of the body for Map Local. */
    private fun updateSections() {
        binding.mapRemoteSection.isVisible = selectedType() == MockType.MAP_REMOTE
        binding.mapLocalSection.isVisible = selectedType() == MockType.MAP_LOCAL
        invalidateOptionsMenu()
    }

    private fun onFormLoaded(
        loaded: LoadedMockForm,
        restoreInput: Boolean,
    ) {
        val form = loaded.form
        if (restoreInput) {
            binding.nameInput.setText(form.name)
            binding.typeGroup.check(if (form.type == MockType.MAP_REMOTE) R.id.typeMapRemote else R.id.typeMapLocal)
            binding.targetUrlInput.setText(form.targetUrl)
            binding.localModeGroup.check(
                if (form.localMode == MapLocalMode.OVERRIDE) R.id.localModeOverride else R.id.localModeMock,
            )
            binding.statusCodeInput.setText(form.statusCode)
            binding.headersInput.setText(form.headers)
        }
        // The other fields restore themselves, the body doesn't: see MockEditViewModel.bodyDraft.
        binding.bodyInput.setText(viewModel.bodyDraft ?: form.body)
        updateSections()
        existingMock = loaded.existingMock
        invalidateOptionsMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::binding.isInitialized) {
            viewModel.bodyDraft = binding.bodyInput.text.toString()
        }
    }

    private fun selectedType() =
        if (binding.typeGroup.checkedRadioButtonId == R.id.typeMapRemote) MockType.MAP_REMOTE else MockType.MAP_LOCAL

    private fun selectedLocalMode() =
        if (binding.localModeGroup.checkedRadioButtonId == R.id.localModeOverride) {
            MapLocalMode.OVERRIDE
        } else {
            MapLocalMode.MOCK
        }

    private fun typedForm() =
        MockForm(
            name = binding.nameInput.text.toString(),
            type = selectedType(),
            targetUrl = binding.targetUrlInput.text.toString(),
            localMode = selectedLocalMode(),
            statusCode = binding.statusCodeInput.text.toString(),
            headers = binding.headersInput.text.toString(),
            body = binding.bodyInput.text.toString(),
        )

    private fun save() {
        viewModel.saveMock(requestUrl, requestMethod, mockId, typedForm())
    }

    /** Closes the search if open, otherwise the screen, asking first if what was typed would be lost. */
    private fun leave() {
        val search = searchItem
        if (search != null && search.isActionViewExpanded) {
            search.collapseActionView()
            return
        }
        val initialForm = viewModel.initialForm
        if (initialForm == null || typedForm() == initialForm) {
            finish()
            return
        }
        showDialog(
            DialogData(
                title = getString(R.string.chucker_mock_discard_title),
                message = getString(R.string.chucker_mock_discard_message),
                positiveButtonText = getString(R.string.chucker_mock_discard),
                negativeButtonText = getString(R.string.chucker_mock_keep_editing),
            ),
            onPositiveClick = ::finish,
            onNegativeClick = null,
        )
    }

    private fun closeWith(message: Int) {
        showToast(getString(message))
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chucker_mock_edit, menu)
        val searchItem = menu.findItem(R.id.search_body).also { searchItem = it }
        (searchItem.actionView as SearchView).setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    bodySearch.next()
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    bodySearch.search(newText)
                    return true
                }
            },
        )
        searchItem.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    bodySearch.close()
                    return true
                }
            },
        )
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.delete_mock).isVisible = existingMock != null
        menu.findItem(R.id.search_body).isVisible = selectedType() == MockType.MAP_LOCAL
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == R.id.delete_mock) {
            existingMock?.let(viewModel::deleteMock)
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    override fun onSupportNavigateUp(): Boolean {
        leave()
        return true
    }

    companion object {
        private const val EXTRA_REQUEST_URL = "request_url"
        private const val EXTRA_REQUEST_METHOD = "request_method"
        private const val EXTRA_MOCK_ID = "mock_id"
        private const val EXTRA_TRANSACTION_ID = "transaction_id"
        private const val EXTRA_NETWORK_INTERCEPTOR = "network_interceptor"
        private const val NONE = -1L

        /**
         * @param requestUrl the endpoint, as returned by `toMockRuleUrl`.
         * @param mockId the mock to edit, or null to add one.
         * @param transactionId the call whose response fills a new mock.
         */
        @Suppress("LongParameterList")
        fun createIntent(
            context: Context,
            requestUrl: String,
            requestMethod: String,
            mockId: Long? = null,
            transactionId: Long? = null,
            isNetworkInterceptor: Boolean = false,
        ): Intent =
            Intent(context, MockEditActivity::class.java)
                .putExtra(EXTRA_REQUEST_URL, requestUrl)
                .putExtra(EXTRA_REQUEST_METHOD, requestMethod)
                .putExtra(EXTRA_MOCK_ID, mockId ?: NONE)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId ?: NONE)
                .putExtra(EXTRA_NETWORK_INTERCEPTOR, isNetworkInterceptor)
    }
}
