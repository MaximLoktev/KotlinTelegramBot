package org.example

import java.io.File

fun main() {
    val wordsFile = File("words.txt")

    if (!wordsFile.exists()) { return }

    val strings = wordsFile.readLines()

    strings.forEach {
        println(it)
    }
}