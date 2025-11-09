package com.example.arguewithai.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.example.arguewithai.R

class ChatAdapter(
    private val items: List<Message>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_OUT = 1  // 사용자
        private const val TYPE_IN = 2   // AI/상대
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
        when (holder) {
            is OutVH -> holder.bind(msg)
            is InVH -> holder.bind(msg)
        }
    }

    override fun getItemCount(): Int = items.size

    private class OutVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvMessage)
        fun bind(m: Message) { tv.text = m.text }
    }

    private class InVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.tvMessage)
        fun bind(m: Message) { tv.text = m.text }
    }
}