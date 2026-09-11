package com.lyihub.archiveassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lyihub.archiveassistant.ui.theme.ImperialBronze
import com.lyihub.archiveassistant.ui.theme.ImperialCinnabar
import com.lyihub.archiveassistant.ui.theme.ImperialIvory

@Composable
fun ArchiveDialog(
  title: String,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String? = null,
  actions: @Composable RowScope.() -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(8.dp)
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier =
        modifier
          .widthIn(min = 320.dp, max = 600.dp)
          .padding(horizontal = 22.dp)
          .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
          .clip(shape)
          .background(ImperialIvory, shape)
          .border(0.8.dp, ImperialBronze.copy(alpha = 0.28f), shape)
    ) {
      XuanPaperBackground(
        modifier = Modifier.matchParentSize(),
        textureAlpha = 0.5f,
        veilAlpha = 0.72f,
      ) {}
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall,
          color = Color.Black,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        content()
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
          verticalAlignment = Alignment.CenterVertically,
          content = actions,
        )
      }
    }
  }
}

@Composable
fun ArchiveDialogAction(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  destructive: Boolean = false,
  primary: Boolean = false,
  testTag: String? = null,
) {
  ArchiveDialogAction(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    destructive = destructive,
    primary = primary,
    testTag = testTag,
  ) { contentColor ->
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = if (enabled) contentColor else contentColor.copy(alpha = 0.42f),
      maxLines = 1,
    )
  }
}

@Composable
fun ArchiveDialogAction(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  destructive: Boolean = false,
  primary: Boolean = false,
  testTag: String? = null,
  content: @Composable RowScope.(Color) -> Unit,
) {
  val shape = RoundedCornerShape(4.dp)
  val contentColor =
    when {
      destructive -> ImperialCinnabar
      primary -> Color.Black
      else -> Color.Black.copy(alpha = 0.76f)
    }
  val backgroundColor =
    if (primary) {
      ImperialBronze.copy(alpha = 0.18f)
    } else {
      Color.White.copy(alpha = 0.42f)
    }
  Box(
    modifier =
      modifier
        .clip(shape)
        .background(backgroundColor, shape)
        .border(0.7.dp, contentColor.copy(alpha = 0.22f), shape)
        .clickable(enabled = enabled, onClick = onClick)
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      content(if (enabled) contentColor else contentColor.copy(alpha = 0.42f))
    }
  }
}
