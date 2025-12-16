package org.example

import java.io.File

fun main() {
    val wordsFile = File("words.txt")

    if (!wordsFile.exists()) { return }

    val lines = wordsFile.readLines()

    val dictionary = mutableListOf<Word>()

    lines.forEach { line ->
        val newLine = line.split("|")

        val model = Word(
            text = newLine[0],
            translate = newLine[1],
            correctAnswersCount = newLine[2].toIntOrNull() ?: 0
        )
        dictionary.add(model)
    }

    println(dictionary)
}

data class Word(
    val text: String,
    val translate: String,
    val correctAnswersCount: Int = 0,
)