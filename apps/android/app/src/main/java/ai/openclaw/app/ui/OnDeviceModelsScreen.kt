package ai.openclaw.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.litert.ModelStatus
import ai.openclaw.app.litert.OnDeviceModelCatalog
import ai.openclaw.app.litert.OnDeviceModelState
import kotlinx.coroutines.launch

@Composable
fun OnDeviceModelsScreen(viewModel: MainViewModel) {
  val scope = rememberCoroutineScope()

  val onDeviceEnabled by viewModel.onDeviceEnabled.collectAsState()
  val selectedModelId by viewModel.selectedOnDeviceModelId.collectAsState()
  val modelStates by viewModel.onDeviceModelStates.collectAsState()
  val modelLoading by viewModel.onDeviceModelLoading.collectAsState()
  val modelReady by viewModel.onDeviceModelReady.collectAsState()

  val listItemColors =
    ListItemDefaults.colors(
      containerColor = Color.Transparent,
      headlineColor = mobileText,
      supportingColor = mobileTextSecondary,
      trailingIconColor = mobileTextSecondary,
      leadingIconColor = mobileTextSecondary,
    )

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(mobileBackgroundGradient),
  ) {
    LazyColumn(
      modifier =
        Modifier
          .fillMaxWidth()
          .fillMaxHeight()
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        Text(
          "ON-DEVICE AI",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }

      item {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .border(width = 1.dp, color = mobileBorder, shape = RoundedCornerShape(14.dp))
              .background(mobileCardSurface, RoundedCornerShape(14.dp)),
        ) {
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("On-Device Mode", style = mobileHeadline) },
            supportingContent = {
              Text(
                if (onDeviceEnabled) {
                  when {
                    modelReady -> "Active — running locally"
                    modelLoading -> "Loading model…"
                    else -> "Enabled — select and download a model below"
                  }
                } else {
                  "Route chat through local AI model instead of Gateway."
                },
                style = mobileCallout,
              )
            },
            trailingContent = {
              Switch(
                checked = onDeviceEnabled,
                onCheckedChange = { viewModel.setOnDeviceModelEnabled(it) },
              )
            },
          )
        }
      }

      item {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          "MODELS",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }

      items(OnDeviceModelCatalog.models) { model ->
        val state = modelStates[model.id] ?: OnDeviceModelState(model, ModelStatus.NotDownloaded)
        val isSelected = selectedModelId == model.id

        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .border(
                width = 1.dp,
                color = if (isSelected && state.status == ModelStatus.Downloaded) mobileAccentBorderStrong else mobileBorder,
                shape = RoundedCornerShape(14.dp),
              )
              .background(mobileCardSurface, RoundedCornerShape(14.dp)),
        ) {
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.name, style = mobileHeadline)
                if (isSelected && modelReady) {
                  Text(
                    " · Active",
                    style = mobileCaption1.copy(fontWeight = FontWeight.Bold),
                    color = mobileSuccess,
                  )
                }
              }
            },
            supportingContent = {
              Column {
                Text(model.description, style = mobileCallout)
                Text(
                  formatSize(model.sizeBytes) + " · " + model.capabilities.joinToString(", "),
                  style = mobileCaption1,
                  color = mobileTextTertiary,
                )
              }
            },
            leadingContent = {
              RadioButton(
                selected = isSelected,
                onClick = {
                  if (state.status == ModelStatus.Downloaded) {
                    viewModel.setSelectedOnDeviceModelId(model.id)
                  }
                },
                enabled = state.status == ModelStatus.Downloaded,
              )
            },
          )

          if (state.status == ModelStatus.Downloading) {
            val animatedProgress by
              animateFloatAsState(targetValue = state.downloadProgress, label = "dl")
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
              LinearProgressIndicator(
                progress = { animatedProgress },
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = mobileAccent,
                trackColor = mobileSurfaceStrong,
              )
              Text(
                "${(state.downloadProgress * 100).toInt()}%",
                style = mobileCaption2,
                color = mobileTextTertiary,
                modifier = Modifier.padding(top = 4.dp),
              )
            }
          }

          HorizontalDivider(color = mobileBorder)

          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            when (state.status) {
              ModelStatus.NotDownloaded, ModelStatus.Error -> {
                Button(
                  onClick = {
                    scope.launch { viewModel.liteRTModelManager.downloadModel(model) }
                  },
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = mobileAccent,
                      contentColor = Color.White,
                    ),
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(
                    if (state.status == ModelStatus.Error) "Retry Download" else "Download",
                    style = mobileCallout.copy(fontWeight = FontWeight.Bold),
                  )
                }
                if (state.status == ModelStatus.Error) {
                  Text(
                    "Download failed",
                    style = mobileCaption1,
                    color = mobileDanger,
                    modifier = Modifier.align(Alignment.CenterVertically),
                  )
                }
              }
              ModelStatus.Downloading -> {
                Text(
                  "Downloading…",
                  style = mobileCallout,
                  color = mobileTextSecondary,
                  modifier = Modifier.align(Alignment.CenterVertically),
                )
              }
              ModelStatus.Downloaded, ModelStatus.Ready -> {
                Button(
                  onClick = {
                    viewModel.liteRTModelManager.deleteModel(model)
                    if (isSelected) {
                      viewModel.setSelectedOnDeviceModelId("")
                    }
                  },
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = mobileDanger,
                      contentColor = Color.White,
                    ),
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(
                    "Delete",
                    style = mobileCallout.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }
              ModelStatus.Loading -> {
                Text(
                  "Loading…",
                  style = mobileCallout,
                  color = mobileTextSecondary,
                  modifier = Modifier.align(Alignment.CenterVertically),
                )
              }
            }
          }
        }
      }

      item {
        val (used, free) = viewModel.liteRTModelManager.storageSummary()
        Text(
          "Storage: ${formatSize(used)} used · ${formatSize(free)} free",
          style = mobileCaption1,
          color = mobileTextTertiary,
          modifier = Modifier.padding(top = 8.dp),
        )
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }
}

private fun formatSize(bytes: Long): String {
  return when {
    bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format("%.0f MB", bytes / 1_000_000.0)
    else -> String.format("%.0f KB", bytes / 1_000.0)
  }
}
