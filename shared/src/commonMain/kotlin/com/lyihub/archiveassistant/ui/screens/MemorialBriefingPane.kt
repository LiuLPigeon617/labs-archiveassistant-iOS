package com.lyihub.archiveassistant.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.ui.archivePainterOrPlaceholder
import com.lyihub.archiveassistant.ui.components.XuanPaperBackground
import com.lyihub.archiveassistant.ui.theme.ImperialBronze
import com.lyihub.archiveassistant.ui.theme.ImperialIvory
import com.lyihub.archiveassistant.ui.theme.ImperialParchment
import com.lyihub.archiveassistant.ui.theme.ImperialUmber
import com.lyihub.archiveassistant.ui.theme.LocalImperialFonts
import com.lyihub.archiveassistant.util.toChineseCount
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val MemorialCoverAspect = 1.1f / 2f
private const val MemorialWheelItemCount = 20
private const val MemorialActiveSlotDegrees = 225f
private const val MemorialWheelDragDegreesPerPixel = -0.10f
private const val MemorialWheelActiveScale = 1.58f
private const val MemorialWheelFocusHalfRangeDegrees = 24f
private const val MemorialWheelDepartmentLabelVisibleStart = 0.8f
private const val MemorialWheelDepartmentLabelVisibleEnd = 0.965f
private const val MemorialWheelCoverSeed = 20260627
private const val MemorialWheelDuplicateGuard = 3
private const val MemorialWheelAutoAdvanceMillis = 3500L
private val MemorialInk = Color.Black

private data class BriefingSample(
  val title: String,
  val body: String,
  val departmentTitle: String,
  val departmentImageAsset: String,
)

private val BriefingSamples =
  listOf(
    BriefingSample(
      title = "端侧模型突破摘要",
      body = "多篇材料指向端侧推理与系统级 AI 能力更新，适合优先判断是否进入今日重点。",
      departmentTitle = "大模型架构研究",
      departmentImageAsset = "tsieina_department_pattern_9617",
    ),
    BriefingSample(
      title = "折叠屏交互线索",
      body = "新增一条适合视频展示的双屏协同链路，可用于解释朝堂视角与批阅流程的关系。",
      departmentTitle = "UX/UI 灵感板",
      departmentImageAsset = "tsieina_department_pattern_10412",
    ),
    BriefingSample(
      title = "素材归档提醒",
      body = "国风纹样、封面图与瀑布流插图已形成一组可复用素材，需要决定归档主题。",
      departmentTitle = "知识管理方法",
      departmentImageAsset = "tsieina_department_pattern_9610",
    ),
  )

