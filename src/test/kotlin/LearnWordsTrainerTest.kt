import org.example.LearnWordsTrainer
import org.example.Statistics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearnWordsTrainerTest {

    @Test
    fun `test statistics with 4 words of 7`() {
        val trainer = LearnWordsTrainer("src/test/4_words_of_7.txt")

        assertEquals(
            Statistics(learnedCount = 4, totalCount = 7, percent = 57),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test statistics with corrupted file`() {
        val trainer = LearnWordsTrainer("src/test/corrupted_file.txt")

        assertEquals(
            Statistics(learnedCount = 1, totalCount = 2, percent = 50),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test getNextQuestion() with 5 unlearned words`() {
        val trainer = LearnWordsTrainer("src/test/5_unlearned_words.txt")

        assertEquals(
            Statistics(learnedCount = 0, totalCount = 5, percent = 0),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test getNextQuestion() with 1 unlearned word`() {
        val trainer = LearnWordsTrainer("src/test/1_unlearned_word.txt")

        assertEquals(
            Statistics(learnedCount = 1, totalCount = 2, percent = 50),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test getNextQuestion() with all words learned`() {
        val trainer = LearnWordsTrainer("src/test/all_learned.txt")

        assertEquals(
            Statistics(learnedCount = 3, totalCount = 3, percent = 100),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test checkAnswer() with true`() {
        val trainer = LearnWordsTrainer("src/test/check_answer.txt")

        trainer.getNextQuestion()

        assertTrue(trainer.checkAnswer(userAnswerIndex = 0))
    }

    @Test
    fun `test checkAnswer() with false`() {
        val trainer = LearnWordsTrainer("src/test/check_answer.txt")

        trainer.getNextQuestion()

        assertFalse(trainer.checkAnswer(userAnswerIndex = 1))
    }

    @Test
    fun `test resetProgress() with 2 words in dictionary`() {
        val trainer = LearnWordsTrainer("src/test/reset_progress.txt")

        assertEquals(
            Statistics(learnedCount = 1, totalCount = 2, percent = 50),
            trainer.getStatistics()
        )

        trainer.resetProgress()

        assertEquals(
            Statistics(learnedCount = 0, totalCount = 2, percent = 0),
            trainer.getStatistics()
        )
    }
}