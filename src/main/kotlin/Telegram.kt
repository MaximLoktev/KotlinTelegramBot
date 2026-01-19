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

    while (true) {
        val updates = service.getUpdates(updateId)
        println(updates)

        val updateIdString = service.extractUpdateValue(updates, "update_id")

        if (updateIdString.isEmpty()) continue

        updateId = updateIdString.toInt() + 1

        val message = service.extractUpdateValue(updates, "text")

        val chatId = service.extractChatId(updates).toIntOrNull() ?: continue

        service.sendMessage(chatId, message)

        Thread.sleep(2000)
    }
}