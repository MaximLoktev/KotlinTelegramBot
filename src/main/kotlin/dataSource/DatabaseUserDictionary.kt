package org.example.dataSource

import org.example.Word
import java.io.File
import java.sql.Connection
import kotlin.text.Regex
import kotlin.use

class DatabaseUserDictionary(
    private val connection: Connection,
    private val chatId: Long,
    private val learnedAnswerCount: Int = MIN_CORRECT_ANSWERS,
) : IUserDictionary {

    private val WORD_ALLOWED = Regex("^[a-zA-Zа-яА-Я0-9\\s\\-]+$")

    private val IMAGE_ID_ALLOWED = Regex("^[a-zA-Z0-9_\\-]+$")

    init {
        connection.createStatement().execute("PRAGMA foreign_keys = ON;")
    }

    fun loadInitialWordsIfEmpty(wordsFile: File) {
        val countSql = "SELECT COUNT(*) FROM words"

        val count = connection.prepareStatement(countSql).use { stmt ->
            val rs = stmt.executeQuery()
            if (rs.next()) rs.getInt(1) else 0
        }

        if (count == 0) {
            if (wordsFile.exists()) {
                println("🌱 Таблица words пуста. Загружаю базовый словарь из ${wordsFile.name}...")
                updateDictionary(wordsFile)
            } else {
                println("⚠️ Файл ${wordsFile.name} не найден. База осталась пустой.")
            }
        }
    }

    fun bindAllWordsToUser() {
        val sql = """
            INSERT OR IGNORE INTO user_answers (user_id, word_id, correct_answer_count)
            SELECT 
                (SELECT id FROM users WHERE chat_id = ?), 
                id, 
                0 
            FROM words
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, chatId)
            stmt.executeUpdate()
        }
    }

    override fun getSize(): Int {
        val sql = "SELECT COUNT(*) FROM words"

        return connection.prepareStatement(sql).use { stmt ->
            val rs = stmt.executeQuery()
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    override fun getNumOfLearnedWords(): Int {
        val sql = """
            SELECT COUNT(*) FROM user_answers 
            WHERE user_id = (SELECT id FROM users WHERE chat_id = ?) 
            AND correct_answer_count >= ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, chatId)
            stmt.setInt(2, learnedAnswerCount)

            val rs = stmt.executeQuery()
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    override fun getLearnedWords(): List<Word> {
        val sql = """
            SELECT w.*, ua.correct_answer_count as count 
            FROM words w
            JOIN user_answers ua ON w.id = ua.word_id
            WHERE ua.user_id = (SELECT id FROM users WHERE chat_id = ?)
            AND ua.correct_answer_count >= ?
        """.trimIndent()

        return executeQuery(sql, learnedAnswerCount)
    }

    override fun getUnlearnedWords(): List<Word> {
        val sql = """
            SELECT w.*, COALESCE(ua.correct_answer_count, 0) as count 
            FROM words w
            LEFT JOIN user_answers ua ON w.id = ua.word_id 
                AND ua.user_id = (SELECT id FROM users WHERE chat_id = ?)
            WHERE count < ?
        """.trimIndent()

        return executeQuery(sql, learnedAnswerCount)
    }

    override fun setCorrectAnswersCount(word: String, correctAnswersCount: Int) {
        if (containsSuspiciousPatterns(word)) { logSuspiciousActivity(word) }

        val safeWord = validateInput(
            value = word,
            fieldName = "word",
            maxLength = 80,
            allowed = WORD_ALLOWED
        )

        val safeCount = validateCount(correctAnswersCount)

        val sql = """
            INSERT INTO user_answers (user_id, word_id, correct_answer_count)
            VALUES (
                (SELECT id FROM users WHERE chat_id = ?),
                (SELECT id FROM words WHERE text = ?),
                ?
            )
            ON CONFLICT(user_id, word_id) DO UPDATE SET 
                correct_answer_count = excluded.correct_answer_count,
                updated_at = CURRENT_TIMESTAMP
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, chatId)
            stmt.setString(2, safeWord)
            stmt.setInt(3, safeCount)
            stmt.executeUpdate()
        }
    }

    override fun setImageId(word: String, imageId: String) {
        if (containsSuspiciousPatterns(word)) { logSuspiciousActivity(word) }

        if (containsSuspiciousPatterns(imageId)) { logSuspiciousActivity(imageId) }

        val safeWord = validateInput(
            value = word,
            fieldName = "word",
            maxLength = 80,
            allowed = WORD_ALLOWED
        )

        val safeImageId = validateInput(
            value = imageId,
            fieldName = "imageId",
            maxLength = 300,
            allowed = IMAGE_ID_ALLOWED
        )

        val sql = "UPDATE words SET file_id = ? WHERE text = ?"

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, safeImageId)
            stmt.setString(2, safeWord)
            stmt.executeUpdate()
        }
    }

    override fun resetUserProgress() {
        val sql = """
            UPDATE user_answers 
            SET correct_answer_count = 0 
            WHERE user_id = (SELECT id FROM users WHERE chat_id = ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, chatId)
            stmt.executeUpdate()
        }
    }

    override fun updateDictionary(wordsFile: File) {
        val insertWordSql = """
            INSERT OR IGNORE INTO words (text, translate, image_path, file_id) 
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        val insertAnswerSql = """
            INSERT INTO user_answers (user_id, word_id, correct_answer_count)
            VALUES (
                (SELECT id FROM users WHERE chat_id = ?),
                (SELECT id FROM words WHERE text = ?),
                ?
            )
            ON CONFLICT(user_id, word_id) DO UPDATE SET 
                correct_answer_count = excluded.correct_answer_count
        """.trimIndent()

        try {
            connection.autoCommit = false

            val wordStmt = connection.prepareStatement(insertWordSql)
            val answerStmt = connection.prepareStatement(insertAnswerSql)

            wordsFile.useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split("|")

                    if (parts.size >= 2) {
                        val textRaw = parts[0]
                        val translateRaw = parts[1]
                        val countRaw = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                        val imagePath = parts.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }
                        val fileIdRaw = parts.getOrNull(4)?.trim()

                        if (containsSuspiciousPatterns(textRaw)) { logSuspiciousActivity(textRaw) }
                        if (containsSuspiciousPatterns(translateRaw)) { logSuspiciousActivity(translateRaw) }

                        val text = runCatching {
                            validateInput(
                                value = textRaw,
                                fieldName = "word",
                                maxLength = 80,
                                allowed = WORD_ALLOWED
                            )
                        }.getOrNull() ?: return@forEach

                        val translate = translateRaw.trim()
                            .takeIf { it.isNotEmpty() && it.length <= 200 } ?: return@forEach

                        val count = runCatching { validateCount(countRaw) }.getOrNull() ?: return@forEach

                        val fileId = fileIdRaw?.let {
                            if (containsSuspiciousPatterns(it)) { logSuspiciousActivity(it) }

                            runCatching {
                                validateInput(
                                    value = it,
                                    fieldName = "imageId",
                                    maxLength = 300,
                                    allowed = IMAGE_ID_ALLOWED
                                )
                            }.getOrNull()
                        }

                        wordStmt.setString(1, text)
                        wordStmt.setString(2, translate)
                        wordStmt.setString(3, imagePath)
                        wordStmt.setString(4, fileId)
                        wordStmt.executeUpdate()

                        answerStmt.setLong(1, chatId)
                        answerStmt.setString(2, text)
                        answerStmt.setInt(3, count)
                        answerStmt.addBatch()
                    }
                }
            }
            answerStmt.executeBatch()
            connection.commit()

            println("✅ Словарь и прогресс для чата $chatId успешно обновлены.")

        } catch (e: Exception) {
            connection.rollback()
            println("❌ Ошибка импорта: ${e.message}")
            e.printStackTrace()
        } finally {
            connection.autoCommit = true
        }
    }

    private fun executeQuery(sql: String, minCount: Int): List<Word> {
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, chatId)
            stmt.setInt(2, minCount)

            val rs = stmt.executeQuery()
            val result = mutableListOf<Word>()

            while (rs.next()) {
                result.add(Word(
                    text = rs.getString("text"),
                    translate = rs.getString("translate"),
                    correctAnswersCount = rs.getInt("count"),
                    imagePath = rs.getString("image_path"),
                    fileId = rs.getString("file_id")
                ))
            }
            result
        }
    }

    private fun validateInput(
        value: String,
        fieldName: String,
        maxLength: Int,
        allowed: Regex
    ): String {
        val trimmed = value.trim()

        require(trimmed.isNotEmpty()) { "Пустое поле: $fieldName" }
        require(trimmed.length <= maxLength) { "Слишком длинное поле: $fieldName" }
        require(allowed.matches(trimmed)) { "Недопустимые символы в поле: $fieldName" }

        return trimmed
    }

    private fun validateCount(count: Int): Int {
        require(count >= 0) { "count не может быть отрицательным" }
        require(count <= 1_000_000) { "count слишком большой" }
        return count
    }

    private fun logSuspiciousActivity(input: String) {
        println("⚠️ Подозрительный ввод обнаружен (chatId=$chatId): $input")
    }

    private fun containsSuspiciousPatterns(input: String): Boolean {
        val suspicious = listOf(
            "'", "\"", ";", "--", "/*", "*/",
            "union", "select", "drop", "delete", "insert", "update"
        )

        val low = input.lowercase()

        if (suspicious.any { low.contains(it) }) return true

        val orAndRegex = Regex("\\b(or|and)\\b", RegexOption.IGNORE_CASE)

        return orAndRegex.containsMatchIn(input)
    }
}