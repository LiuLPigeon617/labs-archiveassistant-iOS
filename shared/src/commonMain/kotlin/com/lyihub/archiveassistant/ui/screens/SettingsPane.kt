package com.lyihub.archiveassistant.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lyihub.archiveassistant.data.AiEndpointLatencyResult
import com.lyihub.archiveassistant.data.AiEnginePresetPreferences.presetFromCurrentSettings
import com.lyihub.archiveassistant.data.AiEnginePresetPreferences.toSettings
import com.lyihub.archiveassistant.data.testAiEndpointLatency
import com.lyihub.archiveassistant.domain.AiEnginePreset
import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.BenchResult
import com.lyihub.archiveassistant.domain.GEMMA_4_E4B_IT
import com.lyihub.archiveassistant.domain.InferenceBackend
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.domain.LocalModelStatus
import com.lyihub.archiveassistant.ui.archivePainter
import com.lyihub.archiveassistant.ui.components.ArchiveDialog
import com.lyihub.archiveassistant.ui.components.ArchiveDialogAction
import com.lyihub.archiveassistant.ui.components.HeaderBackButton
import com.lyihub.archiveassistant.ui.components.PaneContainer
import com.lyihub.archiveassistant.ui.components.PaneContentPadding
import com.lyihub.archiveassistant.ui.components.PaneDivider
import com.lyihub.archiveassistant.ui.components.PaneHeader
import com.lyihub.archiveassistant.ui.theme.ImperialCinnabar
import com.lyihub.archiveassistant.ui.theme.ImperialIvory
import com.lyihub.archiveassistant.ui.theme.ImperialParchment
import com.lyihub.archiveassistant.ui.theme.LocalImperialFonts
import kotlinx.coroutines.delay

