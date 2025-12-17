package org.example

import java.io.File

fun main() {
    val pathName = "words.txt"
    val wordsFile = File(pathName)

    if (!wordsFile.exists()) {
        println("Файл $pathName не найден")
        return
    }

    val lines = wordsFile.readLines()

    val dictionary = mutableListOf<Word>()

    for (line in lines) {
        val newLine = line.split("|")

        if (newLine.size < 3) {
            println("Некорректная строка: $newLine")
            continue
        }

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