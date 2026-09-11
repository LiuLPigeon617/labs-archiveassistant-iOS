package com.lyihub.archiveassistant.platform

import com.lyihub.archiveassistant.data.JvmContentSource
import java.io.File

actual fun resolveContentSource(key: String): ContentSource? =
  if (File(key).exists()) JvmContentSource(key) else null
