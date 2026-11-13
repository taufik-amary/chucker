package com.chuckerteam.chucker.internal.ui.mock

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.databinding.ChuckerListItemMockEndpointBinding
import com.chuckerteam.chucker.internal.data.entity.MockEndpointWithMocks

/** Every mocked endpoint, with the mock chosen for it. */
internal class MockEndpointAdapter(
    private val onEndpointClick: (MockEndpointWithMocks) -> Unit,
) : ListAdapter<MockEndpointWithMocks, MockEndpointAdapter.ViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            ChuckerListItemMockEndpointBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) = holder.bind(getItem(position))

    inner class ViewHolder(
        private val binding: ChuckerListItemMockEndpointBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(endpoint: MockEndpointWithMocks) {
            val context = binding.root.context
            val activeMock = endpoint.activeMock
            val summary =
                if (activeMock == null) {
                    context.getString(R.string.chucker_mock_none_chosen)
                } else {
                    val how = activeMock.describe(context)
                    context.getString(R.string.chucker_mock_summary_named, activeMock.name, how)
                }
            binding.requestUrl.text = "${endpoint.requestMethod} ${endpoint.requestUrl}"
            binding.summary.text =
                if (endpoint.isEnabled) summary else "${context.getString(R.string.chucker_mock_off)} · $summary"
            // On the texts rather than the row, whose alpha the item animator resets after an update.
            val alpha = if (endpoint.isEnabled && activeMock != null) ENABLED_ALPHA else DISABLED_ALPHA
            binding.requestUrl.alpha = alpha
            binding.summary.alpha = alpha
            binding.root.setOnClickListener { onEndpointClick(endpoint) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MockEndpointWithMocks>() {
        override fun areItemsTheSame(
            oldItem: MockEndpointWithMocks,
            newItem: MockEndpointWithMocks,
        ) = oldItem.requestUrl == newItem.requestUrl && oldItem.requestMethod == newItem.requestMethod

        override fun areContentsTheSame(
            oldItem: MockEndpointWithMocks,
            newItem: MockEndpointWithMocks,
        ) = oldItem == newItem
    }

    private companion object {
        const val ENABLED_ALPHA = 1f
        const val DISABLED_ALPHA = 0.5f
    }
}
