package com.yasin.privacymessenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatPreview(
    val username: String,
    val displayName: String,
    val lastMessage: String,
    val timestamp: Long
)

class ChatListAdapter(
    private val onChatClicked: (ChatPreview) -> Unit
) : ListAdapter<ChatPreview, ChatListAdapter.ViewHolder>(DIFF) {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatPreview>() {
            override fun areItemsTheSame(oldItem: ChatPreview, newItem: ChatPreview): Boolean {
                return oldItem.username == newItem.username
            }

            override fun areContentsTheSame(oldItem: ChatPreview, newItem: ChatPreview): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(item: ChatPreview) {
            tvAvatar.text = item.displayName.firstOrNull()?.uppercase() ?: "?"
            tvName.text = item.displayName
            tvLastMessage.text = item.lastMessage
            tvTime.text = timeFormat.format(Date(item.timestamp))

            itemView.setOnClickListener { onChatClicked(item) }
        }
    }
}