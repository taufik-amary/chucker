package com.chuckerteam.chucker.internal.ui.mock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuckerteam.chucker.databinding.ChuckerListItemMockBinding
import com.chuckerteam.chucker.internal.data.entity.Mock

/** The mocks of an endpoint: tapping one chooses it, its edit button opens it. */
internal class MockAdapter(
    private val onMockChosen: (Mock) -> Unit,
    private val onMockEdited: (Mock) -> Unit,
) : ListAdapter<MockAdapter.Item, MockAdapter.ViewHolder>(DiffCallback) {
    data class Item(
        val mock: Mock,
        val isChosen: Boolean,
    )

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            ChuckerListItemMockBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) = holder.bind(getItem(position))

    inner class ViewHolder(
        private val binding: ChuckerListItemMockBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.selected.isChecked = item.isChosen
            binding.name.text = item.mock.name
            binding.summary.text = item.mock.describe(binding.root.context)
            binding.root.setOnClickListener { onMockChosen(item.mock) }
            binding.edit.setOnClickListener { onMockEdited(item.mock) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(
            oldItem: Item,
            newItem: Item,
        ) = oldItem.mock.id == newItem.mock.id

        override fun areContentsTheSame(
            oldItem: Item,
            newItem: Item,
        ) = oldItem == newItem
    }
}
