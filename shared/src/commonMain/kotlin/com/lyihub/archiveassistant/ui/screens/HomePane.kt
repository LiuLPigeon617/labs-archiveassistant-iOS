@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.lyihub.archiveassistant.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.ui.archivePainterOrPlaceholder
import com.lyihub.archiveassistant.ui.theme.ImperialCinnabar
import com.lyihub.archiveassistant.ui.theme.ImperialIvory
import com.lyihub.archiveassistant.ui.theme.ImperialUmber
import com.lyihub.archiveassistant.ui.theme.LocalImperialFonts
import com.lyihub.archiveassistant.util.toChineseCount
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.delay

private val HomeInk = ImperialUmber
private val HomePaper = ImperialIvory
private val ZhongshuWorkLight = Color(0xFF55DDEB)
private val MenxiaWorkLight = ZhongshuWorkLight
private const val HomePulseCycleMillis = 1800
private const val HomePulseCyclesPerTarget = 1
private const val HomePulseShineMillis = 900
private const val HomePulseShakePauseMillis = 720L
private const val HomeWorkTextMinMillis = 420L
private const val HomeWorkTextMaxMillis = 1860L

private enum class HomePulseTarget {
  Zhongshu,
  Menxia,
}

private data class HomeWorkStep(
  val target: HomePulseTarget,
  val text: String,
)

private val ZhongshuWorkTexts =
  listOf(
    "读取剪切板内容...",
    "识别网页地址...",
    "抓取页面标题...",
    "提取正文片段...",
    "生成摘要初稿...",
    "拟定文章标题...",
  )

private val MenxiaWorkTexts =
  listOf(
    "核对内容类型...",
    "匹配所属主题...",
    "分送六部归档...",
    "预备瀑布流条目...",
    "筛选待批奏章...",
    "更新最近主题...",
  )

private val FolderFallbackTitles =
  listOf(
    "大模型架构研究",
    "UX/UI 灵感板",
    "阅读剪报",
    "旅行参考",
    "开源工具",
    "待归档",
  )

private data class DashboardFolder(
  val id: String,
  val title: String,
  val itemCount: Int,
  val updatedAtEpochMillis: Long?,
  val topic: Topic?,
)

/**
 * The archive home screen — the app's main surface, ported from `:app`'s `ui/screens/HomePane.kt`.
 *
 * The port is behaviour-preserving; only the platform seams changed:
 *
 *  - `@DrawableRes ornamentRes: Int` / `R.drawable.*` became asset *names* resolved through
 *    `archivePainterOrPlaceholder` (`composeResources/drawable`), because `R` does not exist outside
 *    `:app`. Unknown names still degrade to a transparent painter rather than crashing.
 *  - `Icons.Default.Settings` / `Icons.Default.Close` became locally drawn glyphs ([SettingsGlyph],
 *    [ClearGlyph]). This module deliberately does not depend on `material-icons`, the same reason
 *    [com.lyihub.archiveassistant.ui.components.HeaderBackButton] draws its own arrow.
 *  - `ImperialTitleFont` / `ImperialDisplayFont` (which were built from `R.font.*`) became
 *    `LocalImperialFonts.current.title` / `.display`, installed by `ProvideImperialFonts`.
 *  - `System.currentTimeMillis()` became `Clock.System.now().toEpochMilliseconds()`.
 *  - `TOTAL_PENDING_MEMORIALS` now comes from this module's own [TOTAL_PENDING_MEMORIALS].
 *  - `toChineseCount` resolves to this module's `com.lyihub.archiveassistant.util.toChineseCount`.
 */
@Composable
fun HomePane(
  title: String,
  parserValidationMessage: String?,
  recentTopics: List<Topic>,
  itemsByTopic: Map<String, List<KnowledgeItem>>,
  searchQuery: String,
  onTopicSelected: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onCreateTopic: () -> Unit,
  onRenameTopic: (String) -> Unit = {},
  onDeleteTopic: (String) -> Unit = {},
  onSearchQueryChanged: (String) -> Unit,
  onOpenClipboard: () -> Unit,
  onOpenMemorialDemo: (() -> Unit)? = null,
  smartSummarizationMessage: String? = null,
  modifier: Modifier = Modifier,
) {
  val pendingCount = TOTAL_PENDING_MEMORIALS
  val folders = dashboardFolders(recentTopics, itemsByTopic, searchQuery)
  var isManagingMinistries by remember { mutableStateOf(false) }

  Box(modifier = modifier.testTag("home-pane").fillMaxSize()) {
    Box(
      modifier =
        Modifier.fillMaxSize().verticalScroll(rememberScrollState(), overscrollEffect = null)
    ) {
      HomeContentColumn(
        modifier =
          Modifier.padding(
              start = 24.dp,
              top = 56.dp,
              end = 24.dp,
              bottom = 36.dp,
            )
            .fillMaxWidth()
      ) {
        HomeMosaic(
          appTitle = title,
          pendingCount = pendingCount,
          folders = folders,
          validationMessage = parserValidationMessage,
          smartSummarizationMessage = smartSummarizationMessage,
          searchQuery = searchQuery,
          onOpenClipboard = onOpenClipboard,
          onOpenMemorialDemo = onOpenMemorialDemo,
          onSearchQueryChanged = onSearchQueryChanged,
          onTopicSelected = onTopicSelected,
          onOpenSettings = onOpenSettings,
          onCreateTopic = onCreateTopic,
          onRenameTopic = onRenameTopic,
          onDeleteTopic = onDeleteTopic,
          isManagingMinistries = isManagingMinistries,
          onToggleManage = { isManagingMinistries = !isManagingMinistries },
        )
      }
    }
  }
}

