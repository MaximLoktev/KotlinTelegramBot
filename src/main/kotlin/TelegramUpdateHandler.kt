package org.example

import java.io.File

class TelegramUpdateHandler(private val service: TelegramBotService) {

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
            data == CALLBACK_DATA_RESET -> {
                trainer.resetProgress()
                service.sendMessage(chatId, "Прогресс сброшен")
            }
            data == CALLBACK_DATA_MAIN_MENU -> service.sendMenu(chatId)
            data.startsWith(CALLBACK_DATA_ANSWER_PREFIX) -> checkAnswerAndSendNextStep(trainer, chatId, data)
        }
    }

    private fun handleMessage(chatId: Long, text: String) {
        if (text == "/start") service.sendMenu(chatId)
        else service.sendMessage(chatId, "Вы написали: $text")
    }

    private fun checkNextQuestionAndSend(trainer: LearnWordsTrainer, chatId: Long) {
        val question = trainer.getNextQuestion()

        if (question != null) service.sendQuestion(chatId, question)
        else service.sendMessage(chatId, "Вы выучили все слова в базе")
    }

    private fun sendStatistics(trainer: LearnWordsTrainer, chatId: Long) {
        val text = trainer.getStatistics()?.let {
            "Выучено ${it.learnedCount} из ${it.totalCount} слов | ${it.percent}%"
        } ?: "Словарь пуст"

        service.sendMessage(chatId, text)
    }

    private fun checkAnswerAndSendNextStep(trainer: LearnWordsTrainer, chatId: Long, data: String) {
        val index = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull()

        val currentQuestion = trainer.question

        if (index != null && currentQuestion != null) {
            if (trainer.checkAnswer(index)) {
                service.sendMessage(chatId, "Правильно!")
            } else {
                val word = currentQuestion.correctAnswer
                service.sendMessage(chatId, "Неправильно! ${word.text} – это ${word.translate}")
            }
            checkNextQuestionAndSend(trainer, chatId)
        } else {
            service.sendMessage(chatId, "Произошла ошибка или сессия устарела!")
            service.sendMenu(chatId)
        }
    }

    private fun downloadAndImportWords(chatId: Long, trainer: LearnWordsTrainer, fileId: String) {
        val fileInfo = service.getFileInfo(fileId) ?: return service.sendMessage(chatId, "Ошибка получения файла")

        val file = File(fileInfo.fileUniqueId)

        val message = try {
            service.downloadFile(fileInfo.filePath, file)
            trainer.addWordsFromFile(file)
            "Файл успешно обработан! Новые слова добавлены в ваш словарь."
        } catch (e: Exception) {
            "Произошла ошибка при обработке файла: ${e.message}"
        } finally {
            if (file.exists()) file.delete()
        }

        service.sendMessage(chatId, message)
    }
}