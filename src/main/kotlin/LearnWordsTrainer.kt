package org.example

import org.example.dataSource.IUserDictionary
import java.io.File

const val WORDS_PER_SESSION = 4

data class Word(
    val text: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
    val imagePath: String? = null,
    var fileId: String? = null,
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
    private val userDictionary: IUserDictionary,
    private val countOfQuestionWords: Int = WORDS_PER_SESSION,
) {
    var question: Question? = null
        private set

    fun getStatistics(): Statistics? {
        val totalCount = userDictionary.getSize()

        if (totalCount == 0) { return null }

        val learnedCount = userDictionary.getNumOfLearnedWords()

        val percent = learnedCount * 100 / totalCount

        return Statistics(learnedCount, totalCount, percent)
    }

    fun getNextQuestion(): Question? {
        val notLearnedList = userDictionary.getUnlearnedWords()

        if (notLearnedList.isEmpty()) { return null }

        val questionWords = notLearnedList.shuffled().take(countOfQuestionWords)

        val correctAnswer = questionWords.random()

        val answerOptions = if (questionWords.size < countOfQuestionWords) {
            val learnedList = userDictionary.getLearnedWords().shuffled()

            questionWords + learnedList.take(countOfQuestionWords - questionWords.size)
        } else {
            questionWords
        }.shuffled()

        question = Question(answerOptions, correctAnswer)

        return question
    }

    fun checkAnswer(userAnswerIndex: Int?): Boolean {
        val currentQuestion = question ?: return false

        val isCorrect = currentQuestion.variants.indexOf(currentQuestion.correctAnswer) == userAnswerIndex

        if (isCorrect) {
            currentQuestion.correctAnswer.correctAnswersCount++

            userDictionary.setCorrectAnswersCount(
                currentQuestion.correctAnswer.text,
                currentQuestion.correctAnswer.correctAnswersCount
            )
        }
        return isCorrect
    }

    fun resetProgress() {
        userDictionary.resetUserProgress()
    }

    fun setImageId(word: String, imageId: String) {
        userDictionary.setImageId(word, imageId)
    }

    fun updateDictionary(wordsFile: File) {
        userDictionary.updateDictionary(wordsFile)
    }
}