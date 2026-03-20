package ai.openclaw.app.litert

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wraps the LiteRT-LM Engine with lifecycle management, thread safety,
 * and a conversation-per-session model that mirrors the Gateway chat flow.
 */
class LiteRTEngineWrapper(private val context: Context) {
  companion object {
    private const val TAG = "LiteRTEngine"
  }

  private val mutex = Mutex()
  private val engineRef = AtomicReference<Engine?>(null)
  private val conversationRef = AtomicReference<Conversation?>(null)
  private var loadedModelId: String? = null

  val isReady: Boolean
    get() = engineRef.get() != null && conversationRef.get() != null

  val currentModelId: String?
    get() = loadedModelId

  suspend fun loadModel(modelFile: File, modelId: String, systemInstruction: String? = null) {
    mutex.withLock {
      if (loadedModelId == modelId && engineRef.get() != null) {
        Log.d(TAG, "Model $modelId already loaded, skipping")
        return
      }

      releaseInternal()

      withContext(Dispatchers.IO) {
        Log.i(TAG, "Loading model $modelId from ${modelFile.absolutePath}")
        val engineConfig =
          EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = Backend.GPU(),
            cacheDir = context.cacheDir.path,
          )
        val engine = Engine(engineConfig)
        engine.initialize()

        val conversationConfig =
          ConversationConfig(
            systemInstruction =
              systemInstruction?.let { Contents.of(it) }
                ?: Contents.of("You are a helpful, concise assistant running on this device."),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95f, temperature = 0.7f),
          )
        val conversation = engine.createConversation(conversationConfig)

        engineRef.set(engine)
        conversationRef.set(conversation)
        loadedModelId = modelId
        Log.i(TAG, "Model $modelId loaded and ready")
      }
    }
  }

  /**
   * Send a message and stream the response token-by-token via Kotlin Flow.
   * Returns a Flow<String> of partial response chunks.
   */
  fun sendMessageStreaming(message: String): Flow<String> {
    val conversation =
      conversationRef.get() ?: throw IllegalStateException("No model loaded")
    return conversation.sendMessageAsync(message)
  }

  /** Send a message and wait for the complete response. */
  suspend fun sendMessage(message: String): String =
    withContext(Dispatchers.IO) {
      val conversation =
        conversationRef.get() ?: throw IllegalStateException("No model loaded")
      conversation.sendMessage(message).toString()
    }

  /** Reset the conversation while keeping the engine/model loaded. */
  suspend fun resetConversation(systemInstruction: String? = null) {
    mutex.withLock {
      val engine = engineRef.get() ?: return
      conversationRef.getAndSet(null)?.close()

      val conversationConfig =
        ConversationConfig(
          systemInstruction =
            systemInstruction?.let { Contents.of(it) }
              ?: Contents.of("You are a helpful, concise assistant running on this device."),
          samplerConfig = SamplerConfig(topK = 40, topP = 0.95f, temperature = 0.7f),
        )
      conversationRef.set(engine.createConversation(conversationConfig))
    }
  }

  suspend fun release() {
    mutex.withLock { releaseInternal() }
  }

  private fun releaseInternal() {
    try {
      conversationRef.getAndSet(null)?.close()
    } catch (e: Exception) {
      Log.w(TAG, "Error closing conversation", e)
    }
    try {
      engineRef.getAndSet(null)?.close()
    } catch (e: Exception) {
      Log.w(TAG, "Error closing engine", e)
    }
    loadedModelId = null
  }
}
