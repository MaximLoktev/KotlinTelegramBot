import org.example.dataSource.DatabaseInitializer
import org.example.dataSource.DatabaseUserDictionary
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.sql.Connection
import java.sql.DriverManager
import kotlin.io.path.createTempFile

class DatabaseUserDictionarySqlTest {

    private lateinit var conn: Connection
    private lateinit var dict: DatabaseUserDictionary

    @BeforeEach
    fun setup() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")

        DatabaseInitializer.setup(conn)

        conn.createStatement().use { st ->
            st.execute("INSERT INTO users(username, chat_id) VALUES ('user', 1);")
            st.execute("INSERT INTO words(text, translate) VALUES ('cat', 'кот');")
            st.execute("INSERT INTO words(text, translate) VALUES ('dog', 'пёс');")

            st.execute("""
            INSERT INTO user_answers(user_id, word_id, correct_answer_count)
            SELECT (SELECT id FROM users WHERE chat_id=1), id, 0 FROM words;
        """.trimIndent())
        }

        dict = DatabaseUserDictionary(conn, chatId = 1)
    }

    @AfterEach
    fun tearDown() {
        conn.close()
    }

    @Test
    fun `should work correctly with valid input`() {
        dict.setCorrectAnswersCount("cat", 3)

        assertEquals(3, maxAnswerCount(), "Обычный ввод должен корректно обновлять данные")
    }

    @Test
    fun `should handle DROP TABLE injection safely`() {
        runSafely {
            dict.setCorrectAnswersCount("'; DROP TABLE words; --", 5)
        }
        assertTrue(wordsTableExists(), "Таблица words не должна быть удалена после попытки DROP TABLE")
        assertEquals(2, countWords(), "Количество слов не должно измениться после попытки DROP TABLE")
        assertEquals(0, maxAnswerCount(), "Прогресс не должен измениться после попытки DROP TABLE")
    }

    @Test
    fun `should handle OR 1=1 injection safely`() {
        runSafely {
            dict.setCorrectAnswersCount("cat' OR '1'='1", 5)
        }
        assertEquals(0, maxAnswerCount(), "OR-инъекция не должна обновлять прогресс для всех слов")
        assertTrue(wordsTableExists(), "Таблица words должна существовать после попытки OR-инъекции")
        assertEquals(2, countWords(), "Количество слов не должно измениться после попытки OR-инъекции")
    }

    @Test
    fun `should handle UNION SELECT injection safely`() {
        runSafely {
            dict.setCorrectAnswersCount("' UNION SELECT * FROM users --", 5)
        }
        assertTrue(wordsTableExists(), "Таблица words должна существовать после попытки UNION-инъекции")
        assertEquals(2, countWords(), "Количество слов не должно измениться после попытки UNION-инъекции")
        assertEquals(0, maxAnswerCount(), "Прогресс не должен измениться после попытки UNION-инъекции")
    }

    @Test
    fun `should handle UPDATE injection safely in setImageId`() {
        runSafely {
            dict.setImageId(
                word = "cat",
                imageId = "x'); UPDATE words SET file_id='HACKED' WHERE 1=1; --"
            )
        }
        assertNull(getFileId("cat"), "file_id для 'cat' не должен установиться на инъекционный ввод")
        assertNull(getFileId("dog"), "file_id для 'dog' не должен измениться из-за инъекционного ввода")
        assertTrue(wordsTableExists(), "Таблица words должна существовать после попытки UPDATE-инъекции")
        assertEquals(2, countWords(), "Количество слов не должно измениться после попытки UPDATE-инъекции")
    }

    @Test
    fun `should handle malicious lines in updateDictionary`() {
        val tempFile = createTempFile(prefix = "words", suffix = ".txt").toFile()

        tempFile.writeText(
            """
            apple|яблоко|1||
            bad'); DROP TABLE words; --|плохо|0||
            nice|хорошо|2||
            """.trimIndent()
        )

        runSafely { dict.updateDictionary(tempFile) }

        val texts = conn.prepareStatement("SELECT text FROM words").use { st ->
            val rs = st.executeQuery()
            val list = mutableListOf<String>()
            while (rs.next()) list.add(rs.getString(1))
            list
        }
        assertTrue(wordsTableExists(), "Таблица words должна существовать после импорта с вредоносной строкой")
        assertTrue(texts.contains("apple"), "Слово 'apple' должно быть импортировано из файла")
        assertTrue(texts.contains("nice"), "Слово 'nice' должно быть импортировано из файла")
        assertFalse(
            texts.any { it.contains("drop", ignoreCase = true) },
            "Инъекционная строка не должна попасть в таблицу words"
        )
    }

    private fun runSafely(block: () -> Unit) {
        assertDoesNotThrow(
            {
                try {
                    block()
                } catch (_: IllegalArgumentException) {
                    // безопасное поведение: отклонили ввод
                }
            },
            "Метод не должен падать неконтролируемо при попытках SQL-инъекции"
        )
    }

    private fun wordsTableExists(): Boolean =
        conn.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='words'"
        ).use { st ->
            val rs = st.executeQuery()
            rs.next()
        }

    private fun countWords(): Int =
        conn.prepareStatement("SELECT COUNT(*) FROM words").use { st ->
            val rs = st.executeQuery()
            rs.next()
            rs.getInt(1)
        }

    private fun maxAnswerCount(): Int =
        conn.prepareStatement("SELECT MAX(correct_answer_count) FROM user_answers").use { st ->
            val rs = st.executeQuery()
            rs.next()
            rs.getInt(1)
        }

    private fun getFileId(text: String): String? =
        conn.prepareStatement("SELECT file_id FROM words WHERE text=?").use { st ->
            st.setString(1, text)
            val rs = st.executeQuery()
            if (rs.next()) rs.getString(1) else null
        }
}