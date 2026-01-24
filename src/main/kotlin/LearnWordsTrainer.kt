package org.example

import java.io.File

const val PATH_NAME = "words.txt"

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

class LearnWordsTrainer(
    private val learnedAnswerCount: Int,
    private val countOfQuestionWords: Int,
) {
    var question: Question? = null
        private set

    private val dictionary = loadDictionary()

    fun getStatistics(): Statistics? {
        val totalCount = dictionary.size

        if (totalCount == 0) { return null }

        val learnedCount = dictionary.count { it.correctAnswersCount >= learnedAnswerCount }

        val percent = learnedCount * 100 / totalCount

        return Statistics(learnedCount, totalCount, percent)
    }

    fun getNextQuestion(): Question? {
        val notLearnedList = dictionary.filter { it.correctAnswersCount < learnedAnswerCount }

        if (notLearnedList.isEmpty()) { return null }

        val questionWords = notLearnedList.shuffled().take(countOfQuestionWords)

        val correctAnswer = questionWords.random()

        val answerOptions = if (questionWords.size < countOfQuestionWords) {
            val learnedList = dictionary.filter {
                it.correctAnswersCount >= learnedAnswerCount
            }.shuffled()

            questionWords + learnedList.take(countOfQuestionWords - questionWords.size)
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