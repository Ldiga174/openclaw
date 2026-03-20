package ai.openclaw.app.litert

import android.util.Log
import ai.openclaw.app.chat.ChatMessage
import ai.openclaw.app.chat.ChatMessageContent
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Chat controller that runs inference on-device via LiteRT-LM instead of
 * routing through the Gateway. Mirrors the ChatController API surface so
 * the UI can switch between local and gateway modes transparently.
 */
class LocalChatController(
  private val scope: CoroutineScope,
  private val engine: LiteRTEngineWrapper,
) {
  companion object {
    private const val TAG = "LocalChat"
  }

  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private val _errorText = MutableStateFlow<String?>(null)
  val errorText: StateFlow<String?> = _errorText.asStateFlow()

  private val _streamingAssistantText = MutableStateFlow<String?>(null)
  val streamingAssistantText: StateFlow<String?> = _streamingAssistantText.asStateFlow()

  private val _isGenerating = MutableStateFlow(false)
  val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

  private var activeJob: Job? = null

  fun sendMessage(message: String) {
    val trimmed = message.trim()
    if (trimmed.isEmpty()) return

    if (!engine.isReady) {
      _errorText.value = "No on-device model loaded. Download one in Settings."
      return
    }

    val userMsg =
      ChatMessage(
        id = UUID.randomUUID().toString(),
        role = "user",
        content = listOf(ChatMessageContent(type = "text", text = trimmed)),
        timestampMs = System.currentTimeMillis(),
      )
    _messages.value = _messages.value + userMsg
    _errorText.value = null
    _streamingAssistantText.value = null
    _isGenerating.value = true

    activeJob =
      scope.launch {
        try {
          val accumulated = StringBuilder()
          engine
            .sendMessageStreaming(trimmed)
            .catch { e ->
              Log.e(TAG, "Streaming error", e)
              _errorText.value = e.message ?: "Inference failed"
              _isGenerating.value = false
            }
            .collect { chunk ->
              accumulated.append(chunk)
              _streamingAssistantText.value = accumulated.toString()
            }

          val finalText = accumulated.toString()
          if (finalText.isNotEmpty()) {
            val assistantMsg =
              ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = listOf(ChatMessageContent(type = "text", text = finalText)),
                timestampMs = System.currentTimeMillis(),
              )
            _messages.value = _messages.value + assistantMsg
          }
          _streamingAssistantText.value = null
          _isGenerating.value = false
        } catch (e: Exception) {
          Log.e(TAG, "Send failed", e)
          _errorText.value = e.message ?: "Inference failed"
          _streamingAssistantText.value = null
          _isGenerating.value = false
        }
      }
  }

  fun abort() {
    activeJob?.cancel()
    activeJob = null
    _streamingAssistantText.value = null
    _isGenerating.value = false
  }

  fun clearHistory() {
    _messages.value = emptyList()
    scope.launch {
      try {
        engine.resetConversation()
      } catch (e: Exception) {
        Log.w(TAG, "Failed to reset conversation", e)
      }
    }
  }
}
