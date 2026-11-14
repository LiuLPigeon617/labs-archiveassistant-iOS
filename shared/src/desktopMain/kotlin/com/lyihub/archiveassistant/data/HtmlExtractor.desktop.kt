package com.lyihub.archiveassistant.data

import org.jsoup.Jsoup

/**
 * The jvm target exists for compile verification and unit tests on Windows/Linux, not for shipping,
 * so it reuses Jsoup rather than pulling in the multiplatform port (Ksoup) that only iOS needs.
 */
actual fun extractWebPageContent(
  originalUrl: String,
  fetchUrl: String,
  resolvedUrl: String,
  html: String,
  contentType: String,
): FetchedWebPageContent {
  val document = Jsoup.parse(html, resolvedUrl)
  document.select("script,style,noscript,nav,header,footer,aside,form").remove()
  val bodyText =
    normalizeWhitespace(document.body()?.text().orEmpty()).take(MAX_BODY_TEXT_LENGTH)

  return FetchedWebPageContent(
    originalUrl = originalUrl,
    fetchUrl = fetchUrl,
    resolvedUrl = resolvedUrl,
    title =
      firstNonBlank(
        document.selectFirst("meta[property=og:title]")?.attr("content"),
        document.selectFirst("meta[name=twitter:title]")?.attr("content"),
        document.title(),
        document.selectFirst("h1")?.text(),
      ),
    description =
      firstNonBlank(
        document.selectFirst("meta[name=description]")?.attr("content"),
        document.selectFirst("meta[property=og:description]")?.attr("content"),
        document.selectFirst("meta[name=twitter:description]")?.attr("content"),
      ),
    bodyText = bodyText,
    contentType = contentType,
  )
}
