package org.example

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

const val TELEGRAM_BASE_URL = "https://api.telegram.org"

class TelegramBotService(private val botToken: String) {

    private val client = HttpClient.newBuilder().build()

    fun sendMenu(chatId: Int) {
        val sendMessageUrl = "$TELEGRAM_BASE_URL/bot$botToken/sendMessage"
        val sendMenuBody = """
            {
                "chat_id": $chatId,
                "text": "Основное меню",
                "reply_markup": {
                    "inline_keyboard": [
                        [
                            {
                                "text": "Изучить слова",
                                "callback_data": "learn_words_clicked"
                            },
                            {
                                "text": "Статистика",
                                "callback_data": "statistics_clicked"
                            }
                        ]
                    ]
                }
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(sendMessageUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(sendMenuBody))
            .build()

        client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun sendMessage(chatId: Int, text: String) {
        if (text.isEmpty() || text.length > 4096) return

        val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8)

        val sendMessageUrl = "$TELEGRAM_BASE_URL/bot$botToken/sendMessage?chat_id=$chatId&text=$encodedText"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(sendMessageUrl))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun getUpdates(updateId: Int): String {
        val urlGetUpdates = "$TELEGRAM_BASE_URL/bot$botToken/getUpdates?offset=$updateId"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(urlGetUpdates))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }

    fun extractUpdateValue(updates: String, key: String): String {
        val stringRegex = """"$key"\s*:\s*"([^"]+)"""".toRegex()
        stringRegex.find(updates)?.let { return it.groupValues[1] }

        val numberRegex = """"$key"\s*:\s*(\d+)""".toRegex()
        numberRegex.find(updates)?.let { return it.groupValues[1] }

        return ""
    }

    fun extractChatId(updates: String): String {
        val chatRegex = """"chat"\s*:\s*\{[^}]*?"id"\s*:\s*(\d+)""".toRegex()

        return chatRegex.find(updates)?.groupValues[1] ?: ""
    }
}

fun main(args: Array<String>) {
    var updateId = 0

    val service = TelegramBotService(botToken = args[0])

    val trainer = LearnWordsTrainer(MIN_CORRECT_ANSWERS, WORDS_PER_SESSION)

    while (true) {
        val updates = service.getUpdates(updateId)
        println(updates)

        val updateIdString = service.extractUpdateValue(updates, "update_id")

        if (updateIdString.isEmpty()) continue

        updateId = updateIdString.toInt() + 1

        val chatId = service.extractChatId(updates).toIntOrNull() ?: continue

        val messageText = service.extractUpdateValue(updates, "text")

        val callbackData = service.extractUpdateValue(updates, "data")

        when {
            callbackData.isNotEmpty() -> {
                val message = when (callbackData) {
                    "learn_words_clicked" -> "Приступаем к изучению слов!"
                    "statistics_clicked" -> "Выучено 10 из 10 слов | 100%"
                    else -> ""
                }
                service.sendMessage(chatId, message)
            }
            messageText.isNotEmpty() -> {
                when (messageText) {
                    "/start" -> service.sendMenu(chatId)
                    else -> service.sendMessage(chatId, "Вы написали: $messageText")
                }
            }
        }

        Thread.sleep(2000)
    }
}