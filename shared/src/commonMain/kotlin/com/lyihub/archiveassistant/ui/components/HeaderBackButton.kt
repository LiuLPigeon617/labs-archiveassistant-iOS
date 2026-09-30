package com.lyihub.archiveassistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A back-arrow affordance drawn locally.
 *
 * `:shared` deliberately does not depend on `material-icons-extended`, and the iOS chrome uses SF
 * Symbols. Panes therefore either receive their icon as an injected slot (`backIcon`, `settingsIcon`
 * in `HomePane`) or draw it themselves — this follows the local-drawing route used by
 * `CloseSearchGlyph`, so a migrated pane is not left without a working back button.
 */
@Composable
fun HeaderBackButton(
  contentDescription: String,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val color = MaterialTheme.colorScheme.onSurfaceVariant
  IconButton(onClick = onClick, modifier = modifier.testTag(testTag)) {
    Canvas(modifier = Modifier.size(20.dp).semantics { this.contentDescription = contentDescription }) {
      val centerY = size.height / 2f
      val strokeWidth = size.height * 0.11f
      val head = size.height * 0.26f

      drawLine(
        color = color,
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
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
      )
    }
  }
}