@Composable
private fun HomeContentColumn(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(14.dp),
    content = content,
  )
}

@Composable
private fun ColumnScope.HomeMosaic(
  appTitle: String,
  pendingCount: Int,
  folders: List<DashboardFolder>,
  validationMessage: String?,
  smartSummarizationMessage: String?,
  searchQuery: String,
  onOpenClipboard: () -> Unit,
  onOpenMemorialDemo: (() -> Unit)?,
  onSearchQueryChanged: (String) -> Unit,
  onTopicSelected: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onCreateTopic: () -> Unit,
  onRenameTopic: (String) -> Unit,
  onDeleteTopic: (String) -> Unit,
  isManagingMinistries: Boolean,
  onToggleManage: () -> Unit,
) {
  var pulseTarget by remember { mutableStateOf<HomePulseTarget?>(null) }
  var pulseText by remember { mutableStateOf("") }
  var pulseRunId by remember { mutableStateOf(0) }
  LaunchedEffect(pulseRunId) {
    if (pulseRunId == 0) return@LaunchedEffect
    val steps =
      ZhongshuWorkTexts.map { HomeWorkStep(HomePulseTarget.Zhongshu, it) } +
        MenxiaWorkTexts.map { HomeWorkStep(HomePulseTarget.Menxia, it) }
    steps.forEach { step ->
      pulseTarget = step.target
      pulseText = step.text
      delay(Random.nextLong(HomeWorkTextMinMillis, HomeWorkTextMaxMillis + 1))
    }
    pulseTarget = null
    pulseText = ""
  }
  HomeHeaderRow(
    appTitle = appTitle,
    onTitlePulseRequested = { pulseRunId += 1 },
    onOpenSettings = onOpenSettings,
    modifier = Modifier.fillMaxWidth(),
  )
  PalaceDashboardBlock(
    pendingCount = pendingCount,
    onOpenClipboard = onOpenClipboard,
    onOpenMemorialDemo = onOpenMemorialDemo,
    searchQuery = searchQuery,
    onSearchQueryChanged = onSearchQueryChanged,
    validationMessage = validationMessage,
    smartSummarizationMessage = smartSummarizationMessage,
    pulseTarget = pulseTarget,
    pulseText = pulseText,
    modifier = Modifier.fillMaxWidth(),
  )
  MinistryStampStack(
    searchQuery = searchQuery,
    folders = folders,
    onTopicSelected = onTopicSelected,
    onCreateTopic = onCreateTopic,
    onRenameTopic = onRenameTopic,
    onDeleteTopic = onDeleteTopic,
    isManagingMinistries = isManagingMinistries,
    onToggleManage = onToggleManage,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun HomeHeaderRow(
  appTitle: String,
  onTitlePulseRequested: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  TitleCell(
    appTitle = appTitle,
    onTitlePulseRequested = onTitlePulseRequested,
    onOpenSettings = onOpenSettings,
    modifier = modifier,
  )
}

@Composable
private fun TitleCell(
  appTitle: String,
  onTitlePulseRequested: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxWidth().padding(bottom = 4.dp)) {
    IconButton(
      onClick = onOpenSettings,
      modifier = Modifier.align(Alignment.TopEnd).testTag("settings-trigger"),
    ) {
      SettingsGlyph(tint = Color.Black.copy(alpha = 0.82f), modifier = Modifier.size(24.dp))
    }
    Column(
      modifier = Modifier.align(Alignment.CenterStart).padding(end = 64.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = appTitle,
        style = MaterialTheme.typography.displayLarge,
        color = Color.Black,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier.clickable(onClick = onTitlePulseRequested),
      )
      Text(
        text = "拾取资料、整理主题、批阅奏折",
        style =
          MaterialTheme.typography.titleSmall.copy(
            fontFamily = LocalImperialFonts.current.display
          ),
        color = Color.Black.copy(alpha = 0.72f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * The settings affordance, drawn locally.
 *
 * `:app` used `Icons.Default.Settings`; this module does not depend on `material-icons`, so the gear
 * is stroked here — an outer ring plus eight radial teeth. Chosen over injecting an `ImageVector`
 * slot because the only callers are shared panes, and an injected icon would push a Material Icons
 * dependency onto whoever hosts them.
 */
@Composable
private fun SettingsGlyph(tint: Color, modifier: Modifier = Modifier) {
  Canvas(
    modifier = modifier.semantics { contentDescription = "设置" },
  ) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val ringRadius = size.minDimension * 0.30f
    val toothInner = size.minDimension * 0.35f
    val toothOuter = size.minDimension * 0.47f
    val strokeWidth = size.minDimension * 0.09f
    repeat(8) { index ->
      val angle = index * (kotlin.math.PI / 4.0)
      val dx = kotlin.math.cos(angle).toFloat()
      val dy = kotlin.math.sin(angle).toFloat()
      drawLine(
        color = tint,
        start = Offset(center.x + dx * toothInner, center.y + dy * toothInner),
        end = Offset(center.x + dx * toothOuter, center.y + dy * toothOuter),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
      )
    }
    drawCircle(
      color = tint,
      radius = ringRadius,
      center = center,
      style = Stroke(width = strokeWidth),
    )
  }
}

/** The "clear search" affordance, drawn locally for the same reason as [SettingsGlyph]. */
@Composable
private fun ClearGlyph(tint: Color, modifier: Modifier = Modifier) {
  Canvas(
    modifier = modifier.semantics { contentDescription = "清空搜索" },
  ) {
    val strokeWidth = size.minDimension * 0.13f
    val inset = size.minDimension * 0.12f
    val far = size.minDimension - inset
    drawLine(
      color = tint,
      start = Offset(inset, inset),
      end = Offset(far, far),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = tint,
      start = Offset(far, inset),
      end = Offset(inset, far),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )
  }
}

@Composable
private fun HomeFeatureCell(
  title: String,
  subtitle: String,
  label: String = "",
  contentColor: Color,
  ornamentAsset: String,
  tileVisual: ArchiveTileVisual,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  large: Boolean = false,
  ornamentSize: Dp = if (large) 132.dp else 68.dp,
  ornamentOffsetX: Dp = if (large) 22.dp else 10.dp,
  ornamentOffsetY: Dp = 0.dp,
  ornamentAlignment: Alignment = Alignment.CenterEnd,
  ornamentTint: Color? = null,
  mirrorOrnament: Boolean = false,
  textAlignment: Alignment = Alignment.BottomStart,
  pulseActive: Boolean = false,
  workText: String = "",
  workLightColor: Color? = null,
) {
  val workProgress by
    animateFloatAsState(
      targetValue = if (pulseActive) 1f else 0f,
      animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
      label = "homeFeatureWorkProgress",
    )
  val cardMotion = remember { Animatable(0f) }
  LaunchedEffect(pulseActive) {
    if (pulseActive) {
      while (pulseActive) {
        cardMotion.snapTo(0f)
        cardMotion.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = HomePulseShineMillis, easing = LinearEasing),
        )
        delay(HomePulseShakePauseMillis)
      }
    } else {
      cardMotion.snapTo(0f)
    }
  }
  val motionPhase = cardMotion.value
  val shakeDegrees =
    when {
      motionPhase < 0.25f -> 3f * (motionPhase / 0.25f)
      motionPhase < 0.5f -> 3f + (-6f) * ((motionPhase - 0.25f) / 0.25f)
      motionPhase < 0.75f -> -3f + 4f * ((motionPhase - 0.5f) / 0.25f)
      motionPhase < 1f -> 1f - 1f * ((motionPhase - 0.75f) / 0.25f)
      else -> 0f
    }
  val shakeOffsetX =
    when {
      motionPhase < 0.25f -> 4f * (motionPhase / 0.25f)
      motionPhase < 0.5f -> 4f + (-7f) * ((motionPhase - 0.25f) / 0.25f)
      motionPhase < 0.75f -> -3f + 5f * ((motionPhase - 0.5f) / 0.25f)
      motionPhase < 1f -> 2f - 2f * ((motionPhase - 0.75f) / 0.25f)
      else -> 0f
    }
  Box(
    modifier =
      modifier.fillMaxSize().clickable(enabled = enabled, onClick = onClick).testTag(testTag)
  ) {
    TilePulseWave(
      active = pulseActive,
      color = tileVisual.borderColor,
      modifier = Modifier.matchParentSize(),
    )
    CutoutCell(
      modifier =
        Modifier.matchParentSize().graphicsLayer {
          rotationZ = shakeDegrees
          translationX = shakeOffsetX
        },
      contentColor = contentColor,
      tileVisual = tileVisual,
    ) {
      BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        val activeOrnamentSize = ornamentSize * 0.5f
        val activeOrnamentOffsetY = -(maxHeight / 2f) + activeOrnamentSize / 2f + 2.dp
        val activeOrnamentOffsetX =
          if (ornamentAlignment == Alignment.CenterStart) 8.dp else (-8).dp
        val ornamentX = lerpDp(ornamentOffsetX, activeOrnamentOffsetX, workProgress)
        val ornamentY = lerpDp(ornamentOffsetY, activeOrnamentOffsetY, workProgress)
        val animatedOrnamentSize = lerpDp(ornamentSize, activeOrnamentSize, workProgress)
        val textOffsetY = -(maxHeight * 0.28f) * workProgress
        val statusOffsetY = 8.dp * (1f - workProgress) + 8.dp
        HomeOrnament(
          ornamentAsset = ornamentAsset,
          modifier =
            Modifier.align(ornamentAlignment)
              .offset(x = ornamentX, y = ornamentY)
              .size(animatedOrnamentSize)
              .graphicsLayer {
                scaleX = if (mirrorOrnament) -1f else 1f
                transformOrigin =
                  if (ornamentAlignment == Alignment.CenterStart) {
                    TransformOrigin(0f, 0f)
                  } else {
                    TransformOrigin(1f, 0f)
                  }
              },
          alpha = if (large) 0.5f else 0.58f,
          tint = ornamentTint,
        )
        if (label.isNotBlank()) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.7f),
            modifier =
              Modifier.align(Alignment.TopStart).padding(horizontal = 12.dp, vertical = 10.dp),
          )
        }
        Column(
          modifier =
            Modifier.align(textAlignment)
              .offset(y = textOffsetY)
              .padding(if (large) 18.dp else 12.dp)
        ) {
          val titleFamily = LocalImperialFonts.current.title
          val displayFamily = LocalImperialFonts.current.display
          Text(
            text = title,
            style =
              if (large) {
                MaterialTheme.typography.headlineMedium.copy(fontFamily = titleFamily)
              } else {
                MaterialTheme.typography.titleLarge.copy(fontFamily = titleFamily)
              },
            color = contentColor,
            fontWeight = FontWeight.Normal,
            maxLines = if (large) 2 else 1,
          )
          Text(
            text = subtitle,
            style =
              if (large) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = displayFamily)
              } else {
                MaterialTheme.typography.bodySmall.copy(fontFamily = displayFamily)
              },
            color = contentColor.copy(alpha = 0.76f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (workLightColor != null) {
          WorkStatusLine(
            active = pulseActive,
            progress = workProgress,
            text = workText.ifBlank { "loading..." },
            color = workLightColor,
            contentColor = contentColor,
            modifier =
              Modifier.align(Alignment.BottomCenter).offset(x = (-6).dp, y = statusOffsetY),
          )
        }
      }
    }
  }
}

