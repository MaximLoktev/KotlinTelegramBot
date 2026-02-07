package org.example

import kotlinx.serialization.json.Json
import java.io.File
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

    private val TELEGRAM_SAND_MESSAGE_URL = "$TELEGRAM_BASE_URL/bot$botToken/sendMessage"

    private val client = HttpClient.newBuilder().build()

    private val json = Json { ignoreUnknownKeys = true }

    fun getUpdates(updateId: Long): List<Update> {
        val url = "$TELEGRAM_BASE_URL/bot$botToken/getUpdates?offset=$updateId"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build()

        return runCatching {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
            json.decodeFromString<Response>(response).result.sortedBy { it.updateId }
        }.getOrDefault(emptyList())
    }

    fun sendMenu(chatId: Long) {
        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = "Основное меню",
            replyMarkup = ReplyMarkup(
                listOf(
                    listOf(
                        InlineKeyboard("Изучить слова", CALLBACK_DATA_LEARN_WORDS),
                        InlineKeyboard("Статистика", CALLBACK_DATA_STATISTICS),
                    ),
                    listOf(InlineKeyboard("Сбросить прогресс", CALLBACK_DATA_RESET),)
                )
            )
        )

        sendPostRequest(
            url = TELEGRAM_SAND_MESSAGE_URL,
            body = json.encodeToString(requestBody)
        )
    }

    fun sendQuestion(chatId: Long, question: Question) {
        val requestBody = SendMessageRequest(
            chatId = chatId,
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

        sendPostRequest(
            url = TELEGRAM_SAND_MESSAGE_URL,
            body = json.encodeToString(requestBody)
        )
    }

    fun sendMessage(chatId: Long, text: String) {
        if (text.isEmpty() || text.length > 4096) return

        val requestBody = SendMessageRequest(chatId, text)

        sendPostRequest(
            url = TELEGRAM_SAND_MESSAGE_URL,
            body = json.encodeToString(requestBody)
        )
    }

    fun getFileInfo(fileId: String): FileInfo? {
        val requestBody = GetFileRequest(fileId = fileId)

        val response = sendPostRequest(
            url = "$TELEGRAM_BASE_URL/bot$botToken/getFile",
            body = json.encodeToString(requestBody))

        return response?.let { json.decodeFromString<GetFileResponse>(it).result }
    }

    fun downloadFile(filePath: String, destinationFile: File) {
        val url = "$TELEGRAM_BASE_URL/file/bot$botToken/$filePath"

        val request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()

        runCatching {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            response.body().use { input ->
                destinationFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun sendPostRequest(url: String, body: String): String? {
        return runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        }.getOrNull()
    }
}

fun main(args: Array<String>) {
    val botToken = args.getOrNull(0) ?: throw IllegalArgumentException("Укажите токен бота")

    val service = TelegramBotService(botToken)
    val updateHandler = TelegramUpdateHandler(service)

    var lastUpdateId = 0L

    println("Бот запущен...")

    while (true) {
        val updates = service.getUpdates(lastUpdateId)
        updates.forEach { updateHandler.handleUpdate(it) }

        if (updates.isNotEmpty()) {
            lastUpdateId = updates.last().updateId + 1
        }
        Thread.sleep(2000)
    }
}