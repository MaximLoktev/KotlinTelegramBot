package org.example

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun main(args: Array<String>) {
    val botToken = args[0]

    val urlGatMe = "https://api.telegram.org/bot$botToken/getMe"
    val urlGatUpdates = "https://api.telegram.org/bot$botToken/getUpdates"

    val client = HttpClient.newBuilder().build()

    val firstRequest = HttpRequest.newBuilder().uri(URI.create(urlGatMe)).build()
    val firstResponse = client.send(firstRequest, HttpResponse.BodyHandlers.ofString())
    println(firstResponse.body())

    val secondRequest = HttpRequest.newBuilder().uri(URI.create(urlGatUpdates)).build()
    val secondResponse = client.send(secondRequest, HttpResponse.BodyHandlers.ofString())
    println(secondResponse.body())
}