package com.tuya.smartai.demo.talk

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tuya.smartai.demo.R
import com.tuya.smartai.demo.ai.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LongEventChatAdapter(
    private val context: Context,
    private val messageList: List<ChatMessage>
) : RecyclerView.Adapter<LongEventChatAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        return if (messageList[position].isSentByUser) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_SENT)
            R.layout.item_long_event_message_sent else R.layout.item_long_event_message_received
        return MessageViewHolder(LayoutInflater.from(parent.context).inflate(layoutId, parent, false))
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messageList[position])
    }

    override fun getItemCount(): Int = messageList.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessageText: TextView = itemView.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        private val ivMessageImage: ImageView? = itemView.findViewById(R.id.iv_message_image)
        private val messageContainer: View? = itemView.findViewById(R.id.message_container)

        fun bind(message: ChatMessage) {
            tvMessageText.visibility = View.GONE
            ivMessageImage?.visibility = View.GONE

            tvTime.text = timeFormat.format(Date(message.timestamp))

            when (message.messageType) {
                ChatMessage.MessageType.TEXT,
                ChatMessage.MessageType.VOICE_TO_TEXT,
                ChatMessage.MessageType.NLG_TEXT -> {
                    message.text?.takeIf { it.isNotEmpty() }?.let {
                        tvMessageText.text = it
                        tvMessageText.visibility = View.VISIBLE
                    }
                }
                ChatMessage.MessageType.IMAGE -> {
                    message.imageUri?.let {
                        Glide.with(context).load(it).into(ivMessageImage!!)
                        ivMessageImage.visibility = View.VISIBLE
                    }
                }
                ChatMessage.MessageType.NLG_IMAGE -> {
                    message.imageUrl?.takeIf { it.isNotEmpty() }?.let {
                        Glide.with(context).load(it).into(ivMessageImage!!)
                        ivMessageImage.visibility = View.VISIBLE
                    }
                }
                else -> {
                    tvMessageText.text = message.text ?: ""
                    tvMessageText.visibility = View.VISIBLE
                }
            }

            messageContainer?.let {
                val drawable = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(if (message.isSentByUser) Color.parseColor("#DCF8C6") else Color.WHITE)
                }
                it.background = drawable
            }
        }
    }
}
