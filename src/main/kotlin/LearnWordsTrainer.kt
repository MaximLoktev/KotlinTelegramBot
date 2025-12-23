package org.example

import java.io.File

const val PATH_NAME = "words.txt"
const val MIN_CORRECT_ANSWERS = 3
const val WORDS_PER_SESSION = 4

data class Word(
    val text: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
)

data class Statistics(
    val learnedCount: Int,
    val totalCount: Int,
    val percent: Int,
)

data class Question(
    val variants: List<Word>,
    val correctAnswer: Word,
)

class LearnWordsTrainer {

    private val dictionary = loadDictionary()

    private var question: Question? = null

    fun getStatistics(): Statistics? {
        val totalCount = dictionary.size

        if (totalCount == 0) { return null }

        val learnedCount = dictionary.count { it.correctAnswersCount >= MIN_CORRECT_ANSWERS }

        val percent = learnedCount * 100 / totalCount

        return Statistics(learnedCount, totalCount, percent)
    }

    fun getNextQuestion(): Question? {
        val notLearnedList = dictionary.filter { it.correctAnswersCount < MIN_CORRECT_ANSWERS }

        if (notLearnedList.isEmpty()) { return null }

        val questionWords = notLearnedList.take(WORDS_PER_SESSION).shuffled()

        val correctAnswer = questionWords.random()

        val answerOptions = if (questionWords.size < WORDS_PER_SESSION) {
            val learnedList = dictionary.filter {
                it.correctAnswersCount >= MIN_CORRECT_ANSWERS
            }.shuffled()

            questionWords + learnedList.take(WORDS_PER_SESSION - questionWords.size)
        } else {
            questionWords
        }.shuffled()

        question = Question(answerOptions, correctAnswer)

        return question
    }

    fun checkAnswer(userAnswerIndex: Int?): Boolean {
        return question?.let {
            val correctAnswerId = it.variants.indexOf(it.correctAnswer)

            if (correctAnswerId == userAnswerIndex) {
                it.correctAnswer.correctAnswersCount++
                saveDictionary(dictionary)
                true
            } else {
                false
            }
        } ?: false
    }

    private fun loadDictionary(): List<Word> {
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

    private fun saveDictionary(dictionary: List<Word>) {
        val wordsFile = File(PATH_NAME)

        val lines = dictionary.map { word ->
            "${word.text}|${word.translate}|${word.correctAnswersCount}"
        }

        wordsFile.writeText(lines.joinToString("\n"))
    }
}