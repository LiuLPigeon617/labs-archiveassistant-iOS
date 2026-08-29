package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.FetchedWebContext

/**
 * Parses HTML into the fields the summarizer prompt needs.
 *
 * Implemented per platform because no single HTML parser spans Android, iOS and JVM: Android keeps
 * Jsoup (already bundled), iOS uses Ksoup, the JVM verification target uses Ksoup as well.
 */
expect fun extractWebPageContent(
  originalUrl: String,
  fetchUrl: String,
  resolvedUrl: String,
  html: String,
  contentType: String,
): FetchedWebPageContent

/** Materializes a fetched page into the domain-owned context the prompt consumes. */
fun FetchedWebPageContent.toContext(): FetchedWebContext =
  FetchedWebContext(
    originalUrl = originalUrl,
    title = title,
    description = description,
    bodyText = bodyText,
  )

internal const val MAX_BODY_TEXT_LENGTH = 12_000

internal fun normalizeWhitespace(value: String): String = value.trim().replace(WHITESPACE_REGEX, " ")

internal fun firstNonBlank(vararg values: String?): String =
  values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

internal val WHITESPACE_REGEX = Regex("""\s+""")