@Composable
private fun WorkStatusLine(
  active: Boolean,
  progress: Float,
  text: String,
  color: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .graphicsLayer { alpha = progress }
        .padding(start = 4.dp, top = 3.dp, end = 4.dp, bottom = 6.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    WorkBreathingLight(
      active = active,
      color = color,
      modifier = Modifier.size(26.dp),
      haloSize = 26.dp,
      dotSize = 6.dp,
    )
    AnimatedContent(
      targetState = text,
      transitionSpec = {
        (slideInVertically(animationSpec = tween(180)) { height -> height } +
            fadeIn(animationSpec = tween(180)))
          .togetherWith(
            slideOutVertically(animationSpec = tween(150)) { height -> -height } +
              fadeOut(animationSpec = tween(150))
          )
          .using(SizeTransform(clip = true))
      },
      label = "homeWorkStatusText",
    ) { activeText ->
      Text(
        text = activeText,
        style =
          MaterialTheme.typography.bodySmall.copy(
            fontFamily = LocalImperialFonts.current.display
          ),
        color = contentColor.copy(alpha = 0.92f),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 128.dp),
      )
    }
  }
}

@Composable
private fun WorkBreathingLight(
  active: Boolean,
  color: Color,
  modifier: Modifier = Modifier,
  haloSize: Dp = 34.dp,
  dotSize: Dp = 8.dp,
) {
  val intensity by
    animateFloatAsState(
      targetValue = if (active) 1f else 0f,
      animationSpec = tween(durationMillis = 360, easing = LinearEasing),
      label = "homeWorkLightIntensity",
    )
  val progress = remember { Animatable(0f) }
  LaunchedEffect(active) {
    if (!active) return@LaunchedEffect
    while (active) {
      progress.snapTo(0f)
      progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = HomePulseCycleMillis, easing = LinearEasing),
      )
    }
  }
  val wave = kotlin.math.sin(progress.value * Math.PI * 2.0).toFloat()
  val glow = ((wave + 1f) / 2f).coerceIn(0f, 1f)
  val activeGlow = intensity * glow
  Box(
    modifier = modifier.size(haloSize),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.matchParentSize()
          .graphicsLayer {
            scaleX = 0.66f + activeGlow * 0.58f
            scaleY = 0.66f + activeGlow * 0.58f
            alpha = 0.16f + intensity * 0.24f + activeGlow * 0.68f
          }
          .background(
            Brush.radialGradient(
              colors =
                listOf(
                  color.copy(alpha = 0.95f),
                  color.copy(alpha = 0.36f),
                  Color.Transparent,
                )
            ),
            RoundedCornerShape(999.dp),
          )
    )
    Box(
      modifier =
        Modifier.size(dotSize)
          .shadow(9.dp + 9.dp * intensity, RoundedCornerShape(999.dp), clip = false)
          .background(color.copy(alpha = 0.36f + intensity * 0.64f), RoundedCornerShape(999.dp))
          .border(
            0.8.dp,
            Color.White.copy(alpha = 0.38f + intensity * 0.5f),
            RoundedCornerShape(999.dp),
          )
    )
  }
}

