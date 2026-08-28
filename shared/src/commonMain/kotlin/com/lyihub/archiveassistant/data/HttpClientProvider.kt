package com.lyihub.archiveassistant.data

import io.ktor.client.HttpClient

/**
 * Provides the platform HTTP engine for Ktor.
 *
 * Android -> OkHttp, iOS -> Darwin (NSURLSession), JVM -> OkHttp. Declared as expect/actual rather
 * than letting Ktor auto-select so each platform gets its native TLS stack and proxy behavior.
 */
expect fun provideHttpClient(): HttpClient
