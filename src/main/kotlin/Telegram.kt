package org.example

import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

const val TELEGRAM_BASE_URL = "https://api.telegram.org"
const val CALLBACK_DATA_LEARN_WORDS = "learn_words_clicked"
const val CALLBACK_DATA_STATISTICS = "statistics_clicked"
const val CALLBACK_DATA_RESET = "reset_clicked"
const val CALLBACK_DATA_MAIN_MENU = "main_menu_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"

class TelegramBotService(private val botToken: String) {

    private val baseUrl = "$TELEGRAM_BASE_URL/bot$botToken"

    private val client = HttpClient.newBuilder().build()

    private val json = Json { ignoreUnknownKeys = true }

    // --- МЕТОД ПОЛУЧЕНИЯ ОБНОВЛЕНИЙ ---

    fun getUpdates(updateId: Long): List<Update> {
        val url = "$baseUrl/getUpdates?offset=$updateId"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build()

        return runCatching {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
            json.decodeFromString<Response>(response).result.sortedBy { it.updateId }
        }.getOrDefault(emptyList())
    }

    // --- МЕТОД ОТПРАВКИ ---

    fun sendMessage(chatId: Long, text: String, replyMarkup: ReplyMarkup? = null): Long? {
        return sendBaseMessage(chatId, text, replyMarkup)
    }

    // --- МЕТОД РЕДАКТИРОВАНИЯ ---

    fun editMessage(chatId: Long, messageId: Long, text: String, keyboard: ReplyMarkup? = null): Long? {
        val requestBody = EditMessageRequest(chatId, messageId, text, keyboard, parseMode = "HTML")

        val responseBody = sendPostRequest(
            "$baseUrl/editMessageText",
            json.encodeToString(requestBody)
        ) ?: return null

        return try {
            val response = json.decodeFromString<TelegramResponse<MessageData>>(responseBody)

            if (response.ok) {
                response.result?.messageId
            } else {
                if (response.description?.contains("message is not modified") == true) {
                    return messageId
                }
                println("Telegram API Error: ${response.description}")
                null
            }
        } catch (e: Exception) {
            println("Parsing error: ${e.message}")
            null
        }
    }

    // --- МЕТОД РЕДАКТИРОВАНИЯ ---

    fun deleteMessage(chatId: Long, messageId: Long): Boolean {
        val requestBody = DeleteMessageRequest(chatId, messageId)

        val responseBody = sendPostRequest(
            "$baseUrl/deleteMessage",
            json.encodeToString(requestBody)
        ) ?: return false

        return responseBody.contains("\"ok\":true")
    }

    // --- КЛАВИАТУРЫ ---

    fun getMainMenuKeyboard(): ReplyMarkup {
        return ReplyMarkup(listOf(
            listOf(InlineKeyboard("Учить слова", CALLBACK_DATA_LEARN_WORDS)),
            listOf(InlineKeyboard("Статистика", CALLBACK_DATA_STATISTICS)),
            listOf(InlineKeyboard("Сбросить прогресс", CALLBACK_DATA_RESET))
        ))
    }

    fun getBackToMenuKeyboard(): ReplyMarkup {
        return ReplyMarkup(listOf(
            listOf(InlineKeyboard("⬅️ В главное меню", CALLBACK_DATA_MAIN_MENU))
        ))
    }

    fun getQuestionKeyboard(question: Question): ReplyMarkup {
        val optionsButtons = question.variants.mapIndexed { index, word ->
            InlineKeyboard(word.translate, "$CALLBACK_DATA_ANSWER_PREFIX$index")
        }

        val rows = optionsButtons.chunked(2)

        val fullKeyboard = rows + listOf(listOf(InlineKeyboard("🏠 Меню", CALLBACK_DATA_MAIN_MENU)))

        return ReplyMarkup(fullKeyboard)
    }

    // --- МЕТОДЫ ОТПРАВКИ ФОТО ---

    fun sendPhoto(chatId: Long, photo: File, hasSpoiler: Boolean = false): Photo? =
        executePhotoRequest { url ->
            val boundary = BigInteger(35, Random()).toString()

            val data = mapOf(
                "chat_id" to chatId.toString(),
                "photo" to photo,
                "has_spoiler" to hasSpoiler.toString()
            )
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .postMultipartFormData(boundary, data)
                .build()
        }

    fun sendPhoto(chatId: Long, photoId: String, hasSpoiler: Boolean = false): Photo? =
        executePhotoRequest { url ->
            val body = json.encodeToString(PhotoRequest(chatId, photoId, hasSpoiler))

            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        }

    // --- МЕТОДЫ РАБОТЫ С ФАЙЛАМИ ---

    fun getFileInfo(fileId: String): FileInfo? {
        val requestBody = GetFileRequest(fileId = fileId)

        val response = sendPostRequest("$baseUrl/getFile", json.encodeToString(requestBody))

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

    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ---

    private fun String?.parseMessageId(): Long? {
        return try {
            this?.let { json.decodeFromString<MessageResponse>(it).result?.messageId }
        } catch (e: Exception) {
            println("Ошибка парсинга ответа: ${e.message}")
            null
        }
    }

    private fun sendBaseMessage(chatId: Long, text: String, replyMarkup: ReplyMarkup? = null): Long? {
        if (text.isEmpty() || text.length > 4096) return null

        val requestBody = SendMessageRequest(chatId, text, replyMarkup, parseMode = "HTML")

        return sendPostRequest("$baseUrl/sendMessage", json.encodeToString(requestBody)).parseMessageId()
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

    private fun executePhotoRequest(requestBuilder: (String) -> HttpRequest): Photo? {
        return runCatching {
            val request = requestBuilder("$baseUrl/sendPhoto")
            val response = client.send(request, HttpResponse.BodyHandlers.ofString()).body()

            json.decodeFromString<PhotoResponse>(response).result?.photo?.last()
        }.onFailure {
            println("Ошибка при отправке фото: ${it.message}")
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


private fun HttpRequest.Builder.postMultipartFormData(
    boundary: String,
    data: Map<String, Any>
): HttpRequest.Builder {
    val charset = StandardCharsets.UTF_8
    val byteArrays = ArrayList<ByteArray>()

    for (entry in data.entries) {
        byteArrays.add("--$boundary\r\n".toByteArray(charset))

        when (val value = entry.value) {
            is File -> {
                val path = Path.of(value.toURI())
                val mimeType = Files.probeContentType(path) ?: "application/octet-stream"

                byteArrays.add(
                    ("Content-Disposition: form-data; name=\"${entry.key}\"; filename=\"${path.fileName}\"\r\n" +
                            "Content-Type: $mimeType\r\n\r\n").toByteArray(charset)
                )
                byteArrays.add(Files.readAllBytes(path))
                byteArrays.add("\r\n".toByteArray(charset))
            }
            else -> {
                byteArrays.add(
                    ("Content-Disposition: form-data; name=\"${entry.key}\"\r\n\r\n$value\r\n").toByteArray(charset)
                )
            }
        }
    }

    byteArrays.add("--$boundary--\r\n".toByteArray(charset))

    this.header("Content-Type", "multipart/form-data; boundary=$boundary")
        .POST(HttpRequest.BodyPublishers.ofByteArrays(byteArrays))

    return this
}