@Composable
private fun TilePulseWave(
  active: Boolean,
  color: Color,
  modifier: Modifier = Modifier,
) {
  val progress = remember { Animatable(0f) }
  LaunchedEffect(active) {
    if (active) {
      progress.snapTo(0f)
      while (active) {
        progress.animateTo(
          targetValue = progress.value + HomePulseCyclesPerTarget.toFloat(),
          animationSpec =
            tween(
              durationMillis = HomePulseCycleMillis * HomePulseCyclesPerTarget,
              easing = LinearEasing,
            ),
        )
      }
    } else {
      progress.snapTo(0f)
    }
  }
  if (!active && progress.value <= 0f) return
  Box(
    modifier =
      modifier.drawBehind {
        val baseStroke = (size.minDimension * 0.052f).coerceIn(4.4f, 8.0f)
        val baseNotch = ArchiveCutCornerNotchDp.dp.toPx().coerceAtMost(size.minDimension * 0.28f)
        val cycleProgress = progress.value % 1f
        listOf(0f, 0.48f).forEachIndexed { index, delay ->
          val phase = (cycleProgress - delay + 1f) % 1f
          val inset = size.minDimension * 0.28f * phase
          val alpha = (1f - phase).coerceIn(0f, 1f) * if (index == 0) 0.62f else 0.5f
          drawPath(
            path = cutoutPulsePath(size, inset, baseNotch),
            color = color.copy(alpha = alpha),
            style = Stroke(width = baseStroke * (1f - phase * 0.22f)),
          )
        }
      }
  )
}

