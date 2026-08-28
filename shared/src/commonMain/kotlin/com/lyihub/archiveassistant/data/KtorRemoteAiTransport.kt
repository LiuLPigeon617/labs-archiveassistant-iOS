package com.lyihub.archiveassistant.data

import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * Ktor-backed [RemoteAiTransport].
 *
 * Replaces the Android `HttpURLConnection` implementation, which is JVM-only.
 */
class KtorRemoteAiTransport(private val client: HttpClient = provideHttpClient()) :
  RemoteAiTransport {
  override suspend fun send(request: RemoteAiRequest): RemoteAiResponse {
    val response =
      client.request(request.endpoint) {
        method = HttpMethod.parse(request.method)
        request.headers.forEach { (key, value) -> headers.append(key, value) }
        request.body?.let {
          contentType(ContentType.Application.Json)
          setBody(it)
        }
      }
    return RemoteAiResponse(code = response.status.value, body = response.bodyAsText())
  }
}

/**
 * Platform default transport. Declared as expect/actual so common code never references a concrete
 * engine; each platform supplies its own (OkHttp on Android/JVM, Darwin on iOS).
 */
expect fun defaultRemoteAiTransport(): RemoteAiTransport
