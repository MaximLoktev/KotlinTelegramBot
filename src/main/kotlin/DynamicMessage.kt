package org.example

import java.util.concurrent.ConcurrentHashMap

class DynamicMessage {

    private val userMessages = ConcurrentHashMap<Long, Long>()

    fun saveId(chatId: Long, messageId: Long) {
        userMessages[chatId] = messageId
    }

    fun getId(chatId: Long): Long? {
        return userMessages[chatId]
    }

    fun clearId(chatId: Long) {
        userMessages.remove(chatId)
    }
}