private fun cutoutPulsePath(
  size: Size,
  outset: Float,
  baseNotch: Float,
): Path {
  val left = -outset
  val top = -outset
  val right = size.width + outset
  val bottom = size.height + outset
  val notch = (baseNotch + outset * 0.32f).coerceAtMost(size.minDimension * 0.3f)
  return Path().apply {
    moveTo(left + notch, top)
    lineTo(right - notch, top)
    quadraticTo(right - notch, top + notch, right, top + notch)
    lineTo(right, bottom - notch)
    quadraticTo(
      right - notch,
      bottom - notch,
      right - notch,
      bottom,
    )
    lineTo(left + notch, bottom)
    quadraticTo(left + notch, bottom - notch, left, bottom - notch)
    lineTo(left, top + notch)
    quadraticTo(left + notch, top + notch, left + notch, top)
    close()
  }
}

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp {
  return start + (stop - start) * fraction.coerceIn(0f, 1f)
}

@Composable
private fun PalaceDashboardBlock(
  pendingCount: Int,
  onOpenClipboard: () -> Unit,
  onOpenMemorialDemo: (() -> Unit)?,
  searchQuery: String,
  onSearchQueryChanged: (String) -> Unit,
  validationMessage: String?,
  smartSummarizationMessage: String?,
  pulseTarget: HomePulseTarget?,
  pulseText: String,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier) {
    val gap = 12.dp
    val bottomSquareWidth = (maxWidth - gap) / 3f
    val searchRowHeight = bottomSquareWidth
    val topColumnWidth = (maxWidth - gap) / 2f
    val topRowHeight = topColumnWidth
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(gap),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().height(topRowHeight),
        horizontalArrangement = Arrangement.spacedBy(gap),
      ) {
        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(gap),
        ) {
          HomeFeatureCell(
            title = "中书录入",
            subtitle = "拾取、摘要、拟题",
            contentColor = Color.White,
            ornamentAsset = "home_ornament_zhongshu",
            tileVisual = ZhongshuTileVisual,
            modifier = Modifier.weight(1f),
            onClick = {},
            testTag = "workflow-zhongshu-cell",
            enabled = false,
            ornamentSize = 96.dp,
            ornamentOffsetX = 18.dp,
            ornamentTint = Color.White,
            pulseActive = pulseTarget == HomePulseTarget.Zhongshu,
            workText = pulseText,
            workLightColor = ZhongshuWorkLight,
          )
          HomeFeatureCell(
            title = "门下递奏",
            subtitle = "筛选、预览、待批",
            contentColor = Color.White,
            ornamentAsset = "home_ornament_menxia",
            tileVisual = MenxiaTileVisual,
            modifier = Modifier.weight(1f),
            onClick = {},
            testTag = "workflow-menxia-cell",
            enabled = false,
            ornamentSize = 88.dp,
            ornamentOffsetX = (-10).dp,
            ornamentAlignment = Alignment.CenterStart,
            textAlignment = Alignment.BottomEnd,
            pulseActive = pulseTarget == HomePulseTarget.Menxia,
            workText = pulseText,
            workLightColor = MenxiaWorkLight,
          )
        }
        MemorialCell(
          pendingCount = pendingCount,
          onClick = onOpenMemorialDemo,
          modifier = Modifier.weight(1f).height(topRowHeight),
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().height(searchRowHeight),
        horizontalArrangement = Arrangement.spacedBy(gap),
      ) {
        HomeFeatureCell(
          title = "宣拾遗",
          subtitle = "读取剪切板",
          contentColor = Color.White,
          ornamentAsset = "home_ornament_sanxingdui",
          tileVisual = ClipboardTileVisual,
          modifier = Modifier.weight(1f).height(searchRowHeight),
          onClick = onOpenClipboard,
          testTag = "clipboard-button",
          ornamentSize = 60.dp,
          ornamentOffsetX = 2.dp,
          ornamentOffsetY = 2.dp,
          ornamentAlignment = Alignment.TopEnd,
          ornamentTint = Color.White,
        )
        SearchCell(
          searchQuery = searchQuery,
          onSearchQueryChanged = onSearchQueryChanged,
          validationMessage = validationMessage,
          smartSummarizationMessage = smartSummarizationMessage,
          modifier = Modifier.weight(2f).height(searchRowHeight),
        )
      }
    }
  }
}

