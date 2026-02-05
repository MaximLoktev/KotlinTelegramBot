package org.example

/**
 * Вспомогательное расширение для очистки строки.
 * Оставляет буквы, цифры и одиночные пробелы внутри.
 */
private fun String.sanitize(): String {
    return this.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ\\s]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun Question.asConsoleString(): String {
    val sanitizedQuestion = correctAnswer.text.sanitize()

    if (sanitizedQuestion.isBlank()) return ""

    if (variants.isEmpty()) return ""

    val validVariants = variants
        .map { it.copy(translate = it.translate.sanitize()) }
        .filter { it.translate.isNotBlank() }
        .take(10)

    if (validVariants.isEmpty()) return ""

    val variantsBlock = validVariants
        .mapIndexed { index, word -> " ${index + 1} - ${word.translate}" }
        .joinToString("\n")

    return "\n${sanitizedQuestion}\n" + variantsBlock + "\n ----------\n 0 - Меню\n"
}

fun main() {
    val trainer = LearnWordsTrainer()

    while (true) {
        println("""
            
            Меню: 
            1 – Учить слова
            2 – Статистика
            0 – Выход
        """.trimIndent())

        print("\nВведите число: ")
        val input = readln()

        when (input) {
            "1" -> {
                while (true) {
                    val question = trainer.getNextQuestion()

                    if (question == null) {
                        println("\nВсе слова в словаре выучены")
                        break
                    }

                    println(question.asConsoleString())

                    print("Введите номер ответа: ")
                    when (val userAnswerInput = readln().toIntOrNull()) {
                        in 1..question.variants.size -> {
                            if (trainer.checkAnswer(userAnswerInput?.minus(1))) {
                                println("\nПравильно!")
                            } else {
                                println("\nНеправильно! ${question.correctAnswer.text} - ${question.correctAnswer.translate}")
                            }
                        }
                        0 -> break
                        else -> println("\nНекорректный ввод")
                    }
                }
            }
            "2" -> {
                trainer.getStatistics()?.let {
                    println("\nВыучено ${it.learnedCount} из ${it.totalCount} слов | ${it.percent}%")
                    continue
                }
                println("\nСловарь пуст")
            }
            "0" -> return
            else -> println("\nВведите число 1, 2 или 0")
        }
    }
}