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

class LearnWordsTrainer(
    private val fileName: String = PATH_NAME,
    private val learnedAnswerCount: Int = MIN_CORRECT_ANSWERS,
    private val countOfQuestionWords: Int = WORDS_PER_SESSION,
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
                saveDictionary()
                true
            } else {
                false
            }
        } ?: false
    }

    fun resetProgress() {
        dictionary.forEach { it.correctAnswersCount = 0 }
        saveDictionary()
    }

    private fun loadDictionary(): List<Word> {
        val dictionary = mutableListOf<Word>()

        val wordsFile = File(fileName)

        if (!wordsFile.exists()) {
            File(PATH_NAME).copyTo(wordsFile)
        }

        val lines = wordsFile.readLines()

        for (line in lines) {
            if (line.trim().isEmpty()) continue

            val newLine = line.split("|")

            if (newLine.size < 3) {
                println("Некорректная строка (недостаточно данных): $line")
                continue
            }

            val text = newLine[0].trim()
            val translate = newLine[1].trim()
            val countStr = newLine[2].trim()

            if (text.isEmpty() || translate.isEmpty()) {
                println("Некорректная строка (пустое слово или перевод): $line")
                continue
            }

            val correctAnswersCount = countStr.toIntOrNull()

            if (correctAnswersCount == null) {
                println("Некорректная строка (некорректное число ответов): $line")
                continue
            }

            val model = Word(
                text = text,
                translate = translate,
                correctAnswersCount = correctAnswersCount
            )
            dictionary.add(model)
        }

        return dictionary
    }

    private fun saveDictionary() {
        val wordsFile = File(fileName)

        val lines = dictionary.map { word ->
            "${word.text}|${word.translate}|${word.correctAnswersCount}"
        }

        wordsFile.writeText(lines.joinToString("\n"))
    }
}