package org.example

import java.io.File

const val MIN_CORRECT_ANSWERS = 3

fun main() {
    val dictionary = loadDictionary()

    while (true) {
        println("""
            Меню: 
            1 – Учить слова
            2 – Статистика
            0 – Выход
        """.trimIndent())

        print("Введите число: ")
        val input = readln()

        when (input) {
            "1" -> println("Учить слова")
            "2" -> {
                val totalCount = dictionary.size

                if (totalCount == 0) {
                    println("Словарь пуст\n")
                    continue
                }

                val learnedCount = dictionary.count {
                    it.correctAnswersCount >= MIN_CORRECT_ANSWERS
                }

                val percent = learnedCount * 100 / totalCount

                println("Выучено $learnedCount из $totalCount слов | $percent%\n")
            }
            "0" -> return
            else -> println("Введите число 1, 2 или 0")
        }
    }
}

fun loadDictionary(): List<Word>  {
    val dictionary = mutableListOf<Word>()

    val pathName = "words.txt"
    val wordsFile = File(pathName)

    if (!wordsFile.exists()) {
        println("Файл $pathName не найден")
        return dictionary
    }

    val lines = wordsFile.readLines()

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

    return dictionary
}

data class Word(
    val text: String,
    val translate: String,
    val correctAnswersCount: Int = 0,
)