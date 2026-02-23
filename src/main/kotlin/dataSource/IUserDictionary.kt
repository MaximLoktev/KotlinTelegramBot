package org.example.dataSource

import org.example.Word
import java.io.File

interface IUserDictionary {
    fun getSize(): Int
    fun getNumOfLearnedWords(): Int
    fun getLearnedWords(): List<Word>
    fun getUnlearnedWords(): List<Word>
    fun setCorrectAnswersCount(word: String, correctAnswersCount: Int)
    fun setImageId(word: String, imageId: String)
    fun resetUserProgress()
    fun updateDictionary(wordsFile: File)
}