package ai.openclaw.app.litert

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Handles model download, storage, and lifecycle management for on-device LiteRT models.
 * Models are stored under the app's internal files directory at `litert-models/`.
 */
class LiteRTModelManager(context: Context) {
  companion object {
    private const val TAG = "LiteRTModelManager"
    private const val MODELS_DIR = "litert-models"
    private const val HF_CDN_BASE = "https://huggingface.co"
    private const val BUFFER_SIZE = 8192
  }

  private val modelsDir = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

  private val _modelStates = MutableStateFlow<Map<String, OnDeviceModelState>>(emptyMap())
  val modelStates: StateFlow<Map<String, OnDeviceModelState>> = _modelStates.asStateFlow()

  init {
    refreshStates()
  }

  fun refreshStates() {
    val states =
      OnDeviceModelCatalog.models.associate { model ->
        val file = modelFile(model)
        val status =
          if (file.exists() && file.length() > 0) ModelStatus.Downloaded else ModelStatus.NotDownloaded
        model.id to OnDeviceModelState(model = model, status = status)
      }
    _modelStates.value = states
  }

  fun modelFile(model: OnDeviceModel): File = File(modelsDir, model.fileName)

  fun isDownloaded(modelId: String): Boolean {
    val model = OnDeviceModelCatalog.findById(modelId) ?: return false
    return modelFile(model).let { it.exists() && it.length() > 0 }
  }

  suspend fun downloadModel(model: OnDeviceModel): Result<File> =
    withContext(Dispatchers.IO) {
      val targetFile = modelFile(model)
      val tempFile = File(modelsDir, "${model.fileName}.tmp")

      updateState(model.id) { it.copy(status = ModelStatus.Downloading, downloadProgress = 0f) }

      try {
        val url = URL("$HF_CDN_BASE/${model.huggingFaceRepo}/resolve/main/${model.fileName}")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true

        if (tempFile.exists()) {
          connection.setRequestProperty("Range", "bytes=${tempFile.length()}-")
        }

        val responseCode = connection.responseCode
        val totalBytes: Long
        val append: Boolean

        if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
          totalBytes = tempFile.length() + connection.contentLengthLong
          append = true
        } else if (responseCode == HttpURLConnection.HTTP_OK) {
          totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: model.sizeBytes
          append = false
          if (tempFile.exists()) tempFile.delete()
        } else {
          connection.disconnect()
          updateState(model.id) { it.copy(status = ModelStatus.Error, downloadProgress = 0f) }
          return@withContext Result.failure(Exception("HTTP $responseCode"))
        }

        connection.inputStream.use { input ->
          tempFile.outputStream(append).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var downloaded = if (append) tempFile.length() else 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
              output.write(buffer, 0, bytesRead)
              downloaded += bytesRead
              val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
              updateState(model.id) { it.copy(downloadProgress = progress.coerceIn(0f, 1f)) }
            }
          }
        }
        connection.disconnect()

        tempFile.renameTo(targetFile)

        updateState(model.id) { it.copy(status = ModelStatus.Downloaded, downloadProgress = 1f) }
        Log.i(TAG, "Downloaded ${model.name} (${targetFile.length()} bytes)")
        Result.success(targetFile)
      } catch (e: CancellationException) {
        updateState(model.id) { it.copy(status = ModelStatus.NotDownloaded, downloadProgress = 0f) }
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Download failed for ${model.name}", e)
        updateState(model.id) { it.copy(status = ModelStatus.Error, downloadProgress = 0f) }
        Result.failure(e)
      }
    }

  fun deleteModel(model: OnDeviceModel) {
    val file = modelFile(model)
    val tempFile = File(modelsDir, "${model.fileName}.tmp")
    file.delete()
    tempFile.delete()
    updateState(model.id) { it.copy(status = ModelStatus.NotDownloaded, downloadProgress = 0f) }
    Log.i(TAG, "Deleted ${model.name}")
  }

  fun storageSummary(): Pair<Long, Long> {
    val used = modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    val free = modelsDir.usableSpace
    return used to free
  }

  private fun updateState(modelId: String, transform: (OnDeviceModelState) -> OnDeviceModelState) {
    _modelStates.value =
      _modelStates.value.toMutableMap().apply {
        this[modelId]?.let { this[modelId] = transform(it) }
      }
  }
}