@Composable
private fun SearchCell(
  searchQuery: String,
  onSearchQueryChanged: (String) -> Unit,
  validationMessage: String?,
  smartSummarizationMessage: String?,
  modifier: Modifier = Modifier,
) {
  CutoutCell(
    modifier = modifier.fillMaxSize(),
    contentColor = Color.White,
    tileVisual = SearchTileVisual,
  ) {
    HomeOrnament(
      ornamentAsset = "home_ornament_library_11092",
      modifier = Modifier.align(Alignment.CenterEnd).offset(x = 18.dp).size(112.dp),
      alpha = 0.66f,
    )
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.SpaceEvenly,
    ) {
      Text(
        text = "藏经阁",
        style =
          MaterialTheme.typography.titleLarge.copy(
            fontFamily = LocalImperialFonts.current.title
          ),
        color = Color.White,
        fontWeight = FontWeight.Normal,
      )
      val searchTextStyle =
        MaterialTheme.typography.bodyMedium.copy(
          color = Color.White,
          fontFamily = LocalImperialFonts.current.display,
        )
      BasicTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        singleLine = true,
        textStyle = searchTextStyle,
        modifier = Modifier.fillMaxWidth().testTag("home-search-input"),
        decorationBox = { innerTextField ->
          Box(
            modifier =
              Modifier.fillMaxWidth().height(34.dp).background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.CenterStart,
          ) {
            Box(
              modifier =
                Modifier.matchParentSize()
                  .padding(start = 10.dp, end = if (searchQuery.isBlank()) 10.dp else 34.dp),
              contentAlignment = Alignment.CenterStart,
            ) {
              if (searchQuery.isBlank()) {
                Text(
                  text = "查找主题或资料...",
                  style = searchTextStyle,
                  color = Color.White.copy(alpha = 0.62f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              innerTextField()
            }
            if (searchQuery.isNotBlank()) {
              Box(
                modifier =
                  Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(34.dp)
                    .clickable { onSearchQueryChanged("") }
                    .testTag("home-search-clear-button"),
                contentAlignment = Alignment.Center,
              ) {
                ClearGlyph(
                  tint = Color.White.copy(alpha = 0.86f),
                  modifier = Modifier.size(15.dp),
                )
              }
            }
          }
        },
      )
      val message = validationMessage ?: smartSummarizationMessage
      Text(
        text =
          message
            ?: if (searchQuery.isBlank()) {
              "输入后筛选下方文件夹与资料"
            } else {
              "正在筛选相关文件夹"
            },
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = if (message == null) 0.72f else 0.9f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun MemorialCell(
  pendingCount: Int,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  CutoutCell(
    modifier =
      modifier
        .fillMaxSize()
        .clickable(enabled = onClick != null) { onClick?.invoke() }
        .testTag("memorial-entry-card"),
    contentColor = Color.White,
    tileVisual = MemorialTileVisual,
    mirrorBackground = true,
  ) {
    HomeOrnament(
      ornamentAsset = "home_ornament_memorial",
      modifier = Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-6).dp).size(116.dp),
      alpha = 0.66f,
    )
    Column(modifier = Modifier.align(Alignment.BottomStart).padding(13.dp)) {
      Text(
        text = "批奏折",
        style =
          MaterialTheme.typography.headlineLarge.copy(
            fontFamily = LocalImperialFonts.current.title
          ),
        color = Color.White,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
      )
      Text(
        text = "今日${pendingCount.toChineseCount()}封待批奏章",
        style =
          MaterialTheme.typography.titleSmall.copy(
            fontFamily = LocalImperialFonts.current.display
          ),
        color = Color.White.copy(alpha = 0.76f),
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun MinistryStampStack(
  searchQuery: String,
  folders: List<DashboardFolder>,
  onTopicSelected: (String) -> Unit,
  onCreateTopic: () -> Unit,
  onRenameTopic: (String) -> Unit,
  onDeleteTopic: (String) -> Unit,
  isManagingMinistries: Boolean,
  onToggleManage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .shadow(14.dp, RoundedCornerShape(8.dp), clip = false)
        .testTag("ministry-stamp-stack"),
    verticalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    MinistryFoldSurface(
      modifier = Modifier.fillMaxWidth(),
      shape = ArchiveCutCornerShape,
      foldIntensity = 0.22f,
      showEndFold = true,
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val headerFamily = LocalImperialFonts.current.title
        Text(
          text =
            buildAnnotatedString {
              withStyle(
                SpanStyle(
                  color = ImperialCinnabar,
                  fontFamily = headerFamily,
                  fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                )
              ) {
                append("尚书省")
              }
              append("\u2002")
              withStyle(
                SpanStyle(
                  color = Color.Black,
                  fontFamily = headerFamily,
                  fontSize = MaterialTheme.typography.titleSmall.fontSize,
                )
              ) {
                append("最近主题")
              }
            },
          style = MaterialTheme.typography.titleMedium.copy(fontFamily = headerFamily),
          fontWeight = FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
          modifier = Modifier.weight(1f),
        )
        MinistryHeaderAction(
          title = "新建",
          onClick = onCreateTopic,
          testTag = "home-create-topic-button",
        )
        MinistryHeaderAction(
          title = if (isManagingMinistries) "完成" else "管理",
          onClick = onToggleManage,
          testTag = "manage-button",
        )
      }
    }
    FolderResultList(
      folders = folders,
      searchQuery = searchQuery,
      onTopicSelected = onTopicSelected,
      onRenameTopic = onRenameTopic,
      onDeleteTopic = onDeleteTopic,
      isManagingMinistries = isManagingMinistries,
      compact = true,
    )
  }
}

@Composable
private fun MinistryHeaderAction(
  title: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Box(
    modifier =
      Modifier.padding(start = 6.dp)
        .background(Color.White.copy(alpha = 0.52f), RoundedCornerShape(4.dp))
        .border(0.7.dp, Color.Black.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      color = Color.Black,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
      maxLines = 1,
    )
  }
}

@Composable
private fun FolderResultList(
  folders: List<DashboardFolder>,
  searchQuery: String,
  onTopicSelected: (String) -> Unit,
  onRenameTopic: (String) -> Unit,
  onDeleteTopic: (String) -> Unit,
  isManagingMinistries: Boolean,
  compact: Boolean,
) {
  if (folders.isEmpty()) {
    PlainCell(
      modifier = Modifier.fillMaxWidth().height(if (compact) 74.dp else 96.dp),
      color = HomePaper,
      contentColor = HomeInk,
    ) {
      Column(
        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "未找到相关文件夹",
          style = MaterialTheme.typography.titleLarge,
          color = HomeInk,
          fontWeight = FontWeight.Normal,
        )
        Text(
          text = "藏经阁暂未检出「$searchQuery」相关内容",
          style = MaterialTheme.typography.bodyMedium,
          color = HomeInk.copy(alpha = 0.68f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    return
  }
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    folders.forEachIndexed { index, folder ->
      MinistryFoldCard(
        folder = folder,
        visual = folderVisual(index),
        searchQuery = searchQuery,
        onTopicSelected = onTopicSelected,
        onRenameTopic = onRenameTopic,
        onDeleteTopic = onDeleteTopic,
        isManagingMinistries = isManagingMinistries,
        modifier = Modifier.fillMaxWidth().height(if (compact) 72.dp else 94.dp),
        compact = compact,
      )
    }
  }
}

@Composable
private fun MinistryFoldCard(
  folder: DashboardFolder,
  visual: FolderVisual,
  searchQuery: String,
  onTopicSelected: (String) -> Unit,
  onRenameTopic: (String) -> Unit,
  onDeleteTopic: (String) -> Unit,
  isManagingMinistries: Boolean,
  modifier: Modifier = Modifier,
  compact: Boolean,
) {
  val enabled = folder.topic != null
  val imageSize = if (compact) 60.dp else 86.dp
  val displayFamily = LocalImperialFonts.current.display
  val metaStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = displayFamily)
  val titleStyle =
    if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge
  val summaryStyle =
    if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
  MinistryFoldSurface(
    modifier =
      modifier
        .clickable(enabled = enabled && !isManagingMinistries) {
          folder.topic?.let { onTopicSelected(it.id) }
        }
        .testTag("topic-card-${folder.id}"),
    shape = ArchiveFlatCutShape,
    foldIntensity = if (compact) 0.16f else 0.2f,
    showEndFold = true,
  ) {
    Row(
      modifier =
        Modifier.fillMaxSize()
          .padding(
            start = 16.dp,
            top = 7.dp,
            end = 10.dp,
            bottom = 7.dp,
          ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
      ) {
        Text(
          text =
            buildHighlightedText(
              text = folder.title,
              query = searchQuery,
              highlightColor = Color.Black,
              highlightBgColor = Color(0xFFF2D88A).copy(alpha = 0.86f),
            ),
          style = titleStyle,
          color = Color.Black.copy(alpha = 0.88f),
          fontWeight = FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            buildHighlightedText(
              text = visual.description,
              query = searchQuery,
              highlightColor = Color.Black,
              highlightBgColor = Color(0xFFF2D88A).copy(alpha = 0.72f),
            ),
          style = summaryStyle.copy(fontFamily = displayFamily),
          color = Color.Black.copy(alpha = 0.52f),
          fontWeight = FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (!isManagingMinistries) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = "${folder.itemCount.toChineseCount()}篇",
              style = metaStyle,
              color = Color.Black.copy(alpha = 0.56f),
              maxLines = 1,
              softWrap = false,
            )
            Text(
              text = folder.updatedAtEpochMillis?.let(::friendlyTime) ?: "待启用",
              style = metaStyle,
              color = Color.Black.copy(alpha = 0.46f),
              maxLines = 1,
              softWrap = false,
            )
          }
          Image(
            painter = archivePainterOrPlaceholder(visual.imageAsset),
            contentDescription = null,
            modifier = Modifier.size(imageSize),
            contentScale = ContentScale.Fit,
            alpha = 0.84f,
          )
        }
      }
      if (isManagingMinistries && folder.topic != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          MinistryInlineAction(
            title = "改名",
            onClick = { onRenameTopic(folder.topic.id) },
            testTag = "rename-topic-button-${folder.id}",
          )
          MinistryInlineAction(
            title = "删除",
            onClick = { onDeleteTopic(folder.topic.id) },
            testTag = "delete-topic-button-${folder.id}",
          )
        }
      }
    }
  }
}

@Composable
private fun MinistryInlineAction(
  title: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Box(
    modifier =
      Modifier.background(Color.White.copy(alpha = 0.58f), RoundedCornerShape(4.dp))
        .border(0.7.dp, Color.Black.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelSmall,
      color = Color.Black,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
      maxLines = 1,
    )
  }
}

@Composable
private fun MinistryFoldSurface(
  modifier: Modifier = Modifier,
  shape: Shape,
  foldIntensity: Float,
  showEndFold: Boolean = false,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier = modifier.clip(shape).background(Color.White, shape)) {
    Image(
      painter = archivePainterOrPlaceholder("home_search_tile"),
      contentDescription = null,
      modifier = Modifier.matchParentSize(),
      contentScale = ContentScale.Crop,
    )
    Box(modifier = Modifier.matchParentSize().background(Color.White.copy(alpha = 0.32f)))
    Box(
      modifier =
        Modifier.matchParentSize()
          .background(
            Brush.horizontalGradient(
              0f to Color.Transparent,
              0.7f to Color.Transparent,
              0.88f to Color.Black.copy(alpha = 0.045f * foldIntensity * 6f),
              0.95f to Color.White.copy(alpha = 0.34f * foldIntensity * 3f),
              1f to Color.Black.copy(alpha = 0.08f * foldIntensity * 5f),
            )
          )
    )
    Box(
      modifier =
        Modifier.align(Alignment.TopStart)
          .fillMaxWidth()
          .height(7.dp)
          .background(
            Brush.verticalGradient(
              0f to Color.Black.copy(alpha = 0.07f * foldIntensity * 4.5f),
              0.55f to Color.White.copy(alpha = 0.18f * foldIntensity * 3f),
              1f to Color.Transparent,
            )
          )
    )
    Box(
      modifier =
        Modifier.align(Alignment.BottomStart)
          .fillMaxWidth()
          .height(10.dp)
          .background(
            Brush.verticalGradient(
              0f to Color.Transparent,
              0.55f to Color.Black.copy(alpha = 0.05f * foldIntensity * 4f),
              1f to Color.Black.copy(alpha = 0.1f * foldIntensity * 4f),
            )
          )
    )
    if (showEndFold) {
      Box(
        modifier =
          Modifier.align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(12.dp)
            .background(
              Brush.horizontalGradient(
                0f to Color.Transparent,
                0.34f to Color.White.copy(alpha = 0.22f),
                1f to Color.Black.copy(alpha = 0.08f * foldIntensity * 5.5f),
              )
            )
      )
      Box(
        modifier =
          Modifier.align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(1.dp)
            .background(Color.Black.copy(alpha = 0.04f))
      )
    }
    content()
  }
}

@Composable
private fun HomeOrnament(
  ornamentAsset: String,
  modifier: Modifier = Modifier,
  alpha: Float = 0.5f,
  tint: Color? = null,
) {
  Image(
    painter = archivePainterOrPlaceholder(ornamentAsset),
    contentDescription = null,
    modifier = modifier,
    contentScale = ContentScale.Fit,
    alpha = alpha,
    colorFilter = tint?.let(ColorFilter::tint),
  )
}

@Composable
private fun PlainCell(
  modifier: Modifier,
  color: Color,
  contentColor: Color,
  content: @Composable BoxScopeWithContentColor.() -> Unit,
) {
  val shape = RoundedCornerShape(6.dp)
  Box(modifier = modifier.clip(shape).background(color, shape)) {
    BoxScopeWithContentColor(this, contentColor).content()
  }
}

@Composable
private fun CutoutCell(
  modifier: Modifier,
  contentColor: Color,
  tileVisual: ArchiveTileVisual,
  mirrorBackground: Boolean = false,
  content: @Composable BoxScopeWithContentColor.() -> Unit,
) {
  Box(
    modifier =
      modifier
        .shadow(8.dp, ArchiveCutCornerShape, clip = false)
        .clip(ArchiveCutCornerShape)
        .background(HomePaper, ArchiveCutCornerShape)
        .border(1.2.dp, tileVisual.borderColor.copy(alpha = 0.86f), ArchiveCutCornerShape)
  ) {
    Image(
      painter = archivePainterOrPlaceholder(tileVisual.backgroundAsset),
      contentDescription = null,
      modifier =
        Modifier.matchParentSize().graphicsLayer(scaleX = if (mirrorBackground) -1f else 1f),
      contentScale = ContentScale.Crop,
    )
    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.18f)))
    BoxScopeWithContentColor(this, contentColor).content()
  }
}

