package org.example.dataSource

import org.example.Word
import java.io.File
import java.sql.Connection
import kotlin.use

class DatabaseUserDictionary(
    private val connection: Connection,
    private val chatId: Long,
    private val learnedAnswerCount: Int = MIN_CORRECT_ANSWERS,
) : IUserDictionary {

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
            stmt.setString(2, word)
            stmt.setInt(3, correctAnswersCount)
            stmt.executeUpdate()
        }
    }

    override fun setImageId(word: String, imageId: String) {
        val sql = "UPDATE words SET file_id = ? WHERE text = ?"

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, imageId)
            stmt.setString(2, word)
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
                        val text = parts[0].trim()
                        val translate = parts[1].trim()
                        val count = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                        val imagePath = parts.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }
                        val fileId = parts.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() }

                        if (text.isNotEmpty() && translate.isNotEmpty()) {
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
}