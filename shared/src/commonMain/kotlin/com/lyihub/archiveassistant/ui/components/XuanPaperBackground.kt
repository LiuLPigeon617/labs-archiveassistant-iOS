package com.lyihub.archiveassistant.ui.components
import com.lyihub.archiveassistant.ui.archivePainter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.lyihub.archiveassistant.ui.theme.ImperialIvory
import com.lyihub.archiveassistant.ui.theme.ImperialParchment
import com.lyihub.archiveassistant.ui.theme.ImperialUmber

@Composable
fun XuanPaperBackground(
  modifier: Modifier = Modifier,
  textureAlpha: Float = 0.34f,
  veilAlpha: Float = 1f,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier = modifier.fillMaxSize().background(ImperialIvory)) {
    // The paper texture is optional: on targets where the bundled asset is unavailable the gradient
    // layers below still carry the same visual identity.
    archivePainter("memorial_xuan_paper")?.let { texture ->
      Image(
        painter = texture,
        contentDescription = null,
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.Crop,
        alpha = textureAlpha,
      )
    }
    Box(
      modifier =
        Modifier.matchParentSize()
          .background(
            Brush.radialGradient(
              colors =
                listOf(
                  ImperialIvory.copy(alpha = 0.1f * veilAlpha),
                  ImperialParchment.copy(alpha = 0.34f * veilAlpha),
                  ImperialIvory.copy(alpha = 0.72f * veilAlpha),
                ),
              center = Offset.Infinite,
              radius = 980f,
            )
          )
    )
    Box(
      modifier =
        Modifier.matchParentSize()
          .background(
            Brush.verticalGradient(
              listOf(
                ImperialIvory.copy(alpha = 0.1f * veilAlpha),
                ImperialIvory.copy(alpha = 0.26f * veilAlpha),
                ImperialUmber.copy(alpha = 0.035f * veilAlpha),
              )
            )
          )
    )
    content()
  }
}
