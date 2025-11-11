package com.p4c.arguewithai.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.p4c.arguewithai.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val items: List<Message>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_OUT = 1
        private const val TYPE_IN = 2
        private const val MINUTE_MS = 60_000L
    }

    private val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREA).apply {
        isLenient = false
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].isUser) TYPE_OUT else TYPE_IN

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_OUT) {
            val v = inflater.inflate(R.layout.item_message_out, parent, false)
            OutVH(v)
        } else {
            val v = inflater.inflate(R.layout.item_message_in, parent, false)
            InVH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        val timeText = timeFormat.format(Date(msg.timestamp))
        val showTime = shouldShowTime(position) // ← tail 기준

        when (holder) {
            is OutVH -> holder.bind(msg.text, timeText, showTime)
            is InVH  -> holder.bind(msg.text, timeText, showTime)
        }
    }

    override fun getItemCount(): Int = items.size

    private class OutVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvMessage: TextView = v.findViewById(R.id.tvMessage)
        val tvTime: TextView = v.findViewById(R.id.tvTime)

        fun bind(text: String, timeText: String, showTime: Boolean) {
            tvMessage.text = text
            tvTime.text = timeText
            tvTime.visibility = if (showTime) View.VISIBLE else View.GONE
        }
    }

    private class InVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvMessage: TextView = v.findViewById(R.id.tvMessage)
        val tvTime: TextView = v.findViewById(R.id.tvTime)

        fun bind(text: String, timeText: String, showTime: Boolean) {
            tvMessage.text = text
            tvTime.text = timeText
            tvTime.visibility = if (showTime) View.VISIBLE else View.GONE
        }
    }


    private fun shouldShowTime(position: Int): Boolean {
        if (position == items.lastIndex) return true

        val cur = items[position]
        val next = items[position + 1]

        val sameSenderAsNext = (cur.isUser == next.isUser)
        val sameMinuteAsNext = (cur.timestamp / MINUTE_MS == next.timestamp / MINUTE_MS)

        return !(sameSenderAsNext && sameMinuteAsNext)
    }
}
