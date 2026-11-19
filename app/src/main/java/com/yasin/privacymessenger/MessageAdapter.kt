package com.yasin.privacymessenger

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yasin.privacymessenger.data.database.Message
import java.text.DateFormat
import java.util.Date

/** One row of the conversation: either a day separator or a message bubble. */
sealed class ChatItem {
    data class DateHeader(val dayStartMillis: Long, val label: String) : ChatItem()
    data class Bubble(val message: Message) : ChatItem()
}

class MessageAdapter(
    private val currentUsername: String,
    private val onDeleteMessage: (Message) -> Unit,
    private val onRetryMessage: (Message) -> Unit
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(DIFF) {

    private val timeFormat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
        private const val TYPE_DATE = 3

        private const val COLOR_NORMAL = 0xFF667781.toInt()
        private const val COLOR_FAILED = 0xFFD32F2F.toInt()

        private val DIFF = object : DiffUtil.ItemCallback<ChatItem>() {
            override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
                return when {
                    oldItem is ChatItem.DateHeader && newItem is ChatItem.DateHeader ->
                        oldItem.dayStartMillis == newItem.dayStartMillis

                    oldItem is ChatItem.Bubble && newItem is ChatItem.Bubble ->
                        oldItem.message.id == newItem.message.id

                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatItem.DateHeader -> TYPE_DATE
            is ChatItem.Bubble ->
                if (item.message.senderUsername == currentUsername) TYPE_SENT else TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            TYPE_DATE -> DateViewHolder(inflater.inflate(R.layout.item_date_header, parent, false))
            TYPE_SENT -> MessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
            else -> MessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatItem.DateHeader -> (holder as DateViewHolder).tvDate.text = item.label
            is ChatItem.Bubble -> bindMessage(holder as MessageViewHolder, item.message)
        }
    }

    private fun bindMessage(holder: MessageViewHolder, message: Message) {
        holder.tvMessage.text = message.content

        val time = timeFormat.format(Date(message.timestamp))
        val isMine = message.senderUsername == currentUsername

        holder.tvTimestamp.setTextColor(COLOR_NORMAL)

        holder.tvTimestamp.text = when {
            !isMine -> time
            message.status == Message.STATUS_PENDING -> "$time  \uD83D\uDD53"
            message.status == Message.STATUS_FAILED -> {
                holder.tvTimestamp.setTextColor(COLOR_FAILED)
                "Not sent \u2022 tap to retry"
            }
            else -> "$time  \u2713"
        }

        // Tap a failed message to retry it (like WhatsApp).
        if (isMine && message.status == Message.STATUS_FAILED) {
            holder.itemView.setOnClickListener { onRetryMessage(message) }
        } else {
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
        }

        holder.itemView.setOnLongClickListener {
            val context = holder.itemView.context

            AlertDialog.Builder(context)
                .setItems(arrayOf("Copy", "Delete for me")) { _, which ->
                    if (which == 0) {
                        copyToClipboard(context, message.content)
                    } else {
                        confirmDelete(context, message)
                    }
                }
                .show()

            true
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(context: Context, message: Message) {
        AlertDialog.Builder(context)
            .setTitle("Delete message?")
            .setMessage("This message will be deleted from this device only.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> onDeleteMessage(message) }
            .show()
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
    }

    class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
    }
}