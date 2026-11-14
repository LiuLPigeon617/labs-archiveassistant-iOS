package com.lyihub.archiveassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ArchiveChip(
  label: String,
  selected: Boolean,
  accentColor: Color,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  height: Dp = 20.dp,
  shape: Shape = MaterialTheme.shapes.small,
  textStyle: TextStyle = MaterialTheme.typography.labelSmall,
  contentPadding: PaddingValues = PaddingValues(horizontal = 7.dp),
  borderWidth: Dp = 0.8.dp,
  selectedBackgroundAlpha: Float = 0.13f,
  unselectedBackgroundColor: Color = Color(0xFFE3E0D8).copy(alpha = 0.5f),
  selectedBorderAlpha: Float = 0.72f,
  unselectedBorderColor: Color = Color.Black.copy(alpha = 0.16f),
  selectedTextColor: Color = Color.Black.copy(alpha = 0.82f),
  unselectedTextColor: Color = Color.Black.copy(alpha = 0.48f),
  onClick: (() -> Unit)? = null,
) {
  val backgroundColor =
    if (selected) accentColor.copy(alpha = selectedBackgroundAlpha) else unselectedBackgroundColor
  val borderColor =
    if (selected) accentColor.copy(alpha = selectedBorderAlpha) else unselectedBorderColor
  val textColor = if (selected) selectedTextColor else unselectedTextColor
  Box(
    modifier =
      modifier
        .height(height)
        .clip(shape)
        .background(backgroundColor, shape)
        .border(BorderStroke(borderWidth, borderColor), shape)
        .then(
          if (onClick != null) {
            Modifier.clickable(enabled = enabled, onClick = onClick)
          } else {
            Modifier
          }
        ),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = textStyle,
      color = if (enabled) textColor else textColor.copy(alpha = 0.42f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(contentPadding),
    )
  }
}
