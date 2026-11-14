package com.lyihub.archiveassistant.platform

import com.lyihub.archiveassistant.data.IosContentSource

/** Swift-side existence check decides whether a persisted source string is still readable. */
actual fun resolveContentSource(key: String): ContentSource? =
  if (IosNativeBridge.callExists(key)) IosContentSource(key) else null
