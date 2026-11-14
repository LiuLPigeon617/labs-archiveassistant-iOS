package com.lyihub.archiveassistant.domain

import kotlinx.coroutines.flow.Flow

interface LocalLlmEngine {
  val state: Flow<LocalModelState>

  suspend fun initialize(modelPath: String, backend: InferenceBackend): Result<InferenceBackend>

  suspend fun generate(prompt: String, maxTokens: Int = 512): Result<String>

  suspend fun benchmark(promptTokens: Int = 128, generateTokens: Int = 128): Result<BenchResult>

  suspend fun release(): Result<Unit>
}
