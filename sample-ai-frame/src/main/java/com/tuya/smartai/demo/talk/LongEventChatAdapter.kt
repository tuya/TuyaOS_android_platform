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
        private const val COLOR_USER = "#DCF8C6"
        private const val COLOR_NLG = "#FFFFFF"
        private const val COLOR_MCP_CALL = "#E8DEF8"
        private const val COLOR_MCP_RESULT_SUCCESS = "#C8E6C9"
        private const val COLOR_MCP_RESULT_ERROR = "#FFCDD2"
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        val msg = messageList[position]
        return when (msg.messageType) {
            ChatMessage.MessageType.MCP_TOOL_CALL -> VIEW_TYPE_RECEIVED
            ChatMessage.MessageType.MCP_TOOL_RESULT -> VIEW_TYPE_SENT
            else -> if (msg.isSentByUser) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
        }
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
                ChatMessage.MessageType.MCP_TOOL_CALL -> bindMcpCall(message)
                ChatMessage.MessageType.MCP_TOOL_RESULT -> bindMcpResult(message)
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
                    message.text?.takeIf { it.isNotEmpty() }?.let {
                        tvMessageText.text = it
                        tvMessageText.visibility = View.VISIBLE
                    }
                }
            }

            applyBackground(message)
        }

        private fun bindMcpCall(message: ChatMessage) {
            val sb = StringBuilder()
            sb.append("🔧 MCP Tool Call\n")
            sb.append("Tool: ${message.mcpToolName ?: "unknown"}\n")
            message.mcpArgs?.let { sb.append("Args: $it") }
            tvMessageText.text = sb.toString()
            tvMessageText.visibility = View.VISIBLE
            tvMessageText.setTextColor(Color.parseColor("#4A148C"))
        }

        private fun bindMcpResult(message: ChatMessage) {
            val statusIcon = if (message.mcpResultStatus == "success") "✅" else "❌"
            val sb = StringBuilder()
            sb.append("$statusIcon MCP Response\n")
            sb.append("Tool: ${message.mcpToolName ?: "unknown"}\n")
            message.text?.let { sb.append(it) }
            tvMessageText.text = sb.toString()
            tvMessageText.visibility = View.VISIBLE
            tvMessageText.setTextColor(Color.parseColor("#1B5E20"))
        }

        private fun applyBackground(message: ChatMessage) {
            messageContainer?.let {
                val color = when (message.messageType) {
                    ChatMessage.MessageType.MCP_TOOL_CALL -> COLOR_MCP_CALL
                    ChatMessage.MessageType.MCP_TOOL_RESULT -> {
                        if (message.mcpResultStatus == "success") COLOR_MCP_RESULT_SUCCESS
                        else COLOR_MCP_RESULT_ERROR
                    }
                    else -> if (message.isSentByUser) COLOR_USER else COLOR_NLG
                }
                val drawable = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(Color.parseColor(color))
                }
                it.background = drawable
            }
        }
    }
}
