package com.yasin.privacymessenger

import android.app.AlertDialog
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yasin.privacymessenger.data.database.Message
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val currentUsername: String,
    private val onDeleteMessage: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var messages = listOf<Message>()

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
        private const val TYPE_DATE = 3
    }

    fun setMessages(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    private fun shouldShowDate(position: Int): Boolean {
        if (position == 0) return true

        val previousDate = Calendar.getInstance().apply {
            timeInMillis = messages[position - 1].timestamp
        }
        val currentDate = Calendar.getInstance().apply {
            timeInMillis = messages[position].timestamp
        }

        return previousDate.get(Calendar.YEAR) !=
                currentDate.get(Calendar.YEAR) ||
                previousDate.get(Calendar.DAY_OF_YEAR) !=
                currentDate.get(Calendar.DAY_OF_YEAR)
    }

    private fun dateLabel(timestamp: Long): String {
        val messageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }

        return when {
            messageDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    messageDate.get(Calendar.DAY_OF_YEAR) ==
                    today.get(Calendar.DAY_OF_YEAR) -> "Today"

            messageDate.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                    messageDate.get(Calendar.DAY_OF_YEAR) ==
                    yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"

            else -> SimpleDateFormat(
                "MMMM d, yyyy",
                Locale.getDefault()
            ).format(Date(timestamp))
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (shouldShowDate(position)) return TYPE_DATE

        return if (messages[position].senderUsername == currentUsername) {
            TYPE_SENT
        } else {
            TYPE_RECEIVED
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        if (viewType == TYPE_DATE) {
            val view = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(android.graphics.Color.DKGRAY)
                setPadding(12, 12, 12, 12)
            }
            return DateViewHolder(view)
        }

        val layout = if (viewType == TYPE_SENT) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }

        val view = LayoutInflater.from(parent.context)
            .inflate(layout, parent, false)

        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val message = messages[position]

        when (holder) {
            is DateViewHolder -> {
                holder.tvDate.text = dateLabel(message.timestamp)
            }

            is MessageViewHolder -> {
                holder.tvMessage.text = message.content
                holder.tvTimestamp.text =
                    DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Date(message.timestamp))

                holder.itemView.setOnLongClickListener {
                    AlertDialog.Builder(holder.itemView.context)
                        .setTitle("Delete message?")
                        .setMessage("Do you want to delete this message from this device?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            onDeleteMessage(message)
                        }
                        .show()
                    true
                }
            }
        }
    }

    class MessageViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView =
            itemView.findViewById(R.id.tvMessage)
        val tvTimestamp: TextView =
            itemView.findViewById(R.id.tvTimestamp)
    }

    class DateViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView as TextView
    }
}