private val SettingsPanelShape = RoundedCornerShape(8.dp)
private val SettingsFieldShape = RoundedCornerShape(5.dp)
private val SettingsButtonShape = RoundedCornerShape(5.dp)
private val SettingsInfoShape = RoundedCornerShape(6.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPane(
  aiSettings: AiEngineSettings,
  onAiSettingsChanged: (AiEngineSettings) -> Unit,
  onBack: () -> Unit,
  presets: List<AiEnginePreset> = emptyList(),
  onPresetsChanged: (List<AiEnginePreset>) -> Unit = {},
  modifier: Modifier = Modifier,
  testLatency: suspend (AiEngineSettings, String) -> AiEndpointLatencyResult =
    ::testAiEndpointLatency,
  onDownloadModel: () -> Unit = {},
  onChooseModelFile: () -> Unit = {},
  onCancelDownload: () -> Unit = {},
  onStartModel: () -> Unit = {},
  onStopModel: () -> Unit = {},
  onBackendPreferenceChange: (InferenceBackend) -> Unit = {},
  onRunBenchmark: () -> Unit = {},
  localModelState: LocalModelState = LocalModelState(LocalModelStatus.NOT_DOWNLOADED),
  benchmarkResult: BenchResult? = null,
  isBenchmarkRunning: Boolean = false,
) {
  var engineTypeExpanded by remember { mutableStateOf(false) }
  var presetExpanded by remember { mutableStateOf(false) }
  var savePresetDialogVisible by remember { mutableStateOf(false) }
  var newPresetName by remember { mutableStateOf("") }
  var savePresetError by remember { mutableStateOf<String?>(null) }
  var latencyResultText by remember { mutableStateOf<String?>(null) }
  var isTestingLatency by remember { mutableStateOf(false) }
  var backendExpanded by remember { mutableStateOf(false) }

  // The Android build launched this from `rememberCoroutineScope()`. The test is a single
  // request/response, so a keyed `LaunchedEffect` both keeps common code off `rememberCoroutineScope`
  // and cancels a stale probe when the settings change mid-flight.
  LaunchedEffect(isTestingLatency, aiSettings) {
    if (!isTestingLatency) return@LaunchedEffect
    latencyResultText = null
    val result = testLatency(aiSettings, aiSettings.apiKey)
    latencyResultText =
      when (result) {
        is AiEndpointLatencyResult.Success -> "延迟：${result.elapsedMillis} ms"
        is AiEndpointLatencyResult.Failure -> "测试失败：${result.message}"
      }
    isTestingLatency = false
  }

  val displayFont = LocalImperialFonts.current.display

  val selectedPresetName: String? =
    remember(presets, aiSettings) {
      presets
        .firstOrNull { preset ->
          preset.toSettings() == aiSettings
        }
        ?.name
    }
  val selectedPresetLabel = selectedPresetName ?: "预设"

  PaneContainer(modifier = modifier.testTag("settings-pane")) {
    PaneHeader(
      title = "设置",
      navigationIcon = {
        HeaderBackButton(
          contentDescription = "返回",
          testTag = "settings-back-button",
          onClick = onBack,
        )
      },
    )
    PaneDivider()
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
      PaneContentPadding {
        SettingsPaperSection {
          Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "AI 推理引擎配置",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                modifier = Modifier.weight(1f),
              )
              Spacer(modifier = Modifier.width(12.dp))
              ExposedDropdownMenuBox(
                expanded = presetExpanded,
                onExpandedChange = { presetExpanded = it },
                modifier = Modifier.width(160.dp),
              ) {
                OutlinedTextField(
                  value = selectedPresetLabel,
                  onValueChange = {},
                  readOnly = true,
                  label = { Text("预设") },
                  trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded)
                  },
                  shape = SettingsFieldShape,
                  colors = settingsTextFieldColors(),
                  textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = displayFont),
                  modifier =
                    Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                      .fillMaxWidth()
                      .testTag("preset-selector"),
                )
                ExposedDropdownMenu(
                  expanded = presetExpanded,
                  onDismissRequest = { presetExpanded = false },
                ) {
                  if (presets.isEmpty()) {
                    DropdownMenuItem(
                      text = { Text("暂无预设") },
                      onClick = { presetExpanded = false },
                    )
                  } else {
                    presets.forEach { preset ->
                      DropdownMenuItem(
                        text = { Text(preset.name) },
                        onClick = {
                          onAiSettingsChanged(preset.toSettings())
                          presetExpanded = false
                        },
                      )
                    }
                  }
                  HorizontalDivider()
                  DropdownMenuItem(
                    text = { Text("保存当前为预设") },
                    onClick = {
                      newPresetName = ""
                      savePresetError = null
                      savePresetDialogVisible = true
                      presetExpanded = false
                    },
                  )
                  DropdownMenuItem(
                    text = { Text("删除当前预设") },
                    enabled =
                      selectedPresetName != null && presets.any { it.name == selectedPresetName },
                    onClick = {
                      val name = selectedPresetName ?: return@DropdownMenuItem
                      onPresetsChanged(presets.filterNot { it.name == name })
                      presetExpanded = false
                    },
                  )
                }
              }
            }

            ExposedDropdownMenuBox(
              expanded = engineTypeExpanded,
              onExpandedChange = { engineTypeExpanded = it },
              modifier = Modifier.fillMaxWidth().testTag("engine-type-selector"),
            ) {
              OutlinedTextField(
                value =
                  when (aiSettings.engineType) {
                    AiEngineType.OPENAI_COMPATIBLE -> "OpenAI-Compatible"
                    AiEngineType.OPENAI_RESPONSES -> "OpenAI-Responses"
                    AiEngineType.ANTHROPIC -> "Anthropic"
                    AiEngineType.GEMINI -> "Gemini"
                    AiEngineType.LOCAL_MODEL -> "本地模型"
                  },
                onValueChange = {},
                readOnly = true,
                label = { Text("引擎类型") },
                trailingIcon = {
                  ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineTypeExpanded)
                },
                shape = SettingsFieldShape,
                colors = settingsTextFieldColors(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = displayFont),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
              )
              ExposedDropdownMenu(
                expanded = engineTypeExpanded,
                onDismissRequest = { engineTypeExpanded = false },
              ) {
                DropdownMenuItem(
                  text = { Text("OpenAI-Compatible") },
                  onClick = {
                    onAiSettingsChanged(
                      aiSettings.copy(engineType = AiEngineType.OPENAI_COMPATIBLE)
                    )
                    engineTypeExpanded = false
                  },
                )
                DropdownMenuItem(
                  text = { Text("OpenAI-Responses") },
                  onClick = {
                    onAiSettingsChanged(aiSettings.copy(engineType = AiEngineType.OPENAI_RESPONSES))
                    engineTypeExpanded = false
                  },
                )
                DropdownMenuItem(
                  text = { Text("Anthropic") },
                  onClick = {
                    onAiSettingsChanged(aiSettings.copy(engineType = AiEngineType.ANTHROPIC))
                    engineTypeExpanded = false
                  },
                )
                DropdownMenuItem(
                  text = { Text("Gemini") },
                  onClick = {
                    onAiSettingsChanged(aiSettings.copy(engineType = AiEngineType.GEMINI))
                    engineTypeExpanded = false
                  },
                )
                DropdownMenuItem(
                  text = { Text("本地模型") },
                  onClick = {
                    onAiSettingsChanged(aiSettings.copy(engineType = AiEngineType.LOCAL_MODEL))
                    engineTypeExpanded = false
                  },
                )
              }
            }

            if (aiSettings.engineType != AiEngineType.LOCAL_MODEL) {
              OutlinedTextField(
                value = aiSettings.baseUrl,
                onValueChange = {
                  onAiSettingsChanged(aiSettings.copy(baseUrl = it))
                },
                label = { Text("Base URL") },
                singleLine = true,
                shape = SettingsFieldShape,
                colors = settingsTextFieldColors(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = displayFont),
                modifier = Modifier.fillMaxWidth().testTag("cloud-base-url-input"),
              )
              OutlinedTextField(
                value = aiSettings.apiKey,
                onValueChange = {
                  onAiSettingsChanged(aiSettings.copy(apiKey = it))
                },
                label = { Text("API 密钥") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = SettingsFieldShape,
                colors = settingsTextFieldColors(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = displayFont),
                modifier = Modifier.fillMaxWidth().testTag("api-key-input"),
              )
              OutlinedTextField(
                value = aiSettings.modelName,
                onValueChange = {
                  onAiSettingsChanged(aiSettings.copy(modelName = it))
                },
                label = { Text("模型名称") },
                singleLine = true,
                shape = SettingsFieldShape,
                colors = settingsTextFieldColors(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = displayFont),
                modifier = Modifier.fillMaxWidth().testTag("cloud-model-input"),
              )
            }

            if (aiSettings.engineType == AiEngineType.LOCAL_MODEL) {
              Column(
                modifier = Modifier.fillMaxWidth().testTag("local-model-panel"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                SettingsInfoPanel(modifier = Modifier.fillMaxWidth()) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Text(
                      text = GEMMA_4_E4B_IT.displayName,
                      style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                      text = "大小: ${formatGigabytes(GEMMA_4_E4B_IT.sizeBytes)} GB",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = "状态: ${localModelStatusLabel(localModelState.status)}",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.testTag("model-status-text"),
                    )
                  }
                }

                when (localModelState.status) {
                  LocalModelStatus.NOT_DOWNLOADED,
                  LocalModelStatus.ERROR -> {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      SettingsActionButton(
                        label = "下载模型",
                        onClick = onDownloadModel,
                        modifier = Modifier.weight(1f).testTag("download-model-button"),
                      )
                      SettingsActionButton(
                        label = "选择文件",
                        onClick = onChooseModelFile,
                        modifier = Modifier.weight(1f).testTag("choose-model-file-button"),
                      )
                    }
                  }
                  LocalModelStatus.DOWNLOADING -> {
                    val isImportingModel =
                      localModelState.downloadBytes == 0L && localModelState.downloadProgress == 0f
                    Column(
                      modifier = Modifier.fillMaxWidth(),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      LinearProgressIndicator(
                        progress = { localModelState.downloadProgress },
                        modifier = Modifier.fillMaxWidth().testTag("download-progress-bar"),
                      )
                      Text(
                        text =
                          if (isImportingModel) {
                            "正在导入并校验模型文件，请稍候"
                          } else {
                            "正在下载模型，请稍候"
                          },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().testTag("model-busy-text"),
                      )
                      if (!isImportingModel) {
                        SettingsTextAction(
                          label = "取消",
                          onClick = onCancelDownload,
                          modifier = Modifier.fillMaxWidth().testTag("cancel-download-button"),
                        )
                      }
                    }
                  }
                  LocalModelStatus.DOWNLOADED -> {
                    SettingsActionButton(
                      label = "开启模型",
                      onClick = onStartModel,
                      modifier = Modifier.fillMaxWidth().testTag("start-model-button"),
                    )
                  }
                  LocalModelStatus.READY,
                  LocalModelStatus.INFERENCING -> {
                    SettingsActionButton(
                      label = "停止模型",
                      onClick = onStopModel,
                      modifier = Modifier.fillMaxWidth().testTag("stop-model-button"),
                    )
                  }
                  else -> {}
                }

                ExposedDropdownMenuBox(
                  expanded = backendExpanded,
                  onExpandedChange = { backendExpanded = it },
                  modifier = Modifier.fillMaxWidth().testTag("backend-preference-selector"),
                ) {
                  OutlinedTextField(
                    value = inferenceBackendLabel(activeBackendOf(aiSettings, localModelState), true),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("推理后端") },
                    trailingIcon = {
                      ExposedDropdownMenuDefaults.TrailingIcon(expanded = backendExpanded)
                    },
                    shape = SettingsFieldShape,
                    colors = settingsTextFieldColors(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = displayFont),
                    modifier =
                      Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                  )
                  ExposedDropdownMenu(
                    expanded = backendExpanded,
                    onDismissRequest = { backendExpanded = false },
                  ) {
                    DropdownMenuItem(
                      text = { Text("NPU") },
                      onClick = {
                        onBackendPreferenceChange(InferenceBackend.NPU)
                        backendExpanded = false
                      },
                    )
                    DropdownMenuItem(
                      text = { Text("GPU") },
                      onClick = {
                        onBackendPreferenceChange(InferenceBackend.GPU)
                        backendExpanded = false
                      },
                    )
                    DropdownMenuItem(
                      text = { Text("CPU") },
                      onClick = {
                        onBackendPreferenceChange(InferenceBackend.CPU)
                        backendExpanded = false
                      },
                    )
                  }
                }

                if (
                  localModelState.status == LocalModelStatus.READY ||
                    localModelState.status == LocalModelStatus.INFERENCING
                ) {
                  SettingsActionButton(
                    label = "测试推理速度",
                    onClick = onRunBenchmark,
                    enabled = !isBenchmarkRunning,
                    modifier = Modifier.fillMaxWidth().testTag("benchmark-button"),
                  ) {
                    if (isBenchmarkRunning) {
                      CircularProgressIndicator(
                        modifier =
                          Modifier.padding(end = 8.dp).testTag("benchmark-loading-indicator"),
                        color = Color.White,
                        strokeWidth = 2.dp,
                      )
                    }
                  }

                  benchmarkResult?.let { result ->
                    Text(
                      text = benchmarkSummary(result),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.fillMaxWidth().testTag("benchmark-result-text"),
                    )
                  }
                }

                if (localModelState.status == LocalModelStatus.ERROR) {
                  localModelState.errorMessage?.let { msg ->
                    Text(
                      text = msg,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.error,
                      modifier = Modifier.fillMaxWidth().testTag("model-error-text"),
                    )
                  }
                }
              }
            }

            if (aiSettings.engineType != AiEngineType.LOCAL_MODEL) {
              SettingsActionButton(
                label = "测试 API 延迟",
                onClick = {
                  latencyResultText = null
                  isTestingLatency = true
                },
                enabled = !isTestingLatency,
                modifier = Modifier.fillMaxWidth().testTag("test-api-latency-button"),
              ) {
                if (isTestingLatency) {
                  CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                  )
                }
              }

              latencyResultText?.let { text ->
                Text(
                  text = text,
                  style = MaterialTheme.typography.bodyMedium,
                  color =
                    when {
                      text.startsWith("延迟：") -> MaterialTheme.colorScheme.primary
                      else -> MaterialTheme.colorScheme.error
                    },
                  modifier = Modifier.fillMaxWidth().testTag("api-latency-result"),
                )
              }
            }
          }
        }
      }
    }
  }

  if (savePresetDialogVisible) {
    ArchiveDialog(
      title = "保存预设",
      onDismissRequest = { savePresetDialogVisible = false },
      actions = {
        ArchiveDialogAction(
          label = "取消",
          onClick = { savePresetDialogVisible = false },
        )
        ArchiveDialogAction(
          label = "保存",
          onClick = {
            val name = newPresetName.trim()
            when {
              name.isBlank() -> savePresetError = "请输入预设名称"
              else -> {
                val newPreset =
                  presetFromCurrentSettings(
                    name = name,
                    settings = aiSettings,
                    rawApiKey = aiSettings.apiKey,
                  )
                val updated = presets.filterNot { it.name == name } + newPreset
                onPresetsChanged(updated)
                savePresetDialogVisible = false
              }
            }
          },
          primary = true,
        )
      },
    ) {
      Column {
        OutlinedTextField(
          value = newPresetName,
          onValueChange = {
            newPresetName = it
            savePresetError = null
          },
          label = { Text("预设名称") },
          singleLine = true,
          isError = savePresetError != null,
          shape = SettingsFieldShape,
          colors = settingsTextFieldColors(),
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = displayFont),
          supportingText = savePresetError?.let { { Text(it) } },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun SettingsInfoPanel(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    modifier =
      modifier
        .clip(SettingsInfoShape)
        .background(Color.White.copy(alpha = 0.42f), SettingsInfoShape)
        .border(0.8.dp, Color.Black.copy(alpha = 0.12f), SettingsInfoShape)
  ) {
    content()
  }
}

@Composable
private fun SettingsActionButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
  leadingContent: @Composable () -> Unit = {},
) {
  val displayFont = LocalImperialFonts.current.display
  val backgroundColor = if (enabled) ImperialCinnabar else ImperialParchment
  val contentColor = if (enabled) Color.White else Color.Black.copy(alpha = 0.38f)
  Row(
    modifier =
      modifier
        .shadow(if (enabled) 3.dp else 0.dp, SettingsButtonShape, clip = false)
        .clip(SettingsButtonShape)
        .background(backgroundColor, SettingsButtonShape)
        .border(0.7.dp, Color.Black.copy(alpha = if (enabled) 0.1f else 0.04f), SettingsButtonShape)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(contentPadding),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leadingContent()
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge.copy(fontFamily = displayFont),
      color = contentColor,
    )
  }
}

