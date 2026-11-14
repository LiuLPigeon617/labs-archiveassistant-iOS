package com.lyihub.archiveassistant.domain

/**
 * Detects a web URL inside arbitrary user input (clipboard text, share sheet payload, etc).
 *
 * Migrated verbatim from the Android build — pure string logic with no platform dependency.
 */
data class DetectedUrl(val originalUrl: String, val fetchUrl: String, val isBare: Boolean)

object WebUrlDetector {

  fun detect(input: String): DetectedUrl? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null

    val match = URL_REGEX.find(trimmed) ?: return null
    val originalUrl = match.value.trimEnd('.', ',', ';', ')', ']', '}')
    if (originalUrl.isBlank()) return null

    val isBare = originalUrl.length == trimmed.length
    val fetchUrl =
      if (originalUrl.startsWith("http://", ignoreCase = true) ||
          originalUrl.startsWith("https://", ignoreCase = true)
      ) {
        originalUrl
      } else {
        "https://$originalUrl"
      }

    return DetectedUrl(originalUrl = originalUrl, fetchUrl = fetchUrl, isBare = isBare)
  }

  private val URL_REGEX =
    Regex(
      """(?:https?://|www\.)[^\s<>"'，。；！？、）】\]]+""",
      RegexOption.IGNORE_CASE,
    )
}
