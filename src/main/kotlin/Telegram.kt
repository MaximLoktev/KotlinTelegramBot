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

    fun sendPhoto(chatId: Long, photo: Any, hasSpoiler: Boolean = false): Photo? {
        val url = "$TELEGRAM_BASE_URL/bot$botToken/sendPhoto"

        val request = when (photo) {
            is File -> buildMultipartRequest(url, chatId, photo, hasSpoiler)
            is String -> buildJsonRequest(url, chatId, photo, hasSpoiler)
            else -> null
        }

        if (request == null) return null

        val response = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        println(response)

        return response.let { json.decodeFromString<PhotoResponse>(it).result?.photo?.last() }
    }

    private fun buildMultipartRequest(url: String, chatId: Long, file: File, hasSpoiler: Boolean): HttpRequest {
        val boundary = BigInteger(35, Random()).toString()

        val data = mapOf(
            "chat_id" to chatId.toString(),
            "photo" to file,
            "has_spoiler" to hasSpoiler.toString()
        )
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .postMultipartFormData(boundary, data)
            .build()
    }

    private fun buildJsonRequest(url: String, chatId: Long, photoId: String, hasSpoiler: Boolean): HttpRequest {
        val body = json.encodeToString(mapOf(
            "chat_id" to chatId.toString(),
            "photo" to photoId,
            "has_spoiler" to hasSpoiler.toString()
        ))
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
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

//private fun HttpRequest.Builder.postMultipartFormData(
//    boundary: String,
//    data: Map<String, Any>
//): HttpRequest.Builder {
//    val charset = StandardCharsets.UTF_8
//
//    val byteArrays = ArrayList<ByteArray>()
//
//    val separator = "--$boundary\r\nContent-Disposition: form-data; name=".toByteArray(charset)
//
//    for (entry in data.entries) {
//        byteArrays.add(separator)
//
//        when (entry.value) {
//            is File -> {
//                val file = entry.value as File
//                val path = Path.of(file.toURI())
//                val mimeType = Files.probeContentType(path)
//
//                byteArrays.add(
//                    "\'${entry.key}\'; filename=\'${path.fileName}\'\r\nContent-Type: $mimeType\r\n\r\n".toByteArray(charset)
//                )
//                byteArrays.add(Files.readAllBytes(path))
//                byteArrays.add("\r\n".toByteArray(charset))
//            }
//            else -> byteArrays.add("\'${entry.key}\'\r\n\r\n${entry.value}\r\n".toByteArray(charset))
//        }
//    }
//    byteArrays.add("--$boundary--".toByteArray(charset))
//
//    this.header("Content-Type", "multipart/form-data;boundary=$boundary")
//        .POST(HttpRequest.BodyPublishers.ofByteArrays(byteArrays))
//
//    return this
//}