@Composable
private fun SettingsTextAction(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .clip(SettingsButtonShape)
        .background(Color.White.copy(alpha = 0.28f), SettingsButtonShape)
        .border(0.7.dp, ImperialCinnabar.copy(alpha = 0.28f), SettingsButtonShape)
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style =
        MaterialTheme.typography.labelLarge.copy(
          fontFamily = LocalImperialFonts.current.display
        ),
      color = ImperialCinnabar,
    )
  }
}

@Composable
private fun SettingsPaperSection(content: @Composable ColumnScope.() -> Unit) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .shadow(9.dp, SettingsPanelShape, clip = false)
        .clip(SettingsPanelShape)
        .background(ImperialIvory, SettingsPanelShape)
        .border(0.8.dp, Color.Black.copy(alpha = 0.1f), SettingsPanelShape)
  ) {
    // Android used `R.drawable.home_search_tile`. The shared kernel carries the asset *name* and
    // lets each platform resolve it; a null painter simply leaves the paper tint underneath.
    val tile = archivePainter("home_search_tile")
    if (tile != null) {
      Image(
        painter = tile,
        contentDescription = null,
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.Crop,
        alpha = 0.82f,
      )
    }
    Box(modifier = Modifier.matchParentSize().background(Color.White.copy(alpha = 0.3f)))
    Box(
      modifier =
        Modifier.matchParentSize()
          .background(
            Brush.horizontalGradient(
              0f to Color.Transparent,
              0.78f to Color.Transparent,
              0.92f to Color.Black.copy(alpha = 0.05f),
              1f to Color.White.copy(alpha = 0.24f),
            )
          )
    )
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
      content = content,
    )
  }
}

