package com.lyihub.archiveassistant.platform

import com.lyihub.archiveassistant.data.AndroidContentSource

actual fun resolveContentSource(key: String): ContentSource? = runCatching {
  AndroidContentSource(key)
}.getOrNull()
