package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic

/**
 * Namespace kept from the Android build so the state store's `when` branches over
 * `AppDataPreferences.DecodeResult` continue to read the same way.
 */
object AppDataPreferences {
  sealed interface DecodeResult<out T> {
    data class Valid<T>(val value: T) : DecodeResult<T>

    data object Missing : DecodeResult<Nothing>

    data class Corrupt(val rawJson: String) : DecodeResult<Nothing>
  }
}

/** Aliases so `AppDataSnapshot` and `AppDataPreferences.DecodeResult` are the same type. */
typealias TopicDecodeResult = AppDataPreferences.DecodeResult<List<Topic>>

typealias ItemDecodeResult = AppDataPreferences.DecodeResult<List<KnowledgeItem>>
