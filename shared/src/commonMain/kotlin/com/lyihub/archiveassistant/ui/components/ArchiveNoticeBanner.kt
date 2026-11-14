package com.lyihub.archiveassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lyihub.archiveassistant.ui.theme.ImperialBronze
import com.lyihub.archiveassistant.ui.theme.ImperialIvory

@Composable
fun ArchiveNoticeBanner(
  message: String?,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = !message.isNullOrBlank(),
    enter = fadeIn() + slideInVertically { -it / 2 },
    exit = fadeOut() + slideOutVertically { -it / 2 },
    modifier = modifier,
  ) {
    val shape = RoundedCornerShape(6.dp)
    Box(
      modifier =
        Modifier.widthIn(max = 360.dp)
          .shadow(10.dp, shape, clip = false)
          .clip(shape)
          .background(ImperialIvory.copy(alpha = 0.96f), shape)
          .border(0.8.dp, ImperialBronze.copy(alpha = 0.32f), shape),
      contentAlignment = Alignment.Center,
    ) {
      XuanPaperBackground(
        modifier = Modifier.matchParentSize(),
        textureAlpha = 0.42f,
        veilAlpha = 0.7f,
      ) {}
      Text(
        text = message.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Black.copy(alpha = 0.82f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      )
    }
  }
}
