package com.lyihub.archiveassistant.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun provideHttpClient(): HttpClient =
  HttpClient(OkHttp) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    install(HttpTimeout) {
      connectTimeoutMillis = 20_000
      requestTimeoutMillis = 60_000
      socketTimeoutMillis = 60_000
    }
  }

actual fun defaultRemoteAiTransport(): RemoteAiTransport = KtorRemoteAiTransport()
