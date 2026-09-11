package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
actual fun archivePainter(assetName: String): Painter? {
  val context = androidx.compose.ui.platform.LocalContext.current
  val id = context.resources.getIdentifier(assetName, "drawable", context.packageName)
  return if (id == 0) null else painterResource(id)
}
