package org.example.dataSource

import java.sql.Connection

object DatabaseInitializer {

    fun setup(connection: Connection) {
        val statement = connection.createStatement()

        statement.execute("PRAGMA foreign_keys = ON;")

        try {
            connection.autoCommit = false

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS words (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    text TEXT UNIQUE,
                    translate TEXT,
                    image_path TEXT,
                    file_id TEXT
                );
            """.trimIndent())

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY,
                    username TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    chat_id INTEGER UNIQUE
                );
            """.trimIndent())

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS user_answers (
                    user_id INTEGER,
                    word_id INTEGER,
                    correct_answer_count INTEGER DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id, word_id),
                    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                    FOREIGN KEY (word_id) REFERENCES words (id) ON DELETE CASCADE
                );
            """.trimIndent())

            connection.commit()
            println("✅ База данных успешно инициализирована.")
        } catch (e: Exception) {
            connection.rollback()
            println("❌ Ошибка при инициализации базы данных: ${e.message}")
            throw e
        } finally {
            connection.autoCommit = true
        }
    }
}