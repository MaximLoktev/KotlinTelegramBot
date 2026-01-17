package org.example

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val TELEGRAM_BASE_URL = "https://api.telegram.org"

fun main(args: Array<String>) {
    val botToken = args[0]
    var updateId = 0

    while (true) {
        val updates = getUpdates(botToken, updateId)
        println(updates)

        val updateIdString = extractUpdateValue(updates, "update_id")

        if (updateIdString.isEmpty()) continue

        updateId = updateIdString.toInt() + 1

        val message = extractUpdateValue(updates, "text")
        println("Сообщение: $message")

        Thread.sleep(2000)
    }
}

fun getUpdates(botToken: String, updateId: Int): String {
    val urlGetUpdates = "$TELEGRAM_BASE_URL/bot$botToken/getUpdates?offset=$updateId"
    val client = HttpClient.newBuilder().build()
    val request = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
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