@Composable
private fun settingsTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    focusedContainerColor = Color.White.copy(alpha = 0.5f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.36f),
    focusedBorderColor = ImperialCinnabar.copy(alpha = 0.62f),
    unfocusedBorderColor = Color.Black.copy(alpha = 0.16f),
    focusedLabelColor = ImperialCinnabar,
    unfocusedLabelColor = Color.Black.copy(alpha = 0.56f),
    cursorColor = ImperialCinnabar,
  )

/**
 * `String.format` and `java.util.Locale` are JVM-only, so the three `"%.Nf".format(...)` call sites
 * in the Android pane are replaced by these deterministically-rounded formatters.
 */
private fun formatOneDecimal(value: Float): String {
  val scaled = kotlin.math.round(value * 10f).toLong()
  val sign = if (scaled < 0L) "-" else ""
  val magnitude = kotlin.math.abs(scaled)
  return "$sign${magnitude / 10}.${magnitude % 10}"
}

private fun formatGigabytes(bytes: Long): String = formatOneDecimal(bytes / (1024f * 1024f * 1024f))

private fun benchmarkSummary(result: BenchResult): String {
  val backend = inferenceBackendLabel(result.backend, false)
  return "预填充: ${formatOneDecimal(result.prefillTokensPerSecond)} tk/s " +
    "(${result.promptTokens} tokens) | " +
    "解码: ${formatOneDecimal(result.decodeTokensPerSecond)} tk/s " +
    "(${result.generateTokens} tokens) | " +
    "TTFT: ${result.timeToFirstTokenMs}ms | 后端: $backend"
}

