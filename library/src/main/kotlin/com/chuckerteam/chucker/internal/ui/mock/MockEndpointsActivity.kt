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
import com.chuckerteam.chucker.databinding.ChuckerActivityMockRulesBinding
import com.chuckerteam.chucker.internal.data.entity.MockEndpointWithMocks
import com.chuckerteam.chucker.internal.data.model.DialogData
import com.chuckerteam.chucker.internal.support.showDialog
import com.chuckerteam.chucker.internal.ui.BaseChuckerActivity

/** Every mocked endpoint, with actions on all of them at once. */
internal class MockEndpointsActivity : BaseChuckerActivity() {
    private val viewModel: MockEndpointsViewModel by viewModels()

    private var endpoints: List<MockEndpointWithMocks> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ChuckerActivityMockRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, binding.appBarLayout)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val adapter =
            MockEndpointAdapter { endpoint ->
                MockEndpointActivity.start(this, endpoint.requestUrl, endpoint.requestMethod)
            }
        binding.rulesRecyclerView.adapter = adapter
        binding.rulesRecyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        viewModel.endpoints.observe(this) { endpoints ->
            this.endpoints = endpoints
            adapter.submitList(endpoints)
            binding.emptyText.isVisible = endpoints.isEmpty()
            invalidateOptionsMenu()
        }
        viewModel.disabled.observe(this) { count ->
            showToast(getString(R.string.chucker_mock_all_disabled, count))
        }
        viewModel.deleted.observe(this) { count ->
            showToast(getString(R.string.chucker_mock_all_deleted, count))
        }
        viewModel.databaseFailed.observe(this) { showToast(getString(R.string.chucker_mock_failed)) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chucker_mock_rules, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.disable_all_rules).isVisible = endpoints.any { it.isEnabled }
        menu.findItem(R.id.delete_all_rules).isVisible = endpoints.isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.disable_all_rules -> {
                viewModel.disableAll()
                true
            }
            R.id.delete_all_rules -> {
                confirmDeleteAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun confirmDeleteAll() {
        showDialog(
            DialogData(
                title = getString(R.string.chucker_mock_delete_all),
                message = getString(R.string.chucker_mock_delete_all_confirmation),
                positiveButtonText = getString(R.string.chucker_mock_delete_all_positive),
                negativeButtonText = getString(R.string.chucker_cancel),
            ),
            onPositiveClick = viewModel::deleteAll,
            onNegativeClick = null,
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, MockEndpointsActivity::class.java))
        }
    }
}