@Composable
fun MemorialBriefingPane(
  pendingCount: Int,
  briefingItems: List<KnowledgeItem>,
  onOpenMemorialDemo: () -> Unit,
  modifier: Modifier = Modifier,
  showBackButton: Boolean = false,
  onBack: (() -> Unit)? = null,
) {
  var activeBriefIndex by remember { mutableIntStateOf(0) }
  var showEmptyState by remember { mutableStateOf(false) }
  val briefingSamples = remember(briefingItems) { briefingSamplesFor(briefingItems) }
  Box(
    modifier =
      modifier.fillMaxSize().background(ImperialIvory).clickable {
        if (showEmptyState) {
          showEmptyState = false
        } else {
          onOpenMemorialDemo()
        }
      }
  ) {
    if (showEmptyState) {
      EmptyMemorialBriefingState(modifier = Modifier.fillMaxSize())
    } else {
      MemorialCoverWheel(
        coverAssets = MemorialCoverAssets,
        briefingSamples = briefingSamples,
        pendingCount = pendingCount,
        onActiveIndexChanged = { activeBriefIndex = it },
        modifier = Modifier.fillMaxSize(),
      )
      BriefingCopy(
        activeIndex = activeBriefIndex,
        samples = briefingSamples,
        showBackButton = showBackButton,
        onBack = onBack,
        onTitleClick = { showEmptyState = true },
        modifier =
          Modifier.align(Alignment.TopStart)
            .padding(start = 24.dp, top = 56.dp, end = 24.dp)
            .fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun BackArrowGlyph(tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val centerY = size.height / 2f
    val strokeWidth = size.height * 0.11f
    val head = size.height * 0.26f
    drawLine(
      color = tint,
      start = Offset(size.width * 0.92f, centerY),
      end = Offset(size.width * 0.12f, centerY),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )
    drawPath(
      path =
        Path().apply {
          moveTo(size.width * 0.44f, centerY - head)
          lineTo(size.width * 0.12f, centerY)
          lineTo(size.width * 0.44f, centerY + head)
        },
      color = tint,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
  }
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
  val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

@Composable
private fun MemorialCoverWheel(
  coverAssets: List<String>,
  briefingSamples: List<BriefingSample>,
  pendingCount: Int,
  onActiveIndexChanged: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var wheelRotation by remember { mutableFloatStateOf(0f) }
  var autoAdvanceEnabled by remember { mutableStateOf(true) }
  val stepDegrees = 360f / MemorialWheelItemCount
  val shuffledCoverAssets =
    remember(coverAssets) {
      MemorialCoverSequence.wheelResources(
        coverResources = coverAssets,
        itemCount = MemorialWheelItemCount,
        duplicateGuard = MemorialWheelDuplicateGuard,
        seed = MemorialWheelCoverSeed,
      )
    }
  if (shuffledCoverAssets.isEmpty()) return
  fun advanceWheelByOneStep() {
    val snappedRotation = (wheelRotation / stepDegrees).roundToInt() * stepDegrees
    val nextRotation = snappedRotation - stepDegrees
    wheelRotation = nextRotation
    onActiveIndexChanged(activeWheelIndex(nextRotation, stepDegrees))
  }
  LaunchedEffect(autoAdvanceEnabled, stepDegrees) {
    if (autoAdvanceEnabled) {
      delay(500L)
      advanceWheelByOneStep()
      while (true) {
        delay(MemorialWheelAutoAdvanceMillis)
        advanceWheelByOneStep()
      }
    }
  }
  val animatedWheelRotation by
    animateFloatAsState(
      targetValue = wheelRotation,
      animationSpec =
        spring(
          dampingRatio = 0.82f,
          stiffness = 420f,
        ),
      label = "memorialWheelRotation",
    )
  BoxWithConstraints(
    modifier =
      modifier.pointerInput(Unit) {
        detectDragGestures(
          onDragStart = {
            autoAdvanceEnabled = false
          },
          onDrag = { change, dragAmount ->
            change.consume()
            val updatedRotation = wheelRotation + dragAmount.y * MemorialWheelDragDegreesPerPixel
            wheelRotation = updatedRotation
            onActiveIndexChanged(activeWheelIndex(updatedRotation, stepDegrees))
          },
          onDragEnd = {
            val snappedRotation = (wheelRotation / stepDegrees).roundToInt() * stepDegrees
            wheelRotation = snappedRotation
            onActiveIndexChanged(activeWheelIndex(snappedRotation, stepDegrees))
          },
          onDragCancel = {
            val snappedRotation = (wheelRotation / stepDegrees).roundToInt() * stepDegrees
            wheelRotation = snappedRotation
            onActiveIndexChanged(activeWheelIndex(snappedRotation, stepDegrees))
          },
        )
      }
  ) {
    val panelMin = min(maxWidth.value, maxHeight.value).dp
    val radius = panelMin * 0.66f
    val innerRadius = radius * 0.69f
    val wheelCenterX = maxWidth + 58.dp
    val centerY = maxHeight * 0.69f
    val cardWidth = 61.dp
    val pendingStampLines = pendingStampLines(pendingCount)
    val pendingStampHeight = 14.dp + 31.dp * pendingStampLines.size
    val pendingStampWidth = 48.dp
    val renderedWheelRotation = normalizedDegrees(animatedWheelRotation)
    val startDegrees = MemorialActiveSlotDegrees + renderedWheelRotation

    MemorialWheelInnerDisc(
      centerX = wheelCenterX,
      centerY = centerY,
      radius = innerRadius,
      contentOffsetX = -innerRadius * 0.63f,
      modifier = Modifier.fillMaxSize(),
    )
    PendingVerticalNote(
      lines = pendingStampLines,
      modifier =
        Modifier.offset(x = 20.dp, y = centerY - pendingStampHeight / 2f)
          .width(pendingStampWidth)
          .height(pendingStampHeight),
    )
    val wheelItems =
      remember(startDegrees) {
        val seamIndex =
          floorMod(((-startDegrees + 45f) / stepDegrees).roundToInt(), MemorialWheelItemCount)
        List(MemorialWheelItemCount) { index ->
            val degrees = startDegrees + index * stepDegrees
            WheelItemPlacement(
              index = index,
              drawOrder = floorMod(index - seamIndex, MemorialWheelItemCount),
              degrees = degrees,
              coverAsset = shuffledCoverAssets[index],
              focus = wheelItemFocus(degrees),
            )
          }
          .sortedBy { it.drawOrder }
      }
    wheelItems.forEach { item ->
      key(item.index) {
        MemorialWheelCover(
          coverAsset = item.coverAsset,
          degrees = item.degrees,
          centerX = wheelCenterX,
          centerY = centerY,
          radius = radius,
          width = cardWidth,
          focus = item.focus,
          departmentTitle =
            briefingSamples[floorMod(item.index, briefingSamples.size)].departmentTitle,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}

@Composable
private fun PendingVerticalNote(
  lines: List<String>,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = archivePainterOrPlaceholder("pending_note_stamp"),
      contentDescription = null,
      modifier = Modifier.matchParentSize(),
      contentScale = ContentScale.FillBounds,
    )
    Text(
      text = lines.joinToString("\n"),
      style = MaterialTheme.typography.headlineLarge.copy(fontFamily = LocalImperialFonts.current.title),
      color = Color.White,
      textAlign = TextAlign.Center,
      lineHeight = 31.sp,
      modifier = Modifier.align(Alignment.Center).width(31.dp),
    )
  }
}

@Composable
private fun EmptyMemorialBriefingState(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    PendingVerticalNote(
      lines = "暂无奏章待批".map { it.toString() },
      modifier = Modifier.width(64.dp).height(214.dp),
    )
  }
}

private fun pendingStampLines(pendingCount: Int): List<String> {
  val normalizedCount = pendingCount.coerceAtLeast(0)
  return "待批${normalizedCount.toChineseCount()}封".map { it.toString() }
}

private data class WheelItemPlacement(
  val index: Int,
  val drawOrder: Int,
  val degrees: Float,
  val coverAsset: String,
  val focus: Float,
)

private fun wheelItemFocus(degrees: Float): Float {
  val distance = angularDistanceDegrees(degrees, MemorialActiveSlotDegrees)
  return (1f - distance / MemorialWheelFocusHalfRangeDegrees).coerceIn(0f, 1f)
}

private fun floorMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus

private fun activeWheelIndex(rotation: Float, stepDegrees: Float): Int {
  return floorMod((-rotation / stepDegrees).roundToInt(), MemorialWheelItemCount)
}

private fun normalizedDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

private fun normalizeWheelRotation(rotation: Float, stepDegrees: Float): Float {
  val index = activeWheelIndex(rotation, stepDegrees)
  return -index * stepDegrees
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
  return start + (stop - start) * fraction.coerceIn(0f, 1f)
}

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp {
  return start + (stop - start) * fraction.coerceIn(0f, 1f)
}

private fun angularDistanceDegrees(a: Float, b: Float): Float {
  val diff = ((a - b + 540f) % 360f) - 180f
  return abs(diff)
}

@Composable
private fun MemorialWheelInnerDisc(
  centerX: Dp,
  centerY: Dp,
  radius: Dp,
  contentOffsetX: Dp,
  modifier: Modifier = Modifier,
) {
  val diameter = radius * 2f
  val iconSize = 68.dp
  Box(modifier = modifier) {
    Box(
      modifier =
        Modifier.offset(x = centerX - radius, y = centerY - radius)
          .size(diameter)
          .background(ImperialParchment.copy(alpha = 0.42f), CircleShape)
          .border(1.dp, ImperialBronze.copy(alpha = 0.22f), CircleShape)
    ) {
      Column(
        modifier = Modifier.align(Alignment.Center).offset(x = contentOffsetX),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Image(
          painter = archivePainterOrPlaceholder("memorial_touch_book"),
          contentDescription = null,
          modifier = Modifier.size(iconSize),
          alpha = 0.46f,
          colorFilter = ColorFilter.tint(MemorialInk),
        )
        Text(
          text = "轻触批阅",
          style = MaterialTheme.typography.titleMedium,
          color = MemorialInk,
          fontWeight = FontWeight.Normal,
          textAlign = TextAlign.Center,
        )
        Text(
          text = "上下拨动奏章轮",
          style = MaterialTheme.typography.bodySmall,
          color = MemorialInk.copy(alpha = 0.78f),
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun MemorialWheelCover(
  coverAsset: String,
  degrees: Float,
  centerX: Dp,
  centerY: Dp,
  radius: Dp,
  width: Dp,
  focus: Float,
  departmentTitle: String,
  modifier: Modifier = Modifier,
) {
  val coverScale = 1f + (MemorialWheelActiveScale - 1f) * focus
  val borderAlpha = lerpFloat(0.46f, 0.7f, focus)
  val radians = degrees * (PI / 180.0)
  val height = width / MemorialCoverAspect
  val x = centerX + radius * cos(radians).toFloat() - width / 2f
  val y = centerY + radius * sin(radians).toFloat() - height * 0.75f
  val departmentLabelAlpha = departmentLabelAlpha(focus)
  val cornerRadius = 2.dp
  val frameOutset = 4.dp
  val frameWidth = width + frameOutset * 2f
  val frameHeight = height + frameOutset * 2f
  val frameCornerRadius = 4.dp
  val frameStrokeWidth = 1.2.dp
  val transformPivotY = ((height.value * 0.75f) + frameOutset.value) / frameHeight.value
  Box(modifier = modifier) {
    Box(
      modifier =
        Modifier.offset(x = x - frameOutset, y = y - frameOutset)
          .size(frameWidth, frameHeight)
          .graphicsLayer(
            rotationZ = degrees + 90f,
            transformOrigin = TransformOrigin(0.5f, transformPivotY),
            scaleX = coverScale,
            scaleY = coverScale,
          )
          .shadow(8.dp, RoundedCornerShape(frameCornerRadius), clip = false)
          .background(
            Color.White,
            RoundedCornerShape(frameCornerRadius),
          )
          .border(
            width = frameStrokeWidth,
            color = ImperialBronze.copy(alpha = borderAlpha),
            shape = RoundedCornerShape(frameCornerRadius),
          )
          .padding(frameOutset)
    ) {
      Text(
        text = departmentTitle,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
        color = MemorialInk.copy(alpha = 0.82f),
        fontWeight = FontWeight.Normal,
        modifier =
          Modifier.align(Alignment.TopCenter)
            .offset(y = (-21).dp)
            .requiredWidth(frameWidth * 3.1f)
            .graphicsLayer { alpha = departmentLabelAlpha },
        textAlign = TextAlign.Center,
      )
      Box(
        modifier =
          Modifier.fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(ImperialParchment)
      ) {
        Image(
          painter = archivePainterOrPlaceholder(coverAsset),
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.verticalGradient(
                  listOf(
                    Color.White.copy(alpha = 0.05f),
                    ImperialUmber.copy(alpha = lerpFloat(0.13f, 0.08f, focus)),
                  )
                )
              )
        )
        MemorialCoverLabel(
          focus = focus,
          modifier = Modifier.align(Alignment.Center),
        )
      }
      Box(
        modifier =
          Modifier.matchParentSize()
            .border(
              width = 0.75.dp,
              color = ImperialIvory.copy(alpha = 0.15f),
              shape = RoundedCornerShape(frameCornerRadius),
            )
      )
    }
  }
}

internal fun departmentLabelAlpha(focus: Float): Float {
  return smoothStep(
    MemorialWheelDepartmentLabelVisibleStart,
    MemorialWheelDepartmentLabelVisibleEnd,
    focus,
  )
}

@Composable
private fun MemorialCoverLabel(
  focus: Float,
  modifier: Modifier = Modifier,
) {
  val labelWidth = 0.49f
  val labelHeight = 0.57f
  val cornerSizeRatio = 0.22f
  val cornerAlpha = lerpFloat(0.72f, 0.94f, focus)
  BoxWithConstraints(modifier = modifier) {
    val labelBoxWidth = maxWidth * labelWidth
    val labelBoxHeight = labelBoxWidth / (MemorialCoverAspect / labelHeight * labelWidth)
    val cornerSize = minOf(labelBoxWidth, labelBoxHeight) * cornerSizeRatio
    Box(
      modifier =
        Modifier.align(Alignment.Center)
          .width(labelBoxWidth)
          .aspectRatio(MemorialCoverAspect / labelHeight * labelWidth)
          .clip(RoundedCornerShape(2.dp))
          .background(ImperialIvory.copy(alpha = lerpFloat(0.9f, 0.96f, focus)))
          .border(
            1.dp,
            ImperialBronze.copy(alpha = lerpFloat(0.52f, 0.76f, focus)),
            RoundedCornerShape(2.dp),
          )
          .padding(lerpDp(4.dp, 5.dp, focus))
    ) {
      XuanPaperBackground(
        modifier = Modifier.matchParentSize(),
        textureAlpha = lerpFloat(0.3f, 0.42f, focus),
        veilAlpha = 0.42f,
      ) {}
      Box(
        modifier =
          Modifier.fillMaxSize()
            .border(
              0.8.dp,
              ImperialBronze.copy(alpha = lerpFloat(0.46f, 0.66f, focus)),
              RoundedCornerShape(1.dp),
            )
      )
      Image(
        painter = archivePainterOrPlaceholder("memorial_cover_corner"),
        contentDescription = null,
        modifier = Modifier.align(Alignment.TopStart).size(cornerSize),
        alpha = cornerAlpha,
      )
      Image(
        painter = archivePainterOrPlaceholder("memorial_cover_corner"),
        contentDescription = null,
        modifier = Modifier.align(Alignment.TopEnd).size(cornerSize).graphicsLayer(rotationZ = 90f),
        alpha = cornerAlpha,
      )
      Image(
        painter = archivePainterOrPlaceholder("memorial_cover_corner"),
        contentDescription = null,
        modifier =
          Modifier.align(Alignment.BottomEnd).size(cornerSize).graphicsLayer(rotationZ = 180f),
        alpha = cornerAlpha,
      )
      Image(
        painter = archivePainterOrPlaceholder("memorial_cover_corner"),
        contentDescription = null,
        modifier =
          Modifier.align(Alignment.BottomStart).size(cornerSize).graphicsLayer(rotationZ = 270f),
        alpha = cornerAlpha,
      )
      Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "奏\n章",
          style = MaterialTheme.typography.titleLarge,
          color = MemorialInk,
          fontWeight = FontWeight.Normal,
          textAlign = TextAlign.Center,
          lineHeight = 25.sp,
        )
      }
    }
  }
}

@Composable
private fun BriefingCopy(
  activeIndex: Int,
  samples: List<BriefingSample>,
  showBackButton: Boolean,
  onBack: (() -> Unit)?,
  onTitleClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val sample = samples[floorMod(activeIndex, samples.size)]
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    PaneHeroHeader(
      title = "奏章",
      description = "轻触此页批阅奏章，准、驳、留中皆可一笔批下",
      showBackButton = showBackButton,
      backIcon = { BackArrowGlyph(tint = MemorialInk, modifier = Modifier.size(20.dp)) },
      onBack = onBack,
      onTitleClick = onTitleClick,
      modifier = Modifier.fillMaxWidth(),
    )
    MemorialBriefCard(
      sample = sample,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun MemorialBriefCard(
  sample: BriefingSample,
  modifier: Modifier = Modifier,
) {
  MemorialFramedPaperPanel(
    modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
    cornerSize = 18.dp,
    contentPadding = PaddingValues(horizontal = 17.dp, vertical = 9.dp),
  ) {
    AnimatedContent(
      targetState = sample,
      transitionSpec = {
        fadeIn(animationSpec = tween(240)) togetherWith fadeOut(animationSpec = tween(180))
      },
      label = "memorialBriefCardContent",
      modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(),
    ) { activeSample ->
      Box(modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
        Image(
          painter = archivePainterOrPlaceholder(activeSample.departmentImageAsset),
          contentDescription = null,
          modifier = Modifier.align(Alignment.CenterEnd).offset(x = 12.dp).size(72.dp),
          contentScale = ContentScale.Fit,
          alpha = 0.24f,
        )
        Column(
          modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = activeSample.title,
            style = MaterialTheme.typography.titleMedium,
            color = MemorialInk,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = activeSample.body,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalImperialFonts.current.display),
            color = MemorialInk.copy(alpha = 0.78f),
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

private fun briefingSamplesFor(items: List<KnowledgeItem>): List<BriefingSample> {
  val samples = orderedBriefingItems(items).map(::briefingSampleFor)
  return samples.ifEmpty { BriefingSamples }
}

internal fun orderedBriefingItems(items: List<KnowledgeItem>): List<KnowledgeItem> {
  val grouped = SampleTopicIds.associateWith { topicId ->
    items.filter { item ->
      item.topicId == topicId && item.title.isNotBlank() && item.summary.isNotBlank()
    }
  }
  val maxCount = grouped.values.maxOfOrNull { it.size } ?: 0
  return buildList {
    repeat(maxCount) { roundIndex ->
      SampleTopicIds.forEach { topicId ->
        grouped[topicId]?.getOrNull(roundIndex)?.let(::add)
      }
    }
  }
}

private fun briefingSampleFor(item: KnowledgeItem): BriefingSample {
  return BriefingSample(
    title = item.title,
    body = item.summary,
    departmentTitle = folderTitleForTopicId(item.topicId),
    departmentImageAsset = folderVisualForTopicId(item.topicId).imageAsset,
  )
}

@Composable
private fun MemorialFramedPaperPanel(
  modifier: Modifier = Modifier,
  cornerSize: Dp,
  contentPadding: PaddingValues,
  content: @Composable BoxScope.() -> Unit,
) {
  val shape = RoundedCornerShape(3.dp)
  Box(
    modifier =
      modifier
        .shadow(9.dp, shape, clip = false)
        .clip(shape)
        .background(ImperialIvory.copy(alpha = 0.96f), shape)
        .border(1.dp, ImperialBronze.copy(alpha = 0.62f), shape)
  ) {
    XuanPaperBackground(
      modifier = Modifier.matchParentSize(),
      textureAlpha = 0.4f,
      veilAlpha = 0.38f,
    ) {}
    Box(
      modifier =
        Modifier.matchParentSize()
          .border(0.8.dp, ImperialBronze.copy(alpha = 0.42f), RoundedCornerShape(1.dp))
    )
    FramedPaperCorner(
      alignment = Alignment.TopStart,
      rotation = 0f,
      size = cornerSize,
    )
    FramedPaperCorner(
      alignment = Alignment.TopEnd,
      rotation = 90f,
      size = cornerSize,
    )
    FramedPaperCorner(
      alignment = Alignment.BottomEnd,
      rotation = 180f,
      size = cornerSize,
    )
    FramedPaperCorner(
      alignment = Alignment.BottomStart,
      rotation = 270f,
      size = cornerSize,
    )
    Box(
      modifier = Modifier.matchParentSize().padding(contentPadding),
      content = content,
    )
  }
}

@Composable
private fun BoxScope.FramedPaperCorner(
  alignment: Alignment,
  rotation: Float,
  size: Dp,
) {
  Image(
    painter = archivePainterOrPlaceholder("memorial_cover_corner"),
    contentDescription = null,
    modifier = Modifier.align(alignment).size(size).graphicsLayer(rotationZ = rotation),
    alpha = 0.62f,
    colorFilter = ColorFilter.tint(ImperialBronze.copy(alpha = 0.76f)),
  )
}