private const val DefaultInferenceBackendLabel = "NPU"

private fun inferenceBackendLabel(backend: InferenceBackend, fallbackToNpu: Boolean): String =
  when (backend) {
    InferenceBackend.NPU -> "NPU"
    InferenceBackend.GPU -> "GPU"
    InferenceBackend.CPU -> "CPU"
    InferenceBackend.UNKNOWN -> if (fallbackToNpu) DefaultInferenceBackendLabel else "UNKNOWN"
  }

/**
 * The Android pane preferred the backend the runtime actually reported, falling back to the user's
 * stored preference while no model is running.
 */
private fun activeBackendOf(
  settings: AiEngineSettings,
  localModelState: LocalModelState,
): InferenceBackend =
  if (localModelState.activeBackend != InferenceBackend.UNKNOWN) {
    localModelState.activeBackend
  } else {
    settings.localBackendPreference
  }

private fun localModelStatusLabel(status: LocalModelStatus): String =
  when (status) {
    LocalModelStatus.NOT_DOWNLOADED -> "未下载"
    LocalModelStatus.DOWNLOADING -> "下载中"
    LocalModelStatus.DOWNLOADED -> "已下载"
    LocalModelStatus.INITIALIZING -> "初始化中"
    LocalModelStatus.READY -> "就绪"
    LocalModelStatus.INFERENCING -> "推理中"
    LocalModelStatus.ERROR -> "错误"
    LocalModelStatus.STOPPING -> "停止中"
  }
