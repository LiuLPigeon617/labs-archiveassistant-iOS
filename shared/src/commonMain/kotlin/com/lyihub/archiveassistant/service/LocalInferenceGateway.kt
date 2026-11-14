package com.lyihub.archiveassistant.service

import com.lyihub.archiveassistant.domain.InferenceBackend
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.LocalLlmEngine
import com.lyihub.archiveassistant.domain.LocalModelInfo
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.domain.SmartSummarizeRequest
import com.lyihub.archiveassistant.domain.SmartSummarizeResult
import com.lyihub.archiveassistant.domain.Topic
import kotlinx.coroutines.flow.Flow

/**
 * Gateway to on-device inference.
 *
 * The Android implementation binds a foreground `Service` over a `Binder`; iOS has no equivalent,
 * so each platform supplies its own mechanism while sharing this contract.
 */
interface LocalInferenceGateway {
  val serviceState: Flow<LocalModelState>

  fun bind()

  fun unbind()

  fun getEngine(): LocalLlmEngine?

  suspend fun summarize(
    request: SmartSummarizeRequest,
    topics: List<Topic>,
    existingItems: List<KnowledgeItem>,
  ): SmartSummarizeResult

  fun startModel(model: LocalModelInfo, backend: InferenceBackend)

  fun stopModel()
}