private class BoxScopeWithContentColor(
  private val boxScope: BoxScope,
  val contentColor: Color,
) : BoxScope by boxScope

private fun dashboardFolders(
  topics: List<Topic>,
  itemsByTopic: Map<String, List<KnowledgeItem>>,
  searchQuery: String = "",
): List<DashboardFolder> {
  val now = Clock.System.now().toEpochMilliseconds()
  if (searchQuery.isNotBlank()) {
    return topics.mapIndexed { index, topic ->
      DashboardFolder(
        id = topic.id,
        title = topic.title,
        itemCount = itemsByTopic[topic.id]?.size ?: 0,
        updatedAtEpochMillis = demoFolderUpdatedAt(index, now),
        topic = topic,
      )
    }
  }
  return List(6) { index ->
    val topic = topics.getOrNull(index)
    DashboardFolder(
      id = topic?.id ?: "dashboard-folder-${index + 1}",
      title = topic?.title ?: FolderFallbackTitles[index],
      itemCount = topic?.let { itemsByTopic[it.id]?.size ?: 0 } ?: 0,
      updatedAtEpochMillis = demoFolderUpdatedAt(index, now),
      topic = topic,
    )
  }
}

private fun demoFolderUpdatedAt(index: Int, nowMillis: Long): Long {
  val offsets =
    listOf(
      36L * 60_000L,
      9L * 3_600_000L,
      2L * 86_400_000L,
      4L * 86_400_000L,
      6L * 86_400_000L,
      8L * 86_400_000L,
    )
  return nowMillis - offsets[index.coerceIn(0, offsets.lastIndex)]
}

