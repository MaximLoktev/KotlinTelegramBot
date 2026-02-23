package org.example.telegram

import java.util.concurrent.ConcurrentHashMap

data class MessageState(
    val messageId: Long,
    val text: String,
    val keyboard: ReplyMarkup?,
)

class DynamicMessage {

    private val history = ConcurrentHashMap<Long, MutableList<MessageState>>()

    fun saveState(chatId: Long, newState: MessageState) {
        val states = history.getOrPut(chatId) { mutableListOf() }

        if (states.lastOrNull() != newState) {
            states.add(newState)
        }

        if (states.size > 5) states.removeAt(0)
    }

    fun getCurrentMessageId(chatId: Long): Long? = history[chatId]?.lastOrNull()?.messageId

    fun popPreviousState(chatId: Long): MessageState? {
        val states = history[chatId] ?: return null

        if (states.size < 2) return null

        states.removeAt(states.size - 1)

        return states.lastOrNull()
    }

    fun clear(chatId: Long) { history.remove(chatId) }
}