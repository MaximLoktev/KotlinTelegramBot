package org.example

import java.io.File

const val PATH_NAME = "words.txt"
const val MIN_CORRECT_ANSWERS = 3
const val WORDS_PER_SESSION = 4

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
            "1" -> {
                while (true) {
                    val notLearnedList = dictionary.filter {
                        it.correctAnswersCount < MIN_CORRECT_ANSWERS
                    }

                    if (notLearnedList.isEmpty()) {
                        println("Все слова в словаре выучены\n")
                        break
                    }

                    val questionWords = notLearnedList.take(WORDS_PER_SESSION).shuffled()

                    val correctAnswer = questionWords.random()

                    println("\n${correctAnswer.text}:")

                    val answerOptions = if (questionWords.size < WORDS_PER_SESSION) {
                        val learnedList = dictionary.filter {
                            it.correctAnswersCount >= MIN_CORRECT_ANSWERS
                        }.shuffled()

                        questionWords + learnedList.take(WORDS_PER_SESSION - questionWords.size)
                    } else {
                        questionWords
                    }.shuffled()

                    answerOptions.forEachIndexed { index, word ->
                        println(" ${index + 1} - ${word.translate}")
                    }

                    println(" ----------")
                    println(" 0 - Меню\n")

                    print("Введите номер ответа: ")
                    when (val userAnswerInput = readln().toIntOrNull()) {
                        in 1..answerOptions.size -> {
                            val correctAnswerId = answerOptions.indexOf(correctAnswer) + 1

                            if (userAnswerInput == correctAnswerId) {
                                correctAnswer.correctAnswersCount++

                                saveDictionary(dictionary)

                                println("\nПравильно!")
                            } else {
                                println("\nНеправильно! ${correctAnswer.text} - ${correctAnswer.translate}")
                            }
                        }
                        0 -> break
                        else -> println("\nНекорректный ввод")
                    }
                }
            }
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

    val wordsFile = File(PATH_NAME)

    if (!wordsFile.exists()) {
        println("Файл $PATH_NAME не найден")
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

fun saveDictionary(dictionary: List<Word>) {
    val wordsFile = File(PATH_NAME)

    val lines = dictionary.map { word ->
        "${word.text}|${word.translate}|${word.correctAnswersCount}"
    }

    wordsFile.writeText(lines.joinToString("\n"))
}

data class Word(
    val text: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
)