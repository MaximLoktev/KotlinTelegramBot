package org.example

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Update(
    @SerialName("update_id")
    val updateId: Long,
    @SerialName("message")
    val message: Message? = null,
    @SerialName("callback_query")
    val callbackQuery: CallbackQuery? = null,
)

@Serializable
data class Response(
    @SerialName("result")
    val result: List<Update>,
)

@Serializable
data class Message(
    @SerialName("message_id")
    val messageId: Long,
    @SerialName("text")
    val text: String? = null,
    @SerialName("chat")
    val chat: Chat,
    @SerialName("document")
    val document: Document? = null,
)

@Serializable
data class Document(
    @SerialName("file_name")
    val fileName: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Long,
)

@Serializable
data class CallbackQuery(
    @SerialName("data")
    val data: String,
    @SerialName("message")
    val message: Message? = null,
)

@Serializable
data class Chat(
    @SerialName("id")
    val id: Long,
)

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("text")
    val text: String,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null,
    @SerialName("parse_mode")
    val parseMode: String,
)

@Serializable
data class ReplyMarkup(
    @SerialName("inline_keyboard")
    val inlineKeyboard: List<List<InlineKeyboard>>,
)

@Serializable
data class InlineKeyboard(
    @SerialName("text")
    val text: String,
    @SerialName("callback_data")
    val callbackData: String,
)

@Serializable
data class EditMessageRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("message_id")
    val messageId: Long,
    @SerialName("text")
    val text: String,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null,
    @SerialName("parse_mode")
    val parseMode: String,
)

@Serializable
data class PhotoRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("photo")
    val photoId: String,
    @SerialName("has_spoiler")
    val hasSpoiler: Boolean,
)

@Serializable
data class PhotoResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("result")
    val result: Result? = null,
)

@Serializable
data class Result(
    @SerialName("photo")
    val photo: List<Photo>,
)

@Serializable
data class Photo(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Long,
    @SerialName("width")
    val width: Long,
    @SerialName("height")
    val height: Long,
)

@Serializable
data class GetFileRequest(
    @SerialName("file_id")
    val fileId: String,
)

@Serializable
data class GetFileResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("result")
    val result: FileInfo? = null,
)

@Serializable
data class FileInfo(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Long,
    @SerialName("file_path")
    val filePath: String,
)

@Serializable
data class MessageResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("result")
    val result: MessageData? = null,
)

@Serializable
data class MessageData(
    @SerialName("message_id")
    val messageId: Long,
)

@Serializable
data class TelegramResponse<T>(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("result")
    val result: T? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("error_code")
    val errorCode: Int? = null,
)

@Serializable
data class DeleteMessageRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("message_id")
    val messageId: Long,
)