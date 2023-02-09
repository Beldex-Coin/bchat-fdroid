package com.thoughtcrimes.securesms.contacts.blocked

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.beldex.libbchat.utilities.recipients.Recipient
import com.thoughtcrimes.securesms.mms.GlideApp
import io.beldex.bchat.R
import io.beldex.bchat.databinding.BlockedContactLayoutBinding

class BlockedContactsAdapter: ListAdapter<Recipient, BlockedContactsAdapter.ViewHolder>(RecipientDiffer()) {

    class RecipientDiffer: DiffUtil.ItemCallback<Recipient>() {
        override fun areItemsTheSame(oldItem: Recipient, newItem: Recipient) = oldItem === newItem
        override fun areContentsTheSame(oldItem: Recipient, newItem: Recipient) = oldItem == newItem
    }

    private val selectedItems = mutableListOf<Recipient>()

    fun getSelectedItems() = selectedItems

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.blocked_contact_layout, parent, false)
        return ViewHolder(itemView)
    }

    private fun toggleSelection(recipient: Recipient, isSelected: Boolean, position: Int) {
        if (isSelected) {
            selectedItems -= recipient
        } else {
            selectedItems += recipient
        }
        notifyItemChanged(position)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recipient = getItem(position)
        val isSelected = recipient in selectedItems
        holder.bind(recipient, isSelected) {
            toggleSelection(recipient, isSelected, position)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.binding.profilePictureView.recycle()
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

        val glide = GlideApp.with(itemView)
        val binding = BlockedContactLayoutBinding.bind(itemView)

        fun bind(recipient: Recipient, isSelected: Boolean, toggleSelection: () -> Unit) {
            binding.recipientName.text = recipient.name
            with (binding.profilePictureView) {
                glide = this@ViewHolder.glide
                update(recipient)
            }
            binding.root.setOnClickListener { toggleSelection() }
            binding.selectButton.isSelected = isSelected
        }
    }

}