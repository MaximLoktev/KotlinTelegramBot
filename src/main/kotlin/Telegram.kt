package org.example

import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val TELEGRAM_BASE_URL = "https://api.telegram.org"
const val CALLBACK_DATA_LEARN_WORDS = "learn_words_clicked"
const val CALLBACK_DATA_STATISTICS = "statistics_clicked"
const val CALLBACK_DATA_RESET = "reset_clicked"
const val CALLBACK_DATA_MAIN_MENU = "main_menu_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"

class TelegramBotService(private val botToken: String) {

    private val client = HttpClient.newBuilder().build()

    fun getUpdates(updateId: Long): String {
        val urlGetUpdates = "$TELEGRAM_BASE_URL/bot$botToken/getUpdates?offset=$updateId"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(urlGetUpdates))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }

    fun sendMenu(json: Json, chatId: Long) {
        val requestBody = SendMessageRequest(
            chatId,
            text = "Основное меню",
            replyMarkup = ReplyMarkup(
                listOf(
                    listOf(
                        InlineKeyboard("Изучить слова", CALLBACK_DATA_LEARN_WORDS),
                        InlineKeyboard("Статистика", CALLBACK_DATA_STATISTICS),
                    ),
                    listOf(
                        InlineKeyboard("Сбросить прогресс", CALLBACK_DATA_RESET),
                    )
                )
            )
        )

        val requestBodyString = json.encodeToString(requestBody)
        baseSendMessage(requestBodyString)
    }

    fun sendQuestion(json: Json, chatId: Long, question: Question) {
        val requestBody = SendMessageRequest(
            chatId,
            text = question.correctAnswer.text,
            replyMarkup = ReplyMarkup(
                listOf(
                    question.variants.mapIndexed { index, word ->
                        InlineKeyboard(word.translate, "$CALLBACK_DATA_ANSWER_PREFIX$index")
                    },
                    listOf(InlineKeyboard("🏠Меню", CALLBACK_DATA_MAIN_MENU)),
                )
            )
        )

        val requestBodyString = json.encodeToString(requestBody)
        baseSendMessage(requestBodyString)
    }

    fun sendMessage(json: Json, chatId: Long, text: String) {
        if (text.isEmpty() || text.length > 4096) return

        val requestBody = SendMessageRequest(chatId, text)
        val requestBodyString = json.encodeToString(requestBody)

        baseSendMessage(requestBodyString)
    }

    private fun baseSendMessage(requestBodyString: String) {
        val sendMessageUrl = "$TELEGRAM_BASE_URL/bot$botToken/sendMessage"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(sendMessageUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()

        client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

fun main(args: Array<String>) {
    var lastUpdateId = 0L
    val json = Json { ignoreUnknownKeys = true }

    val service = TelegramBotService(botToken = args[0])
    val trainers = HashMap<Long, LearnWordsTrainer>()

    while (true) {
        val responseString = service.getUpdates(lastUpdateId)
        println(responseString)

        val response: Response = json.decodeFromString(responseString)
        if (response.result.isEmpty()) continue

        val sortedUpdates = response.result.sortedBy { it.updateId }
        sortedUpdates.forEach { handleUpdate(json, it, trainers, service) }

        lastUpdateId = sortedUpdates.last().updateId + 1

        Thread.sleep(2000)
    }
}

fun handleUpdate(
    json: Json,
    update: Update,
    trainers: HashMap<Long, LearnWordsTrainer>,
    service: TelegramBotService
) {
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return
    val messageText = update.message?.text ?: ""
    val callbackData = update.callbackQuery?.data ?: ""

    val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer(fileName = "$chatId.txt") }

    when {
        callbackData.isNotEmpty() -> {
            when {
                callbackData == CALLBACK_DATA_LEARN_WORDS -> {
                    checkNextQuestionAndSend(json, trainer, service, chatId)
                }
                callbackData == CALLBACK_DATA_STATISTICS -> {
                    sendStatistics(json, trainer, service, chatId)
                }
                callbackData == CALLBACK_DATA_RESET -> {
                    trainer.resetProgress()
                    service.sendMessage(json, chatId, "Прогресс сброшен")
                }
                callbackData == CALLBACK_DATA_MAIN_MENU -> {
                    service.sendMenu(json, chatId)
                }
                callbackData.startsWith(CALLBACK_DATA_ANSWER_PREFIX) -> {
                    checkAnswerAndSendNextStep(json, trainer, service, chatId, callbackData)
                }
            }
        }
        messageText.isNotEmpty() -> {
            when (messageText) {
                "/start" -> service.sendMenu(json, chatId)
                else -> service.sendMessage(json, chatId, "Вы написали: $messageText")
            }
        }
    }
}

fun checkNextQuestionAndSend(
    json: Json,
    trainer: LearnWordsTrainer,
    service: TelegramBotService,
    chatId: Long
) {
    val question = trainer.getNextQuestion()

    if (question != null) {
        service.sendQuestion(json, chatId, question)
    } else {
        service.sendMessage(json, chatId, "Вы выучили все слова в базе")
    }
}

fun sendStatistics(
    json: Json,
    trainer: LearnWordsTrainer,
    service: TelegramBotService,
    chatId: Long
) {
    val statistics = trainer.getStatistics()

    val message = if (statistics != null) {
        "Выучено ${statistics.learnedCount} из ${statistics.totalCount} слов | ${statistics.percent}%"
    } else {
        "Словарь пуст"
    }
    service.sendMessage(json, chatId, message)
}

fun checkAnswerAndSendNextStep(
    json: Json,
    trainer: LearnWordsTrainer,
    service: TelegramBotService,
    chatId: Long,
    callbackData: String
) {
    val index = callbackData.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull()

    val currentQuestion = trainer.question

    if (index != null && currentQuestion != null) {
        val isCorrect = trainer.checkAnswer(index)

        if (isCorrect) {
            service.sendMessage(json, chatId, "Правильно!")
        } else {
            val word = currentQuestion.correctAnswer

            service.sendMessage(json, chatId, "Неправильно! ${word.text} – это ${word.translate}")
        }

        checkNextQuestionAndSend(json, trainer, service, chatId)
    } else {
        service.sendMessage(json, chatId, "Произошла ошибка или сессия устарела!")
        service.sendMenu(json, chatId)
    }
}