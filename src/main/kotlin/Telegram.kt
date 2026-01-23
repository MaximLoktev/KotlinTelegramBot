package org.example

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val TELEGRAM_BASE_URL = "https://api.telegram.org"
const val CALLBACK_DATA_LEARN_WORDS = "learn_words_clicked"
const val CALLBACK_DATA_STATISTICS = "statistics_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"

class TelegramBotService(private val botToken: String) {

    private val client = HttpClient.newBuilder().build()

    fun sendMenu(chatId: Int) {
        val menuBody = """
            {
                "inline_keyboard": [
                    [
                        { 
                            "text": "Изучить слова", 
                            "callback_data": "$CALLBACK_DATA_LEARN_WORDS" 
                        },
                        { 
                            "text": "Статистика", 
                            "callback_data": "$CALLBACK_DATA_STATISTICS"
                        }
                    ]
                ]
            }
        """.trimIndent()

        sendMessage(chatId, text = "Основное меню", menuBody)
    }

    fun sendQuestion(chatId: Int, question: Question) {
        val buttons = question.variants.mapIndexed { index, word ->
            """
            {
                "text": "${word.translate}",
                "callback_data": "$CALLBACK_DATA_ANSWER_PREFIX$index"
            }
            """.trimIndent()
        }.joinToString(",")

        val replyMarkup = """
            {
                "inline_keyboard": [ [ $buttons ] ]
            }
        """.trimIndent()

        sendMessage(chatId, question.correctAnswer.text, replyMarkup)
    }

    fun sendMessage(chatId: Int, text: String, replyMarkup: String? = null) {
        if (text.isEmpty() || text.length > 4096) return

        val sendMessageUrl = "$TELEGRAM_BASE_URL/bot$botToken/sendMessage"

        val replyMarkupPart = if (replyMarkup != null) ",\"reply_markup\": $replyMarkup" else ""
        val requestBody = """{"chat_id":$chatId,"text":"$text"$replyMarkupPart}"""

        val request = HttpRequest.newBuilder()
            .uri(URI.create(sendMessageUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
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
                when (callbackData) {
                    CALLBACK_DATA_LEARN_WORDS -> {
                        checkNextQuestionAndSend(trainer, service, chatId)
                    }
                    CALLBACK_DATA_STATISTICS -> {
                        sendStatistics(trainer, service, chatId)
                    }
                }
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

fun checkNextQuestionAndSend(
    trainer: LearnWordsTrainer,
    service: TelegramBotService,
    chatId: Int
) {
    val question = trainer.getNextQuestion()

    if (question != null) {
        service.sendQuestion(chatId, question)
    } else {
        service.sendMessage(chatId, "Вы выучили все слова в базе")
    }
}

fun sendStatistics(
    trainer: LearnWordsTrainer,
    service: TelegramBotService,
    chatId: Int
) {
    val statistics = trainer.getStatistics()

    val message = if (statistics != null) {
        "Выучено ${statistics.learnedCount} из ${statistics.totalCount} слов | ${statistics.percent}%"
    } else {
        "Словарь пуст"
    }
    service.sendMessage(chatId, message)
}