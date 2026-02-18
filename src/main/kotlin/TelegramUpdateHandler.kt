package org.example

import java.io.File

class TelegramUpdateHandler(
    private val service: TelegramBotService,
    private val dynamicMessage: DynamicMessage = DynamicMessage(),
) {

    private val trainers = HashMap<Long, LearnWordsTrainer>()

    fun handleUpdate(update: Update) {
        val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return

        val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer(fileName = "$chatId.txt") }

        val callbackData = update.callbackQuery?.data
        val messageText = update.message?.text
        val document = update.message?.document

        when {
            callbackData != null -> handleCallback(chatId, trainer, callbackData)
            messageText != null -> handleMessage(chatId, messageText)
            document != null -> downloadAndImportWords(chatId, trainer, document.fileId)
        }
    }

    private fun handleCallback(chatId: Long, trainer: LearnWordsTrainer, data: String) {
        when {
            data == CALLBACK_DATA_LEARN_WORDS -> checkNextQuestionAndSend(trainer, chatId)
            data == CALLBACK_DATA_STATISTICS -> sendStatistics(trainer, chatId)
            data == CALLBACK_DATA_RESET -> resetProgressAndShowMenu(chatId, trainer)
            data == CALLBACK_DATA_MAIN_MENU -> showMainMenu(chatId)
            data.startsWith(CALLBACK_DATA_ANSWER_PREFIX) -> checkAnswerAndSendNextStep(trainer, chatId, data)
        }
    }

    private fun handleMessage(chatId: Long, text: String) {
        if (text == "/start") {
            showMainMenu(chatId)
        } else {
            service.sendMessage(chatId, "Вы написали: <i>$text</i>")
        }
    }

    private fun showMainMenu(chatId: Long) {
        val lastId = dynamicMessage.getId(chatId)
        val menuText = "<b>Добро пожаловать в тренажер!</b>\n\nВыбери нужный раздел ниже:"
        val menuKeyboard = service.getMainMenuKeyboard()

        if (lastId != null) {
            service.editMessage(chatId, lastId, menuText, menuKeyboard)
        } else {
            val newId = service.sendMessage(chatId, menuText, menuKeyboard)
            newId?.let { dynamicMessage.saveId(chatId, it) }
        }
    }

    private fun checkAnswerAndSendNextStep(trainer: LearnWordsTrainer, chatId: Long, data: String) {
        val index = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull()
        val currentQuestion = trainer.question
        val lastId = dynamicMessage.getId(chatId)

        if (index != null && currentQuestion != null && lastId != null) {
            dynamicMessage.clearId(chatId)

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

        service.editMessage(chatId, messageId, text, null)
    }

    private fun checkNextQuestionAndSend(trainer: LearnWordsTrainer, chatId: Long) {
        val question = trainer.getNextQuestion()

        if (question == null) {
            service.sendMessage(chatId, "⭐ <b>Поздравляем!</b> Вы выучили все слова.")
            showMainMenu(chatId)
            return
        }

        sendPhotoAndUpdateFileId(trainer, chatId, question.correctAnswer)

        val questionId = service.sendQuestion(chatId, question)
        questionId?.let { dynamicMessage.saveId(chatId, it) }
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

        val keyboard = service.getBackToMenuKeyboard()

        val lastId = dynamicMessage.getId(chatId)

        if (lastId != null) {
            service.editMessage(chatId, lastId, text, keyboard)
        } else {
            val newId = service.sendMessage(chatId, text, keyboard)
            newId?.let { dynamicMessage.saveId(chatId, it) }
        }
    }

    private fun resetProgressAndShowMenu(chatId: Long, trainer: LearnWordsTrainer) {
        trainer.resetProgress()

        val lastId = dynamicMessage.getId(chatId)
        val text = "✅ <b>Прогресс успешно сброшен!</b>"
        val keyboard = service.getBackToMenuKeyboard()

        if (lastId != null) {
            service.editMessage(chatId, lastId, text, keyboard)
        } else {
            val newId = service.sendMessage(chatId, text, keyboard)
            newId?.let { dynamicMessage.saveId(chatId, it) }
        }
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
            trainer.saveDictionary()
        }
    }

    private fun downloadAndImportWords(chatId: Long, trainer: LearnWordsTrainer, fileId: String) {
        val fileInfo = service.getFileInfo(fileId) ?: return
        val file = File(fileInfo.fileUniqueId)

        val message = try {
            service.downloadFile(fileInfo.filePath, file)
            trainer.addWordsFromFile(file)
            "📂 <b>Файл обработан!</b> Новые слова добавлены."
        } catch (e: Exception) {
            "⚠️ Ошибка импорта: ${e.message}"
        } finally {
            if (file.exists()) file.delete()
        }

        service.sendMessage(chatId, message)
        showMainMenu(chatId)
    }
}