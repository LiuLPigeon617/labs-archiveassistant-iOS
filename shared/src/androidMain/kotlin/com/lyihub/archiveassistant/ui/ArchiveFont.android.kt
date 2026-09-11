package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@Composable
actual fun archiveFontFamily(assetName: String): FontFamily? {
  val context = LocalContext.current
  val id = context.resources.getIdentifier(assetName, "font", context.packageName)
  return if (id == 0) null else FontFamily(Font(id, FontWeight.Normal))
}
