package org.example.telegram

import org.example.LearnWordsTrainer
import org.example.Word
import org.example.dataSource.DatabaseUserDictionary
import org.example.dataSource.PATH_NAME
import java.io.File
import java.sql.Connection

class TelegramUpdateHandler(
    private val service: TelegramBotService,
    private val connection: Connection,
    private val dynamicMessage: DynamicMessage = DynamicMessage(),
) {

    private val trainers = HashMap<Long, LearnWordsTrainer>()

    fun handleUpdate(update: Update) {
        val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return

        ensureUserExists(chatId, getDisplayName(chatId, update))

        val trainer = trainers.getOrPut(chatId) {
            val dictionary = DatabaseUserDictionary(connection, chatId)
            dictionary.loadInitialWordsIfEmpty(File(PATH_NAME))
            dictionary.bindAllWordsToUser()

            LearnWordsTrainer(userDictionary = dictionary)
        }

        val callbackData = update.callbackQuery?.data
        val messageText = update.message?.text
        val messageId = update.message?.messageId
        val document = update.message?.document

        when {
            callbackData != null -> handleCallback(chatId, trainer, callbackData)
            messageText != null -> handleMessage(chatId, messageText, messageId)
            document != null -> downloadAndImportWords(chatId, trainer, document.fileId)
        }
    }

    private fun handleCallback(chatId: Long, trainer: LearnWordsTrainer, data: String) {
        when {
            data == CALLBACK_DATA_LEARN_WORDS -> {
                val currentMenuId = dynamicMessage.getCurrentMessageId(chatId)

                if (currentMenuId != null) {
                    service.deleteMessage(chatId, currentMenuId)
                    dynamicMessage.clear(chatId)
                }
                checkNextQuestionAndSend(trainer, chatId)
            }
            data == CALLBACK_DATA_STATISTICS -> sendStatistics(trainer, chatId)
            data == CALLBACK_DATA_RESET -> resetProgressAndShowMenu(chatId, trainer)
            data == CALLBACK_DATA_MAIN_MENU -> showMainMenu(chatId)
            data.startsWith(CALLBACK_DATA_ANSWER_PREFIX) -> checkAnswerAndSendNextStep(trainer, chatId, data)
        }
    }

    private fun handleMessage(chatId: Long, text: String, messageId: Long?) {
        when (text) {
            "/start" -> showMainMenu(chatId)
            "/undo" -> {
                performUndo(chatId)
                messageId?.let { service.deleteMessage(chatId, it) }
            }
            else -> service.sendMessage(chatId, "Вы написали: <i>$text</i>")
        }
    }

    private fun showMainMenu(chatId: Long) {
        updateScreen(
            chatId = chatId,
            text = "<b>Добро пожаловать в тренажер!</b>\n\nВыбери нужный раздел ниже:",
            keyboard = service.getMainMenuKeyboard()
        )
    }

    private fun checkAnswerAndSendNextStep(trainer: LearnWordsTrainer, chatId: Long, data: String) {
        val index = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull()
        val currentQuestion = trainer.question
        val lastId = dynamicMessage.getCurrentMessageId(chatId)

        if (index != null && currentQuestion != null && lastId != null) {
            val isCorrect = trainer.checkAnswer(index)

            showAnswerStatus(chatId, lastId, currentQuestion.correctAnswer, isCorrect)

            checkNextQuestionAndSend(trainer, chatId)
        } else {
            showMainMenu(chatId)
        }
    }

    private fun showAnswerStatus(chatId: Long, messageId: Long, word: Word, isCorrect: Boolean) {
        val icon = if (isCorrect) "✅" else "❌"

        val text = """
            <b>$icon ${if (isCorrect) "Правильно!" else "Ошибка"}</b>
            Слово: <u>${word.text}</u> — это <b>${word.translate}</b>
        """.trimIndent()

        val result = service.editMessage(chatId, messageId, text, null)

        if (result == null) {
            service.sendMessage(chatId, text)
        }
    }

    private fun checkNextQuestionAndSend(trainer: LearnWordsTrainer, chatId: Long) {
        val question = trainer.getNextQuestion()

        if (question == null) {
            updateScreen(
                chatId = chatId,
                text ="⭐ <b>Поздравляем!</b> Вы выучили все слова.",
                keyboard = service.getMainMenuKeyboard(),
                saveToHistory = true
            )
            return
        }

        dynamicMessage.clear(chatId)

        sendPhotoAndUpdateFileId(trainer, chatId, question.correctAnswer)

        updateScreen(
            chatId = chatId,
            text = "Как переводится слово: <b>${question.correctAnswer.text}</b>?",
            keyboard = service.getQuestionKeyboard(question),
            saveToHistory = true
        )
    }

    private fun sendStatistics(trainer: LearnWordsTrainer, chatId: Long) {
        val stats = trainer.getStatistics() ?: return

        val doneSteps = stats.percent / 10
        val progressBar = "🟩".repeat(doneSteps) + "⬜".repeat(10 - doneSteps)

        val text = """
            📊 <b>Твой прогресс:</b>
            $progressBar ${stats.percent}%
        
            ✅ Выучено слов: <b>${stats.learnedCount}</b>
            📚 Всего в базе: <b>${stats.totalCount}</b>
        """.trimIndent()

        updateScreen(chatId, text, service.getBackToMenuKeyboard())
    }

    private fun resetProgressAndShowMenu(chatId: Long, trainer: LearnWordsTrainer) {
        trainer.resetProgress()

        updateScreen(
            chatId = chatId,
            text = "✅ <b>Прогресс успешно сброшен!</b>",
            keyboard = service.getBackToMenuKeyboard()
        )
    }

    private fun sendPhotoAndUpdateFileId(trainer: LearnWordsTrainer, chatId: Long, word: Word) {
        val photoResult = when {
            !word.fileId.isNullOrBlank() -> {
                word.fileId?.let {
                    service.sendPhoto(chatId, it, hasSpoiler = true)
                }
            }
            !word.imagePath.isNullOrBlank() -> {
                val file = File(word.imagePath)

                if (file.exists()) {
                    service.sendPhoto(chatId, file, hasSpoiler = true)
                } else {
                    println("Файл по пути ${word.imagePath} не найден")
                    null
                }
            }
            else -> return
        }

        if (word.fileId.isNullOrBlank() && photoResult != null) {
            word.fileId = photoResult.fileId
            trainer.setImageId(word.text, photoResult.fileId)
        }
    }

    private fun downloadAndImportWords(chatId: Long, trainer: LearnWordsTrainer, fileId: String) {
        val fileInfo = service.getFileInfo(fileId) ?: return
        val file = File(fileInfo.fileUniqueId)

        val message = try {
            service.downloadFile(fileInfo.filePath, file)
            trainer.updateDictionary(file)
            "📂 <b>Файл обработан!</b> Новые слова добавлены."
        } catch (e: Exception) {
            "⚠️ Ошибка импорта: ${e.message}"
        } finally {
            if (file.exists()) file.delete()
        }

        service.sendMessage(chatId, message)
        showMainMenu(chatId)
    }

    private fun performUndo(chatId: Long) {
        val previousState = dynamicMessage.popPreviousState(chatId)
        val currentId = dynamicMessage.getCurrentMessageId(chatId)

        if (previousState != null && currentId != null) {
            val result = service.editMessage(chatId, currentId, previousState.text, previousState.keyboard)

            if (result == null) {
                showMainMenu(chatId)
            }
        } else {
            showMainMenu(chatId)
        }
    }

    private fun updateScreen(chatId: Long, text: String, keyboard: ReplyMarkup?, saveToHistory: Boolean = true) {
        val currentId = dynamicMessage.getCurrentMessageId(chatId)
        var finalId: Long? = null

        if (currentId != null) {
            finalId = service.editMessage(chatId, currentId, text, keyboard)
        }

        if (finalId == null) {
            finalId = service.sendMessage(chatId, text, keyboard)
        }

        if (saveToHistory && finalId != null) {
            dynamicMessage.saveState(
                chatId = chatId,
                newState = MessageState(finalId, text, keyboard)
            )
        }
    }

    private fun ensureUserExists(chatId: Long, username: String) {
        val sql = """
            INSERT INTO users (chat_id, username) 
            VALUES (?, ?)
            ON CONFLICT(chat_id) DO UPDATE SET username = excluded.username
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, chatId)
            stmt.setString(2, username)
            stmt.executeUpdate()
        }
    }

    private fun getDisplayName(chatId: Long, update: Update): String {
        val message = update.message ?: update.callbackQuery?.message
        val chatTitle = message?.chat?.title

        if (!chatTitle.isNullOrBlank()) { return "Группа: $chatTitle" }

        val from = update.message?.from ?: update.callbackQuery?.from

        return if (from != null) {
            val fullName = "${from.firstName} ${from.lastName ?: ""}".trim()

            if (!from.username.isNullOrBlank()) {
                "@${from.username} ($fullName)"
            } else {
                fullName
            }
        } else {
            "User_$chatId"
        }
    }
}