internal fun friendlyTime(
  epochMillis: Long,
  nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
): String {
  val diff = nowMillis - epochMillis
  return when {
    diff < 0 -> "未来"
    diff < 60_000 -> "刚刚"
    diff < 3_600_000 -> "${(diff / 60_000).toInt().toChineseCount()}分钟前"
    diff < 86_400_000 -> "${(diff / 3_600_000).toInt().toChineseCount()}小时前"
    diff < 14L * 86_400_000L -> "一周前"
    diff < 2_592_000_000L -> "${(diff / 86_400_000).toInt().toChineseCount()}天前"
    else -> "很久以前"
  }
}

internal fun buildHighlightedText(
  text: String,
  query: String,
  highlightColor: Color,
  highlightBgColor: Color,
): androidx.compose.ui.text.AnnotatedString {
  val normalizedQuery = query.trim()
  if (normalizedQuery.isEmpty()) {
    return buildAnnotatedString { append(text) }
  }
  val lowerText = text.lowercase()
  val lowerQuery = normalizedQuery.lowercase()
  return buildAnnotatedString {
    var start = 0
    while (start < text.length) {
      val matchIndex = lowerText.indexOf(lowerQuery, start)
      if (matchIndex < 0) {
        append(text.substring(start))
        break
      }
      if (matchIndex > start) {
        append(text.substring(start, matchIndex))
      }
      withStyle(
        SpanStyle(
          fontWeight = FontWeight.Bold,
          background = highlightBgColor,
          color = highlightColor,
        )
      ) {
        append(text.substring(matchIndex, matchIndex + normalizedQuery.length))
      }
      start = matchIndex + normalizedQuery.length
    }
  }
}
