package org.example.dataSource

import org.example.Word
import java.io.File

const val PATH_NAME = "words.txt"
const val MIN_CORRECT_ANSWERS = 3

class FileUserDictionary(
    private val fileName: String = PATH_NAME,
    private val learnedAnswerCount: Int = MIN_CORRECT_ANSWERS,
) : IUserDictionary {

    private val dictionary = try {
        loadDictionary().toMutableList()
    } catch (_: Exception) {
        throw IllegalArgumentException("Некорректный файл")
    }

    override fun getSize(): Int = dictionary.size

    override fun getNumOfLearnedWords(): Int =
        dictionary.count { it.correctAnswersCount >= learnedAnswerCount }

    override fun getLearnedWords(): List<Word> =
        dictionary.filter { it.correctAnswersCount >= learnedAnswerCount }

    override fun getUnlearnedWords(): List<Word> =
        dictionary.filter { it.correctAnswersCount < learnedAnswerCount }

    override fun setCorrectAnswersCount(word: String, correctAnswersCount: Int) {
        dictionary.find { it.text == word }?.correctAnswersCount = correctAnswersCount
        saveDictionary()
    }

    override fun setImageId(word: String, imageId: String) {
        dictionary.find { it.text == word }?.fileId = imageId
        saveDictionary()
    }

    override fun resetUserProgress() {
        dictionary.forEach { it.correctAnswersCount = 0 }
        saveDictionary()
    }

    /**
     * Метод для добавления слов из внешнего файла
     */
    override fun updateDictionary(wordsFile: File) {
        val newWords = parseFile(wordsFile)

        val existingTexts = dictionary.map { it.text.lowercase() }.toSet()

        val uniqueNewWords = newWords.filter {
            it.text.lowercase() !in existingTexts
        }

        dictionary.addAll(uniqueNewWords)
        saveDictionary()
    }

    private fun loadDictionary(): List<Word> {
        val wordsFile = File(fileName)

        if (!wordsFile.exists()) {
            File(PATH_NAME).copyTo(wordsFile)
        }

        return parseFile(wordsFile)
    }

    /**
     * Метод для парсинга файла
     */
    private fun parseFile(file: File): List<Word> {
        if (!file.exists()) return emptyList()

        return file.useLines { lines ->
            lines.mapNotNull { line ->
                val trimmedLine = line.trim()

                if (trimmedLine.isEmpty()) return@mapNotNull null

                val parts = trimmedLine.split("|")

                if (parts.size < 2) return@mapNotNull null

                val text = parts[0].trim()
                val translate = parts[1].trim()

                if (text.isEmpty() || translate.isEmpty()) return@mapNotNull null

                Word(
                    text = text,
                    translate = translate,
                    correctAnswersCount = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0,
                    imagePath = parts.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() },
                    fileId = parts.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() }
                )
            }.toList()
        }
    }

    private fun saveDictionary() {
        val lines = dictionary.map {
            "${it.text}|${it.translate}|${it.correctAnswersCount}|${it.imagePath.orEmpty()}|${it.fileId.orEmpty()}"
        }
        File(fileName).writeText(lines.joinToString("\n"))
    }
}