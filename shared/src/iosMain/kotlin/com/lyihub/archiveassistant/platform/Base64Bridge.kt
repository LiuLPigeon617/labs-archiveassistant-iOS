package com.lyihub.archiveassistant.platform

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Base64 helpers used to move bytes across the Kotlin/Swift boundary.
 *
 * `ByteArray` maps to `KotlinByteArray` in Swift, which requires per-element accessors; Base64 is a
 * plain `String` on both sides and is therefore far less error-prone for the platform bridge.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun ByteArray.toBase64(): String = Base64.Default.encode(this)

@OptIn(ExperimentalEncodingApi::class)
internal fun String.fromBase64(): ByteArray? =
  runCatching { Base64.Default.decode(this) }.getOrNull()
