import org.example.Question
import org.example.Word
import org.example.asConsoleString
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class QuestionTest {

    @Test
    fun `test asConsoleString should format 4 variants correctly`() {
        val correctAnswer = Word("apple", "яблоко")

        val question = Question(
            variants = listOf(
                correctAnswer,
                Word("pear", "груша"),
                Word("peach", "персик"),
                Word("banana", "банан")
            ),
            correctAnswer = correctAnswer
        )

        val expected = """
            
            apple
             1 - яблоко
             2 - груша
             3 - персик
             4 - банан
             ----------
             0 - Меню
         
        """.trimIndent()

        assertEquals(expected, question.asConsoleString())
    }

    @Test
    fun `test asConsoleString variants order preservation`() {
        val correctAnswer = Word("apple", "яблоко")

        val question = Question(
            variants = listOf(
                Word("peach", "персик"),
                Word("banana", "банан"),
                correctAnswer,
                Word("pear", "груша")
            ),
            correctAnswer = correctAnswer
        )

        val expected = """
            
            apple
             1 - персик
             2 - банан
             3 - яблоко
             4 - груша
             ----------
             0 - Меню
         
        """.trimIndent()

        assertEquals(expected, question.asConsoleString())
    }

    @Test
    fun `test asConsoleString with empty variants`() {
        val correctAnswer = Word("apple", "яблоко")

        val question = Question(
            variants = emptyList(),
            correctAnswer = correctAnswer
        )

        assertEquals("", question.asConsoleString())
    }

    @Test
    fun `test asConsoleString handles 10 variants correctly`() {
        val variants = List(10) { i ->
            Word("word${i + 1}", "слово${i + 1}")
        }

        val question = Question(variants, variants[0])

        val expected = """
            
            word1
             1 - слово1
             2 - слово2
             3 - слово3
             4 - слово4
             5 - слово5
             6 - слово6
             7 - слово7
             8 - слово8
             9 - слово9
             10 - слово10
             ----------
             0 - Меню
         
        """.trimIndent()

        assertEquals(expected, question.asConsoleString())
    }

    @Test
    fun `test asConsoleString should limit output to maximum 10 variants`() {
        val variants = List(200) { i -> Word("word${i+1}", "слово${i + 1}") }

        val question = Question(variants, variants[0])

        val expected = """
            
            word1
             1 - слово1
             2 - слово2
             3 - слово3
             4 - слово4
             5 - слово5
             6 - слово6
             7 - слово7
             8 - слово8
             9 - слово9
             10 - слово10
             ----------
             0 - Меню
         
        """.trimIndent()

        assertEquals(expected, question.asConsoleString())
    }

    @Test
    fun `test asConsoleString preserves special characters in formatting`() {
        val correctAnswer = Word("cat/ .dog", "кошка (и собака)!")

        val question = Question(
            variants = listOf(
                correctAnswer,
                Word("[peach]", ".персик:"),
            ),
            correctAnswer = correctAnswer
        )

        val expected = """
            
            cat dog
             1 - кошка и собака
             2 - персик
             ----------
             0 - Меню
         
        """.trimIndent()

        assertEquals(expected, question.asConsoleString())
    }

    @Test
    fun `test asConsoleString with only blank variants returns empty string`() {
        val question = Question(
            variants = listOf(
                Word("apple", "   "),
                Word("pear", " "),
                Word("peach", "")
            ),
            correctAnswer = Word("apple", "яблоко")
        )

        assertEquals("", question.asConsoleString())
    }

    @Test
    fun `test asConsoleString filters blank words but keeps valid ones`() {
        val question = Question(
            variants = listOf(
                Word("apple", "яблоко"),
                Word("pear", "   "),
                Word("peach", "персик")
            ),
            correctAnswer = Word("apple", "яблоко")
        )

        val expected = """
        
        apple
         1 - яблоко
         2 - персик
         ----------
         0 - Меню
        
    """.trimIndent()

        assertEquals(expected, question.asConsoleString())
    }

    @Test
    fun `test asConsoleString invalid question text returns empty string`() {
        val question = Question(
            variants = listOf(
                Word("apple", "яблоко"),
                Word("pear", "груша")
            ),
            correctAnswer = Word("  ", "яблоко")
        )

        assertEquals("", question.asConsoleString())
    }
}