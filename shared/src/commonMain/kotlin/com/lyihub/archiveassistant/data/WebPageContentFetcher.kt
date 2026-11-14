package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.FetchedWebContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import io.ktor.http.isSuccess

interface WebPageContentFetcher {
  suspend fun fetch(originalUrl: String, fetchUrl: String): WebPageContentFetchResult
}

sealed interface WebPageContentFetchResult {
  data class Success(val content: FetchedWebPageContent) : WebPageContentFetchResult

  data class Failure(val message: String) : WebPageContentFetchResult
}

data class FetchedWebPageContent(
  val originalUrl: String,
  val fetchUrl: String,
  val resolvedUrl: String,
  val title: String,
  val description: String,
  val bodyText: String,
  val contentType: String,
)

/** Transport seam kept so tests can supply a fake without network access. */
interface WebPageTransport {
  suspend fun fetch(url: String): WebPageResponse
}

data class WebPageResponse(
  val code: Int,
  val contentType: String?,
  val body: String,
  val resolvedUrl: String,
)

class DefaultWebPageContentFetcher(
  private val transport: WebPageTransport = KtorWebPageTransport(),
) : WebPageContentFetcher {
  override suspend fun fetch(
    originalUrl: String,
    fetchUrl: String,
  ): WebPageContentFetchResult {
    val response =
      runCatching { transport.fetch(fetchUrl) }.getOrElse { error ->
        return WebPageContentFetchResult.Failure(mapWebPageFetchError(error))
      }

    if (!response.code.let { it in 200..299 }) {
      return WebPageContentFetchResult.Failure("网页抓取失败：HTTP ${response.code}")
    }

    val contentType = response.contentType?.trim().orEmpty()
    if (contentType.isBlank() || !contentType.contains("text/html", ignoreCase = true)) {
      return WebPageContentFetchResult.Failure("不支持的网页内容类型")
    }

    val html = response.body.trim()
    if (html.isEmpty()) {
      return WebPageContentFetchResult.Failure("网页内容为空")
    }

    val resolvedUrl = response.resolvedUrl.ifBlank { fetchUrl }
    return WebPageContentFetchResult.Success(
      extractWebPageContent(originalUrl, fetchUrl, resolvedUrl, html, contentType)
    )
  }
}

class KtorWebPageTransport(private val client: HttpClient = provideHttpClient()) :
  WebPageTransport {
  override suspend fun fetch(url: String): WebPageResponse {
    val response = client.get(url) { headers.append("User-Agent", USER_AGENT) }
    return WebPageResponse(
      code = response.status.value,
      contentType = response.contentType()?.toString(),
      body = response.bodyAsText(),
      // Intentionally the requested URL rather than a post-redirect URL: the summarizer prompt only
      // needs it for attribution, and reading the executed request URL is not part of Ktor's stable
      // response API across the engines used here (Darwin, OkHttp, CIO).
      resolvedUrl = url,
    )
  }

  private companion object {
    const val USER_AGENT = "Mozilla/5.0 JuHeShiYi/1.0"
  }
}

private fun mapWebPageFetchError(error: Throwable): String = "网页抓取失败，请检查链接或网络"
