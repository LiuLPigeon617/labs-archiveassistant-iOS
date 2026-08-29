package com.lyihub.archiveassistant.platform

import com.lyihub.archiveassistant.data.IosContentSource
import platform.Foundation.NSFileManager

actual fun resolveContentSource(key: String): ContentSource? =
  if (NSFileManager.defaultManager.fileExistsAtPath(key)) IosContentSource(key) else null
