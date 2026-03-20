package ai.openclaw.app.litert

import kotlinx.serialization.Serializable

@Serializable
data class OnDeviceModel(
  val id: String,
  val name: String,
  val huggingFaceRepo: String,
  val fileName: String,
  val sizeBytes: Long,
  val description: String,
  val capabilities: List<String> = listOf("chat"),
)

data class OnDeviceModelState(
  val model: OnDeviceModel,
  val status: ModelStatus,
  val downloadProgress: Float = 0f,
)

enum class ModelStatus {
  NotDownloaded,
  Downloading,
  Downloaded,
  Loading,
  Ready,
  Error,
}

/** Curated catalog of models known to work well on mobile via LiteRT-LM. */
object OnDeviceModelCatalog {
  val models: List<OnDeviceModel> =
    listOf(
      OnDeviceModel(
        id = "gemma3-1b",
        name = "Gemma 3 1B",
        huggingFaceRepo = "litert-community/Gemma3-1B-IT",
        fileName = "Gemma3-1B-IT.litertlm",
        sizeBytes = 1_100_000_000L,
        description = "Compact chat model, fast on-device inference.",
      ),
      OnDeviceModel(
        id = "gemma3-4b",
        name = "Gemma 3 4B",
        huggingFaceRepo = "litert-community/Gemma3-4B-IT",
        fileName = "Gemma3-4B-IT.litertlm",
        sizeBytes = 4_000_000_000L,
        description = "Balanced quality and speed for capable devices.",
        capabilities = listOf("chat", "vision"),
      ),
      OnDeviceModel(
        id = "gemma3n-e2b",
        name = "Gemma 3n E2B",
        huggingFaceRepo = "litert-community/Gemma3n-E2B-IT",
        fileName = "Gemma3n-E2B-IT.litertlm",
        sizeBytes = 2_000_000_000L,
        description = "Multimodal: text, image, and audio input.",
        capabilities = listOf("chat", "vision", "audio"),
      ),
    )

  fun findById(id: String): OnDeviceModel? = models.firstOrNull { it.id == id }
}
